#!/usr/bin/env python3
"""
Genera el catálogo PS1 USA (NTSC-U) con el mismo formato que SNES/Genesis:
{code, title, publisher, release_date, slug}.

Fuente: Wikipedia "List of PlayStation (console) games (A–L)" y "(M–Z)".
Se filtran los juegos con lanzamiento en North America (3ª columna de región) y se limpia
el markup (enlaces, plantillas, títulos regionales con <sup>NA</sup>, etc.).

Uso:  python3 tools/build_psx_catalog.py            # escribe data/catalogs/psx-usa.json
Requiere: internet (API de Wikipedia). Sin dependencias externas.
"""
import json, re, calendar, urllib.parse, urllib.request

MONTH = {i: calendar.month_name[i] for i in range(1, 13)}
PAGES = ["List of PlayStation (console) games (A–L)", "List of PlayStation (console) games (M–Z)"]
UA = "fullset-collection/1.0 (catalog builder)"


def wikitext(page):
    url = ("https://en.wikipedia.org/w/api.php?action=parse&prop=wikitext&format=json"
           "&formatversion=2&page=" + urllib.parse.quote(page))
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["parse"]["wikitext"]


def get_table(wt):
    i = wt.find('id="softwarelist"')
    if i < 0:
        i = wt.find('wikitable sortable')
    start = wt.rfind('{|', 0, i)
    return wt[start:wt.find('\n|}', start)]


def parse_rows(tbl):
    rows, cur = [], None
    for line in tbl.split('\n'):
        l = line.rstrip()
        if l.startswith('|-'):
            if cur:
                rows.append(cur)
            cur = []
            continue
        if cur is None or l.startswith('!'):
            continue
        if l.startswith('|'):
            cur.extend(l[1:].split('||'))
    if cur:
        rows.append(cur)
    return rows


def strip_attrs(s):
    return re.sub(r'^\s*(?:[\w-]+\s*=\s*"[^"]*"\s*)+\|', '', s)


def wiki_text(s):
    s = re.sub(r'<ref[^>]*>.*?</ref>', '', s, flags=re.S)
    s = re.sub(r'<ref[^>]*/>', '', s)
    s = re.sub(r'<sup>.*?</sup>', '', s)
    s = re.sub(r'<small>.*?</small>', '', s, flags=re.S)
    s = re.sub(r'\{\{\s*[Nn]ot a typo\s*\|([^{}]*)\}\}', lambda m: m.group(1).replace('|', ''), s)
    s = re.sub(r'\{\{[^{}]*\}\}', '', s)
    s = re.sub(r"''+", '', s)
    s = re.sub(r'\[\[([^\]|]*)\|([^\]]*)\]\]', r'\2', s)
    s = re.sub(r'\[\[([^\]]*)\]\]', r'\1', s)
    s = (s.replace('&amp;', '&').replace('&nbsp;', ' ').replace('&ndash;', '–')
         .replace('&#39;', "'").replace('&quot;', '"'))
    s = re.sub(r'<[^>]+>', '', s)
    return re.sub(r'\s+', ' ', s).strip().strip(',').strip()


def clean_title(cell):
    cell = strip_attrs(cell)
    segs = []
    for p in re.split(r'<br\s*/?>|•', cell):
        p = p.strip().lstrip('•').strip()
        if not p:
            continue
        mr = re.search(r'<sup>(.*?)</sup>', p)
        reg = (mr.group(1) if mr else '').upper()
        txt = wiki_text(p)
        if txt:
            segs.append((txt, reg))
    if not segs:
        return ''
    for t, r in segs:
        if 'NA' in r:
            return t
    for t, r in segs:
        if not r:
            return t
    return segs[0][0]


def na_date(cell):
    if re.search(r'unreleased|\{\{n/a|\{\{unk|cancell', cell, re.I):
        return None
    m = re.search(r'\{\{dts\|(\d{4})(?:\|0?(\d{1,2}))?', cell)
    if m:
        y = m.group(1)
        mo = int(m.group(2)) if m.group(2) else None
        return f"{y}, {MONTH[mo]}" if mo else y
    m = re.search(r'\b(19\d{2}|20\d{2})\b', cell)
    return m.group(1) if m else None


def slug(t):
    s = t.lower().replace('&', ' and ')
    s = re.sub(r"[.'’:]", '', s)
    s = re.sub(r'[^a-z0-9]+', '-', s)
    return s.strip('-')


# publishers que Wikipedia deja vacíos (sin fuente en la tabla)
PUB_FALLBACK = {"Darkstalkers: The Night Warriors": "Capcom",
                "Detective Barbie: The Mystery Cruise": "Mattel Interactive"}


def main(out_path):
    games = {}
    for page in PAGES:
        for cells in parse_rows(get_table(wikitext(page))):
            if len(cells) < 6:
                continue
            title = clean_title(cells[0])
            if not title or title.lower() == 'title':
                continue
            na = na_date(cells[5])
            if not na:
                continue
            pub = wiki_text(strip_attrs(cells[2])).split(',')[0].strip() or PUB_FALLBACK.get(title, "")
            games[title] = {"title": title, "publisher": pub, "release_date": na, "slug": slug(title)}

    items = sorted(games.values(), key=lambda g: g["title"].lower())
    # Esquema normalizado (igual que snes/genesis): title/platform/region/year/publisher/genre/slug.
    out = [{"title": g["title"], "platform": "PlayStation", "region": "NTSC-U",
            "year": year_of(g["release_date"]), "publisher": clean_pub(g["publisher"]),
            "genre": "", "slug": g["slug"]} for g in items]
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"{len(out)} juegos PS1 USA -> {out_path}")


def year_of(s):
    if not s:
        return None
    m = re.search(r'\b(19|20)\d{2}\b', str(s))
    if not m:
        return None
    y = int(m.group(0))
    return y if 1970 <= y <= 2006 else None


def clean_pub(p):
    p = (p or "").strip()
    return "" if p.lower() in ("", "no information") else p


if __name__ == "__main__":
    main("data/catalogs/psx-usa.json")
