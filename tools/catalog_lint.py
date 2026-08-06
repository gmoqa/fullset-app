#!/usr/bin/env python3
"""
Validador/gate de los catálogos (solo lectura, no toca nada). Garantiza que TODOS estén en la
misma forma canónica, para que se puedan versionar y mantener sin sorpresas:

  - las 11 claves EXACTAS y EN ORDEN: title, platform, region, year, releaseDate, publisher,
    genre, slug, serial, coverUrl, rating
  - identidad presente (no vacía): title, platform, region, slug
  - tipos correctos (year entero o null; el resto strings)
  - region de un set válido
  - slug único y en kebab-case · título único
  - entradas ordenadas por slug
  - un objeto por línea (para diffs limpios)

Y los invariantes de **significado**, que es donde vivieron todos los defectos que encontramos:

  - `year` es el año de `releaseDate` (el mismo hecho contado dos veces, no pueden separarse)
  - ningún juego es anterior al lanzamiento de su consola
  - el prefijo del catalog number no es de otra región (SLUS/SLES/SLPM…)
  - las carátulas de otra región no superan la línea base de `baseline-semantica.json`

Las primeras reglas validan la forma, y daban OK con fechas japonesas en catálogos americanos,
carátulas europeas en el americano y seriales de discos promocionales: todo eso es
estructuralmente impecable. `tools/test_catalog_lint.py` prueba cada invariante contra el defecto
real que le dio origen.

Devuelve código 1 si hay errores, así sirve de pre-commit/CI. Si algo falla, casi siempre se
arregla con:  python3 tools/normalize_catalogs.py
Uso:  python3 tools/catalog_lint.py
"""
import json, os, re, sys, urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import slug as slugify  # noqa: E402

CAT_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "catalogs")

CANON_KEYS = ["title", "platform", "region", "year", "releaseDate", "publisher", "genre", "slug", "serial", "coverUrl", "rating"]
REQUIRED = {"title", "platform", "region", "slug"}          # identidad, nunca vacíos
REGIONS = {"NTSC-U", "NTSC-J", "PAL"}
SLUG_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def lint_file(path):
    errs = []
    raw = open(path, encoding="utf-8").read()
    data = json.loads(raw)

    # Formato: un objeto por línea (array con `[` y `]` en su propia línea, cada juego en una).
    lines = raw.splitlines()
    obj_lines = [l for l in lines if l.startswith("{")]
    if not (lines and lines[0] == "[" and lines[-1] == "]" and len(obj_lines) == len(data)):
        errs.append("no está en forma canónica (un objeto por línea) → corré normalize_catalogs.py")

    seen_slug, seen_title = {}, {}
    for i, e in enumerate(data):
        where = f"[{i}] {e.get('title', '?')!r}"
        if list(e.keys()) != CANON_KEYS:
            errs.append(f"{where}: claves/orden distinto al canónico {list(e.keys())}")
        for k in REQUIRED:
            if not str(e.get(k, "")).strip():
                errs.append(f"{where}: falta '{k}' (obligatorio)")
        if e.get("region") and e["region"] not in REGIONS:
            errs.append(f"{where}: region inválida {e['region']!r}")
        y = e.get("year")
        if y is not None and not isinstance(y, int):
            errs.append(f"{where}: year no es entero ({y!r})")
        slug = e.get("slug", "")
        if slug and not SLUG_RE.match(slug):
            errs.append(f"{where}: slug no kebab-case ({slug!r})")
        if slug in seen_slug:
            errs.append(f"{where}: slug duplicado de [{seen_slug[slug]}]")
        seen_slug[slug] = i
        tl = (e.get("title") or "").lower()
        if tl in seen_title:
            errs.append(f"{where}: título duplicado de [{seen_title[tl]}]")
        seen_title[tl] = i

    slugs = [e.get("slug", "") for e in data]
    if slugs != sorted(slugs):
        errs.append("las entradas no están ordenadas por slug → corré normalize_catalogs.py")
    return len(data), errs


OVR_DIR = os.path.join(os.path.dirname(__file__), "overrides")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# ---- Invariantes semánticos --------------------------------------------------------------------
# Las reglas de arriba validan la **forma**: 11 claves, orden, tipos, unicidad. Todas se cumplían
# mientras el dataset tenía fechas japonesas en catálogos americanos, carátulas europeas en el
# americano y seriales del disco promocional. Una fecha de otra región es estructuralmente
# impecable. Lo que sigue valida el **significado**, que es donde vivieron todos esos defectos.

PLATFORMS = os.path.join(os.path.dirname(__file__), "..", "data", "config", "platforms.json")
BASELINE = os.path.join(os.path.dirname(__file__), "baseline-semantica.json")

