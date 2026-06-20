# Chiffrement — transit & repos (P2.5)

Référence des mécanismes de chiffrement de ClinicApp et de ce qui reste à la
charge de l'**exploitation** (déploiement). Trois couches : en transit, au repos
applicatif (colonnes PHI), au repos infrastructure (volumes).

---

## 1. En transit — TLS (reverse-proxy)

- **Quoi** : un service `nginx` (cf. `docker-compose.yml`) termine le HTTPS et
  relaie vers le backend sur le réseau Docker privé. Le backend n'est **plus
  exposé** sur l'hôte (`expose` au lieu de `ports`) → aucun accès en clair depuis
  l'extérieur.
- **HTTP → HTTPS** : redirection 301. **HSTS** activé (1 an).
- **Côté Spring** : `server.forward-headers-strategy=framework` (respecte
  `X-Forwarded-Proto`) + cookie de session `Secure`/`HttpOnly` (profil prod).
- **Mise en route** : générer le certificat avant le 1er démarrage — voir
  `nginx/README.md` (auto-signé pour LAN, ou Let's Encrypt pour un domaine).

## 2. Au repos — colonnes PHI (applicatif)

- **Quoi** : chiffrement transparent **AES-256-GCM** des colonnes médicales
  sensibles, via un `AttributeConverter` JPA (`com.clinic.backend.crypto`).
- **Colonnes chiffrées** (table `patients`) : `address`, `allergies`,
  `chronic_conditions`, `medical_history`, `notes`. Stockées sous la forme
  `gcm:<base64(IV‖ciphertext+tag)>`.
- **Non chiffrées (volontairement)** : `first_name`, `last_name`,
  `record_number`, `phone`, `national_id` — utilisées par la **recherche**
  (`LIKE`) et l'unicité ; les chiffrer casserait la recherche patient.
- **Clé** : `app.encryption.key` (env `APP_ENCRYPTION_KEY`), dérivée en clé AES
  par SHA-256. **Fail-fast** en prod (aucun défaut). Dev/test : valeur fixe dans
  `application-{dev,test}.properties`.
- **Propriétés** : IV aléatoire par écriture (pas de fuite par égalité), tag GCM
  authentifié (détecte l'altération), tolérance des valeurs en clair legacy
  (déchiffrement renvoie tel quel si pas de marqueur `gcm:`).
- **⚠️ Rotation de clé** : changer `APP_ENCRYPTION_KEY` après mise en service
  rend les colonnes existantes illisibles. Une rotation nécessite un
  ré-chiffrement (déchiffrer avec l'ancienne clé → re-chiffrer avec la nouvelle).
- **Couverture test** : `EncryptionConverterTest` (aller-retour, IV aléatoire,
  altération détectée, mauvaise clé rejetée, **+ intégration** : la colonne est
  bien chiffrée en base mais restituée en clair via JPA).

## 3. Au repos — volumes (infrastructure / exploitation)

À la charge de l'hébergeur (non gérable dans le code). Recommandations :

- **Volume PostgreSQL** (`postgres_data`) et **uploads** (`backend_uploads`,
  photos/images = PHI) : placer le stockage Docker sur un **volume chiffré**.
  - Linux : **LUKS/dm-crypt** sur le disque/partition hébergeant
    `/var/lib/docker/volumes` (ou un volume dédié monté chiffré).
    ```bash
    cryptsetup luksFormat /dev/sdX
    cryptsetup open /dev/sdX clinic_data
    mkfs.ext4 /dev/mapper/clinic_data
    mount /dev/mapper/clinic_data /srv/clinic-data
    ```
  - Cloud : activer le chiffrement de volume natif (EBS encryption, etc.).
- **Sauvegardes** : chiffrer les dumps `pg_dump` (ex. `gpg`) et les stocker sur
  un support chiffré. Le chiffrement applicatif (couche 2) protège déjà les
  colonnes PHI **dans** le dump, mais pas les colonnes en clair (noms, etc.).
- **Chiffrement transparent côté Postgres** : non natif ; le chiffrement de
  volume (LUKS) couvre l'intégralité du fichier de données.

---

## Récapitulatif des responsabilités

| Couche | Mécanisme | Géré par |
|---|---|---|
| Transit | nginx TLS + HSTS, backend non exposé | **Code + config** (ce repo) + certif (exploitation) |
| Repos — colonnes PHI | AES-256-GCM (`AttributeConverter`) | **Code** (ce repo) |
| Repos — volumes DB/uploads | LUKS / chiffrement de volume | **Exploitation** (déploiement) |
| Sauvegardes | dumps chiffrés (gpg) sur support chiffré | **Exploitation** |
