#!/usr/bin/env python3
"""
Catálogo de **Atari 2600** (NTSC-U), desde `List of Atari 2600 games` de Wikipedia.

Tiene builder propio y no usa el `build()` genérico de `catalog_common` porque esa página **no
tiene eje de región**: donde las demás listas traen un bloque `colspan` con Japón / Norteamérica /
Europa y una fecha por mercado, esta trae una sola columna `Year`. El genérico se ancla justo a ese
bloque, así que acá no encuentra nada. Tampoco hay una tabla única: son dos, con columnas distintas.

    Games published by Atari and Sears   Atari title | Sears title | Designer | Year | Notes | CX
    Games published by third parties     Title | Developer | Publisher | Year | Genre | Notes

De la primera sale el **serial** —el número CX impreso en el cartucho— y de la segunda el
**género**, que es raro: en casi todo el dataset ese campo viene vacío porque libretro no lo publica
para esas consolas. Para la 2600 lo publica *y además* Wikipedia lo trae en columna.

**Solo NTSC-U, y es un hueco conocido, no un hecho sobre la consola.** La 2600 sí tuvo mercado PAL
—libretro tiene 139 carátulas europeas y 25 japonesas de la Atari 2800— pero esta página no
distingue mercados, así que no hay de dónde sacar esas listas. Es distinto del caso de la SG-1000
(que solo existió en Japón) o de la TurboGrafx (que nunca se vendió en Europa): ahí la región única
es la verdad; acá es lo que tenemos.

**Qué queda afuera y por qué** — la página tiene seis tablas y solo entran dos:

  - `Homebrew games`, `Prototypes`: no se vendieron en su época.
  - `Official aftermarket releases`, `Multi-game cartridges`: son reediciones de los **2020** para
    la Atari 2600+, no la biblioteca histórica.
  - `Xonox double-sided cartridges`: cartuchos 2-en-1 cuyos juegos, dice la propia sección, "were
    also available individually" — ya están en la tabla de terceros. Entrarían duplicados bajo
    títulos compuestos tipo `Artillery Duel/Ghost Manor`.
  - `Non-game cartridges`: `Color Bar Generator`, `Venetian Blinds`. No son juegos.

Uso:

    python3 tools/build_atari2600_catalog.py            # escribe el catálogo
    python3 tools/build_atari2600_catalog.py --dry-run  # solo reporta

Después hay que enriquecer, como con cualquier otro:

    python3 tools/enrich_covers_libretro.py data/catalogs/atari-2600-usa.json Atari_-_2600 --prefer "(USA"
    python3 tools/enrich_meta_libretro.py   data/catalogs/atari-2600-usa.json "Atari - 2600"
"""

from __future__ import annotations

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import clean_cell, slug, write_catalog  # noqa: E402
from enrich_dates_wikipedia import wikitext  # noqa: E402

PAGINA = "List of Atari 2600 games"
PLATAFORMA = "Atari 2600"
REGION = "NTSC-U"
SALIDA = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "atari-2600-usa.json")

# Sección -> de qué celda sale cada campo. El título es siempre la 0.
SECCIONES = {
    "Games published by Atari and Sears": {"serial": 5},
    "Games published by third parties": {"publisher": 2, "genre": 4},
}


def seccion(texto: str, nombre: str) -> str:
    """El trozo que va desde ese encabezado hasta el **siguiente encabezado de cualquier nivel**.

    El corte tiene que ser `\\n==` a secas y no `\\n== `: las dos tablas que queremos son de nivel 2,
    pero cuelgan de ellas subsecciones de **nivel 3** que no queremos —`Multi-game cartridges` bajo
    la de Atari, `Xonox` y `Non-game cartridges` bajo la de terceros—. Buscando el próximo nivel 2 se
    las pasaba por arriba y entraban al catálogo: así aparecieron "10 Games in 1", "2048 2600" y los
    homebrew de AtariAge, que es justo lo que se decidió dejar afuera.
    """
    i = texto.find("== " + nombre)
    if i < 0:
        raise SystemExit(f"no está la sección {nombre!r}: ¿cambió la página?")
    j = texto.find("\n==", i + 10)
    return texto[i:j] if j > 0 else texto[i:]


def filas(texto: str, campos: dict[str, int]) -> list[dict]:
    out = []
    for bloque in texto.split("\n|-")[1:]:
        celdas = [c for c in re.split(r"\n\|(?!\|)", bloque) if c.strip()]
        # La fila de cabecera vuelve a aparecer al partir por `|-`: sus celdas empiezan con `!`.
        if not celdas or celdas[0].strip().startswith("!"):
            continue
        titulo = clean_cell(celdas[0])
        if not titulo:
            continue
        e = {"title": titulo}
        for clave, idx in campos.items():
            e[clave] = clean_cell(celdas[idx]) if len(celdas) > idx else ""
        # `{{dts|1980|7}}` -> año 1980, fecha "1980-07". El mes puede faltar; el día nunca está.
        m = re.search(r"\{\{dts\|(\d{4})(?:\|(\d{1,2}))?", bloque)
        e["year"] = int(m.group(1)) if m else None
        e["releaseDate"] = (m.group(1) + (f"-{int(m.group(2)):02d}" if m.group(2) else "")) if m else ""
        out.append(e)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    texto = wikitext(PAGINA)
    entradas, vistos = [], set()
    for nombre, campos in SECCIONES.items():
        for e in filas(seccion(texto, nombre), campos):
            s = slug(e["title"])
            if not s or s in vistos:
                continue
            vistos.add(s)
            entradas.append({
                "title": e["title"], "platform": PLATAFORMA, "region": REGION,
                "year": e["year"], "releaseDate": e["releaseDate"],
                # Los de la primera tabla son de Atari por definición: la sección **es** eso.
                "publisher": e.get("publisher") or ("Atari" if "serial" in campos else ""),
                "genre": e.get("genre", ""), "slug": s,
                # El cartucho dice "CX2618"; Wikipedia lo escribe con espacio.
                "serial": re.sub(r"^CX\s+", "CX", e.get("serial", "").strip()),
                "coverUrl": "", "rating": "",
            })

    n = len(entradas) or 1
    print(f"  {len(entradas)} juegos")
    for campo in ("year", "publisher", "genre", "serial"):
        print(f"     {campo:10} {sum(1 for e in entradas if e[campo]) * 100 // n}%")
    if not a.dry_run:
        write_catalog(SALIDA, entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
