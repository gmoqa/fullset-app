#!/usr/bin/env python3
"""
Motor común de los generadores de catálogo (una función [build] por consola).

Fuente de cada campo:
  - título / editora / año : Wikipedia "List of … games" (columna North America → NTSC-U).
  - serial (horneado)      : DAT `metadat/serial/…` de libretro-database (por `comment "T (Región)"`).
  - coverUrl (horneada)    : árbol de `libretro-thumbnails/{repo}`, match por título, región más
                             cercana a NTSC-U, evitando Beta/Proto.

Todo en **merge**: no pisa valores presentes en el JSON; agrega juegos y rellena vacíos. Cada
generador por consola solo aporta la config (página, plataforma, salida, DAT, repo, columna NA).
"""
import json, os, re, unicodedata, urllib.error, urllib.parse, urllib.request

UA = {"User-Agent": "fullset-catalog/1.0"}
DAT_BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/serial/"
COVER_HOST = "https://raw.githubusercontent.com/libretro-thumbnails/{}/master/Named_Boxarts/"
COVER_PREF = ["(USA", "(World", "(Europe", "(Brazil", "(Japan"]


def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=45).read().decode("utf-8", "ignore")


def wikitext(page):
    u = ("https://en.wikipedia.org/w/api.php?action=parse&prop=wikitext&format=json"
         "&formatversion=2&page=" + urllib.parse.quote(page))
    return json.loads(fetch(u))["parse"]["wikitext"]


def get_table(wt):
    """La lista de juegos: preferimos la tabla id="softwarelist"; si no, la primera wikitable."""
    i = wt.find('id="softwarelist"')
    if i < 0:
        i = wt.find("wikitable")
    start = wt.rfind("{|", 0, i)
    return wt[start:wt.find("\n|}", start)]


def parse_rows(tbl):
    """Sirve tanto para celdas en su propia línea (NES) como separadas por || (Master System)."""
    rows, cur = [], None
    for line in tbl.split("\n"):
        l = line.rstrip()
        if l.startswith("|-"):
            if cur:
                rows.append(cur)
            cur = []
            continue
        if cur is None or l.startswith("!") or l.startswith("|+"):
            continue
        if l.startswith("|"):
            cur.extend(l[1:].split("||"))
    if cur:
        rows.append(cur)
    return rows


def strip_attrs(s):
    s = re.sub(r'<span[^>]*>\s*</span>\s*\|?', '', s)
    s = re.sub(r'[\w-]+\s*=\s*"[^"]*"\s*\|', '', s)      # data-sort-value="…"|
    return s


def wiki_text(s):
    s = re.sub(r'<ref[^>]*>.*?</ref>', '', s, flags=re.S)
    s = re.sub(r'<ref[^>]*/>', '', s)
    s = re.sub(r'<sup>.*?</sup>', '', s)
    # {{Sort|clave|texto}} → texto (el título va anidado; sin esto se borra con el {{…}} genérico).
    s = re.sub(r'\{\{[Ss]ort\|[^|{}]*\|(.*?)\}\}', r'\1', s)
    s = re.sub(r'\{\{[^{}]*\}\}', '', s)
    s = re.sub(r"''+", '', s)
    s = re.sub(r'\[\[([^\]|]*)\|([^\]]*)\]\]', r'\2', s)
    s = re.sub(r'\[\[([^\]]*)\]\]', r'\1', s)
    s = s.replace('&amp;', '&').replace('&nbsp;', ' ').replace('&#39;', "'").replace('&quot;', '"')
    s = re.sub(r'<[^>]+>', '', s)
    return re.sub(r'\s+', ' ', s).strip()


def first_title(cell):
    return wiki_text(re.split(r'<br\s*/?>', strip_attrs(cell))[0])  # nombre US primero


def na_year(cell, lo=1983, hi=2010):
    if re.search(r'unreleased|cancell|\{\{n/a', cell, re.I):
        return None
    m = re.search(r'\{\{dts\|(\d{4})', cell) or re.search(r'\b(19\d{2}|20\d{2})\b', cell)
    if not m:
        return None
    y = int(m.group(1))
    return y if lo <= y <= hi else None


def slug(t):
    s = t.lower().replace('&', ' and ')
    s = re.sub(r"[.'’:]", '', s)
    return re.sub(r'[^a-z0-9]+', '-', s).strip('-')


def core(t):
    # NFKD normaliza los títulos de Wikipedia a ASCII, como los nombres de archivo de No-Intro:
    # diacríticos (Pokémon→Pokemon) y fracciones (63⅓→63 1⁄3→6313). Sin esto no matchean.
    t = ''.join(c for c in unicodedata.normalize('NFKD', t) if not unicodedata.combining(c))
    s = re.sub(r'\s*\([^)]*\)', '', t).lower().replace('&', ' and ')
    s = re.sub(r'^the\s+|,\s*the\b', '', s)
    return re.sub(r'[^a-z0-9]+', '', s)


