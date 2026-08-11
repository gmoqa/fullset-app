#!/usr/bin/env python3
"""
Lectura de los DAT de **No-Intro** que publica libretro-database, para las consolas donde la lista
de cartuchos es mejor fuente que Wikipedia.

Cuándo se usa esto en vez del camino normal: cuando la lista de Wikipedia **mezcla lo físico con lo
digital**. En 3DS y DS más de la mitad de los "juegos" son de eShop o DSiWare y nunca existieron en
cartucho — para una app de colección física eso no es algo que puedas tener. El DAT, en cambio, es
literalmente la lista de volcados de cartucho: si algo está ahí, existió en plástico. Y trae el
`serial` impreso en la etiqueta, que ninguna otra fuente da.

Lo que el DAT **no** trae es año ni editora: eso lo aporta Wikipedia después, y cada consola tiene su
propia forma de tabla, así que ese parseo vive en el builder de cada una.
"""

from __future__ import annotations

import re

# Región del DAT -> la nuestra. PAL es una **unión de territorios**, igual que en los catálogos Sega:
# un cartucho alemán o australiano es PAL. Corea, Taiwán y China quedan afuera porque no tenemos
# región para ellos, y meterlos en otra sería mentir sobre dónde salió ese cartucho.
REGIONES = {
    "NTSC-U": {"USA", "Canada"},
    "NTSC-J": {"Japan"},
    "PAL": {"Europe", "Germany", "France", "Spain", "Italy", "Australia", "Netherlands",
            "United Kingdom", "Russia", "Sweden", "Denmark", "Norway", "Finland", "Portugal",
            "Greece", "Belgium", "Austria", "Switzerland", "Poland"},
}

# Lo que está en el DAT y **no** es un cartucho que se vendió. Ojo que `(Rev N)` no entra acá: una
# revisión sigue siendo el mismo cartucho, y se resuelve deduplicando por título.
RUIDO = re.compile(
    r"\(.*(eShop|Virtual Console|Demo|Beta|Proto|Kiosk|Sample|Debug|Promo|Trial).*\)", re.I)

# El DAT lista cada volcado con su región y su serial, uno debajo del otro.
ENTRADA = re.compile(
    r'game\s*\(\s*name\s+"([^"]+)"\s*\n\s*region\s+"([^"]+)"\s*\n\s*serial\s+"([^"]+)"')


def titulo_retail(nombre: str) -> str:
    """El nombre de volcado convertido en el título como se lee en la caja.

    Tres reglas, y alcanzan: se van los paréntesis técnicos —región, idiomas, revisión—, el artículo
    pospuesto vuelve al frente (`Sims 3, The` -> `The Sims 3`) y el guión rodeado de espacios, que es
    como No-Intro escribe los dos puntos porque `:` no puede ir en un nombre de archivo, vuelve a ser
    `: ` (`Zero Escape - Zero Time Dilemma` -> `Zero Escape: Zero Time Dilemma`).
    """
    n = re.sub(r"\s*\([^)]*\)", "", nombre)
    n = re.sub(r"^(.*?), (The|A|An)\b", r"\2 \1", n)
    return re.sub(r"\s+-\s+", ": ", n).strip()


def cartuchos(texto: str, core) -> dict[str, list[dict]]:
    """Los cartuchos del DAT por región nuestra, ya filtrados y deduplicados.

    [core] se pasa como parámetro y no se importa para que este módulo no dependa de
    `catalog_common` (que arrastra red y escritura de catálogos): acá solo se parsea texto.
    """
    de_region = {pais: region for region, paises in REGIONES.items() for pais in paises}
    out: dict[str, list[dict]] = {r: [] for r in REGIONES}
    vistos: dict[str, set] = {r: set() for r in REGIONES}
    for nombre, pais, serial in ENTRADA.findall(texto):
        region = de_region.get(pais)
        if region is None or RUIDO.search(nombre):
            continue
        titulo = titulo_retail(nombre)
        clave = core(titulo)
        if not clave or clave in vistos[region]:
            continue
        vistos[region].add(clave)
        out[region].append({"title": titulo, "serial": serial.strip()})
    return out
