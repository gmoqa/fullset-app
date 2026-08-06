#!/usr/bin/env python3
"""Repara los títulos que quedaron con el nombre alternativo pegado.

`regional_title` partía el título solo por `•`, pero las listas de Nintendo y PlayStation usan
`''Título''<br/>''Alternativo''<sup>PAL</sup>` sin viñeta. El alternativo quedaba pegado al
principal, con su código de región incluido:

    Avatar: The Last AirbenderAvatar: The Legend of AangPAL

El bug ya está arreglado, pero **el dato guardado no se puede desarmar solo**: el separador se
perdió al escribirlo. Hay que volver a la fuente.

**Cómo se sabe qué fila le corresponde a cada título roto:** reproduciendo el bug. Se aplica la
lógica vieja a cada fila de la lista de Wikipedia; si da exactamente el título que está guardado,
esa es la fila. De ahí sale el título correcto con la lógica nueva. Es un emparejamiento exacto, no
una heurística de parecido.

Se toca **solo `title` y `slug`**. Fecha, editora, serial, carátula y rating vienen de otras fuentes
y están bien; regenerar el catálogo entero los perdería.

    python3 tools/fix_titulos_pegados.py --dry-run   # qué cambiaría
    python3 tools/fix_titulos_pegados.py             # lo escribe
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

import catalog_common as cc  # noqa: E402
from catalog_common import slug, write_catalog  # noqa: E402
from enrich_dates_wikipedia import wikitext  # noqa: E402

CATALOGOS = RAIZ / "data" / "catalogs"
CACHE = Path(__file__).resolve().parent / ".wiki-cache"

# Qué catálogo salió de qué páginas. Solo los afectados: el resto usa listas que sí traen viñeta.
FUENTES = {
    "gamecube-usa.json": ("NTSC-U", ["List of GameCube games"]),
    "gamecube-jp.json": ("NTSC-J", ["List of GameCube games"]),
    "gamecube-eu.json": ("PAL", ["List of GameCube games"]),
    "psx-jp.json": ("NTSC-J", ["List of PlayStation (console) games (A–L)",
                               "List of PlayStation (console) games (M–Z)"]),
    "psx-eu.json": ("PAL", ["List of PlayStation (console) games (A–L)",
                            "List of PlayStation (console) games (M–Z)"]),
}


def regional_title_viejo(text: str, region: str | None) -> str:
    """La implementación **con el bug**, tal cual estaba. Es la clave de emparejamiento."""
    partes = text.split("•")
    if len(partes) > 1 and region:
        for alt in partes[1:]:
            marca = re.search(r"<sup>\s*([A-Za-z/]+)\s*</sup>|\^([A-Za-z]+)", alt)
            codigo = (marca.group(1) or marca.group(2) or "").upper() if marca else ""
            if codigo in cc.TITLE_SUP.get(region, ()):
                return re.sub(r"<sup>[^<]*</sup>|\^[A-Za-z]+", "", alt)
    return partes[0]


def celdas_de_titulo(texto: str) -> list[str]:
    """La primera celda de cada fila de la tabla, cruda."""
    fuera = []
    for bloque in texto.split("\n|-")[1:]:
        campos = [c for c in re.split(r"\n\|(?!\|)", bloque) if c.strip()]
        if campos:
            fuera.append(campos[0])
    return fuera


def texto_pagina(nombre: str) -> str:
    CACHE.mkdir(exist_ok=True)
    ruta = CACHE / (re.sub(r"[^\w]+", "_", nombre) + ".wikitext")
    if ruta.exists():
        return ruta.read_text(encoding="utf-8")
    t = wikitext(nombre)
    ruta.write_text(t, encoding="utf-8")
    return t


def mapa_de_correcciones(paginas: list[str], region: str) -> dict[str, str]:
    """`título como quedó guardado` → `título correcto`, para las filas donde difieren."""
    mapa = {}
    for pagina in paginas:
        for celda in celdas_de_titulo(texto_pagina(pagina)):
            # `clean_cell` llama a `regional_title` por nombre, así que cambiarlo en el módulo
            # alcanza para reproducir el pipeline viejo entero, no solo esa función.
            original = cc.regional_title
            try:
                cc.regional_title = regional_title_viejo
                # Dos reproducciones, porque hubo dos épocas del bug. Con región es el pipeline tal
                # como quedó; **sin** región es el de antes de que `clean_cell` la recibiera, y ahí
                # el título salía crudo con el texto del `<sup>` pegado: "…D-ballNA". Los catálogos
                # de PlayStation tienen entradas de las dos.
                viejos = {cc.clean_cell(celda, region), cc.clean_cell(celda, None)}
            finally:
                cc.regional_title = original
            nuevo = cc.clean_cell(celda, region)
            for viejo in viejos:
                if viejo and nuevo and viejo != nuevo:
                    mapa[viejo] = nuevo
    return mapa


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo muestra")
    args = ap.parse_args()

    total, colisiones = 0, []
    for archivo, (region, paginas) in FUENTES.items():
        ruta = CATALOGOS / archivo
        entradas = json.loads(ruta.read_text(encoding="utf-8"))
        mapa = mapa_de_correcciones(paginas, region)

        slugs = {e["slug"] for e in entradas}
        cambios = []
        for e in entradas:
            nuevo = mapa.get(e["title"])
            if not nuevo or nuevo == e["title"]:
                continue
            s = slug(nuevo)
            # El bug **duplicaba**: el catálogo puede tener ya el juego bajo su nombre correcto, y
            # los dos no se reconocían. Al arreglarlo chocarían, y un slug repetido rompe el lint.
            if s != e["slug"] and s in slugs:
                colisiones.append((archivo, e["title"], nuevo, s))
                continue
            cambios.append((e, nuevo, s))

        for e, nuevo, s in cambios:
            slugs.discard(e["slug"])
            slugs.add(s)
            e["title"], e["slug"] = nuevo, s

        if cambios:
            total += len(cambios)
            print(f"  {archivo}: {len(cambios)} títulos")
            for e, nuevo, _ in cambios[:3]:
                print(f"      → {nuevo!r}")
            if not args.dry_run:
                write_catalog(str(ruta), entradas)

    print(f"\n  {total} títulos corregidos{' (en seco)' if args.dry_run else ''}")
    if colisiones:
        print(f"\n  {len(colisiones)} chocarían con una entrada que ya existe — se dejaron como están:")
        for archivo, viejo, nuevo, s in colisiones[:10]:
            print(f"      {archivo}: {viejo!r} → {nuevo!r} (slug {s} ocupado)")
        print("      son duplicados que creó el mismo bug; hay que resolverlos a mano")
    return 0


if __name__ == "__main__":
    sys.exit(main())
