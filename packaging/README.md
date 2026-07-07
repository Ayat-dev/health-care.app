# Packaging desktop (Phase 2)

Construit le paquet **desktop tout-en-un** ClinicApp : un runtime Java bundlé + le jar
Spring Boot + un launcher natif Windows, profil `desktop` figé (PostgreSQL embarqué +
licence + assistant `/setup`). Aucune installation de serveur chez le client.

## Prérequis

- **JDK 21+** (jpackage, jlink fournis avec le JDK). Testé avec JDK 24.
- **PowerShell** (Windows).
- **WiX Toolset** — *uniquement* pour produire un installeur `.msi`. Sans WiX, on produit
  une **app-image** (dossier portable exécutable), suffisante pour tester et distribuer en zip.

## Construire

```powershell
# App-image portable (dist\ClinicApp\ClinicApp.exe)
pwsh packaging\build-desktop.ps1 -SkipTests

# Installeur .msi (si WiX sur le PATH) + pg_dump officiel embarqué
pwsh packaging\build-desktop.ps1 -Installer -PgDump C:\pg14\bin\pg_dump.exe -Icon packaging\clinicapp.ico
```

Résultat dans `dist\`. Lancer : `dist\ClinicApp\ClinicApp.exe`.

## Où vivent les données

Tout est sous `%LOCALAPPDATA%\ClinicApp` (voir `DesktopPaths`) : cluster PostgreSQL
(`db\`), secrets auto-générés (`secrets.properties`), marqueur d'essai (`license.trial`),
uploads, sauvegardes. Survit à une réinstallation de l'app. Surcharge : `CLINICAPP_HOME`.

## Points reportés / à finaliser

- **Sauvegardes automatiques** : les binaires PG embarqués (zonky « réduits ») n'incluent
  pas `pg_dump`. Fournir un `pg_dump.exe` officiel PostgreSQL 14 via `-PgDump` (le script le
  copie dans `app\`) puis pointer `app.desktop.backup.pg-dump-path` dessus.
- **Signature de code** : différée (installs sur site assistées). Le jour venu → **Azure
  Trusted Signing** (cloud, sans token) sur le `.exe`/`.msi`, sinon certificat EV.
- **Icône** : fournir un `.ico` de marque via `-Icon` (défaut jpackage sinon).
- **Auto-update** : v1 = installeurs versionnés manuels. v2 = Velopack / update4j si besoin.
- **jlink** (runtime minimal) : jpackage bundle actuellement le runtime du JDK courant. Pour
  réduire la taille, générer un runtime `jlink` ciblé (via `jdeps`) et le passer en
  `--runtime-image` — optimisation, non bloquante.
