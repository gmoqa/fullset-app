#!/usr/bin/env python3
"""Arma `data/catalogs/manifest.json`: qué archivos son los datos, cuánto pesan y qué contienen.

Antes esto vivía dentro de `normalize_catalogs.py`, que necesita internet y **rehornea catálogos**.
Para actualizar el manifest había que correr el script que pisa los datos, y eso hacía del manifest
algo que nadie regeneraba. Acá solo se lee y se escribe el manifest.

    python3 tools/catalog_manifest.py           # lo reescribe
    python3 tools/catalog_manifest.py --check   # falla si está desactualizado (lo usa el lint)

Qué agrega respecto del anterior, y por qué:

- **`sha256` y `bytes` por archivo.** Es lo que permite preguntar "¿cambió?" sin bajar el archivo,
  y verificar que lo que llegó es lo que se esperaba. Sin esto no hay sincronización posible.
- **`schema`.** Un entero que describe la *forma* del dato, no su contenido. Un cliente que entienda
  hasta el esquema N tiene que **negarse** a leer un manifest con esquema N+1 en vez de intentarlo:
  si no, el día que el formato cambie, cada versión vieja instalada se rompe sola.
- **`version`.** Sale del hash de los hashes, no de un timestamp. Así el diff sigue reflejando avance
  real —criterio que ya traía el generador viejo— y correr esto dos veces sin tocar nada no ensucia
  el repo.
- **`platforms.json` entra al manifest.** No es un catálogo, pero lleva los conteos por consola,
  que son una copia del tamaño de cada catálogo. Si viajaran por separado, un día los catálogos se
  actualizarían y los conteos no, y la app mostraría un número que no corresponde. Van juntos o no
  van — y por eso además viven en la misma carpeta.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_common import NO_SON_CATALOGOS  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
DATOS = RAIZ / "data"
CATALOGOS = DATOS / "catalogs"
MANIFEST = CATALOGOS / "manifest.json"

# Forma del manifest y de los catálogos. Se sube **solo** cuando un cliente viejo ya no podría leer
# el dato nuevo. Agregar un campo no cuenta: el parser de la app ignora las claves que no conoce y
# todos los campos del catálogo tienen valor por defecto, así que agregar y quitar son compatibles.
SCHEMA = 1

# Qué campos se miden para la cobertura. Es diagnóstico —cuánto le falta al dataset—, no contrato.
COBERTURA = ("year", "publisher", "serial", "coverUrl")


def sha256(ruta: Path) -> str:
    return hashlib.sha256(ruta.read_bytes()).hexdigest()


def cobertura(filas: list[dict], campo: str) -> int:
    if not filas:
        return 0
    con = sum(1 for e in filas if str(e.get(campo) or "").strip())
    return con * 100 // len(filas)


def construir() -> dict:
    archivos = []

    # El config va primero y a propósito: es el que decide qué consolas existen, así que quien
    # sincronice tiene que aplicarlo antes que los catálogos que describe.
    config = CATALOGOS / "platforms.json"
    archivos.append({
        "path": f"catalogs/{config.name}",
        "bytes": config.stat().st_size,
        "sha256": sha256(config),
    })

    for ruta in sorted(CATALOGOS.glob("*.json")):
        # `manifest.json` es este archivo y `platforms.json` ya entró arriba; ninguno es una
        # lista de juegos, así que parsearlos como catálogo reventaría en `filas[0]["platform"]`.
        if ruta.name in NO_SON_CATALOGOS:
            continue
        filas = json.loads(ruta.read_text(encoding="utf-8"))
        archivos.append({
            "path": f"catalogs/{ruta.name}",
            "platform": filas[0]["platform"] if filas else "",
            "games": len(filas),
            "bytes": ruta.stat().st_size,
            "sha256": sha256(ruta),
            "coverage": {c: cobertura(filas, c) for c in COBERTURA},
        })

    # La versión sale del contenido: mismo dato, misma versión, corras esto las veces que corras.
    huella = hashlib.sha256("".join(a["sha256"] for a in archivos).encode()).hexdigest()[:12]
    return {"schema": SCHEMA, "version": huella, "files": archivos}


def diferencias(actual, nuevo: dict) -> list[str]:
    """Qué cambió, en palabras. La versión sola no alcanza: sale de los archivos reales, así que un
    hash mal escrito a mano en el manifest da la misma versión y el mensaje quedaría en `X → X`."""
    if not isinstance(actual, dict) or "files" not in actual:
        return ["el manifest no tiene el formato actual (falta `files`)"]
    if actual.get("schema") != nuevo["schema"]:
        return [f"schema {actual.get('schema')} → {nuevo['schema']}"]

    viejos = {a["path"]: a for a in actual["files"]}
    nuevos = {a["path"]: a for a in nuevo["files"]}
    fuera = []
    for path in sorted(set(viejos) | set(nuevos)):
        if path not in viejos:
            fuera.append(f"nuevo: {path}")
        elif path not in nuevos:
            fuera.append(f"ya no está: {path}")
        elif viejos[path] != nuevos[path]:
            v, n = viejos[path], nuevos[path]
            if v.get("sha256") != n.get("sha256"):
                fuera.append(f"cambió: {path} ({v.get('games', '?')} → {n.get('games', '?')} juegos)")
            else:
                fuera.append(f"metadatos distintos: {path}")
    if not fuera and actual.get("version") != nuevo["version"]:
        fuera.append(f"versión {actual.get('version')} → {nuevo['version']}")
    return fuera


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="no escribe; falla si está desactualizado")
    args = ap.parse_args()

    nuevo = construir()
    actual = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else None

    if args.check:
        if actual != nuevo:
            print("manifest desactualizado:")
            for d in diferencias(actual, nuevo)[:20]:
                print(f"  {d}")
            print("correr: python3 tools/catalog_manifest.py")
            return 1
        print(f"manifest al día (schema {nuevo['schema']}, versión {nuevo['version']})")
        return 0

    MANIFEST.write_text(json.dumps(nuevo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    juegos = sum(a.get("games", 0) for a in nuevo["files"])
    pesos = sum(a["bytes"] for a in nuevo["files"])
    print(f"schema {nuevo['schema']} · versión {nuevo['version']}")
    print(f"{len(nuevo['files'])} archivos · {juegos} juegos · {pesos / 1024 / 1024:.1f} MB")
    if actual and isinstance(actual, dict) and actual.get("version") != nuevo["version"]:
        print(f"  (venía de la versión {actual.get('version')})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
