#!/usr/bin/env python3
"""Extrait UNIQUEMENT pg_dump.exe d'un zip « binaires » PostgreSQL distant, via HTTP Range.

Pourquoi : les binaires PostgreSQL embarqués (zonky, « réduits ») n'incluent pas pg_dump, mais
fournissent déjà TOUTES ses DLL (libpq, libssl/crypto, libintl, zlib, lz4…). L'installeur n'a donc
besoin QUE de pg_dump.exe (~700 Ko). Plutôt que de télécharger le zip EDB complet (~300 Mo), on lit
son End-Of-Central-Directory + le central directory, puis on récupère le seul membre voulu et on le
décompresse. Idéal sur lien lent. Version majeure 14 = compatible avec le serveur 14.x embarqué.

Usage :
    python fetch_pgdump.py [sortie.exe] [--url URL] [--member bin/pg_dump.exe]
Sans argument : écrit ./pg_dump.exe depuis PostgreSQL 14.19 x64 (EDB).
"""
import argparse, struct, sys, time, zlib, urllib.request

DEFAULT_URL = "https://get.enterprisedb.com/postgresql/postgresql-14.19-1-windows-x64-binaries.zip"
CHUNK = 262144  # 256 Ko : requêtes courtes, résilientes au lien lent


def make_rng(url):
    def _get_once(a, b):
        req = urllib.request.Request(url, headers={"Range": f"bytes={a}-{b}"})
        with urllib.request.urlopen(req, timeout=180) as r:
            if r.status not in (206, 200):
                raise SystemExit(f"Range non supporté (status {r.status})")
            return r.read()

    def rng(a, b):
        """GET octets [a, b] inclus, en sous-plages de 256 Ko avec retries."""
        out = bytearray()
        pos = a
        while pos <= b:
            end = min(pos + CHUNK - 1, b)
            for attempt in range(5):
                try:
                    out += _get_once(pos, end)
                    break
                except Exception as e:  # noqa: BLE001 — lien lent/flaky : on retente
                    if attempt == 4:
                        raise
                    print(f"  retry {pos}-{end} ({e})", flush=True)
                    time.sleep(2)
            pos = end + 1
        return bytes(out)

    return rng


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out", nargs="?", default="pg_dump.exe")
    ap.add_argument("--url", default=DEFAULT_URL)
    ap.add_argument("--member", default="bin/pg_dump.exe", help="suffixe du chemin dans le zip")
    args = ap.parse_args()
    rng = make_rng(args.url)

    # 1) Taille totale
    with urllib.request.urlopen(urllib.request.Request(args.url, method="HEAD"), timeout=60) as r:
        total = int(r.headers["Content-Length"])
    print(f"zip distant : {total} octets ({total // 1024 // 1024} Mo)", flush=True)

    # 2) EOCD dans la queue (22 o mini + commentaire éventuel)
    tail = rng(total - min(65557, total), total - 1)
    i = tail.rfind(b"PK\x05\x06")
    if i < 0:
        raise SystemExit("EOCD introuvable (zip64 ?)")
    eocd = tail[i:i + 22]
    cd_size = struct.unpack("<I", eocd[12:16])[0]
    cd_off = struct.unpack("<I", eocd[16:20])[0]
    n_ent = struct.unpack("<H", eocd[10:12])[0]
    print(f"central directory : {n_ent} entrées, {cd_size} o @ {cd_off}", flush=True)
    if cd_off == 0xFFFFFFFF:
        raise SystemExit("zip64 — non géré")

    # 3) Lire le central directory, trouver le membre
    cd = rng(cd_off, cd_off + cd_size - 1)
    p, found = 0, None
    while p < len(cd) and cd[p:p + 4] == b"PK\x01\x02":
        method = struct.unpack("<H", cd[p + 10:p + 12])[0]
        comp = struct.unpack("<I", cd[p + 20:p + 24])[0]
        uncomp = struct.unpack("<I", cd[p + 24:p + 28])[0]
        fnl = struct.unpack("<H", cd[p + 28:p + 30])[0]
        exl = struct.unpack("<H", cd[p + 30:p + 32])[0]
        cml = struct.unpack("<H", cd[p + 32:p + 34])[0]
        lho = struct.unpack("<I", cd[p + 42:p + 46])[0]
        name = cd[p + 46:p + 46 + fnl].decode("utf-8", "replace")
        if name.replace("\\", "/").endswith(args.member):
            found = (name, method, comp, uncomp, lho)
            break
        p += 46 + fnl + exl + cml
    if not found:
        raise SystemExit(f"{args.member} absent du zip")
    name, method, comp, uncomp, lho = found
    print(f"trouvé : {name} (method={method}, comp={comp} o, uncomp={uncomp} o)", flush=True)

    # 4) Local header → début réel des données
    lh = rng(lho, lho + 30 - 1)
    if lh[:4] != b"PK\x03\x04":
        raise SystemExit("local header invalide")
    lfnl = struct.unpack("<H", lh[26:28])[0]
    lexl = struct.unpack("<H", lh[28:30])[0]
    raw = rng(lho + 30 + lfnl + lexl, lho + 30 + lfnl + lexl + comp - 1)

    # 5) Décompresser (deflate=8, stored=0)
    if method == 8:
        data = zlib.decompress(raw, -15)
    elif method == 0:
        data = raw
    else:
        raise SystemExit(f"méthode de compression {method} non gérée")
    if len(data) != uncomp:
        raise SystemExit(f"taille décompressée {len(data)} != attendue {uncomp}")
    if data[:2] != b"MZ":
        raise SystemExit("le contenu extrait n'est pas un exécutable PE (MZ)")

    with open(args.out, "wb") as f:
        f.write(data)
    print(f"écrit : {args.out} ({len(data)} o)", flush=True)


if __name__ == "__main__":
    sys.exit(main())