def _rank(name, prefs):
    base = -100 if any(b in name for b in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate")) else 0
    for i, r in enumerate(prefs):
        if r in name:
            return base + len(prefs) - i
    return base


def dat_serials(dat_name):
    # No todas las plataformas tienen DAT de seriales (p. ej. PlayStation): si no está, sin serial.
    try:
        body = fetch(DAT_BASE + urllib.parse.quote(dat_name) + ".dat")
    except urllib.error.HTTPError:
        print(f"  (sin DAT de seriales para {dat_name!r})")
        return {}
    idx = {}
    for comment, ser in re.findall(r'game\s*\(\s*comment "([^"]+)"\s*serial "([^"]+)"', body):
        r = _rank(comment, COVER_PREF)
        k = core(comment)
        if k not in idx or r > idx[k][1]:
            idx[k] = (ser.split(",")[0].strip(), r)
    return {k: v[0] for k, v in idx.items()}


def cover_index(repo):
    u = f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master?recursive=1"
    try:
        tree = json.loads(fetch(u))["tree"]
    except urllib.error.HTTPError:
        print(f"  (sin repo de carátulas {repo!r})")
        return {}
    host = COVER_HOST.format(repo)
    idx = {}
    for t in tree:
        p = t["path"]
        if not (p.startswith("Named_Boxarts/") and p.endswith(".png")):
            continue
        name = p[len("Named_Boxarts/"):-4]
        k = core(name)
        if k not in idx or _rank(name, COVER_PREF) > _rank(idx[k], COVER_PREF):
            idx[k] = name
    return {k: host + urllib.parse.quote(v + ".png") for k, v in idx.items()}


def _merge(existing, new):
    old = existing.get(new["slug"])
    if old is None:
        existing[new["slug"]] = new
    else:
        for k, v in new.items():
            if not str(old.get(k, "")).strip() and str(v).strip():
                old[k] = v


# --- Forma canónica: mismo esquema, orden y formato en TODOS los catálogos (para versionar) -------

# Orden fijo de claves. Estas 9 van SIEMPRE (aunque vacías), así todos los catálogos son idénticos
# en estructura y el diff refleja solo cambios de contenido.
CANON_KEYS = ["title", "platform", "region", "year", "releaseDate", "publisher", "genre", "slug", "serial", "coverUrl", "rating"]


def canonical(entry):
    """Entrada con las 9 claves en orden fijo. year → null si falta; el resto → "" si falta."""
    return {k: entry.get(k, None if k == "year" else "") for k in CANON_KEYS}


# Región del catálogo -> cómo la marca el `<sup>` del título alternativo.
TITLE_SUP = {"NTSC-U": ("NA", "US", "USA"), "NTSC-J": ("JP", "JPN"), "PAL": ("PAL", "EU", "EUR")}


def regional_title(text: str, region: str | None) -> str:
    """
    De un título con nombre alternativo, el que corresponde a [region].

    Las listas escriben `Título<br />•Alternativo<sup>NA</sup>`, donde el `<sup>` dice **en qué
    mercado se usó el otro nombre**. En un catálogo NTSC-U eso importa: *Final Fantasy VI* se vendió
    como **Final Fantasy III** y *The Chaos Engine* como **Soldiers of Fortune**. Quedarse siempre
    con el primero no solo pone el nombre equivocado — además duplica, porque el catálogo ya tiene
    el juego bajo su nombre americano y no se reconocen entre sí.

    Sin `region`, o si ningún alternativo es de la nuestra, vale el principal.
    """
    partes = text.split("•")
    if len(partes) > 1 and region:
        for alt in partes[1:]:
            marca = re.search(r"<sup>\s*([A-Za-z/]+)\s*</sup>|\^([A-Za-z]+)", alt)
            codigo = (marca.group(1) or marca.group(2) or "").upper() if marca else ""
            if codigo in TITLE_SUP.get(region, ()):
                # La marca sale acá: más adelante `<sup>` cae con el resto de las etiquetas, pero
                # el `^JP` no es HTML y quedaría pegado al final del nombre.
                return re.sub(r"<sup>[^<]*</sup>|\^[A-Za-z]+", "", alt)
    return partes[0]


def clean_cell(text: str, region: str | None = None) -> str:
    """
    Quita el wikitext de una celda y deja el texto visible.

    Tres cosas que no son obvias y rompen si faltan:
      - Las celdas pueden llevar atributos HTML antes del contenido, separados por `|`
        (`data-sort-value="Legend of Zelda…"|The Legend of Zelda…`, `id="0–9"|…`). Sin quitarlos, el
        atributo termina dentro del título.
      - Una fila puede separar celdas con `||` en la misma línea, así que se corta ahí: si no, la
        desarrolladora se pega al nombre del juego.
      - El título alternativo tras un "•" puede ser **el de nuestra región**: ver [regional_title].
    """
    s = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", text, flags=re.S)
    s = s.split("||")[0]
    s = re.sub(r'^\s*(?:[\w-]+="[^"]*"\s*)+\|', "", s)
    # Las plantillas se resuelven ANTES que los enlaces, porque pueden ir adentro:
    # `[[{{Not a typo|Med|iEvil}}]]`. Si se procesara el enlace primero, su regex cortaría por el
    # primer `|` de la plantilla y el título quedaría partido. Las de texto (Not a typo, sic…)
    # concatenan sus argumentos, así que se unen; las de marcado (unreleased, dts…) desaparecen.
    s = re.sub(r"\{\{(?:not a typo|sic|nowrap|small)\|([^}]*)\}\}",
               lambda m: m.group(1).replace("|", ""), s, flags=re.I)
    s = re.sub(r"\{\{[^}]*\}\}", "", s)
    # El corte por "•" va **antes** de sacar las etiquetas, porque la marca de región vive en un
    # `<sup>` y quitarla primero dejaría los alternativos indistinguibles entre sí.
    s = regional_title(s, region)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"''+|<[^>]+>", "", s)
    return re.sub(r"\s+", " ", s).strip(" |")

