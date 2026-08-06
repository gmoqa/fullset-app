#!/usr/bin/env python3
"""Tests de `catalog_common`, con los defectos reales que le dieron origen a cada regla.

    python3 tools/test_catalog_common.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import regional_title, slug  # noqa: E402

# (nombre, texto crudo del wikitext, región, resultado esperado)
CASOS_TITULO = [
    (
        "separador <br/> sin viñeta",
        # El defecto: partiendo solo por `•`, el alternativo quedaba pegado al principal y salían
        # 109 títulos así en los catálogos publicados de GameCube y PlayStation.
        "Avatar: The Last Airbender<br/>Avatar: The Legend of Aang<sup>PAL</sup>",
        "PAL", "Avatar: The Legend of Aang",
    ),
    (
        "el mismo, desde la otra región",
        "Avatar: The Last Airbender<br/>Avatar: The Legend of Aang<sup>PAL</sup>",
        "NTSC-U", "Avatar: The Last Airbender",
    ),
    (
        "marca en el título principal",
        # `''Mia Hamm Soccer 64''<sup>NA</sup><br/>…`: la marca del principal va **antes** del <br>,
        # así que no la limpiaba nadie y quedaba "Mia Hamm Soccer 64NA".
        "Mia Hamm Soccer 64<sup>NA</sup><br/>Michael Owen's WLS 2000<sup>PAL</sup>",
        "NTSC-U", "Mia Hamm Soccer 64",
    ),
    (
        "un código sub-regional no reemplaza nada",
        # FRA es Francia, no PAL: el catálogo PAL cubre Europa entera y se queda con el nombre
        # general. Tratarlo como PAL renombraría el juego para todos los europeos.
        "All Star Tennis '99<br/>Yannick Noah All Star Tennis '99<sup>FRA</sup>",
        "PAL", "All Star Tennis '99",
    ),
    (
        "la viñeta sigue funcionando",
        # El formato que ya se soportaba, que no se puede romper al agregar el <br>.
        "Final Fantasy VI<br />•Final Fantasy III<sup>NA</sup>",
        "NTSC-U", "Final Fantasy III",
    ),
    (
        "<br /> con espacio y mayúsculas",
        "Título<BR />Alternativo<sup>JP</sup>",
        "NTSC-J", "Alternativo",
    ),
    (
        "sin alternativo",
        "Super Mario 64", "PAL", "Super Mario 64",
    ),
    (
        "sin región no se elige nada",
        "Título<br/>Alternativo<sup>NA</sup>", None, "Título",
    ),
]

# El slug se deriva del título: si el título viene sucio, el slug también, y el lint no lo ve
# porque para él son coherentes entre sí.
CASOS_SLUG = [
    ("diacríticos se transliteran", "Astérix", "asterix"),
    ("ampersand como 'and'", "Chip & Dale", "chip-and-dale"),
]


def main() -> int:
    fallos = 0
    print("  regional_title")
    for nombre, texto, region, esperado in CASOS_TITULO:
        real = regional_title(texto, region).strip()
        ok = real == esperado
        fallos += not ok
        print(f"    {'OK   ' if ok else 'FALLA'} {nombre}")
        if not ok:
            print(f"          esperaba {esperado!r}, vino {real!r}")

    print("  slug")
    for nombre, texto, esperado in CASOS_SLUG:
        real = slug(texto)
        ok = real == esperado
        fallos += not ok
        print(f"    {'OK   ' if ok else 'FALLA'} {nombre}")
        if not ok:
            print(f"          esperaba {esperado!r}, vino {real!r}")

    total = len(CASOS_TITULO) + len(CASOS_SLUG)
    print(f"\n  {total - fallos}/{total} casos")
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
