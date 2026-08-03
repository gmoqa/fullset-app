#!/usr/bin/env python3
"""
Rellena `coverUrl` vacíos de un catálogo desde libretro-thumbnails, **prefiriendo** las carátulas de
una región (p. ej. `(Japan` para catálogos NTSC-J, `(Europe` para PAL). Match por título normalizado
(`core`), evitando Beta/Proto. Reproducible; solo toca los que están sin carátula.

Uso:  python3 tools/enrich_covers_libretro.py <catalogo.json> <repo-libretro> --prefer "(Japan"
"""
import json, os, re, sys, argparse, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import fetch, core, COVER_HOST, write_catalog


CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".libretro-trees")


def listado(repo: str) -> list[dict]:
    """
    Los nombres de archivo de `Named_Boxarts`, cacheados en disco por repositorio.

    La API de GitHub sin autenticar da **60 pedidos por hora** y cada repo cuesta dos. Los tres
    catálogos regionales de una consola comparten repositorio, así que sin caché una pasada por los
    34 catálogos gasta 68 pedidos y se corta a la mitad — que fue exactamente lo que pasó, y encima
    en silencio, porque el error sale por stderr y el resumen igual imprime "0 corregidas".
    """
    os.makedirs(CACHE, exist_ok=True)
    guardado = os.path.join(CACHE, f"{repo}.json")
    if os.path.exists(guardado):
        return json.load(open(guardado, encoding="utf-8"))
    # Se pide SOLO el subárbol de carátulas, no el recursivo del repo entero: en los repos grandes
    # (PlayStation tiene decenas de miles de archivos entre boxarts, snaps y títulos) la API
    # responde 500. Además baja una fracción de los datos, porque lo demás no lo usamos.
    root = json.loads(fetch(f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master"))
    boxarts = next((e for e in root["tree"] if e["path"] == "Named_Boxarts"), None)
    if boxarts is None:
        raise SystemExit(f"{repo}: no tiene carpeta Named_Boxarts")
    tree = json.loads(fetch(
        f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/{boxarts['sha']}"
    ))["tree"]
    json.dump(tree, open(guardado, "w", encoding="utf-8"))
    return tree


def cover_index(repo: str, prefer: str) -> dict:
    tree = listado(repo)
    host = COVER_HOST.format(repo)
    # Se compara contra las regiones que **declara** el nombre, no por subcadena. El nombre lista
    # varias separadas por coma cuando el juego salió con un solo cartucho para más de un mercado:
    # el 32X compartía el americano con Japón, así que la tapa de EE.UU. de *Doom* se llama
    # `Doom (Japan, USA) (En)`. Buscar la subcadena "(USA" no la encontraba —el paréntesis no está
    # pegado— y ganaba `Doom (Europe)`, que sí calzaba. 1953 carátulas apuntaban a otra región.
    quiero = prefer.strip("( ").upper()
    orden = [quiero, "WORLD", "USA", "EUROPE", "JAPAN", "BRAZIL"]

    def regiones(name: str) -> set[str]:
        m = re.search(r"\(([^)]*)\)", name)
        return {x.strip().upper() for x in m.group(1).split(",")} if m else set()

    def rank(name: str) -> int:
        base = -100 if any(b in name for b in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate")) else 0
        suyas = regiones(name)
        for i, r in enumerate(orden):
            if r in suyas:
                return base + len(orden) - i
        return base

    idx = {}
    for t in tree:
        p = t["path"]
        if not p.endswith(".png"):
            continue
        name = p[:-4]
        k = core(name)
        if k not in idx or rank(name) > rank(idx[k][1]):
            idx[k] = (host + urllib.parse.quote(name + ".png"), name)
    return {k: v[0] for k, v in idx.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("repo")
    ap.add_argument("--prefer", required=True)
    ap.add_argument("--overwrite", action="store_true",
                    help="reemplazar también las que ya tienen carátula, si la de esta región es "
                         "mejor. Necesario después de arreglar el ranking: las que quedaron mal no "
                         "se corrigen solas porque el campo ya está lleno.")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    cat = json.load(open(a.catalog, encoding="utf-8"))
    covers = cover_index(a.repo, a.prefer)
    filled = changed = 0
    ejemplos = []
    for e in cat:
        url = covers.get(core(e["title"]))
        if not url:
            continue
        actual = str(e.get("coverUrl") or "").strip()
        if not actual:
            e["coverUrl"] = url
            filled += 1
        elif a.overwrite and actual != url:
            if len(ejemplos) < 8:
                nombre = lambda u: urllib.parse.unquote(u.rsplit("/", 1)[-1])[:-4]
                ejemplos.append((e["title"], nombre(actual), nombre(url)))
            e["coverUrl"] = url
            changed += 1
    print(f"{os.path.basename(a.catalog)}: +{filled} carátulas"
          + (f" · {changed} corregidas de región" if changed else ""))
    for t, viejo, nuevo in ejemplos:
        print(f"    {t[:30]:<32} {viejo[:34]:<36} → {nuevo[:34]}")
    if a.dry_run:
        print("   (dry-run: no se escribió)")
        return
    write_catalog(a.catalog, cat)


if __name__ == "__main__":
    main()
