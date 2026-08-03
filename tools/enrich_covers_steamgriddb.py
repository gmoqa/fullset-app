#!/usr/bin/env python3
"""
Rellena `coverUrl` desde **SteamGridDB** para las consolas que libretro-thumbnails no cubre.

Por qué existe además de `enrich_covers_libretro.py`: libretro es la fuente para todo lo retro
—tiene 8.503 carátulas de PS2 y 9.351 de PlayStation— pero de **PS3 solo publica 67**, así que ese
catálogo se quedaba con un 2% de tapas. SteamGridDB sí lo cubre. Es la misma fuente que la app ya
consulta en vivo para PS5, solo que acá se resuelve una vez y se hornea la URL en el catálogo.

**Solo acepta coincidencia exacta de título** (normalizado con `core`). Tomar el primer resultado
del buscador daba un 97% de "aciertos" que incluía carátulas de otro juego: *Goosebumps: The Game*
traía la de *Attack of the Mutant*, y *Saint Seiya: Brave Soldiers* la de *Soldiers' Soul*, que es su
secuela. En un catálogo de colección una tapa equivocada es peor que ninguna, porque se ve legítima.
Con match exacto la cobertura baja a ~81% y lo que entra es del juego que dice ser.

Pide la clave en `local.properties` (`STEAMGRIDDB_API_KEY=…`), que está fuera de git.
Cachea cada búsqueda en disco: los catálogos regionales comparten la mayoría de los títulos y
volver a correrlo no debería repetir miles de consultas.

Uso:  python3 tools/enrich_covers_steamgriddb.py <catalogo.json> [<catalogo2.json> …] [--dry-run]
      python3 tools/enrich_covers_steamgriddb.py app/src/main/assets/catalogs/ps3-{usa,jp,eu}.json
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical  # noqa: E402

API = "https://www.steamgriddb.com/api/v2"
UA = "fullset-catalog/0.1 (+https://github.com/gmoqa/fullset-app)"
PAUSE = 0.2  # cortesía con la API; no hay límite documentado, pero son miles de consultas


def read_key(root: str) -> str:
    path = os.path.join(root, "local.properties")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip().startswith("STEAMGRIDDB_API_KEY"):
                key = line.split("=", 1)[1].strip()
                if key:
                    return key
    raise SystemExit("falta STEAMGRIDDB_API_KEY en local.properties")


def get(path: str, key: str):
    req = urllib.request.Request(API + path, headers={
        "Authorization": f"Bearer {key}", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=45) as r:
        return json.load(r)


def cover_for(title: str, key: str) -> str:
    """URL de la tapa 600×900 del juego cuyo nombre coincide **exactamente**, o "" si no hay."""
    try:
        hits = get("/search/autocomplete/" + urllib.parse.quote(title), key).get("data") or []
    except urllib.error.HTTPError:
        return ""
    match = next((h for h in hits if core(h["name"]) == core(title)), None)
    if not match:
        return ""
    try:
        # `nsfw`/`humor` fuera: SteamGridDB acepta arte de fans y tiene tapas paródicas.
        grids = get(f"/grids/game/{match['id']}?dimensions=600x900&types=static"
                    "&nsfw=false&humor=false&limit=1", key).get("data") or []
    except urllib.error.HTTPError:
        return ""
    return grids[0]["url"] if grids else ""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("catalogs", nargs="+")
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                    ".steamgriddb-cache.json"))
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    key = read_key(root)
    cache = json.load(open(args.cache, encoding="utf-8")) if os.path.exists(args.cache) else {}

    loaded = {p: json.load(open(p, encoding="utf-8")) for p in args.catalogs}
    # Un solo pedido por título aunque aparezca en los tres catálogos regionales.
    pending = sorted({e["title"] for c in loaded.values() for e in c
                      if not e["coverUrl"].strip() and core(e["title"]) not in cache})
    print(f"{len(pending)} títulos por resolver ({len(cache)} en caché)")

    for i, title in enumerate(pending, 1):
        cache[core(title)] = cover_for(title, key)
        if i % 100 == 0 or i == len(pending):
            found = sum(1 for v in cache.values() if v)
            print(f"   {i}/{len(pending)} · {found} con tapa", flush=True)
            json.dump(cache, open(args.cache, "w", encoding="utf-8"))
        time.sleep(PAUSE)
    json.dump(cache, open(args.cache, "w", encoding="utf-8"))

    for path, catalog in loaded.items():
        filled = 0
        for entry in catalog:
            if entry["coverUrl"].strip():
                continue
            url = cache.get(core(entry["title"]), "")
            if url:
                entry["coverUrl"] = url
                filled += 1
        total = len(catalog) or 1
        have = sum(1 for e in catalog if e["coverUrl"].strip())
        print(f"{os.path.basename(path)}: +{filled} carátulas · "
              f"{have}/{total} ({have * 100 // total}%)")
        if args.dry_run:
            continue
        rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
        body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
        open(path, "w", encoding="utf-8").write("[\n" + body + "\n]\n")
    if args.dry_run:
        print("   (dry-run: no se escribió)")


if __name__ == "__main__":
    main()
