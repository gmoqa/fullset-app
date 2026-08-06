#!/usr/bin/env python3
"""Escribe en `platforms.json` cuántos juegos tiene cada consola en cada región.

Por qué existe: la grilla de "Add game" muestra "668 games" debajo de cada cubo. Para saber ese
número la app abría y parseaba el catálogo de **cada** consola —9,4 MB de JSON, con `psx-jp.json`
en 1,1 MB— durante la composición, o sea en el hilo principal. Medido en un S22: 50 fotogramas
perdidos al abrir la grilla y un fotograma de 888 ms al entrar a un catálogo.

El conteo es un dato del dataset, no algo que haya que descubrir en tiempo de ejecución. Se calcula
acá, una vez, y la app lo lee del config sin tocar un solo catálogo.

    python3 tools/platform_counts.py            # escribe los conteos
    python3 tools/platform_counts.py --check    # falla si están desactualizados (para el lint)

El `--check` es lo que evita que el número mienta: si alguien agrega juegos a un catálogo y no
regenera, el lint lo caza en vez de que la app muestre un conteo viejo para siempre.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CONFIG = RAIZ / "data" / "config" / "platforms.json"
DATOS = RAIZ / "data"

# Las mismas etiquetas que `RegionFilter.label` en Kotlin. El orden es el de la app.
REGIONES = ("NTSC-U", "NTSC-J", "PAL")


def archivo_de(plataforma: dict, region: str) -> str:
    """Catálogo de una región, con la **misma cascada** que `Platform.catalogFor` en Kotlin.

    Si divergen, el conteo miente: una consola de una sola región (la SG-1000, solo Japón) muestra
    su catálogo japonés también en NTSC-U, y el número tiene que acompañar a lo que se ve.
    """
    catalogos = plataforma.get("catalogs") or {}
    propio = catalogos.get(region)
    if propio:
        return propio
    defecto = plataforma.get("catalog")
    if defecto:
        return defecto
    return next((v for v in catalogos.values() if v), "")


def contar(ruta_rel: str, cache: dict[str, int]) -> int:
    if ruta_rel in cache:
        return cache[ruta_rel]
    ruta = DATOS / ruta_rel
    if not ruta.exists():
        raise SystemExit(f"falta el catálogo {ruta_rel}")
    cache[ruta_rel] = len(json.loads(ruta.read_text()))
    return cache[ruta_rel]


def conteos(plataformas: list[dict]) -> dict[str, dict[str, int]]:
    cache: dict[str, int] = {}
    salida: dict[str, dict[str, int]] = {}
    for p in plataformas:
        por_region = {}
        for region in REGIONES:
            archivo = archivo_de(p, region)
            if archivo:
                por_region[region] = contar(archivo, cache)
        # Sin catálogo en ninguna región (la PS5) no hay nada que contar: no se escribe la clave,
        # y del lado de Kotlin la ausencia significa "se carga a mano".
        if por_region:
            salida[p["id"]] = por_region
    return salida


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="no escribe; falla si están desfasados")
    args = ap.parse_args()

    original = CONFIG.read_text()
    plataformas = json.loads(original)
    nuevos = conteos(plataformas)

    desfasadas = []
    for p in plataformas:
        esperado = nuevos.get(p["id"])
        if p.get("counts") != esperado:
            desfasadas.append((p["id"], p.get("counts"), esperado))
        if esperado is None:
            p.pop("counts", None)
        else:
            p["counts"] = esperado

    if args.check:
        if desfasadas:
            print("conteos desactualizados en platforms.json:")
            for pid, viejo, nuevo in desfasadas:
                print(f"  {pid}: {viejo} → {nuevo}")
            print("\ncorrer: python3 tools/platform_counts.py")
            return 1
        print(f"conteos al día ({len(nuevos)} plataformas)")
        return 0

    CONFIG.write_text(json.dumps(plataformas, indent=2, ensure_ascii=False) + "\n")
    total = sum(c.get("NTSC-U", 0) for c in nuevos.values())
    print(f"{len(nuevos)} plataformas · {total} juegos en NTSC-U")
    for pid, por_region in nuevos.items():
        print(f"  {pid:22} {por_region}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
