<#
.SYNOPSIS
    Récupère un pg_dump.exe officiel (PostgreSQL 14) dans packaging\tools\pgdump\ pour activer
    les sauvegardes automatiques du mode desktop.

.DESCRIPTION
    Les binaires PostgreSQL embarqués (zonky) fournissent les DLL de pg_dump mais pas l'exécutable.
    Ce script extrait le SEUL pg_dump.exe du zip « binaires » EDB via HTTP Range (fetch_pgdump.py) —
    ~700 Ko au lieu des ~300 Mo du zip complet. build-desktop.ps1 le détecte ensuite automatiquement
    (packaging\tools\pgdump\pg_dump.exe) et le copie dans l'app-image.

    Nécessite Python 3 sur le PATH. Le binaire n'est PAS versionné (packaging\tools\ est gitignoré).

.EXAMPLE
    pwsh packaging\get-pgdump.ps1
    pwsh packaging\build-desktop.ps1 -Installer -Sign -CertThumbprint <TP>   # embarque pg_dump
#>
param(
    [string]$Url = 'https://get.enterprisedb.com/postgresql/postgresql-14.19-1-windows-x64-binaries.zip'
)

$ErrorActionPreference = 'Stop'

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python3 -ErrorAction SilentlyContinue }
if (-not $python) { throw "Python 3 requis (introuvable sur le PATH)." }

$outDir = Join-Path $PSScriptRoot 'tools\pgdump'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'pg_dump.exe'

& $python.Source (Join-Path $PSScriptRoot 'fetch_pgdump.py') $out --url $Url
if ($LASTEXITCODE -ne 0) { throw "fetch_pgdump.py a échoué (code $LASTEXITCODE)" }

$size = [math]::Round((Get-Item $out).Length / 1KB, 0)
Write-Host "OK : $out ($size Ko)" -ForegroundColor Green
Write-Host "build-desktop.ps1 l'embarquera automatiquement (-PgDump non requis)."
