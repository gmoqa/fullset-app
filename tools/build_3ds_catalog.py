#!/usr/bin/env python3
"""
Catálogo de **Nintendo 3DS**, las tres regiones, **solo cartuchos físicos**.

Es el primero que se arma **desde el DAT y no desde Wikipedia**, y el motivo es que en la 3DS
Wikipedia no sirve como fuente principal: su lista tiene 1.078 juegos y **más de la mitad son de
eShop**, que nunca existieron en cartucho. Para una app de colección física eso no es un juego que
puedas tener; es ruido que además hunde la cobertura de carátulas (42% contra 93%).

La fuente es el DAT de **No-Intro** que publica libretro-database, que es literalmente la lista de
volcados de cartucho: si algo está ahí, existió en plástico. Trae además el `serial` impreso en la
etiqueta —`CTR-P-BP4J`— así que esta es la primera consola del dataset con **serial al 100%**.

El precio de invertir las fuentes es que el título viene en formato de volcado y hay que convertirlo
(`Sims 3, The (USA) (En,Fr,Es)` -> `The Sims 3`), y que el año y la editora solo aparecen donde el
título cruza con Wikipedia: **51% en NTSC-U, 46% en PAL y 27% en NTSC-J**. Se eligió a conciencia
contra la alternativa —Wikipedia primero, filtrando por el DAT— que daba cada entrada completa pero
**la mitad de los cartuchos**: 216 contra 419 en NTSC-U. En una app que mide completitud ("148 of
1893"), el denominador tiene que ser la biblioteca real. Un juego sin año se puede tener igual; uno
que no está en la lista, no.

    python3 tools/build_3ds_catalog.py            # escribe los tres catálogos
    python3 tools/build_3ds_catalog.py --dry-run  # solo reporta

Después, las carátulas:

    for r in usa jp eu; do
      python3 tools/enrich_covers_libretro.py data/catalogs/3ds-$r.json Nintendo_-_Nintendo_3DS --prefer ...
    done
"""

from __future__ import annotations

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import core, fetch, slug, write_catalog  # noqa: E402
from nointro import REGIONES, cartuchos as cartuchos_dat  # noqa: E402
from enrich_dates_wikipedia import wikitext, row_cells  # noqa: E402

DAT = ("https://raw.githubusercontent.com/libretro/libretro-database/master/"
       "metadat/no-intro/Nintendo%20-%20Nintendo%203DS.dat")
PAGINA = "List of Nintendo 3DS games"
PLATAFORMA = "Nintendo 3DS"
SALIDA = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs")

ARCHIVOS = {"NTSC-U": "3ds-usa.json", "NTSC-J": "3ds-jp.json", "PAL": "3ds-eu.json"}

# Fechas de Wikipedia. Esta lista no usa `{{dts}}` como el resto sino el módulo de ordenamiento.
FECHA = re.compile(r"\{\{#invoke:Date table sorting\|main\|(\d{4})(?:\|(\d{1,2}))?(?:\|(\d{1,2}))?")
# Columnas de región de la tabla: Título | Desarrolladora | Editora | JP | NA | Australasia | EU.
COLUMNA = {"NTSC-J": 3, "NTSC-U": 4, "PAL": 6}


def cartuchos() -> dict[str, list[dict]]:
    """Los cartuchos del DAT, por región nuestra, ya deduplicados y con su serial."""
    texto = fetch(DAT)
    if isinstance(texto, bytes):
        texto = texto.decode("utf-8", "replace")
    return cartuchos_dat(texto, core)


def desde_wikipedia() -> dict[str, dict]:
    """Año, fecha y editora por región, indexados por título normalizado."""
    texto = wikitext(PAGINA)
    span = re.search(r'colspan="?4"?', texto)
    if not span:
        raise SystemExit("la tabla de 3DS cambió de forma: no está el bloque de 4 regiones")
    tabla = texto[texto.rfind("{|", 0, span.start()):]

    datos: dict[str, dict] = {r: {} for r in COLUMNA}
    for bloque in tabla.split("\n|-")[1:]:
        celdas = row_cells(bloque)
        if len(celdas) < 7:
            continue
        from catalog_common import clean_cell
        titulo = clean_cell(celdas[0])
        if not titulo or titulo.lower().startswith("title"):
            continue
        editora = clean_cell(celdas[2]).split(",")[0].strip()
        for region, i in COLUMNA.items():
            m = FECHA.search(celdas[i])
            if not m:
                continue  # no salió en ese mercado
            año, mes, dia = m.group(1), m.group(2), m.group(3)
            iso = año + (f"-{int(mes):02d}" if mes else "") + (f"-{int(dia):02d}" if mes and dia else "")
            datos[region][core(titulo)] = {"year": int(año), "releaseDate": iso, "publisher": editora}
    return datos


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    carts = cartuchos()
    wiki = desde_wikipedia()

    for region, lista in carts.items():
        extra = wiki.get(region, {})
        entradas = []
        for c in lista:
            d = extra.get(core(c["title"]), {})
            entradas.append({
                "title": c["title"], "platform": PLATAFORMA, "region": region,
                "year": d.get("year"), "releaseDate": d.get("releaseDate", ""),
                "publisher": d.get("publisher", ""), "genre": "", "slug": slug(c["title"]),
                "serial": c["serial"], "coverUrl": "", "rating": "",
            })
        # Puede haber dos volcados con títulos distintos que colapsen al mismo slug.
        unicos, vistos = [], set()
        for e in entradas:
            if e["slug"] and e["slug"] not in vistos:
                vistos.add(e["slug"])
                unicos.append(e)
        n = len(unicos) or 1
        print(f"  {ARCHIVOS[region]:14} {len(unicos):4} cartuchos · "
              f"año {sum(1 for e in unicos if e['year']) * 100 // n}% · "
              f"editora {sum(1 for e in unicos if e['publisher']) * 100 // n}% · "
              f"serial {sum(1 for e in unicos if e['serial']) * 100 // n}%")
        if not a.dry_run:
            write_catalog(os.path.join(SALIDA, ARCHIVOS[region]), unicos)
    return 0


if __name__ == "__main__":
    sys.exit(main())
