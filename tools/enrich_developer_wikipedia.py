#!/usr/bin/env python3
"""
Rellena `developer` desde las listas "List of … games" de **Wikipedia** (CC BY-SA 4.0).

Existe porque libretro no publica `metadat/developer/` para todas las consolas —no hay nada para
PlayStation 3, 3DS, GameCube ni DS— pero varias de esas listas **sí traen la columna**, y en algunos
casos el builder ya la estaba leyendo y descartando: el de 3DS documenta sus columnas como
`Título | Desarrolladora | Editora | …`, toma la editora de `celdas[2]` y tira `celdas[1]`.

La columna se busca **por su cabecera** (`Developer`, `Developer(s)`, `Desarrolladora`), no por
índice fijo, igual que en `enrich_dates_wikipedia`: entre PS3 y 3DS la posición no es la misma, y
asumirla asignaría a cada juego la empresa de otra columna.

Solo completa lo que esté **vacío**. `developer` y `publisher` son campos distintos y este script no
toca el segundo: quién hizo el juego no es quién lo vendió.

Uso:  python3 tools/enrich_developer_wikipedia.py <catalogo.json> "<Página 1>" [… ] [--dry-run]
      python3 tools/enrich_developer_wikipedia.py data/catalogs/3ds-usa.json "List of Nintendo 3DS games"
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import clean_cell, core, write_catalog  # noqa: E402
from enrich_dates_wikipedia import row_cells, wikitext  # noqa: E402

# Cabeceras que nombran la columna. `Developer(s)` es la forma más común; las listas traducidas y
# algunas viejas usan otras.
CABECERA = re.compile(r"\b(developer|desarrolladora)s?\b", re.I)


def columna_developer(texto: str) -> int | None:
    """Índice de la celda con la desarrolladora, leído de la cabecera de la tabla.

    Las cabeceras vienen como `! width=20%|Developer(s)` o `! scope="col" | Developer`: se descarta
    lo que esté antes del último `|` antes de comparar.
    """
    encabezados = []
    for linea in texto.split("\n"):
        linea = linea.strip()
        if not linea.startswith("!"):
            continue
        if encabezados and not linea.startswith("!"):
            break
        for celda in linea[1:].split("!!"):
            encabezados.append(celda.rsplit("|", 1)[-1].strip())
        if len(encabezados) > 12:      # ya pasamos la cabecera; no seguir leyendo la tabla entera
            break
    for i, h in enumerate(encabezados):
        if CABECERA.search(h):
            return i
    return None


def indice(texto: str, columna: int) -> dict[str, str]:
    """`{título normalizado: desarrolladora}` de una página."""
    out: dict[str, str] = {}
    for bloque in texto.split("\n|-")[1:]:
        celdas = row_cells(bloque)
        if len(celdas) <= columna:
            continue
        titulo = clean_cell(celdas[0])
        if not titulo or titulo.lower().startswith("title"):
            continue
        # Varias listas ponen más de una empresa separadas por coma o `<br>`; vale la primera, que es
        # la principal. Igual criterio que usa el builder para la editora.
        dev = re.split(r"<br\s*/?>|,", clean_cell(celdas[columna]))[0].strip()
        if dev and dev not in ("-", "—", "N/A"):
            out.setdefault(core(titulo), dev)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("catalog")
    ap.add_argument("pages", nargs="+", help="títulos de las páginas de Wikipedia")
    ap.add_argument("--column", type=int,
                    help="índice de la celda con la desarrolladora, si la cabecera no la nombra")
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    entradas = json.load(open(a.catalog, encoding="utf-8"))
    devs: dict[str, str] = {}
    for pagina in a.pages:
        texto = wikitext(pagina)
        col = a.column if a.column is not None else columna_developer(texto)
        if col is None:
            print(f"   ({pagina!r}: la tabla no nombra una columna de desarrolladora)")
            continue
        encontrados = indice(texto, col)
        print(f"   {pagina[:52]:54} columna {col} · {len(encontrados)} desarrolladoras")
        for k, v in encontrados.items():
            devs.setdefault(k, v)

    puestos = 0
    for e in entradas:
        if e.get("developer", "").strip():
            continue
        v = devs.get(core(e["title"]))
        if v:
            e["developer"] = v
            puestos += 1
    n = len(entradas) or 1
    con = sum(1 for e in entradas if e.get("developer", "").strip())
    print(f"  {os.path.basename(a.catalog)}: +{puestos} · {con}/{len(entradas)} ({con * 100 // n}%)")
    if not a.dry_run and puestos:
        write_catalog(a.catalog, entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
