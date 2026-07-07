<#
.SYNOPSIS
    Construit (et signe) le paquet desktop tout-en-un ClinicApp (Phase 2) avec jpackage.

.DESCRIPTION
    1. Compile + assemble le jar exécutable Spring Boot (mvnd package).
    2. Empaquète via jpackage une APP-IMAGE (runtime Java bundlé + jar + launcher natif).
    3. -Sign        : signe le launcher ClinicApp.exe (signtool + horodatage RFC3161).
    4. -Installer   : emballe l'app-image (signée) en .msi (nécessite WiX 3.x) ; puis, si
                      -Sign, signe le .msi lui-même. Résultat : launcher ET installeur signés.
    Le profil Spring `desktop` est figé dans le launcher (PostgreSQL embarqué + licence).

    WiX 3.x : jpackage exige WiX **3.x** pour le .msi (il NE supporte NI WiX 4 NI 5). Le script
    le cherche sur le PATH, sinon dans packaging\tools\wix314\ (téléchargé localement, gitignoré).

    Signature : signtool.exe est auto-localisé dans le Windows SDK. L'identité vient soit d'une
    empreinte de certificat du magasin Windows (-CertThumbprint — cas cert store, auto-signé de
    test, ou Azure Trusted Signing), soit d'un fichier PFX (-Pfx / -PfxPassword).

.PARAMETER SkipTests       Ne pas relancer la suite de tests pendant l'assemblage.
.PARAMETER Installer       Produire un installeur .msi (nécessite WiX 3.x, cf. ci-dessus).
.PARAMETER PgDump          Chemin d'un pg_dump.exe officiel (PostgreSQL 14) à embarquer
                           pour activer les sauvegardes automatiques.
.PARAMETER Icon            Chemin d'un .ico de marque (facultatif).
.PARAMETER Sign            Signer le launcher (et le .msi si -Installer).
.PARAMETER CertThumbprint  Empreinte SHA1 d'un certif de signature dans le magasin Windows
                           (CurrentUser\My ou LocalMachine\My). Cf. packaging\make-test-cert.ps1.
.PARAMETER Pfx             Chemin d'un fichier .pfx de signature (alternative à -CertThumbprint).
.PARAMETER PfxPassword     Mot de passe du .pfx.
.PARAMETER TimestampUrl    Serveur d'horodatage RFC3161 (défaut : DigiCert).

.EXAMPLE
    # App-image portable, non signée
    pwsh packaging\build-desktop.ps1 -SkipTests

.EXAMPLE
    # .msi signé avec un certif auto-signé de test (preuve de chaîne)
    $tp = (& packaging\make-test-cert.ps1).Thumbprint
    pwsh packaging\build-desktop.ps1 -SkipTests -Installer -Sign -CertThumbprint $tp

