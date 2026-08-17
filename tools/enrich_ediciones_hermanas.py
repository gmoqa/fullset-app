#!/usr/bin/env python3
"""
Propaga metadatos entre **ediciones del mismo juego** dentro de un catálogo, usando el serial.

En Nintendo DS el serial son cuatro caracteres y el **último es el idioma/mercado**: `1 vs 100` sale
como `YJYP` (Europa genérica), `1 gegen 100` como `YJYD` (Alemania) y `1 contre 100` como `YJYF`
(Francia). Son cartuchos distintos —y por eso están las tres entradas— pero es **el mismo juego**,
así que quien lo desarrolló es el mismo.

Eso importa porque los catálogos de DS y 3DS se arman del DAT de No-Intro y sus metadatos salen de
Wikipedia, que lista el título comercial **en inglés**: `1 gegen 100` no cruza nunca, aunque su
hermano inglés sí. Propagando dentro del grupo, la edición alemana hereda lo que encontró la inglesa.

**Solo se propagan los campos que no cambian entre ediciones**: desarrolladora, editora y género.
La **fecha no**, a propósito — la edición alemana suele salir semanas o meses después que la
inglesa, y copiarla inventaría un lanzamiento que no ocurrió.

Uso:  python3 tools/enrich_ediciones_hermanas.py <catalogo.json> [--dry-run]
"""
import argparse
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import write_catalog  # noqa: E402

# Campos que son del **juego**, no de la edición. La fecha y el catalog number son de la edición.
COMPARTIDOS = ("developer", "publisher", "genre")


def grupos(entradas: list[dict]) -> dict[str, list[dict]]:
    """Entradas agrupadas por juego: el serial sin su última letra, que es el idioma.

    Las dos formas de Nintendo portátil, que solo cambian en el prefijo:

        DS    `YJYP`         -> grupo `YJY`
        3DS   `CTR-P-BMFP`   -> grupo `CTR-P-BMF`

    Cualquier otro largo se ignora: en PlayStation o Sega el último carácter no es el idioma y
    agrupar por ahí juntaría juegos que no tienen nada que ver.
    """
    out: dict[str, list[dict]] = defaultdict(list)
    for e in entradas:
        s = str(e.get("serial") or "").strip()
        if len(s) == 4 or (len(s) == 10 and s.startswith("CTR-")):
            out[s[:-1]].append(e)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("catalog")
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    entradas = json.load(open(a.catalog, encoding="utf-8"))
    puestos = {c: 0 for c in COMPARTIDOS}
    for _pref, grupo in grupos(entradas).items():
        if len(grupo) < 2:
            continue
        for campo in COMPARTIDOS:
            valor = next((str(g.get(campo) or "").strip() for g in grupo
                          if str(g.get(campo) or "").strip()), "")
            if not valor:
                continue
            for g in grupo:
                if not str(g.get(campo) or "").strip():
                    g[campo] = valor
                    puestos[campo] += 1

    total = sum(puestos.values())
    detalle = " · ".join(f"{c} +{n}" for c, n in puestos.items() if n)
    print(f"  {os.path.basename(a.catalog)}: +{total}" + (f" ({detalle})" if detalle else ""))
    if not a.dry_run and total:
        write_catalog(a.catalog, entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
