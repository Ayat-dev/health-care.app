<#
.SYNOPSIS
    Génère un certificat de signature de code AUTO-SIGNÉ, à usage de TEST uniquement.

.DESCRIPTION
    Crée (ou réutilise) un certif « CodeSigning » dans le magasin CurrentUser\My et renvoie un
    objet portant son empreinte, à passer à build-desktop.ps1 -Sign -CertThumbprint <Thumbprint>.

    ⚠ Ce certificat n'est PAS approuvé par les autres postes : il sert à PROUVER la chaîne de
    signature de bout en bout (le .msi/exe est bien signé + horodaté), pas à distribuer.
    Pour un installeur réellement distribuable, remplace-le par un vrai certif (PFX de l'AC, ou
    Azure Trusted Signing) — même paramètre -CertThumbprint / -Pfx, aucun changement de script.

.PARAMETER Subject   Sujet du certificat (défaut : CN=ClinicApp Test Publisher).
.PARAMETER Years      Durée de validité en années (défaut : 3).
.PARAMETER ExportPfx  Chemin optionnel où exporter un .pfx (nécessite -PfxPassword).
.PARAMETER PfxPassword Mot de passe du .pfx exporté.

.EXAMPLE
    $tp = (.\packaging\make-test-cert.ps1).Thumbprint
    pwsh packaging\build-desktop.ps1 -SkipTests -Installer -Sign -CertThumbprint $tp
#>
param(
    [string]$Subject = 'CN=ClinicApp Test Publisher',
    [int]$Years = 3,
    [string]$ExportPfx,
    [string]$PfxPassword
)

$ErrorActionPreference = 'Stop'

# Réutilise un certif de test existant au même sujet plutôt que d'en empiler.
$existing = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.Subject -eq $Subject -and $_.NotAfter -gt (Get-Date) } |
    Sort-Object NotAfter -Descending | Select-Object -First 1

if ($existing) {
    Write-Host "Certif de test existant réutilisé (expire $($existing.NotAfter.ToString('yyyy-MM-dd')))." -ForegroundColor Yellow
    $cert = $existing
} else {
    $cert = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject $Subject `
        -CertStoreLocation Cert:\CurrentUser\My `
        -KeyUsage DigitalSignature `
        -KeyExportPolicy Exportable `
        -NotAfter (Get-Date).AddYears($Years) `
        -HashAlgorithm SHA256
    Write-Host "Certif de test créé (expire $($cert.NotAfter.ToString('yyyy-MM-dd')))." -ForegroundColor Green
}

if ($ExportPfx) {
    if (-not $PfxPassword) { throw "-ExportPfx nécessite -PfxPassword." }
    $sec = ConvertTo-SecureString -String $PfxPassword -Force -AsPlainText
    Export-PfxCertificate -Cert $cert -FilePath $ExportPfx -Password $sec | Out-Null
    Write-Host "PFX exporté : $ExportPfx" -ForegroundColor Green
}

Write-Host "Thumbprint : $($cert.Thumbprint)"
[pscustomobject]@{ Thumbprint = $cert.Thumbprint; Subject = $cert.Subject; NotAfter = $cert.NotAfter }
