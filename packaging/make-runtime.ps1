<#
.SYNOPSIS
    Génère un runtime Java MINIMAL (jlink) pour le paquet desktop, au lieu du runtime JDK complet
    bundlé par défaut par jpackage (~118 Mo → ~62 Mo).

.DESCRIPTION
    1. jdeps sur le fat jar (éclaté) → base de modules JDK réellement référencés.
    2. Union avec un set « de sûreté » : modules chargés par RÉFLEXION que jdeps ne voit pas
       (JNDI, JDBC, JMX, crypto EC/PKCS11 pour TLS, locales complètes FR/AR, charsets, zipfs).
    3. jlink → image runtime compressée et allégée.

    Le set de sûreté a été validé par boot-test (démarrage OK, sauvegarde, /setup rendu, aucun
    module manquant). Si un chemin rare échoue en prod sur un module absent, l'ajouter à $Extra.

.PARAMETER Jar       Fat jar Spring Boot à analyser (obligatoire).
.PARAMETER Out       Dossier de sortie du runtime (défaut : <jar-dir>\runtime-min).
.PARAMETER Compress  Niveau de compression jlink (défaut : zip-6).

.EXAMPLE
    pwsh packaging\make-runtime.ps1 -Jar backend\target\medical-backend-0.0.1-SNAPSHOT.jar
#>
param(
    [Parameter(Mandatory)][string]$Jar,
    [string]$Out,
    [string]$Compress = 'zip-6'
)

$ErrorActionPreference = 'Stop'

function Invoke-Native {
    param([Parameter(Mandatory)][string]$Exe, [string[]]$Arguments)
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { & $Exe @Arguments; $code = $LASTEXITCODE } finally { $ErrorActionPreference = $prev }
    if ($code -ne 0) { throw "$Exe a échoué (code $code)" }
}

if (-not (Test-Path $Jar)) { throw "Jar introuvable : $Jar" }
$Jar = (Resolve-Path $Jar).Path
if (-not $Out) { $Out = Join-Path (Split-Path $Jar -Parent) 'runtime-min' }

# Modules chargés par réflexion / dynamiquement, invisibles pour jdeps mais requis à l'exécution.
# (chacun justifié — retirer avec prudence, un manque = échec runtime sur un chemin précis)
$extra = @(
    'java.naming',            # JNDI (Spring, Hibernate, logging)
    'java.sql',               # JDBC (driver PostgreSQL)
    'java.management',        # JMX / Actuator
    'jdk.crypto.ec',          # cipher suites TLS à courbes elliptiques (clients HTTPS)
    'jdk.crypto.cryptoki',    # PKCS#11 (chaînes TLS)
    'jdk.localedata',         # locales complètes (i18n FR/EN/AR, formatage nombres/dates)
    'jdk.charsets',           # jeux de caractères hors base
    'jdk.zipfs'               # FileSystem zip (chargeur Spring Boot, NIO)
) -join ','

# 1) Éclater le jar dans un dossier temporaire pour jdeps.
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("jlink-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    Push-Location $tmp
    try { Invoke-Native -Exe 'jar' -Arguments @('xf', $Jar) } finally { Pop-Location }

    # 2) jdeps → modules de base (ignore les deps non résolues, multi-release runtime courant).
    Write-Host "jdeps : analyse des modules…" -ForegroundColor Cyan
    $classes = Join-Path $tmp 'BOOT-INF\classes'
    $libGlob = Join-Path $tmp 'BOOT-INF\lib\*'
    $base = (& jdeps --multi-release 24 --ignore-missing-deps --print-module-deps `
                     --class-path $libGlob $classes)
    if ($LASTEXITCODE -ne 0 -or -not $base) { throw "jdeps a échoué." }
    $base = $base.Trim()
    $mods = "$base,$extra"
    Write-Host "modules : $mods"

    # 3) jlink → runtime minimal.
    if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
    Write-Host "jlink : construction du runtime…" -ForegroundColor Cyan
    Invoke-Native -Exe 'jlink' -Arguments @(
        '--add-modules', $mods,
        '--strip-debug', '--no-header-files', '--no-man-pages',
        "--compress=$Compress",
        '--output', $Out
    )
} finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

$size = [math]::Round((Get-ChildItem $Out -Recurse | Measure-Object Length -Sum).Sum / 1MB, 0)
Write-Host "OK : runtime minimal -> $Out ($size Mo)" -ForegroundColor Green
$Out
