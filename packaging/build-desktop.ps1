<#
.SYNOPSIS
    Construit le paquet desktop tout-en-un ClinicApp (Phase 2) avec jpackage.

.DESCRIPTION
    1. Compile + assemble le jar exécutable Spring Boot (mvnd package).
    2. Empaquète via jpackage un runtime Java bundlé + le jar + un launcher natif.
       - Sans WiX : produit une APP-IMAGE (dossier portable dist\ClinicApp\ClinicApp.exe).
       - Avec WiX sur le PATH : passer -Installer pour produire aussi un .msi.
    Le profil Spring `desktop` est figé dans le launcher (PostgreSQL embarqué + licence).

.PARAMETER SkipTests   Ne pas relancer la suite de tests pendant l'assemblage.
.PARAMETER Installer   Tenter un installeur .msi (nécessite WiX Toolset sur le PATH).
.PARAMETER PgDump      Chemin d'un pg_dump.exe officiel (PostgreSQL 14) à embarquer
                       pour activer les sauvegardes automatiques.
.PARAMETER Icon        Chemin d'un .ico de marque (facultatif).

.EXAMPLE
    pwsh packaging\build-desktop.ps1 -SkipTests
    pwsh packaging\build-desktop.ps1 -PgDump C:\pg14\bin\pg_dump.exe -Installer
#>
param(
    [switch]$SkipTests,
    [switch]$Installer,
    [string]$PgDump,
    [string]$Icon
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

$Root       = Split-Path -Parent $PSScriptRoot
$Backend    = Join-Path $Root 'backend'
$Target     = Join-Path $Backend 'target'
$Stage      = Join-Path $Target 'jpackage-input'
$Dest       = Join-Path $Root 'dist'
$AppName    = 'ClinicApp'
$AppVersion = '0.0.1'

Write-Host "== 1/3  Assemblage du jar Spring Boot ==" -ForegroundColor Cyan
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

Write-Host "== 2/3  Préparation de l'entrée jpackage ==" -ForegroundColor Cyan
if (Test-Path $Stage) { Remove-Item $Stage -Recurse -Force }
New-Item -ItemType Directory -Path $Stage | Out-Null
Copy-Item $fatJar.FullName (Join-Path $Stage $fatJar.Name)
if (Test-Path $Dest) { Remove-Item $Dest -Recurse -Force }

Write-Host "== 3/3  jpackage ==" -ForegroundColor Cyan
$hasWix = [bool](Get-Command candle -ErrorAction SilentlyContinue) -or `
          [bool](Get-Command wix    -ErrorAction SilentlyContinue)
$type = if ($Installer -and $hasWix) { 'msi' } else { 'app-image' }
if ($Installer -and -not $hasWix) {
    Write-Warning "WiX introuvable sur le PATH → repli sur app-image (dossier portable)."
}

$jpArgs = @(
    '--type', $type,
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
if ($Icon) { $jpArgs += @('--icon', $Icon) }
if ($type -eq 'msi') { $jpArgs += @('--win-dir-chooser', '--win-shortcut', '--win-menu') }

Invoke-Native -Exe 'jpackage' -Arguments $jpArgs

# pg_dump officiel (facultatif) : les binaires PG embarqués (zonky réduits) ne
# l'incluent pas ; le fournir active les sauvegardes automatiques.
if ($PgDump) {
    $appDir = Join-Path $Dest "$AppName\app"
    if (Test-Path $appDir) {
        Copy-Item $PgDump (Join-Path $appDir 'pg_dump.exe')
        Write-Host "   pg_dump.exe embarqué → définir app.desktop.backup.pg-dump-path sur ce fichier."
    }
}

Write-Host ""
Write-Host "OK. Résultat dans : $Dest" -ForegroundColor Green
if ($type -eq 'app-image') {
    Write-Host "Lancer : $Dest\$AppName\$AppName.exe"
}
