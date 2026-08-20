#!/usr/bin/env python3
"""
Catálogo de **PlayStation 5**, solo **formato físico**, desde Wikidata (CC0).

Es el primero de las consolas modernas, y sale de una fuente nueva porque ninguna de las que usamos
sirve acá: libretro no cubre PS4 ni PS5, y **Redump tampoco** —su catálogo llega hasta PS3, porque
los discos de PS4/PS5 están cifrados y no se preservan—. La lista de Wikipedia trae 1.125 títulos
pero **no distingue físico de digital**: la palabra "retail" no aparece ni una vez.

Wikidata sí. Tiene `distribution format` (P437) con 92% de cobertura sobre los juegos de PS5, y
separa `Ultra HD Blu-ray` y `Blu-ray Disc` de `digital distribution`. Ese corte es el que ninguna
otra fuente hace, y es exactamente el que esta app necesita: acá solo entra lo que existió en disco.

## Las trampas que tiene el dato, y cómo se manejan

**`P437` es del juego, no de la plataforma.** Un multiplataforma declara *todos* sus formatos: por eso
entre los "formatos de juegos de PS5" aparecen `ROM cartridge` (157) y `Nintendo Game Card` (28), que
son de su versión de Switch. Solo se aceptan los formatos que **una PS5 puede leer**.

**`platform = PS5` incluye retrocompatibles.** *Sly 3* (2005) y *DC Universe Online* (2011) figuran
como juegos de PS5 porque se pueden jugar ahí, pero nunca salieron en un disco de PS5. Se exige que
el juego sea de la era de la consola.

**La fecha simple es de otra consola.** *The Witcher 3* figuraba con 2015, su lanzamiento original.
Wikidata permite calificar la fecha por plataforma, y **solo esa se escribe**: si no hay fecha
declarada como de PS5, el campo queda **vacío** en vez de inventar un año de otra máquina. Es un
catálogo que se completa a mano con el tiempo; una fecha falsa es peor que ninguna.

    python3 tools/build_ps5_catalog.py            # escribe data/catalogs/ps5-usa.json
    python3 tools/build_ps5_catalog.py --dry-run  # solo reporta
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_common import slug, write_catalog  # noqa: E402

ENDPOINT = "https://query.wikidata.org/sparql"
# Wikidata pide un User-Agent que identifique a quién pregunta.
UA = "fullset-catalog/0.1 (https://github.com/gmoqa/fullset-app)"

PS5 = "wd:Q63184502"

# Los formatos que **una PS5 puede leer**. Salen de enumerar los que declaran sus juegos, no de
# adivinarlos: en un intento anterior se coló `Q1441459`, que es "ohm metre" —una unidad de
# resistividad— y habría filtrado el catálogo entero por un disparate.
FORMATOS_FISICOS = [
    "wd:Q19953724",    # Ultra HD Blu-ray — el disco de la PS5
    "wd:Q47770",       # Blu-ray Disc
    "wd:Q234870",      # optical disc (genérico)
    "wd:Q108039701",   # physical distribution
]

SALIDA = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs", "ps5-usa.json")

# Los discos de PS5 **no tienen bloqueo regional**, así que hay una sola lista en vez de tres. La
# etiqueta NTSC-U es la que usa la app por defecto; no significa "solo América".
REGION = "NTSC-U"

# **Dos consultas, no una.** La primera pide solo los IDs; la segunda, los datos de esos IDs.
#
# El primer intento fue una sola consulta con `GROUP BY` y seis `OPTIONAL`, y **perdía 220 juegos en
# silencio**: *Elden Ring* no aparecía pese a declarar `Blu-ray Disc` y PS5. El cruce de opcionales
# con el `HAVING` descartaba filas sin avisar. Partido en dos, cada consulta hace una cosa y se puede
# verificar por separado.
IDS = """
SELECT DISTINCT ?item WHERE {
  ?item wdt:P400 %(ps5)s ; wdt:P437 ?f .
  VALUES ?f { %(formatos)s }
}
""" % {"ps5": PS5, "formatos": " ".join(FORMATOS_FISICOS)}

DATOS = """
SELECT ?item
       (SAMPLE(?en) AS ?titEn) (SAMPLE(?otro) AS ?titOtro)
       (SAMPLE(?ps5date) AS ?fecha) (MIN(?cualquiera) AS ?primera)
       (SAMPLE(?genLabel) AS ?genero) (SAMPLE(?devLabel) AS ?dev) (SAMPLE(?pubLabel) AS ?pub)