# Prefijos de catalog number que identifican la región sin ambigüedad. Solo Sony y Nintendo: en
# Sega la misma `T` aparece en las tres regiones, así que ahí no dice nada.
SERIAL_REGION = {
    "SLUS": "NTSC-U", "SCUS": "NTSC-U", "SKUS": "NTSC-U", "BLUS": "NTSC-U", "BCUS": "NTSC-U",
    "SNS": "NTSC-U", "NES": "NTSC-U", "NUS": "NTSC-U",
    "SLES": "PAL", "SCES": "PAL", "SCED": "PAL", "SLED": "PAL", "BLES": "PAL", "BCES": "PAL",
    "SLPS": "NTSC-J", "SLPM": "NTSC-J", "SCPS": "NTSC-J", "SIPS": "NTSC-J", "PAPX": "NTSC-J",
    "PBPX": "NTSC-J", "BLJM": "NTSC-J", "BLJS": "NTSC-J", "BCJS": "NTSC-J",
    "SHVC": "NTSC-J", "HVC": "NTSC-J",
}
# `BLAS`/`BCAS`/`BLKS`/`BCKS` (Asia y Corea) aparecen en las tres regiones de PS3: no diagnostican.

REGION_KEY = {"NTSC-U": "ntsc", "NTSC-J": "ntsc-j", "PAL": "pal"}
COVER_REGION = {"NTSC-U": "USA", "NTSC-J": "JAPAN", "PAL": "EUROPE"}


def _regiones_archivo(url):
    """Regiones que declara el nombre de archivo de No-Intro, como conjunto."""
    nombre = urllib.parse.unquote(url.rsplit("/", 1)[-1])
    m = re.search(r"\(([^)]*)\)", nombre)
    return {x.strip().upper() for x in m.group(1).split(",")} if m else set()


def lint_semantica(data, lanzamiento):
    """
    Errores de significado y el conteo de carátulas de otra región (que se compara contra la línea
    base, porque muchas son inevitables: libretro no publica tapa de ese mercado).
    """
    errs, fuera_de_region = [], 0
    for i, e in enumerate(data):
        donde = f"[{i}] {e.get('title', '?')!r}"

        # `year` tiene que ser el año de `releaseDate`: son el mismo hecho contado dos veces, y que
        # se separen significa que un enriquecedor escribió uno y se olvidó del otro.
        iso = (e.get("releaseDate") or "").strip()
        if iso and e.get("year") != int(iso[:4]):
            errs.append(f"{donde}: year={e.get('year')} no coincide con releaseDate={iso!r}")

        # Un juego no pudo salir antes que su consola. Lo contrario —salir después de
        # discontinuarla— es normal y NO se valida: Tec Toy publicó Master System en Brasil hasta
        # 2011 y Japón tuvo Dreamcast hasta 2004.
        if e.get("year") and lanzamiento and e["year"] < lanzamiento:
            errs.append(f"{donde}: año {e['year']} anterior al lanzamiento de la consola ({lanzamiento})")

        # El slug se deriva del título. Se admite un sufijo extra porque a veces hace falta
        # desambiguar a mano —`Street Fighter Zero 2` y `Street Fighter Zero 2'` darían el mismo,
        # y el segundo lleva su catalog number pegado— pero no que sea otra cosa. Sin esta regla,
        # cambiar `slug()` renombraba juegos en silencio y los desvinculaba de la colección.
        esperado = slugify(e.get("title") or "")
        actual_slug = e.get("slug") or ""
        if esperado and actual_slug != esperado and not actual_slug.startswith(esperado + "-"):
            errs.append(f"{donde}: slug {actual_slug!r} no deriva del título (esperado {esperado!r})")

        serial = (e.get("serial") or "").strip()
        m = re.match(r"^[A-Za-z]+", serial)
        if m:
            esperada = SERIAL_REGION.get(m.group(0).upper())
            if esperada and esperada != e.get("region"):
                errs.append(f"{donde}: serial {serial!r} es de {esperada}, no de {e.get('region')}")

        url = (e.get("coverUrl") or "").strip()
        if url and "steamgriddb" not in url:
            suyas = _regiones_archivo(url)
            quiero = COVER_REGION.get(e.get("region"))
            if suyas and quiero not in suyas and "WORLD" not in suyas:
                fuera_de_region += 1
    return errs, fuera_de_region