.EXAMPLE
    # .msi réellement distribuable (vrai certif) + pg_dump + icône
    pwsh packaging\build-desktop.ps1 -Installer -Sign -Pfx C:\certs\clinicapp.pfx -PfxPassword ****** `
        -PgDump C:\pg14\bin\pg_dump.exe -Icon packaging\clinicapp.ico
#>
param(
    [switch]$SkipTests,
    [switch]$Installer,
    [string]$PgDump,
    [string]$Icon,
    [switch]$Sign,
    [string]$CertThumbprint,
    [string]$Pfx,
    [string]$PfxPassword,
    [string]$TimestampUrl = 'http://timestamp.digicert.com'
)

$ErrorActionPreference = 'Stop'

# Invoque un exécutable natif sans que sa sortie stderr (ex. warnings mvnd/jline) ne
# soit convertie en erreur terminante par Windows PowerShell 5.1. Seul le code de
# sortie fait foi.
function Invoke-Native {
    param([Parameter(Mandatory)][string]$Exe, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Exe @Arguments
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) { throw "$Exe a échoué (code $code)" }
}

# Localise signtool.exe : PATH d'abord, sinon la version x64 la plus récente du Windows SDK.
function Resolve-SignTool {
    $onPath = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $roots = @(
        "${env:ProgramFiles(x86)}\Windows Kits\10\bin",
        "${env:ProgramFiles}\Windows Kits\10\bin"
    ) | Where-Object { Test-Path $_ }
    $cand = foreach ($r in $roots) {
        Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'x64\signtool.exe' } |
            Where-Object { Test-Path $_ }
    }
    $st = $cand | Select-Object -First 1
    if (-not $st) { throw "signtool.exe introuvable (installe le Windows SDK, composant « Signing Tools »)." }
    return $st
}

# S'assure que candle.exe (WiX 3.x) est atteignable ; sinon injecte packaging\tools\wix314 au PATH.
function Ensure-Wix {
    if (Get-Command candle.exe -ErrorAction SilentlyContinue) { return }
    $localRoot = Join-Path $PSScriptRoot 'tools'
    $candle = Get-ChildItem -Path $localRoot -Filter candle.exe -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($candle) {
        $env:PATH = "$($candle.DirectoryName);$env:PATH"
        Write-Host "   WiX 3.x local : $($candle.DirectoryName)"
        return
    }
    throw @"
WiX 3.x introuvable (ni sur le PATH, ni dans packaging\tools\wix314\).
jpackage exige WiX 3.x pour le .msi (WiX 4/5 NON supportés). Récupère les binaires officiels :
  https://github.com/wixtoolset/wix3/releases  ->  wix314-binaries.zip
puis extrais-les dans packaging\tools\wix314\ . Voir packaging\README.md.
"@
}

$script:SignTool = $null
function Invoke-CodeSign {
    param([Parameter(Mandatory)][string]$Path)
    if (-not $script:SignTool) { $script:SignTool = Resolve-SignTool }
    # jpackage marque le launcher (et le .msi) en lecture seule ; signtool doit écrire la
    # signature dans le fichier -> lever le read-only, sinon « SignTool Error: Access is denied ».
    $item = Get-Item $Path
    if ($item.IsReadOnly) { $item.IsReadOnly = $false }
    $a = @('sign', '/fd', 'SHA256', '/tr', $TimestampUrl, '/td', 'SHA256')
    if ($CertThumbprint)  { $a += @('/sha1', ($CertThumbprint -replace '\s','')) }
    elseif ($Pfx)         { $a += @('/f', $Pfx); if ($PfxPassword) { $a += @('/p', $PfxPassword) } }
    else { throw "-Sign demandé sans identité : fournis -CertThumbprint ou -Pfx." }
    $a += $Path
    Write-Host "   signtool sign -> $(Split-Path $Path -Leaf)"
    Invoke-Native -Exe $script:SignTool -Arguments $a
}

$Root       = Split-Path -Parent $PSScriptRoot
$Backend    = Join-Path $Root 'backend'
$Target     = Join-Path $Backend 'target'
$Stage      = Join-Path $Target 'jpackage-input'
$Dest       = Join-Path $Root 'dist'
$AppName    = 'ClinicApp'
$AppVersion = '0.0.1'

# Garde-fous précoces : mieux vaut échouer avant 3 min de build.
if ($Sign -and -not ($CertThumbprint -or $Pfx)) {
    throw "-Sign demandé sans identité : fournis -CertThumbprint ou -Pfx (cf. packaging\make-test-cert.ps1)."
}
if ($Installer) { Ensure-Wix }
if ($Sign)      { $script:SignTool = Resolve-SignTool; Write-Host "signtool : $script:SignTool" }

Write-Host "== 1/4  Assemblage du jar Spring Boot ==" -ForegroundColor Cyan
Push-Location $Backend
try {
    $mvndArgs = @('clean', 'package')
    if ($SkipTests) { $mvndArgs += '-DskipTests' }
    Invoke-Native -Exe 'mvnd' -Arguments $mvndArgs
} finally { Pop-Location }

# Jar exécutable = celui repackagé (on écarte *.jar.original et *-sources.jar).
$fatJar = Get-ChildItem $Target -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*.original' -and $_.Name -notlike '*-sources.jar' } |
    Select-Object -First 1
if (-not $fatJar) { throw "Jar exécutable introuvable dans $Target" }
Write-Host "   jar : $($fatJar.Name)"

Write-Host "== 2/4  App-image jpackage ==" -ForegroundColor Cyan
if (Test-Path $Stage) { Remove-Item $Stage -Recurse -Force }
New-Item -ItemType Directory -Path $Stage | Out-Null
Copy-Item $fatJar.FullName (Join-Path $Stage $fatJar.Name)
if (Test-Path $Dest) { Remove-Item $Dest -Recurse -Force }
New-Item -ItemType Directory -Path $Dest | Out-Null

$appImageArgs = @(
    '--type', 'app-image',
    '--name', $AppName,
    '--app-version', $AppVersion,
    '--input', $Stage,
    '--main-jar', $fatJar.Name,
    '--dest', $Dest,
    '--vendor', 'ClinicApp',
    # Profil desktop figé + AWT non headless (ouverture navigateur).
    '--java-options', '-Dspring.profiles.active=desktop',
    '--java-options', '-Djava.awt.headless=false'
)
if ($Icon) { $appImageArgs += @('--icon', $Icon) }
Invoke-Native -Exe 'jpackage' -Arguments $appImageArgs

$AppImageDir = Join-Path $Dest $AppName          # dist\ClinicApp
$LauncherExe = Join-Path $AppImageDir "$AppName.exe"

# pg_dump officiel (facultatif) : les binaires PG embarqués (zonky réduits) ne l'incluent
# pas ; le fournir active les sauvegardes automatiques. Copié AVANT l'emballage msi pour
# qu'il soit inclus dans l'installeur.
if ($PgDump) {
    $appDir = Join-Path $AppImageDir 'app'
    if (Test-Path $appDir) {
        Copy-Item $PgDump (Join-Path $appDir 'pg_dump.exe')
        Write-Host "   pg_dump.exe embarqué -> définir app.desktop.backup.pg-dump-path sur ce fichier."
    }
}

# Signer le launcher AVANT l'emballage msi -> l'exe interne à l'installeur est signé lui aussi.
if ($Sign) {
    Write-Host "== 3/4  Signature du launcher ==" -ForegroundColor Cyan
    Invoke-CodeSign -Path $LauncherExe
} else {
    Write-Host "== 3/4  Signature : ignorée (-Sign absent) ==" -ForegroundColor DarkGray
}

$deliverable = $AppImageDir
if ($Installer) {
    Write-Host "== 4/4  Installeur .msi (jpackage + WiX) ==" -ForegroundColor Cyan
    $msiArgs = @(
        '--type', 'msi',
        '--app-image', $AppImageDir,
        '--name', $AppName,
        '--app-version', $AppVersion,
        '--dest', $Dest,
        '--vendor', 'ClinicApp',
        '--win-dir-chooser', '--win-shortcut', '--win-menu'
    )
    if ($Icon) { $msiArgs += @('--icon', $Icon) }
    Invoke-Native -Exe 'jpackage' -Arguments $msiArgs

    $msi = Get-ChildItem $Dest -Filter '*.msi' | Select-Object -First 1
    if (-not $msi) { throw "MSI introuvable dans $Dest après jpackage." }
    $deliverable = $msi.FullName
    if ($Sign) { Invoke-CodeSign -Path $msi.FullName }
} else {
    Write-Host "== 4/4  Installeur : ignoré (-Installer absent) ==" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "OK. Résultat : $deliverable" -ForegroundColor Green
if (-not $Installer) {
    Write-Host "Lancer : $LauncherExe"
}
if ($Sign) {
    Write-Host ""
    Write-Host "Vérifier la signature :" -ForegroundColor Cyan
    Write-Host "  Get-AuthenticodeSignature '$deliverable' | Format-List Status, SignerCertificate"
    Write-Host "  (Status 'Valid' avec un vrai certif ; 'UnknownError/NotTrusted' attendu avec un auto-signé de test.)"
}
