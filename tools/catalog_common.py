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
    """
    Identidad estable del juego dentro de su plataforma.

    **Los diacríticos se transliteran, no se descartan.** Sin esto `Astérix` daba `ast-rix` y
    `Crüe Ball` daba `cr-e-ball`: la `é` no es `[a-z0-9]`, así que el `re.sub` final la convertía en
    separador y partía la palabra al medio. Son 7 juegos con el slug roto.

    Ojo al tocar esta función: la tabla `game` de la app guarda el `slug` como vínculo con el
    catálogo, así que cambiar cómo se genera **renombra juegos que ya están en la colección del
    usuario** y rompe ese vínculo en silencio. Lo que ya está escrito se respeta aunque no coincida
    con lo que esta función daría hoy (ver `slugs-heredados.json` y la regla del lint).
    """
    t = ''.join(c for c in unicodedata.normalize('NFKD', t) if not unicodedata.combining(c))
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


# Sufijo de región del catalog number de Nintendo, que es donde de verdad está: el prefijo (`NUS`,
# `NES`) es el **producto** y es igual en los tres mercados. Incluye los códigos por país europeo,
# que son PAL: `UKV` (Reino Unido), `NOE` (Alemania), `FAH` (Francia/Holanda), `SCN` (Escandinavia),
# `ANZ`/`AUS` (Australia y Nueva Zelanda).
SERIAL_SUFFIX = {
    "USA": "NTSC-U",
    "JPN": "NTSC-J",
    "EUR": "PAL", "EUU": "PAL", "EEC": "PAL", "UKV": "PAL", "NOE": "PAL", "GER": "PAL",
    "FRA": "PAL", "ESP": "PAL", "ITA": "PAL", "HOL": "PAL", "SCN": "PAL", "FAH": "PAL",
    "AUS": "PAL", "ANZ": "PAL",
}


# Prefijos que identifican la región **sin ambigüedad**. Solo Sony y Nintendo: en Sega la misma `T`
# aparece en las tres regiones, así que ahí no dice nada. Ojo con los que NO están: `NUS` (N64) y
# `NES` son códigos de **producto**, iguales en los tres mercados, y estuvieron acá hasta que los
# catálogos japonés y europeo tuvieron seriales.
SERIAL_PREFIX = {
    "SLUS": "NTSC-U", "SCUS": "NTSC-U", "SKUS": "NTSC-U", "BLUS": "NTSC-U", "BCUS": "NTSC-U",
    "SNS": "NTSC-U",
    "SLES": "PAL", "SCES": "PAL", "SCED": "PAL", "SLED": "PAL", "BLES": "PAL", "BCES": "PAL",
    "SNSP": "PAL",
    "SLPS": "NTSC-J", "SLPM": "NTSC-J", "SCPS": "NTSC-J", "SIPS": "NTSC-J", "PAPX": "NTSC-J",
    "PBPX": "NTSC-J", "BLJM": "NTSC-J", "BLJS": "NTSC-J", "BCJS": "NTSC-J",
    "SHVC": "NTSC-J", "HVC": "NTSC-J",
}


def region_de_serial(serial):
    """La región que declara un catalog number, o None si no dice nada.

    Mira el **sufijo primero** y el prefijo después, porque cuando los dos opinan el que sabe es el
    de atrás: en Nintendo el prefijo es el producto (`NUS` = Nintendo 64, igual en los tres
    mercados) y el sufijo el mercado (`NUS-NGEJ-JPN`). Pero hay seriales viejos **sin sufijo**,
    como el `HVC-IC` del Famicom, y ahí el prefijo es lo único que hay.

    Es la única verdad sobre esto en el repo: la usan el lint, los enriquecedores y `dat_serials`.
    Cuando estaba duplicada, cada copia sabía una mitad y los datos malos pasaban por la grieta.
    """
    s = (serial or "").strip()
    if not s:
        return None
    if "-" in s:
        por_sufijo = SERIAL_SUFFIX.get(s.rsplit("-", 1)[-1].upper())
        if por_sufijo:
            return por_sufijo
    m = re.match(r"^[A-Za-z]+", s)
    return SERIAL_PREFIX.get(m.group(0).upper()) if m else None


