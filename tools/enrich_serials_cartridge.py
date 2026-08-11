#!/usr/bin/env python3
"""
Rellena `serial` de las consolas de **cartucho** desde `metadat/serial/` de libretro-database.

Es el hermano de `enrich_serials_redump.py`, que hace lo mismo para las de disco. Existen aparte
porque los dos DAT tienen **forma distinta**: el de Redump trae `name` y un campo `region` propio;
el de cartucho trae `comment "Título (Japan) (En,Fr)"` y el serial con la región en el sufijo
(`NUS-NGEJ-JPN`). Acá la región hay que leerla del comentario.

Por qué hace falta y no alcanzaba `catalog_common.dat_serials`: ese elige **un** serial por título,
prefiriendo el americano, porque se usa al construir catálogos NTSC-U. Aplicado a un catálogo
japonés le pondría el número de la caja equivocada — que es justo lo que el lint rechaza.

Solo completa lo que esté **vacío**: nunca pisa un serial ya cargado.

    python3 tools/enrich_serials_cartridge.py data/catalogs/n64-jp.json "Nintendo - Nintendo 64"
    python3 tools/enrich_serials_cartridge.py data/catalogs/n64-jp.json "Nintendo - Nintendo 64" --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import _regiones, core, fetch, region_de_serial, write_catalog  # noqa: E402

BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/serial/"

# Región del catálogo -> etiquetas del DAT, **en orden de preferencia**. `World` cierra las tres
# porque un cartucho publicado para todos los mercados es el de cualquiera de ellos.
ETIQUETAS = {
    "NTSC-U": ["USA", "World"],
    "NTSC-J": ["Japan", "World"],
    "PAL": ["Europe", "Australia", "World"],
}

# Volcados que no son la edición vendida.
DESCARTAR = re.compile(r"\((Beta|Proto|Demo|Sample|Kiosk|Debug|Pirate|Unl|Aftermarket)", re.I)


def indice(texto: str, etiquetas: list[str], region: str) -> dict[str, str]:
    """`{título normalizado: serial}` de las regiones pedidas, la primera de la lista gana.

    Se descarta el serial cuyo **sufijo contradiga** [region] aunque la entrada del DAT calce. Pasa
    con los volcados marcados `(World)`: `Hogan's Alley (World)` trae `NES-HA-USA`, o sea el número
    de la caja americana, y tomarlo para el catálogo japonés le pone el catalog number de otro país.
    El cartucho será el mismo; el número impreso, no.
    """
    por_etiqueta: dict[str, dict[str, str]] = {t: {} for t in etiquetas}
    for comentario, serial in re.findall(r'game\s*\(\s*comment\s+"([^"]+)"\s*\n\s*serial\s+"([^"]+)"', texto):
        if DESCARTAR.search(comentario):
            continue
        suyas = _regiones(comentario)
        for t in etiquetas:
            if t.upper() in suyas:
                # Un juego puede traer varios seriales separados por coma: vale el primero.
                s = serial.split(",")[0].strip()
                dice = region_de_serial(s)
                if dice is None or dice == region:
                    por_etiqueta[t].setdefault(core(comentario), s)
                break
    fusion: dict[str, str] = {}
    for t in reversed(etiquetas):     # se recorre al revés para que la primera pise a las demás
        fusion.update(por_etiqueta[t])
    return fusion


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("catalog")
    ap.add_argument("dat", help='nombre del DAT, p. ej. "Nintendo - Nintendo 64"')
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    ap.add_argument("--fix-region", action="store_true",
                    help="además, vacía los seriales ya cargados cuyo sufijo sea de otra región "
                         "(quedaron de cuando la regla miraba el prefijo, que en Nintendo es el "
                         "producto y no el mercado) y los vuelve a llenar con el correcto")
    a = ap.parse_args()

    entradas = json.load(open(a.catalog, encoding="utf-8"))
    region = entradas[0]["region"] if entradas else "NTSC-U"
    etiquetas = ETIQUETAS.get(region)
    if not etiquetas:
        raise SystemExit(f"región desconocida: {region!r}")

    texto = fetch(BASE + urllib.parse.quote(a.dat) + ".dat")
    if isinstance(texto, bytes):
        texto = texto.decode("utf-8", "replace")
    seriales = indice(texto, etiquetas, region)

    puestos = limpiados = 0
    if a.fix_region:
        for e in entradas:
            dice = region_de_serial(e.get("serial", ""))
            if dice and dice != region:
                e["serial"] = ""
                limpiados += 1
    for e in entradas:
        if str(e.get("serial", "")).strip():
            continue
        s = seriales.get(core(e["title"]))
        if s:
            e["serial"] = s
            puestos += 1
    n = len(entradas) or 1
    con = sum(1 for e in entradas if e["serial"])
    extra = f" · {limpiados} de otra región vaciados" if limpiados else ""
    print(f"  {os.path.basename(a.catalog)}: +{puestos} seriales{extra} · {con}/{len(entradas)} ({con * 100 // n}%)")
    if not a.dry_run and (puestos or limpiados):
        write_catalog(a.catalog, entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
