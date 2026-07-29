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


def cover_index(repo: str, prefer: str) -> dict:
    tree = json.loads(fetch(f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master?recursive=1"))["tree"]
    host = COVER_HOST.format(repo)
    prefs = [prefer, "(World", "(USA", "(Europe", "(Brazil"]

    def rank(name: str) -> int:
        base = -100 if any(b in name for b in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate")) else 0
        for i, r in enumerate(prefs):
            if r in name:
                return base + len(prefs) - i
        return base

    idx = {}
    for t in tree:
        p = t["path"]
        if not (p.startswith("Named_Boxarts/") and p.endswith(".png")):
            continue
        name = p[len("Named_Boxarts/"):-4]
        k = core(name)
        if k not in idx or rank(name) > rank(idx[k][1]):
            idx[k] = (host + urllib.parse.quote(name + ".png"), name)
    return {k: v[0] for k, v in idx.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("repo")
    ap.add_argument("--prefer", required=True)
    a = ap.parse_args()

    cat = json.load(open(a.catalog, encoding="utf-8"))
    covers = cover_index(a.repo, a.prefer)
    filled = 0
    for e in cat:
        if str(e.get("coverUrl") or "").strip():
            continue
        url = covers.get(core(e["title"]))
        if url:
            e["coverUrl"] = url
            filled += 1
    write_catalog(a.catalog, cat)
    print(f"carátulas rellenadas: {filled}/{len(cat)}")


if __name__ == "__main__":
    main()