def _regiones(name):
    """Regiones que declara el nombre de archivo de No-Intro, como conjunto.

    Se leen **todos** los paréntesis, no solo el primero: cuando el nombre desambigua por editora,
    la región queda en el segundo —`Splash Lake (NEC Avenue) (Japan)`— y mirando solo el primero la
    región de ese archivo salía «NEC AVENUE». Al no reconocerse como japonés perdía contra
    `Splash Lake (USA)`, y un catálogo japonés terminaba con la tapa americana.
    """
    return {x.strip().upper()
            for grupo in re.findall(r"\(([^)]*)\)", name)
            for x in grupo.split(",")}


def _rank(name, prefs):
    # Se compara contra las regiones **declaradas**, no por subcadena: un cartucho compartido entre
    # mercados se llama `Doom (Japan, USA) (En)`, donde no aparece la subcadena "(USA" porque el
    # paréntesis no está pegado. Con la comparación vieja esa tapa era invisible y ganaba la europea.
    suyas = _regiones(name)
    base = -100 if any(b in name for b in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate")) else 0
    for i, r in enumerate(prefs):
        if r.strip("( ").upper() in suyas:
            return base + len(prefs) - i
    return base


def dat_serials(dat_name, region="NTSC-U"):
    """`{título normalizado: serial}` para un catálogo de [region].

    **Se descarta el serial cuyo sufijo contradiga la región**, aunque la entrada gane el ranking.
    Sin eso, el volcado marcado `(World)` de `Ice Climber` traía `HVC-IC` —`HVC` es el prefijo del
    Famicom— y lo metía en el catálogo **americano**. Ese defecto ya se había limpiado a mano una
    vez; volvió al correr un builder viejo, porque la limpieza estaba en el dato y no en la función.
    Lo cazó el lint, que valida la región del serial; acá se corta en el origen.
    """
    # No todas las plataformas tienen DAT de seriales (p. ej. PlayStation): si no está, sin serial.
    try:
        body = fetch(DAT_BASE + urllib.parse.quote(dat_name) + ".dat")
    except urllib.error.HTTPError:
        print(f"  (sin DAT de seriales para {dat_name!r})")
        return {}
    idx = {}
    for comment, ser in re.findall(r'game\s*\(\s*comment "([^"]+)"\s*serial "([^"]+)"', body):
        valor = ser.split(",")[0].strip()
        dice = region_de_serial(valor)
        if dice is not None and dice != region:
            continue
        r = _rank(comment, COVER_PREF)
        k = core(comment)
        if k not in idx or r > idx[k][1]:
            idx[k] = (valor, r)
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

# Orden fijo de claves. Estas 11 van SIEMPRE (aunque vacías), así todos los catálogos son idénticos
# en estructura y el diff refleja solo cambios de contenido.
CANON_KEYS = ["title", "platform", "region", "year", "releaseDate", "publisher", "genre", "slug", "serial", "coverUrl", "rating"]


def canonical(entry):
    """Entrada con las 11 claves en orden fijo. year → null si falta; el resto → "" si falta."""
    return {k: entry.get(k, None if k == "year" else "") for k in CANON_KEYS}


# Lo que separa el título principal de sus nombres alternativos. `<br>` en cualquiera de sus formas
# (`<br/>`, `<br />`, `<BR>`) y la viñeta que algunas listas agregan además.
#
# El `\s*` antes del `>` no es adorno: la lista de PS2 escribe el tag **partido por un salto de
# línea** (`<br /\n>`). Sin eso no separaba, el título se cortaba en el salto y quedaban entradas
# literales como `Club Football<br /`.
SEPARADOR_TITULO = re.compile(r"<br\s*/?\s*>|•", re.I)

# Un `<br>` no siempre separa dos nombres: a veces solo parte un título largo en dos renglones, y
# entonces el primer trozo queda colgando de su conector —`''Adventure Quiz: Capcom World /''<br/>
# ''Hatena no Daibōken''`—. Cortar ahí deja el título mutilado, con la barra al final.
CONECTOR_COLGANDO = re.compile(r"[/:,-]\s*(?:'')?\s*$")

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

    **El separador no siempre es el `•`.** Las listas de Nintendo y PlayStation escriben
    `''Título''<br/>''Alternativo''<sup>PAL</sup>`, sin viñeta. Partiendo solo por `•` el alternativo
    no se separaba y quedaba **pegado al principal**, código incluido: así salieron 109 títulos como
    `Avatar: The Last AirbenderAvatar: The Legend of AangPAL`. El lint no lo veía porque el slug
    derivaba correctamente de ese título — solo que el título estaba mal.

    Un código que **no** es de las tres regiones —`FRA`, `AUS`, `GER`— no reemplaza nada: un nombre
    usado solo en Francia no es el nombre del catálogo PAL, que cubre Europa entera.

    Sin `region`, o si ningún alternativo es de la nuestra, vale el principal.
    """
    partes = SEPARADOR_TITULO.split(text)
    # Título partido en dos renglones, no dos nombres: se vuelve a unir antes de elegir nada.
    while len(partes) > 1 and CONECTOR_COLGANDO.search(partes[0]):
        # Se descartan las comillas de cursiva que cerraban un renglón y abrían el otro: al unir
        # los dos trozos sobran en el medio, y sin sacarlas quedan en mitad del título.
        izq = partes[0].rstrip().rstrip("'").rstrip()
        der = partes[1].lstrip().lstrip("'").lstrip()
        partes = [f"{izq} {der}"] + partes[2:]
    if len(partes) > 1 and region:
        for alt in partes[1:]:
            marca = re.search(r"<sup>\s*([A-Za-z/]+)\s*</sup>|\^([A-Za-z]+)", alt)
            codigo = (marca.group(1) or marca.group(2) or "").upper() if marca else ""
            if codigo in TITLE_SUP.get(region, ()):
                return _sin_marca(alt)
    # El principal también puede traer marca —`''Mia Hamm Soccer 64''<sup>NA</sup><br/>…`— y hay que
    # sacársela igual: si no, queda "Mia Hamm Soccer 64NA".
    return _sin_marca(partes[0])


# Región del catálogo -> cómo la marca el paréntesis que acompaña a cada editora.
PUBLISHER_PAREN = {"NTSC-U": ("US", "NA", "USA"), "NTSC-J": ("JP", "JPN"), "PAL": ("EU", "PAL", "EUR")}


def regional_publisher(text: str, region: str | None) -> str:
    """
    De una celda con **varias editoras marcadas por mercado**, la que corresponde a [region].

    Un juego podía salir con distinta editora en cada región, y la lista lo escribe todo junto:

        [[NEC]] (US)<br>[[Hudson Soft]] (JP)

    Quedarse siempre con la primera le ponía a *Aero Blasters* la editora americana **también en el
    catálogo japonés** —y con el `(US)` pegado—, así que el catálogo japonés repetía las 57 «NEC
    (US)» y 16 «TTI (US)» del americano. Es el mismo problema que [regional_title] resuelve para el
    nombre, pero acá el mercado va entre paréntesis en vez de en un `<sup>`.

    Si no hay marcas, o ninguna es de nuestra región, vale la primera: una sola editora para todos
    los mercados es el caso normal.
    """
    partes = [p for p in SEPARADOR_TITULO.split(text) if p.strip()]
    if not partes:
        return text
    if region and len(partes) > 1:
        for parte in partes:
            marca = re.search(r"\(([A-Za-z]{2,3})\)\s*$", parte.strip())
            if marca and marca.group(1).upper() in PUBLISHER_PAREN.get(region, ()):
                return _sin_paren(parte)
    return _sin_paren(partes[0])


def _sin_paren(texto: str) -> str:
    """Saca el `(US)`/`(JP)` final: es un marcador de mercado, no parte del nombre de la editora."""
    return re.sub(r"\s*\((?:US|NA|USA|JP|JPN|EU|PAL|EUR)\)\s*$", "", texto.strip(), flags=re.I)


def _sin_marca(texto: str) -> str:
    """Saca el `<sup>XX</sup>` o `^XX` que marca el mercado.

    Más adelante `<sup>` cae con el resto de las etiquetas HTML, pero el `^JP` no es HTML y quedaría
    pegado al final del nombre; y para el principal la limpieza tiene que pasar **acá**, porque su
    marca va antes del `<br>` y no la toca nadie más.
    """
    return re.sub(r"<sup>[^<]*</sup>|\^[A-Za-z]+", "", texto)


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
    faltantes (desde el DAT y el índice de carátulas, por título), completa las 11 claves, aplica
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


# Archivos que viven en `data/catalogs/` pero **no son catálogos**: no son una lista de juegos y
# recorrerlos como tal revienta (`data[0]["platform"]`). Está acá y no en cada script porque el
# lint, el generador del manifest y los builders recorren el mismo directorio; con la definición
# repetida, agregar un archivo nuevo rompía el que se hubiera olvidado de actualizar.
NO_SON_CATALOGOS = {"manifest.json", "platforms.json"}
