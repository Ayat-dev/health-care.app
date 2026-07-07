# Packaging desktop (Phase 2)

Construit le paquet **desktop tout-en-un** ClinicApp : un runtime Java bundlé + le jar
Spring Boot + un launcher natif Windows, profil `desktop` figé (PostgreSQL embarqué +
licence + assistant `/setup`). Aucune installation de serveur chez le client.

## Prérequis

- **JDK 21+** (jpackage, jlink fournis avec le JDK). Testé avec JDK 24.
- **PowerShell** (Windows).
- **WiX 3.x** — *uniquement* pour l'installeur `.msi`. ⚠ jpackage exige WiX **3.x** ; il ne
  supporte **ni WiX 4 ni 5**. Sans WiX, on produit une **app-image** (dossier portable), suffisante
  pour tester et distribuer en zip. Récupération recommandée (local, non versionné) :
  ```powershell
  # binaires officiels -> packaging\tools\wix314\ (candle.exe/light.exe à la racine)
  # https://github.com/wixtoolset/wix3/releases  ->  wix314-binaries.zip
  ```
  Le script détecte WiX sur le PATH, sinon dans `packaging\tools\wix314\` (injecté au PATH le
  temps du build).
- **Windows SDK** (« Signing Tools ») — *uniquement* pour la signature de code : fournit
  `signtool.exe`, auto-localisé par le script.

## Construire

```powershell
# App-image portable, non signée (dist\ClinicApp\ClinicApp.exe)
pwsh packaging\build-desktop.ps1 -SkipTests

# .msi + pg_dump officiel + icône (non signé)
pwsh packaging\build-desktop.ps1 -Installer -PgDump C:\pg14\bin\pg_dump.exe -Icon packaging\clinicapp.ico
```

Résultat dans `dist\`. Lancer l'app-image : `dist\ClinicApp\ClinicApp.exe`.

## Signer le `.msi` (et le launcher)

Le flux signe **le launcher `ClinicApp.exe` puis le `.msi`** (l'exe interne à l'installeur est donc
signé lui aussi), avec horodatage RFC3161.

```powershell
# 1) certif AUTO-SIGNÉ de test (preuve de chaîne — NON distribuable)
$tp = (.\packaging\make-test-cert.ps1).Thumbprint
pwsh packaging\build-desktop.ps1 -SkipTests -Installer -Sign -CertThumbprint $tp

# 2) vrai certif distribuable : PFX de l'AC…
pwsh packaging\build-desktop.ps1 -Installer -Sign -Pfx C:\certs\clinicapp.pfx -PfxPassword ******
# …ou empreinte d'un certif du magasin Windows (inclut Azure Trusted Signing)
pwsh packaging\build-desktop.ps1 -Installer -Sign -CertThumbprint <THUMBPRINT>
```

Vérifier :
```powershell
Get-AuthenticodeSignature dist\ClinicApp-0.0.1.msi | Format-List Status, SignerCertificate
```
`Status Valid` avec un vrai certif ; `UnknownError/NotTrusted` **attendu** avec l'auto-signé de
test (le certif n'est pas dans un magasin racine de confiance) — la signature est bien présente et
horodatée, seul le maillon « confiance » manque, apporté par un vrai certif ou Azure Trusted Signing.

## Où vivent les données

Tout est sous `%LOCALAPPDATA%\ClinicApp` (voir `DesktopPaths`) : cluster PostgreSQL
(`db\`), secrets auto-générés (`secrets.properties`), marqueur d'essai (`license.trial`),
uploads, sauvegardes. Survit à une réinstallation de l'app. Surcharge : `CLINICAPP_HOME`.

## Points reportés / à finaliser

- **Sauvegardes automatiques** : les binaires PG embarqués (zonky « réduits ») n'incluent
  pas `pg_dump`. Fournir un `pg_dump.exe` officiel PostgreSQL 14 via `-PgDump` (le script le
  copie dans `app\`) puis pointer `app.desktop.backup.pg-dump-path` dessus.
- **Signature de code** : chaîne **implémentée + prouvée** avec un certif auto-signé de test
  (`-Sign`, cf. ci-dessus). Reste à brancher un **vrai** certif pour la distribution → **Azure
  Trusted Signing** (cloud, sans token, `-CertThumbprint` d'un certif du magasin) ou un **PFX** EV
  (`-Pfx`). Aucun changement de script : seul le paramètre d'identité change.
- **Icône** : fournir un `.ico` de marque via `-Icon` (défaut jpackage sinon).
- **Auto-update** : v1 = installeurs versionnés manuels. v2 = Velopack / update4j si besoin.
- **jlink** (runtime minimal) : jpackage bundle actuellement le runtime du JDK courant. Pour
  réduire la taille, générer un runtime `jlink` ciblé (via `jdeps`) et le passer en
  `--runtime-image` — optimisation, non bloquante.
