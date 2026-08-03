#!/usr/bin/env python3
"""
Catálogo Super Nintendo USA (NTSC-U) desde la lista de Wikipedia (CC BY-SA 4.0).

Existe porque `snes-usa.json` era el **único catálogo sin builder**: nadie podía regenerarlo ni
auditarlo como el resto, y resultó tener 149 fechas de otra región (la japonesa o la PAL) heredadas
de un generador legacy que ya no está. Ahora su procedencia es la misma que la de PlayStation y
GameCube: `Título | Desarrolladora | Editora | JP | NA | PAL`, y entra el juego cuyo mercado tiene
fecha, porque la tabla marca `{{unreleased}}` donde no salió.

**Fusiona, no reemplaza.** La tabla `game` de la app guarda el `slug` como vínculo al catálogo, así
que renombrarlo rompe en silencio la conexión con los juegos que el usuario ya cargó. Por eso, si un
título de Wikipedia coincide con una entrada existente por título normalizado, se conserva el `slug`
y el `title` que ya estaban aunque Wikipedia los escriba distinto ("Ballz 3D" vs "Ballz",
"Desert Strike" vs "Desert Strike: Return to the Gulf"). Y **nunca se borra**: los juegos que están
en el catálogo y no en la lista se reportan, no se descartan — que Wikipedia no los liste no prueba
que no existan.

`serial`, `coverUrl` y `genre` los completan después los enriquecedores; las correcciones a mano de
`tools/overrides/snes-usa.json` (42 catalog numbers verificados contra SNES Central) se aplican
solas al escribir.

Uso:  python3 tools/build_snes_catalog.py [--dry-run]
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import core, slug as make_slug, write_catalog  # noqa: E402
from enrich_dates_wikipedia import wikitext, region_column  # noqa: E402
from build_catalog_from_wikipedia import parse, publisher_column  # noqa: E402

PAGE = "List of Super Nintendo Entertainment System games"
PLATFORM = "Super Nintendo"
REGION = "NTSC-U"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets",
                   "catalogs", "snes-usa.json")


ROMANOS = {"ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"}


def es_secuela(a: str, b: str) -> bool:
    """
    Si dos títulos emparentados por prefijo son en realidad **juegos distintos de una serie**.

    Se decide sobre el título original y no sobre el normalizado, porque ahí se ve la diferencia
    entre un número de entrega y un descriptor pegado: *Mortal Kombat* → *Mortal Kombat 3* agrega la
    palabra "3" sola, mientras que *Ballz* → *Ballz 3D* agrega "3D", que es parte del nombre. Sin
    esto, el primer *Mortal Kombat* y *Jurassic Park 2* se descartaban como duplicados de otro juego.
    """
    corto, largo = sorted((a.strip(), b.strip()), key=len)
    if not largo.lower().startswith(corto.lower()):
        return False
    resto = largo[len(corto):].lstrip(" :-")
    primera = resto.split(" ")[0].rstrip(":,")
    return primera.isdigit() or primera.lower() in ROMANOS


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    text = wikitext(PAGE)
    column = region_column(text, REGION)
    if column is None:
        raise SystemExit(f"no encontré la columna de {REGION} en '{PAGE}'")
    filas = parse(text, column, publisher_column(text), REGION)

    actuales = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else []
    por_titulo = {core(e["title"]): e for e in actuales}

    nuevos, vistos, mejorados, ambiguos = [], set(), 0, []
    for fila in filas:
        clave = core(fila["title"])
        if clave in vistos:
            continue
        vistos.add(clave)
        e = por_titulo.get(clave)
        if e is None:
            # Antes de dar de alta, ver si el juego ya está bajo un nombre más corto o más largo
            # ("Ballz 3D" / "Ballz", "Desert Strike" / "Desert Strike: Return to the Gulf"). No se
            # fusionan solos porque el prefijo también relaciona cosas que **sí** son distintas
            # —"Mortal Kombat" y "Mortal Kombat: Competition Edition" son dos cartuchos— así que se
            # reportan para revisar a mano. Duplicar es peor que no dar de alta: el juego ya está.
            # Se elige el pariente **más cercano** (el de menor diferencia), no el primero que
            # aparezca: "Jurassic Park 2: The Chaos Continues" tiene de prefijo a "Jurassic Park",
            # pero el catálogo ya lo trae como "Jurassic Park Part 2: The Chaos Continues", que es
            # con quien hay que compararlo.
            candidatos = [(abs(len(k) - len(clave)), o) for k, o in por_titulo.items()
                          if k and (k.startswith(clave) or clave.startswith(k))]
            pariente = min(candidatos, key=lambda c: c[0])[1] if candidatos else None
            if pariente is not None and not es_secuela(pariente["title"], fila["title"]):
                ambiguos.append((pariente["title"], fila["title"]))
                continue
            nuevos.append({
                "title": fila["title"], "platform": PLATFORM, "region": REGION,
                "year": fila["year"], "releaseDate": fila["releaseDate"],
                "publisher": fila["publisher"], "genre": "", "slug": make_slug(fila["title"]),
                "serial": "", "coverUrl": "", "rating": "",
            })
            continue
        # La fecha se adopta si es más precisa **o** si contradice: la de Wikipedia está citada a la
        # lista oficial de Nintendo of America y la del catálogo legacy no tiene procedencia.
        if fila["releaseDate"] and fila["releaseDate"] != e["releaseDate"]:
            e["releaseDate"] = fila["releaseDate"]
            e["year"] = fila["year"]
            mejorados += 1
        if not e["publisher"].strip() and fila["publisher"]:
            e["publisher"] = fila["publisher"]

    emparejados = {core(a) for a, _ in ambiguos}
    faltan = [e for e in actuales
              if core(e["title"]) not in vistos and core(e["title"]) not in emparejados]
    print(f"{PAGE}: {len(filas)} filas con fecha NTSC-U")
    print(f"  {len(actuales)} en el catálogo · +{len(nuevos)} nuevos · {mejorados} fechas actualizadas")
    if ambiguos:
        print(f"  {len(ambiguos)} sin dar de alta porque ya están con otro nombre — revisar a mano:")
        for a, b in ambiguos:
            print(f"      catálogo {a[:40]:<42} ←→ wikipedia {b[:44]}")
    print(f"  {len(faltan)} del catálogo no figuran en la lista (se conservan):")
    for e in faltan[:12]:
        print(f"      {e['title']}")
    if len(faltan) > 12:
        print(f"      … y {len(faltan) - 12} más")

    if args.dry_run:
        print("  (dry-run: no se escribió)")
        return
    write_catalog(OUT, actuales + nuevos)


if __name__ == "__main__":
    main()
