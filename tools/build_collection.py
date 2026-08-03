#!/usr/bin/env python3
"""
Genera data/seed/collection.json a partir del Excel de la colección.

- Lee una hoja por consola (Título, Consola, Género, Año, Estado, Notas) + hoja "Wishlist".
- Resuelve la carátula de cada juego en Libretro Thumbnails: primero probando nombres
  No-Intro (colon -> " - ", región USA/…); si falla, hace fuzzy-match contra el listado
  real del repo (maneja artículos "The" al final, números romanos II<->2, subtítulos, etc.).
- Deja la mejor URL (o "" si no encuentra) en cada entrada.

Uso:  python3 tools/build_collection.py /ruta/a/coleccion.xlsx
Requiere: openpyxl. Necesita internet (GitHub API + raw.githubusercontent.com).
"""
import sys, os, re, json, time, urllib.parse, urllib.request, difflib
import openpyxl

BASE = "https://raw.githubusercontent.com/libretro-thumbnails"
# consola (mayúsculas) -> (nombre visible, repo de Libretro)
PLAT = {
    "SNES": ("Super Nintendo", "Nintendo_-_Super_Nintendo_Entertainment_System"),
    "GENESIS": ("Sega Genesis", "Sega_-_Mega_Drive_-_Genesis"),
    "NINTENDO 64": ("Nintendo 64", "Nintendo_-_Nintendo_64"),
    "SEGA CD": ("Sega CD", "Sega_-_Mega-CD_-_Sega_CD"),
    "PSX": ("PlayStation", "Sony_-_PlayStation"),
}
# Catálogo oficial por consola (para canonicalizar el nombre del Excel contra nuestros datos
# antes de buscar la carátula: corrige typos, subtítulos y mayúsculas de los títulos US).
CATALOG_DIR = os.path.join("data", "catalogs")
CATALOG_FILE = {"SNES": "snes-usa.json", "GENESIS": "genesis-usa.json", "PSX": "psx-usa.json"}

# Títulos anotados en el Excel cuyo nombre real es tan distinto que la comparación por similitud
# no los alcanza. Clave: (hoja/consola, título en minúsculas) -> título real del juego.
MANUAL_ALIASES = {
    ("SNES", "the force"): "D-Force",
    ("SNES", "tmnt tournament fighters"): "Teenage Mutant Ninja Turtles: Tournament Fighters",
}
_catalog_cache = {}
ILLEGAL = re.compile(r'[&*/:`<>?\\|"]')
ROMAN = {'ii':'2','iii':'3','iv':'4','v':'5','vi':'6','vii':'7','viii':'8','ix':'9','x':'10'}
BAD = ['(Beta', '(Proto', '(Demo', '(Sample', '(Virtual Console', '(Competition', '(Kiosk', '(Promo']

# Normalización al esquema estándar (inglés). Ver también tools/build_psx_catalog.py.
REGION = "NTSC-U"
GENRE_EN = {
    '': '', 'Acción': 'Action', 'Acción-Aventura': 'Action-Adventure',
    'Acción-Sigilo': 'Action-Stealth', 'Acción-Simulación': 'Action-Simulation',
    'Aventura': 'Adventure', 'Carreras': 'Racing', 'Deportes': 'Sports',
    'Lucha': 'Fighting', 'Plataformas': 'Platformer',
}
COND_EN = {'': '', 'CIB': 'CIB', 'Completo': 'Complete', 'Suelto': 'Loose', 'Suelto + Manual': 'Loose + manual'}


def year_int(s):
    m = re.search(r'\b(19|20)\d{2}\b', str(s or ''))
    if not m:
        return None
    y = int(m.group(0))
    return y if 1970 <= y <= 2006 else None


def slugify(t):
    s = t.lower().replace('&', ' and ')
    s = re.sub(r"[.'’:]", '', s)
    s = re.sub(r'[^a-z0-9]+', '-', s)
    return s.strip('-')


def core(s):
    s = s.rsplit(".png", 1)[0]
    s = re.sub(r'\s*\([^)]*\)', '', s)
    s = s.lower().replace('&', ' and ').replace('_', ' and ').replace('-', ' ')
    s = re.sub(r"[':,.!\"/]", ' ', s)
    toks = [ROMAN.get(t, t) for t in s.split()]
    toks = [t for t in toks if t not in ('the', 'a', 'an')]
    return ' '.join(toks)


