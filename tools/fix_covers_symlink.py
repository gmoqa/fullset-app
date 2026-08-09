#!/usr/bin/env python3
"""Reemplaza las carátulas que apuntan a un **symlink** por su archivo real.

libretro-thumbnails guarda las variantes de un juego —`(Rev 1)`, `(Disc 1)`, `(Arcade)`,
`(Virtual Console)`, `(GameCube)`…— como **enlaces simbólicos** al archivo canónico. Git guarda un
symlink como un archivo de texto con la ruta destino, y `raw.githubusercontent.com` **no lo sigue**:
devuelve HTTP 200 con el texto del destino.

O sea que la app se baja 23 bytes que dicen `Banjo-Kazooie (USA).png`, no puede decodificarlos como
imagen, y muestra el hueco. Medido en una colección real: **32 de 411 carátulas** eran esto —casi el
8%—, entre ellas Star Fox, Donkey Kong Country, Ocarina of Time y Metal Gear Solid.

No se puede detectar por el nombre: `(Rev 1)` a veces es un symlink y a veces un archivo real con su
propio escaneo. Hay que **mirar el contenido**: si los primeros bytes no son los de un PNG o un
JPEG, es un symlink y el propio contenido dice a dónde apunta.

    python3 tools/fix_covers_symlink.py --dry-run    # qué cambiaría
    python3 tools/fix_covers_symlink.py              # lo escribe

Solo se consultan las que tienen sufijo de variante (~6% del dataset): el resto no puede ser un
symlink y bajarlas sería pedirle 21.000 archivos a GitHub para nada.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CATALOGOS = RAIZ / "data" / "catalogs"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_common import NO_SON_CATALOGOS, write_catalog  # noqa: E402

# Sufijos que libretro usa para las variantes. Es solo un filtro para no consultar todo el dataset:
# la decisión real la toma el contenido del archivo, no su nombre.
VARIANTE = re.compile(
    r"\((Rev [^)]*|Disc \d+|Arcade|.*Virtual Console|GameCube|Debug[^)]*|RE|\d+M"
    r"|Collector's Edition|Beta[^)]*|Proto[^)]*|Alt[^)]*)\)",
    re.I,
)

# Un PNG empieza con \x89PNG y un JPEG con \xff\xd8. Cualquier otra cosa en un .png/.jpg de este
# repositorio es el texto de un symlink.
MAGIA = (b"\x89PNG", b"\xff\xd8")

# Sin User-Agent, el CDN devuelve 403 en algunos hosts. Se identifica el cliente.
CABECERAS = {"User-Agent": "fullset-catalog-tools/1.0 (+https://github.com/gmoqa/fullset-app)"}


def destino(url: str) -> str | None:
    """Si `url` es un symlink, el nombre de archivo al que apunta; None si es una imagen real."""
    try:
        req = urllib.request.Request(url, headers=CABECERAS)
        with urllib.request.urlopen(req, timeout=25) as r:
            cabeza = r.read(512)
    except Exception:
        return None  # inaccesible: no se toca, no se puede saber
    if any(cabeza.startswith(m) for m in MAGIA):
        return None
    texto = cabeza.decode("utf-8", "replace").strip()
    # El destino es un nombre de archivo relativo, en el mismo directorio.
    if "\n" in texto or "/" in texto or not texto.lower().endswith((".png", ".jpg", ".jpeg")):
        return None
    return texto


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo muestra")
    args = ap.parse_args()

    total = 0
    for ruta in sorted(CATALOGOS.glob("*.json")):
        if ruta.name in NO_SON_CATALOGOS:
            continue
        entradas = json.loads(ruta.read_text(encoding="utf-8"))
        candidatas = [
            e for e in entradas
            if e.get("coverUrl") and VARIANTE.search(urllib.parse.unquote(e["coverUrl"].rsplit("/", 1)[-1]))
        ]
        if not candidatas:
            continue
        with ThreadPoolExecutor(max_workers=12) as ex:
            destinos = list(ex.map(lambda e: destino(e["coverUrl"]), candidatas))

        cambios = 0
        for e, dest in zip(candidatas, destinos):
            if not dest:
                continue
            base = e["coverUrl"].rsplit("/", 1)[0]
            e["coverUrl"] = f"{base}/{urllib.parse.quote(dest)}"
            cambios += 1
        if cambios:
            total += cambios
            print(f"  {ruta.name:22} {cambios} de {len(candidatas)} candidatas eran symlinks")
            if not args.dry_run:
                write_catalog(str(ruta), entradas)

    print(f"\n  {total} carátulas resueltas{' (en seco)' if args.dry_run else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
