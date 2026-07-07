# Licences & vente (Phase 4)

Comment émettre, livrer et renouveler les licences ClinicApp. La vérification côté client
est **hors-ligne** (signature Ed25519, cf. `docs/DESKTOP-LICENSING-PLAN.md` §Phase 3).

> **Règle d'or :** la **clé privée** de l'éditeur ne doit JAMAIS se trouver dans l'application
> distribuée ni dans le dépôt. Seule la **clé publique** est embarquée (`app.license.public-key`).
> Toute émission se fait sur un poste éditeur de confiance.

---

## 0. Préparer la clé éditeur (une seule fois)

Sur un poste sûr :

```bash
java -cp backend/target/classes;<deps> com.clinic.backend.license.LicenseKeyTool keygen --out C:\coffre\clinicapp-keys
```

- `private.key` → **sauvegarde chiffrée hors ligne** (gestionnaire de mots de passe / coffre).
  Sa perte = impossibilité d'émettre de nouvelles clés reconnues par les installations existantes.
- `public.key` → coller la valeur dans `application.properties` (`app.license.public-key`) **avant**
  de packager (Phase 2). La clé publique committée est une clé de **dev/démo** ; remplacer par la vôtre pour la prod.

*(Le classpath `<deps>` s'obtient via `mvnd dependency:build-classpath -Dmdep.outputFile=cp.txt`.)*

---

## 1. Flux manuel v1 (marché Niger)

Adapté à un marché sans paiement carte en ligne généralisé.

1. **Vente** : le client paie (virement, mobile money AmanaTa/MyNITA, espèces via revendeur).
2. **Émission** :
   ```bash
   ... LicenseKeyTool issue --key C:\coffre\clinicapp-keys\private.key \
        --clinic "Clinique du Sahel" --edition PRO --days 365 --max-users 15 \
        --registry C:\coffre\licenses.jsonl
   ```
   Sortie : un récapitulatif + **la clé à remettre** (jeton signé). L'émission est **enregistrée**
   dans le registre (`licenses.jsonl`) pour le suivi.
3. **Livraison** : envoyer la clé au client (e-mail).
4. **Activation** : le client colle la clé dans **`/license`** (Paramètres → Licence). Vérification
   locale immédiate → passe de « Essai » à « Licence active ». Aucune connexion requise.

### Renouvellement / abonnement annuel
Réémettre une clé avec une nouvelle échéance (même clinique) ; le client la recolle dans `/license`.
```bash
... issue --key ...\private.key --clinic "Clinique du Sahel" --edition PRO --expires 2028-07-07 --registry ...\licenses.jsonl
```

### Suivi
```bash
... LicenseKeyTool list --registry C:\coffre\licenses.jsonl     # historique des licences émises
... LicenseKeyTool verify --pubkey ...\public.key --token <jeton>   # contrôle d'une clé
```

---

## 2. Flux automatisé v2 (international) — design

Pour vendre en self-service (carte). **Architecture clé** : l'émission signe avec la clé privée,
donc elle vit sur **l'infra de l'éditeur**, jamais dans l'app cliente.

```
Client paie sur Lemon Squeezy / Paddle (Merchant of Record → gère la TVA)
        │  webhook "order_created" (signé HMAC)
        ▼
Petit service éditeur (séparé de ClinicApp)
   1. vérifie la signature du webhook
   2. LicenseCodec.encode(License, PRIVATE_KEY)   ← clé privée locale au service
   3. enregistre au registre + envoie la clé par e-mail au client
```

- **Ne pas** héberger ce service dans l'app clinique (elle ne doit pas contenir la clé privée).
- Réutilise `License`/`LicenseCodec`/`LicenseRegistry` (déjà sans dépendance Spring) — le service
  peut être un petit main/Spring Boot séparé, ou une fonction serverless.
- MoR (Lemon Squeezy/Paddle) recommandé pour un éditeur solo : facturation + TVA gérées.

Statut : **non implémenté** (nécessite un compte marchand + infra éditeur). Point d'extension prêt.

---

## Référence CLI

```
keygen --out <dossier>
issue  --key <privée> --clinic "Nom" [--edition STANDARD] (--days N | --expires AAAA-MM-JJ)
       [--max-users N] [--features a,b] [--id LIC-...] [--registry fichier.jsonl | --no-registry]
list   [--registry fichier.jsonl]
verify --pubkey <base64|fichier> --token <jeton|fichier>
```