def fetch_index(repo, attempts=4):
    """Listado completo de carátulas del repo. La API de GitHub devuelve 5xx esporádicos,
    así que se reintenta: un fallo pasajero no debe tirar abajo toda la generación."""
    url = f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master?recursive=1"
    req = urllib.request.Request(url, headers={"User-Agent": "fullset", "Accept": "application/vnd.github+json"})
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.load(r)
            break
        except Exception as e:
            if attempt == attempts - 1:
                raise
            print(f"  reintentando {repo} ({e})", file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    idx = {}
    for t in data.get("tree", []):
        p = t["path"]
        if p.startswith("Named_Boxarts/") and p.endswith(".png"):
            idx.setdefault(core(p.split("/", 1)[1]), []).append(p.split("/", 1)[1])
    return idx


# Base de datos de volcados por consola: de ahí sale el `serial`, el código de producto impreso
# en el cartucho/disco (SCUS-94163, MK-4407-00…). Los cartuchos van en No-Intro y los discos en
# Redump. SNES queda afuera a propósito: sus entradas casi nunca traen serial.
DAT_SOURCE = {
    "GENESIS": ("no-intro", "Sega - Mega Drive - Genesis"),
    "NINTENDO 64": ("no-intro", "Nintendo - Nintendo 64"),
    "PSX": ("redump", "Sony - PlayStation"),
    "SEGA CD": ("redump", "Sega - Mega-CD - Sega CD"),
}
_dat_cache = {}
DAT_BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"


def fetch_serials(console_key):
    """core(título) -> serial, quedándose con la edición de mejor región para cada juego."""
    if console_key in _dat_cache:
        return _dat_cache[console_key]
    src = DAT_SOURCE.get(console_key)
    index = {}
    if src:
        kind, name = src
        url = f"{DAT_BASE}/{kind}/" + urllib.parse.quote(name) + ".dat"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "fullset"})
            with urllib.request.urlopen(req, timeout=30) as r:
                txt = r.read().decode("utf-8", "ignore")
            for gname, body in re.findall(r'game \(\s*\n\s*name "([^"]+)"(.*?)\n\)', txt, re.S):
                m = re.search(r'serial "([^"]+)"', body)
                if not m or any(b in gname for b in BAD):
                    continue
                key = core(gname)
                prev = index.get(key)
                if prev is None or region_rank(gname) > prev[1]:
                    index[key] = (m.group(1).split(",")[0].strip(), region_rank(gname))
        except Exception as e:
            print(f"  sin serials para {console_key} ({e})", file=sys.stderr)
    _dat_cache[console_key] = {k: v[0] for k, v in index.items()}
    return _dat_cache[console_key]


def region_rank(f):
    """Prioridad de región para una colección USA/NTSC-U (mayor = mejor).
    Reconoce las etiquetas multi-región que incluyen USA — `(Japan, USA)`,
    `(USA, Europe)` — no solo el `(USA)` puro. Europa (PAL) queda como último
    recurso por encima de Japón (misma estética occidental que la caja US)."""
    low = f.lower()
    if '(usa)' in low:            return 6   # USA puro
    if 'usa' in low:              return 5   # multi-región con USA: (Japan, USA), (USA, Europe)…
    if '(world)' in low:          return 4   # World incluye USA
    if '(europe' in low:          return 2   # PAL: arte occidental, aceptable si no hay USA
    if '(japan' in low:           return 1   # Japón: solo si no hay nada mejor
    return 3                                 # sin etiqueta de región clara


def best(files):
    return sorted(files, key=lambda f: (region_rank(f), not any(b in f for b in BAD),
                                        '(Rev' not in f, -len(f)), reverse=True)[0]


def lookup(idx, gc):
    """Archivos de un juego uniendo variantes de escritura: el índice agrupa por `core`, pero
    'QuackShot' y 'Quack Shot' son el mismo juego y deben competir juntos por región."""
    key = gc.replace(' ', '')
    return [f for c, files in idx.items() if c.replace(' ', '') == key for f in files]


def fuzzy(gc, idx):
    # Subtítulos: cores que extienden `gc` con texto no numérico (evita tomar secuelas
    # "juego 2"). Cubre relanzamientos US retitulados, p. ej. BlaZeon -> "BlaZeon - The
    # Bio-Cyborg Challenge (USA)", cuyo core difiere del japonés "blazeon".
    sub = [fs for fc, fs in idx.items()
           if fc.startswith(gc + " ") and not re.fullmatch(r'\d+', fc[len(gc) + 1:])]
    sub = [f for group in sub for f in group]

    exact = lookup(idx, gc)
    if exact:
        b = best(exact)
        # Si el core exacto no ofrece arte USA/World pero un subtítulo sí, prefiérelo.
        if sub and region_rank(b) < 4:
            bs = best(sub)
            if region_rank(bs) >= 4:
                return bs
        return b
    if sub:
        return best(sub)
    for fc in difflib.get_close_matches(gc, idx.keys(), n=3, cutoff=0.86):
        if fc.split()[:1] == gc.split()[:1] and set(re.findall(r'\d+', fc)) == set(re.findall(r'\d+', gc)):
            return best(idx[fc])
    return None


def load_catalog(console_key):
    if console_key not in _catalog_cache:
        fn = CATALOG_FILE.get(console_key)
        data = []
        if fn:
            try:
                data = json.load(open(os.path.join(CATALOG_DIR, fn), encoding="utf-8"))
            except Exception:
                data = []
        _catalog_cache[console_key] = (data, {core(e["title"]): e for e in data})
    return _catalog_cache[console_key]


_slug_index = {}


