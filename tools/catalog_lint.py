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

Devuelve código 1 si hay errores, así sirve de pre-commit/CI. Si algo falla, casi siempre se
arregla con:  python3 tools/normalize_catalogs.py
Uso:  python3 tools/catalog_lint.py
"""
import json, os, re, sys

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


def main():
    # manifest.json no es un catálogo de juegos: se saltea.
    files = sorted(f for f in os.listdir(CAT_DIR) if f.endswith(".json") and f != "manifest.json")
    total_errs = 0
    for f in files:
        n, errs = lint_file(os.path.join(CAT_DIR, f))
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
    sys.exit(1 if total_errs else 0)


if __name__ == "__main__":
    main()