def lint_override(path):
    """
    Valida una corrección a mano: que apunte a algo que existe y que **diga de dónde salió**.

    Un override pisa un valor auto-derivado, así que sin `_source` queda un dato que nadie puede
    verificar ni revisar más adelante: solo se sabe que alguien, alguna vez, escribió otra cosa.
    Estas claves viven **solo acá** — `apply_overrides` no las copia al catálogo, que tiene que
    cumplir el esquema de 11 campos.
    """
    name = os.path.basename(path)
    cat_path = os.path.join(CAT_DIR, name)
    if not os.path.exists(cat_path):
        return 0, [f"no hay catálogo {name} para estas correcciones"]
    slugs = {e["slug"] for e in json.load(open(cat_path, encoding="utf-8"))}
    data = json.load(open(path, encoding="utf-8"))
    errs = []
    for slug, fields in data.items():
        if slug not in slugs:
            errs.append(f"{slug!r}: no existe en {name}")
        campos = [k for k in fields if not k.startswith("_")]
        if not campos:
            errs.append(f"{slug!r}: no corrige ningún campo")
        for k in campos:
            if k not in CANON_KEYS:
                errs.append(f"{slug!r}: {k!r} no es un campo del esquema")
        if not str(fields.get("_source", "")).strip():
            errs.append(f"{slug!r}: falta '_source' (de dónde salió la corrección)")
        fecha = str(fields.get("_date", ""))
        if fecha and not DATE_RE.match(fecha):
            errs.append(f"{slug!r}: '_date' no es AAAA-MM-DD ({fecha!r})")
    return len(data), errs


def lint_conteos(plataformas):
    """Verifica que `platforms.json.counts` coincida con el tamaño real de cada catálogo.

    Reusa `platform_counts.py` en vez de recalcular acá: si la cascada de regiones se implementara
    dos veces, tarde o temprano una de las dos se olvidaría de un caso —la SG-1000, que solo salió
    en Japón y muestra su catálogo japonés en las tres regiones— y el lint aprobaría un conteo malo.
    """
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from platform_counts import conteos

    plataformas = list(plataformas)
    esperado = conteos(plataformas)
    errs = 0
    for p in plataformas:
        real, tiene = esperado.get(p["id"]), p.get("counts")
        if tiene != real:
            print(f"platforms.json  {p['id']}: counts {tiene} → debería ser {real}")
            errs += 1
    print(f"\nconteos de plataformas: {'OK' if not errs else f'{errs} DESFASADOS'}"
          + ("" if not errs else " → correr: python3 tools/platform_counts.py"))
    return errs


def main():
    # manifest.json no es un catálogo de juegos: se saltea.
    files = sorted(f for f in os.listdir(CAT_DIR) if f.endswith(".json") and f != "manifest.json")
    plats = {p["name"]: p for p in json.load(open(PLATFORMS, encoding="utf-8"))}
    base = json.load(open(BASELINE, encoding="utf-8")) if os.path.exists(BASELINE) else {}
    actual = {}
    total_errs = 0
    for f in files:
        n, errs = lint_file(os.path.join(CAT_DIR, f))
        data = json.load(open(os.path.join(CAT_DIR, f), encoding="utf-8"))
        if data:
            info = plats.get(data[0]["platform"], {}).get("info", {})
            lanz = info.get("released", {}).get(REGION_KEY.get(data[0]["region"]))
            sem, fuera = lint_semantica(data, lanz)
            errs += sem
            actual[f] = fuera
            # La deuda conocida no puede **crecer**. Muchas carátulas de otra región son inevitables
            # —libretro no publica tapa de ese mercado— así que exigir cero sería mentir; lo que no
            # se tolera es que aparezcan nuevas, que es como se coló el bug del 32X.
            tope = base.get(f)
            if tope is not None and fuera > tope:
                errs.append(f"carátulas de otra región: {fuera} (la línea base es {tope}) "
                            f"→ revisá el ranking de región antes de subir la base")
        print(f"{f:<24} {n:>5} entradas   {'OK' if not errs else f'{len(errs)} ERRORES'}")
        for e in errs[:30]:
            print(f"    - {e}")
        if len(errs) > 30:
            print(f"    … y {len(errs) - 30} más")
        total_errs += len(errs)

    if os.path.isdir(OVR_DIR):
        print()
        for f in sorted(x for x in os.listdir(OVR_DIR) if x.endswith(".json")):
            n, errs = lint_override(os.path.join(OVR_DIR, f))
            print(f"overrides/{f:<13} {n:>5} correcciones   {'OK' if not errs else f'{len(errs)} ERRORES'}")
            for e in errs[:15]:
                print(f"    - {e}")
            total_errs += len(errs)
    # Los conteos de `platforms.json` son una copia del tamaño de cada catálogo, y una copia puede
    # quedar vieja sin que nada se rompa: la app mostraría "668 games" para siempre. Se verifica acá
    # y no en un test de Kotlin porque quien agrega juegos corre el lint, no la suite.
    total_errs += lint_conteos(plats.values())

    fuera = sum(actual.values())
    if not base:
        json.dump(actual, open(BASELINE, "w", encoding="utf-8"), indent=2, sort_keys=True)
        open(BASELINE, "a", encoding="utf-8").write("\n")
        print(f"\nlínea base de carátulas creada ({fuera} de otra región)")
    else:
        bajo = sum(max(base.get(k, 0) - v, 0) for k, v in actual.items())
        print(f"\ncarátulas de otra región: {fuera}"
              + (f" · {bajo} menos que la línea base (podés bajarla)" if bajo else ""))
    sys.exit(1 if total_errs else 0)


if __name__ == "__main__":
    main()