def overrides_path(out):
    """Correcciones a mano por plataforma: tools/overrides/<basename-del-catálogo>.json."""
    return os.path.join(os.path.dirname(__file__), "overrides", os.path.basename(out))


def apply_overrides(by_slug, out):
    """
    Aplica `tools/overrides/<cat>.json` = `{slug: {campo: valor, ...}}`. Se aplica al final y
    **pisa** el valor auto-derivado, así tus correcciones sobreviven un rebuild desde cero.

    Las claves que empiezan con `_` (`_source`, `_note`, `_date`) documentan **de dónde salió** la
    corrección y se quedan en el archivo de overrides: no viajan al catálogo, que tiene que cumplir
    el esquema de 11 campos. Copiarlas rompía el lint —42 errores en `snes-usa.json`— y además
    duraban poco, porque el primer enriquecedor que pasara las borraba al canonicalizar.
    """
    p = overrides_path(out)
    if not os.path.exists(p):
        return
    for s, fields in json.load(open(p, encoding="utf-8")).items():
        if s in by_slug:
            by_slug[s].update({k: v for k, v in fields.items() if not k.startswith("_")})
        else:
            print(f"  ⚠ override sin match (slug {s!r})")


def write_catalog(out, entries):
    """Escribe en forma canónica: overrides aplicados, ordenado por slug, **un objeto por línea**
    (sigue siendo un array JSON válido). Reporta cobertura."""
    by_slug = {e["slug"]: canonical(e) for e in entries}
    apply_overrides(by_slug, out)
    rows = sorted(by_slug.values(), key=lambda e: e["slug"])
    body = ",\n".join(json.dumps(e, ensure_ascii=False) for e in rows)
    with open(out, "w", encoding="utf-8") as f:
        f.write("[\n" + body + "\n]\n")
    n = len(rows)
    def pct(k): return f"{sum(1 for e in rows if str(e.get(k) or '').strip()) * 100 // n}%" if n else "0%"
    print(f"{n} juegos -> {os.path.relpath(out)}")
    print(f"  año {pct('year')} · editora {pct('publisher')} · serial {pct('serial')} · carátula {pct('coverUrl')}")
    return rows


def build(page, platform, out, dat_name, repo, na_col, year=(1983, 2010)):
    """Genera desde Wikipedia (+ DAT de seriales + índice de carátulas), en merge sobre lo existente."""
    existing = {}
    if os.path.exists(out):
        for e in json.load(open(out, encoding="utf-8")):
            existing[e["slug"]] = e

    serials = dat_serials(dat_name)
    covers = cover_index(repo)
    for cells in parse_rows(get_table(wikitext(page))):
        if len(cells) <= na_col:
            continue
        title = first_title(cells[0])
        if not title or title.lower().startswith("title"):
            continue
        y = na_year(cells[na_col], *year)
        if y is None:
            continue  # no salió en Norteamérica → no es NTSC-U
        pub = wiki_text(re.split(r'<br', strip_attrs(cells[2]))[0])
        s = slug(title)
        _merge(existing, {"title": title, "platform": platform, "region": "NTSC-U",
                          "year": y, "publisher": pub, "genre": "", "slug": s,
                          "serial": serials.get(core(title), ""),
                          "coverUrl": covers.get(core(title), "")})

    write_catalog(out, existing.values())


def normalize(out, dat_name, repo):
    """
    Normaliza un catálogo YA existente sin regenerar el set de juegos: hornea `serial` y `coverUrl`
    faltantes (desde el DAT y el índice de carátulas, por título), completa las 9 claves, aplica
    overrides y reescribe en forma canónica. Es la vía para poner los catálogos viejos (SNES,
    Genesis, PSX) al mismo molde que NES/N64 sin arriesgar duplicados.
    """
    entries = json.load(open(out, encoding="utf-8"))
    serials = dat_serials(dat_name)
    covers = cover_index(repo)
    for e in entries:
        if not str(e.get("serial", "")).strip():
            e["serial"] = serials.get(core(e["title"]), "")
        if not str(e.get("coverUrl", "")).strip():
            e["coverUrl"] = covers.get(core(e["title"]), "")
    write_catalog(out, entries)