WHERE {
  VALUES ?item { %(items)s }
  # El título se pide **explícito** y por idioma, y hay que aceptar `mul` además de `en`.
  #
  # Wikidata migró las etiquetas en escritura latina al código **`mul`** (multilingüe) para no
  # repetir el mismo texto en cada idioma que usa ese alfabeto. *Elden Ring* tiene `[mul] Elden Ring`
  # y **ningún `en`**: filtrando solo por inglés desaparecían Elden Ring, Skyrim, Gran Turismo 7,
  # God of War y Overwatch 2 — los físicos más obvios que existen.
  #
  # Con `SERVICE wikibase:label` el síntoma era otro y más engañoso: devolvía el nombre japonés,
  # así que parecía un problema de preferencia de idioma y no de dónde vive el dato.
  OPTIONAL { ?item rdfs:label ?en . FILTER(LANG(?en) IN ("en", "mul")) }
  OPTIONAL { ?item rdfs:label ?otro . FILTER(LANG(?otro) IN ("ja", "fr", "de", "es", "it", "pt")) }
  # Fecha calificada **como de PS5**: la simple es la del lanzamiento original en otra consola.
  OPTIONAL { ?item p:P577 ?d . ?d ps:P577 ?ps5date ; pq:P400 %(ps5)s . }
  OPTIONAL { ?item wdt:P577 ?cualquiera . }
  OPTIONAL { ?item wdt:P136 ?g . ?g rdfs:label ?genLabel . FILTER(LANG(?genLabel) = "en") }
  OPTIONAL { ?item wdt:P178 ?dv . ?dv rdfs:label ?devLabel . FILTER(LANG(?devLabel) = "en") }
  OPTIONAL { ?item wdt:P123 ?pb . ?pb rdfs:label ?pubLabel . FILTER(LANG(?pubLabel) = "en") }
}
GROUP BY ?item
"""

# Antes de la PS5 no hubo discos de PS5. Un juego cuyo primer lanzamiento es anterior llegó por
# retrocompatibilidad —*Sly 3* de 2005, *DC Universe Online* de 2011— y nunca existió en este disco.
PRIMER_AÑO = 2020


def consultar(sparql: str) -> list[dict]:
    url = ENDPOINT + "?format=json&query=" + urllib.parse.quote(sparql)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=240) as r:
        return json.load(r)["results"]["bindings"]


def lotes(items: list[str], tam: int = 150):
    """Los IDs van de a poco: una sola consulta con 700 `VALUES` hace que el endpoint largue 504."""
    for i in range(0, len(items), tam):
        yield items[i:i + tam]


def valor(fila: dict, clave: str) -> str:
    return (fila.get(clave) or {}).get("value", "").strip()


def fecha_iso(crudo: str) -> str:
    """`AAAA-MM-DD`, o vacío si no es una fecha.

    Wikidata representa "valor desconocido" con un nodo anónimo, que vuelve como una URI: dos juegos
    entraban con `releaseDate = "http://www"` y hacían fallar al lint al querer leer el año.
    """
    d = crudo[:10]
    return d if len(d) == 10 and d[:4].isdigit() and d[4] == "-" else ""


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="no escribe; solo reporta")
    a = ap.parse_args()

    ids = [x["item"]["value"].rsplit("/", 1)[-1] for x in consultar(IDS)]
    print(f"  {len(ids)} juegos de PS5 con formato de disco")

    filas: list[dict] = []
    for lote in lotes(ids):
        filas += consultar(DATOS % {"ps5": PS5, "items": " ".join(f"wd:{q}" for q in lote)})
    print(f"  datos de {len(filas)}")

    entradas, vistos = [], set()
    sin_etiqueta = descartados_por_era = 0
    for f in filas:
        # El inglés manda; el otro idioma solo cubre a los que no lo tienen —un exclusivo japonés
        # es mejor con su nombre japonés que afuera de la lista—.
        titulo = valor(f, "titEn") or valor(f, "titOtro")
        if not titulo:
            sin_etiqueta += 1
            continue
        fecha_ps5 = fecha_iso(valor(f, "fecha"))
        primera = valor(f, "primera")[:5].lstrip("+")
        if not fecha_ps5 and primera.isdigit() and int(primera) < PRIMER_AÑO:
            descartados_por_era += 1
            continue
        s = slug(titulo)
        if not s or s in vistos:
            continue
        vistos.add(s)
        entradas.append({
            "title": titulo,
            "platform": "PlayStation 5",
            "region": REGION,
            "year": int(fecha_ps5[:4]) if fecha_ps5[:4].isdigit() else None,
            "releaseDate": fecha_ps5,
            "developer": valor(f, "dev"),
            "publisher": valor(f, "pub"),
            "genre": valor(f, "genero"),
            "slug": s,
            "serial": "",
            "coverUrl": "",
            "rating": "",
        })

    n = len(entradas) or 1
    print(f"  {len(entradas)} juegos físicos · {sin_etiqueta} sin título · {descartados_por_era} anteriores a la consola")
    print(f"    fecha de PS5 {sum(1 for e in entradas if e['releaseDate']) * 100 // n}% · "
          f"desarrolladora {sum(1 for e in entradas if e['developer']) * 100 // n}% · "
          f"editora {sum(1 for e in entradas if e['publisher']) * 100 // n}% · "
          f"género {sum(1 for e in entradas if e['genre']) * 100 // n}%")
    if not a.dry_run:
        write_catalog(SALIDA, entradas)
    return 0


if __name__ == "__main__":
    sys.exit(main())