def catalog_by_slug(slug, console_key):
    """Entrada del catálogo oficial identificada por `slug` (el 'SKU' con el que se asocia cada
    juego de la colección), o None si esa consola no tiene catálogo o el slug no está."""
    if not slug or not console_key:
        return None
    if console_key not in _slug_index:
        data, _ = load_catalog(console_key)
        _slug_index[console_key] = {e.get("slug"): e for e in (data or [])}
    return _slug_index[console_key].get(slug)


def canonicalize(title, console_key):
    """Devuelve (título canónico, slug) del catálogo oficial que mejor coincide con `title`, o
    (title, None) si no hay catálogo/match. Corrige typos, subtítulos y mayúsculas de los títulos
    US usando nuestros propios datos, ANTES de buscar la carátula (así resuelve más)."""
    # El nombre del Excel es demasiado distinto del real y la similitud no alcanza: se mapea a mano.
    title = MANUAL_ALIASES.get((console_key, title.strip().lower()), title)
    data, by_core = load_catalog(console_key)
    if not data:
        return title, None
    tc = core(title)
    if tc in by_core:                          # core exacto
        e = by_core[tc]
        return e["title"], e.get("slug")
    n = len(tc.split())
    best_e, best_r = None, 0.0                 # vs. el prefijo del catálogo de igual nº de palabras
    for e in data:
        prefix = " ".join(core(e["title"]).split()[:n])
        r = difflib.SequenceMatcher(None, tc, prefix).ratio()
        if r > best_r:
            best_r, best_e = r, e
    if best_e and best_r >= 0.85:
        return best_e["title"], best_e.get("slug")
    return title, None


def main(xlsx, out_path):
    wb = openpyxl.load_workbook(xlsx, data_only=True)
    library, wishlist = [], []
    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))[1:]
        if ws.title.strip().lower() == "wishlist":
            for r in rows:
                if r and r[0]:
                    console_key = str(r[1]).strip().upper()
                    name, repo = PLAT.get(console_key, (str(r[1]).strip(), ""))
                    title, slug = canonicalize(str(r[0]).strip(), console_key)
                    wishlist.append({"title": title, "slug": slug, "platform": name, "repo": repo})
            continue
        console_key = ws.title.strip().upper()
        name, repo = PLAT.get(console_key, (ws.title.strip(), ""))
        for r in rows:
            if r and r[0]:
                title, slug = canonicalize(str(r[0]).strip(), console_key)
                library.append({"title": title, "slug": slug, "platform": name, "repo": repo,
                                "console_key": console_key,
                                "genre": str(r[2] or "").strip(), "year": str(r[3] or "").strip(),
                                "condition": str(r[4] or "").strip(), "notes": str(r[5] or "").strip()})

    # Carátulas: se eligen contra el listado real del repo, no probando sufijos a ciegas.
    # Probar nombres devolvía la primera edición que existiera —muchas veces la (World)—
    # sin ver que había una (USA); con el índice, `best()` compara todas y prioriza la USA.
    idx_cache = {}
    for it in library + wishlist:
        it["cover"] = ""
        if not it["repo"]:
            continue
        if it["platform"] not in idx_cache:
            idx_cache[it["platform"]] = fetch_index(it["repo"])
        fn = fuzzy(core(it["title"]), idx_cache[it["platform"]])
        if fn:
            it["cover"] = f"{BASE}/{it['repo']}/master/Named_Boxarts/" + urllib.parse.quote(fn)

    # 3) Completar desde el catálogo oficial lo que el Excel no anota (año y editora). Así un juego
    #    importado queda con los mismos datos que uno agregado desde la app.
    for g in library:
        entry = catalog_by_slug(g.get("slug"), g.get("console_key", ""))
        g["cat_year"] = entry.get("year") if entry else None
        g["cat_publisher"] = (entry.get("publisher") or "") if entry else ""
        # Código de producto (el "SKU" impreso en el cartucho/disco), donde la base lo tenga.
        g["serial"] = fetch_serials(g.get("console_key", "")).get(core(g["title"]), "")

    # Esquema normalizado en inglés (mismo que los catálogos + condition/notes/cover).
    out = {
        "library": [{
            "title": g["title"], "platform": g["platform"], "region": REGION,
            "year": year_int(g["year"]) or g["cat_year"], "publisher": g["cat_publisher"],
            "serial": g["serial"],
            "genre": GENRE_EN.get(g["genre"], g["genre"]), "slug": g.get("slug") or slugify(g["title"]),
            "condition": COND_EN.get(g["condition"], g["condition"]), "notes": g["notes"], "cover": g["cover"],
        } for g in library],
        "wishlist": [{
            "title": w["title"], "platform": w["platform"], "region": REGION,
            "year": None, "publisher": "", "genre": "", "slug": w.get("slug") or slugify(w["title"]), "cover": w["cover"],
        } for w in wishlist],
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    got = sum(1 for g in library if g["cover"])
    ser = sum(1 for g in library if g["serial"])
    print(f"{len(library)} juegos ({got} con carátula, {ser} con serial), "
          f"{len(wishlist)} en wishlist -> {out_path}")


if __name__ == "__main__":
    xlsx = sys.argv[1] if len(sys.argv) > 1 else "collection.xlsx"
    main(xlsx, "data/seed/collection.json")
