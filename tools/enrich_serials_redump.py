#!/usr/bin/env python3
"""
Rellena `serial` desde los **DAT de Redump** que republica libretro-database (CC BY-SA 4.0), que es
la fuente de catalog numbers para las consolas de disco: PlayStation, PS2, PS3, GameCube, Saturn,
Dreamcast y Sega CD. Para las de cartucho, el equivalente es `metadat/serial/` (ver
`enrich_meta_libretro.py`).

Cada entrada trae `region` y `serial`, así que se toma la del mercado del catálogo.

Solo completa lo que esté **vacío**: nunca pisa un serial ya cargado.

Uso:  python3 tools/enrich_serials_redump.py <catalogo.json> "<nombre del DAT>" [--region USA] [--dry-run]
      python3 tools/enrich_serials_redump.py app/src/main/assets/catalogs/psx-usa.json \
          "Sony - PlayStation"
"""
import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, canonical  # noqa: E402

BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/redump"
USER_AGENT = "fullset-catalog/0.1 (+https://github.com/gmoqa/fullset-app)"

# Región de nuestro catálogo -> etiqueta de Redump, en orden de preferencia.
REGION_TAGS = {
    "NTSC-U": ["USA", "World"],
    "NTSC-J": ["Japan", "World"],
    "PAL": ["Europe", "Australia", "World"],
}


def keys_for(title: str) -> list[str]:
    """
    Claves con las que buscar un título, contemplando cómo cada fuente ordena los artículos.

    Redump sigue la convención de No-Intro y pospone el artículo —"Bug's Life, A"— mientras que
    Wikipedia lo deja adelante —"A Bug's Life"—. `core()` ya normaliza "the", pero no "a"/"an", y no
    conviene tocarla porque de ella dependen todos los catálogos ya cruzados.
    """
    plain = core(title)
    out = [plain]
    # "A Bug's Life" -> "Bug's Life, A"  y viceversa.
    lead = re.match(r"^(a|an)\s+(.*)$", title.strip(), re.I)
    if lead:
        out.append(core(f"{lead.group(2)}, {lead.group(1)}"))
    trail = re.match(r"^(.*),\s*(a|an)$", title.strip(), re.I)
    if trail:
        out.append(core(f"{trail.group(2)} {trail.group(1)}"))
    return out


# Nombres que no son el lanzamiento comercial. "(Bonus Disc" y "(Promo" importan tanto como
# "(Beta": el DAT lista `Killzone (Europe) (…) (Bonus Disc)` **antes** que el juego, porque ordena
# alfabéticamente y "Europe)" viene antes que "Europe,".
REJECT = ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate", "(Unl",
          "(Bonus Disc", "(Promo", "(Kiosk", "(Preview", "(Trade")

# Prefijos de disco promocional/demo en PAL. Comparten el número con el retail —My Street es
# SCED-51677 y SCES-51677— así que ni el nombre ni el orden alcanzan para distinguirlos.
PROMO_PREFIXES = ("SCED", "SLED")


def rank(full_name: str, serial: str) -> tuple[int, int]:
    """
    Qué tan probable es que esta entrada sea **la edición de tienda**. Menor es mejor.

    El lanzamiento normal se llama `Título (Región) (Idiomas)`; las ediciones promocionales agregan
    un grupo más —`(BMW 1 Series Virtual Test Drive)`— y a veces solo cambian el prefijo del serial.
    Sin esto, `Gran Turismo 4` europeo se quedaba con el serial del disco de demostración de BMW.
    """
    groups = full_name.count("(")
    promo = 1 if serial.split("-")[0].upper() in PROMO_PREFIXES else 0
    return (groups, promo)


def parse_dat(text: str, tags: list[str]) -> dict[str, str]:
    """`{título normalizado: serial}` de las entradas de las regiones pedidas, por prioridad."""
    by_region: dict[str, dict[str, tuple[tuple[int, int], str]]] = {t: {} for t in tags}
    for block in re.findall(r"game\s*\((.*?)\n\)", text, re.S):
        name = re.search(r'name\s+"([^"]+)"', block)
        serial = re.search(r'\n\tserial\s+"([^"]+)"', block)
        region = re.search(r'region\s+"([^"]+)"', block)
        if not (name and serial):
            continue
        tag = region.group(1) if region else ""
        if tag not in by_region:
            continue
        full = name.group(1)
        if any(bad in full for bad in REJECT):
            continue
        # Un juego multi-disco repite serial; y algunos traen varios separados por coma.
        value = serial.group(1).split(",")[0].strip()
        title = re.sub(r"\s*\(.*$", "", full)
        score = rank(full, value)
        for key in keys_for(title):
            best = by_region[tag].get(key)
            if best is None or score < best[0]:
                by_region[tag][key] = (score, value)

    merged: dict[str, str] = {}
    for tag in reversed(tags):          # la primera de la lista debe ganar
        merged.update({k: v for k, (_, v) in by_region[tag].items()})
    return merged


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("dat", help='nombre del DAT, p. ej. "Sony - PlayStation"')
    ap.add_argument("--overwrite", action="store_true",
                    help="recalcular también los seriales ya cargados. Solo para catálogos cuyos "
                         "seriales salen enteros de Redump (PlayStation, PS2, PS3, GameCube): en los "
                         "de Sega vienen de Sega Retro y esto los pisaría.")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    catalog = json.load(open(args.catalog, encoding="utf-8"))
    region = catalog[0]["region"] if catalog else "NTSC-U"
    tags = REGION_TAGS.get(region, ["USA"])

    url = f"{BASE}/{urllib.parse.quote(args.dat)}.dat"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=180) as r:
        serials = parse_dat(r.read().decode("utf-8", "replace"), tags)

    filled = changed = 0
    for entry in catalog:
        had = entry["serial"].strip()
        if had and not args.overwrite:
            continue
        for key in keys_for(entry["title"]):
            if key in serials:
                if had:
                    if serials[key] != had:
                        entry["serial"] = serials[key]
                        changed += 1
                else:
                    entry["serial"] = serials[key]
                    filled += 1
                break

    total = len(catalog) or 1
    have = sum(1 for e in catalog if e["serial"].strip())
    print(f"{os.path.basename(args.catalog)}: {len(catalog)} juegos · +{filled} seriales"
          + (f" · {changed} corregidos" if changed else ""))
    print(f"   serial {have}/{total} ({have * 100 // total}%) · fuente: Redump {tags[0]}")

    if args.dry_run:
        print("   (dry-run: no se escribió)")
        return
    rows = sorted((canonical(e) for e in catalog), key=lambda x: x["slug"])
    body = ",\n".join(json.dumps(x, ensure_ascii=False) for x in rows)
    open(args.catalog, "w", encoding="utf-8").write("[\n" + body + "\n]\n")


if __name__ == "__main__":
    main()
