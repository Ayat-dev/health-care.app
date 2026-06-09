# Module 15 — Déploiement & Maintenance

## Environments

| Env | Base de données | Profil Spring | Objectif |
|---|---|---|---|
| dev | H2 in-memory | (défaut) | Développement local, données de test |
| staging | PostgreSQL Docker | staging | Tests avant prod |
| prod | PostgreSQL | prod | Clinique réelle |

## Option A — Serveur local (LAN)

### Prérequis matériels
- Mini-PC ou vieux PC : 4 Go RAM, 50 Go HDD, réseau câblé recommandé
- OS : Ubuntu Server 22.04 LTS (gratuit)
- Java 17 + Docker + Docker Compose

### Installation initiale (30 minutes)
```bash
# 1. Installer Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 2. Cloner le projet
git clone https://github.com/votre-org/clinicapp.git /opt/clinicapp
cd /opt/clinicapp

# 3. Configurer
cp .env.example .env
nano .env  # remplir DB_PASSWORD, JWT_SECRET, etc.

# 4. Démarrer
docker-compose up -d

# 5. Vérifier
docker-compose ps   # backend + db doivent être "Up"
curl http://localhost:8080/login
```

### Accès réseau local
- Le serveur a une IP fixe : `192.168.1.10` (à configurer sur le routeur)
- Accès web : `http://192.168.1.10:8080/login`
- Accès convivial : configurer DNS local → `http://clinique.local/login`

### Démarrage automatique au boot
```bash
# Docker Compose redémarre automatiquement (restart: unless-stopped dans docker-compose.yml)
# Activer Docker au démarrage :
sudo systemctl enable docker
```

---

## Option B — VPS Cloud (accès distant)

### Fournisseurs recommandés (rapport qualité/prix Afrique)
- **OVH** : datacenter Europe + Afrique du Nord (Roubaix, Gravelines)
- **Hetzner** : prix très compétitifs (3-5€/mois pour un VPS starter)
- **DigitalOcean** : interface simple, bonne documentation
- **Contabo** : meilleur rapport GB RAM / prix

### Configuration supplémentaire vs Option A
```bash
# 1. Nom de domaine (ex: clinique-xyz.com) — ≈ 10€/an
# 2. HTTPS avec Let's Encrypt (gratuit)
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d clinique-xyz.com

# 3. Nginx comme reverse proxy
# → redirige clinique-xyz.com:443 vers localhost:8080
```

---

## Sauvegardes

### Automatique (quotidienne à 2h)
```bash
# Script : /opt/clinicapp/scripts/backup.sh
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M)
docker exec clinic_db pg_dump -U clinic_user clinicdb > /backups/clinicdb_$DATE.sql
# Garder 30 jours
find /backups -name "*.sql" -mtime +30 -delete
```
Ajouter dans crontab : `0 2 * * * /opt/clinicapp/scripts/backup.sh`

### Optionnel : copie vers stockage externe
```bash
# Clé USB montée sur /mnt/usb
cp /backups/clinicdb_$DATE.sql /mnt/usb/backups/
# ou rsync vers serveur distant / cloud privé
```

---

## Mise à jour de l'application

```bash
cd /opt/clinicapp
git pull origin main                    # récupérer le nouveau code
docker-compose up -d --build backend    # reconstruire + redémarrer le backend
# La BDD n'est pas touchée — Flyway applique les migrations automatiquement
```
**Downtime** : < 30 secondes.

---

## Surveillance

### Logs
```bash
docker-compose logs -f backend   # logs en temps réel
docker-compose logs --tail=100   # 100 dernières lignes
```

### Santé de l'application
```
GET /actuator/health  → { "status": "UP" }
```
Ajouter Spring Actuator (Phase 4) pour monitoring avancé.

### Alertes serveur (recommandé Phase 4)
- **UptimeRobot** (gratuit) : ping toutes les 5 min → SMS si le serveur tombe
- **Grafana + Prometheus** : métriques détaillées (RAM, CPU, requêtes/sec)

---

## Client desktop — distribution

### Package Windows (.exe)
```bash
# Avec jpackage (inclus dans JDK 17)
jpackage --input target/ \
  --name "ClinicApp" \
  --main-jar medical-desktop.jar \
  --type exe \
  --win-shortcut \
  --win-menu
```

### Auto-update (Phase 4)
Au démarrage, le client vérifie `http://serveur:8080/api/version`.
Si version serveur > version client → proposition de mise à jour → téléchargement automatique.

---

## Checklist déploiement production

- [ ] `.env` configuré avec des secrets forts (pas les valeurs par défaut)
- [ ] `SPRING_PROFILES_ACTIVE=prod` défini
- [ ] HTTPS configuré (si VPS)
- [ ] Backup automatique configuré et testé
- [ ] Utilisateur admin créé avec mot de passe fort (pas admin/admin123)
- [ ] Modules inutiles désactivés dans `clinic_config`
- [ ] Test de connexion depuis un poste client
- [ ] Formation du personnel (guide utilisateur remis)
- [ ] Numéro de support communiqué
