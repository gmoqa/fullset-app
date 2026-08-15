#!/usr/bin/env python3
"""
Rellena `coverUrl` vacíos de un catálogo desde libretro-thumbnails, **prefiriendo** las carátulas de
una región (p. ej. `(Japan` para catálogos NTSC-J, `(Europe` para PAL). Match por título normalizado
(`core`), evitando Beta/Proto. Reproducible; solo toca los que están sin carátula.

Uso:  python3 tools/enrich_covers_libretro.py <catalogo.json> <repo-libretro> --prefer "(Japan"
"""
import json, os, re, sys, argparse, unicodedata, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog_common import fetch, core, COVER_HOST, write_catalog, _regiones


CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".libretro-trees")


def listado(repo: str) -> list[dict]:
    """
    Los nombres de archivo de `Named_Boxarts`, cacheados en disco por repositorio.

    La API de GitHub sin autenticar da **60 pedidos por hora** y cada repo cuesta dos. Los tres
    catálogos regionales de una consola comparten repositorio, así que sin caché una pasada por los
    34 catálogos gasta 68 pedidos y se corta a la mitad — que fue exactamente lo que pasó, y encima
    en silencio, porque el error sale por stderr y el resumen igual imprime "0 corregidas".
    """
    os.makedirs(CACHE, exist_ok=True)
    guardado = os.path.join(CACHE, f"{repo}.json")
    if os.path.exists(guardado):
        return json.load(open(guardado, encoding="utf-8"))
    # Se pide SOLO el subárbol de carátulas, no el recursivo del repo entero: en los repos grandes
    # (PlayStation tiene decenas de miles de archivos entre boxarts, snaps y títulos) la API
    # responde 500. Además baja una fracción de los datos, porque lo demás no lo usamos.
    root = json.loads(fetch(f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master"))
    boxarts = next((e for e in root["tree"] if e["path"] == "Named_Boxarts"), None)
    if boxarts is None:
        raise SystemExit(f"{repo}: no tiene carpeta Named_Boxarts")
    tree = json.loads(fetch(
        f"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/{boxarts['sha']}"
    ))["tree"]
    json.dump(tree, open(guardado, "w", encoding="utf-8"))
    return tree


def cover_index(repo: str, prefer: str) -> dict:
    tree = listado(repo)
    host = COVER_HOST.format(repo)
    # Se compara contra las regiones que **declara** el nombre, no por subcadena. El nombre lista
    # varias separadas por coma cuando el juego salió con un solo cartucho para más de un mercado:
    # el 32X compartía el americano con Japón, así que la tapa de EE.UU. de *Doom* se llama
    # `Doom (Japan, USA) (En)`. Buscar la subcadena "(USA" no la encontraba —el paréntesis no está
    # pegado— y ganaba `Doom (Europe)`, que sí calzaba. 1953 carátulas apuntaban a otra región.
    quiero = prefer.strip("( ").upper()
    orden = [quiero, "WORLD", "USA", "EUROPE", "JAPAN", "BRAZIL"]

    def rank(name: str) -> int:
        base = -100 if any(b in name for b in ("(Beta", "(Proto", "(Demo", "(Sample", "(Pirate")) else 0
        suyas = _regiones(name)
        for i, r in enumerate(orden):
            if r in suyas:
                return base + len(orden) - i
        return base

    # Tres índices. `exacto` es el de siempre: el mejor archivo para cada título, venga de donde
    # venga. Los otros dos solo miran archivos **de la región pedida**, y existen porque el mismo
    # juego casi nunca se llama igual en los dos mercados:
    #
    #   `propio`  el título completo — para cuando sí coincide y solo hay que preferirlo.
    #   `sinsub`  la parte anterior al " - " — el lanzamiento japonés suele llevar un subtítulo que
    #             el americano deja afuera (`Dead Moon - Getsu Sekai no Akumu` vs `Dead Moon`), así
    #             que el emparejamiento exacto solo encontraba el archivo `(USA)` y lo usaba.
    # `propio` y `sinsub` guardan **todos** los candidatos de cada clave, no el mejor. Quedarse con
    # uno solo envenena el índice cuando dos títuloscolapsan a la misma clave: `Ys I & II` y `Ys III`
    # dan los dos `ysiii`, y el primero que entraba se llevaba la clave, así que *Ys III* no
    # encontraba su propia tapa japonesa aunque existiera. Elegir es tarea de `elegir()`, que sí sabe
    # qué título se está buscando y puede descartar al impostor.
    exacto, propio, sinsub = {}, {}, {}
    for t in tree:
        p = t["path"]
        if not p.endswith(".png"):
            continue
        name = p[:-4]
        url = host + urllib.parse.quote(name + ".png")
        k = core(name)
        # A igual región gana el **nombre más corto**: es el lanzamiento base, y las variantes
        # (`(Rev 1)`, `(Arcade)`) suelen ser symlinks al canónico, que `raw.githubusercontent` sirve
        # como texto y la app no puede decodificar. Sin este desempate ganaba la variante —ordena
        # antes, porque el espacio va antes que el punto— y volvía a meter los symlinks que
        # una reparación puntual ya había resuelto (ver docs/CATALOGS.md).
        if k not in exacto or (rank(name), -len(name)) > (rank(exacto[k][1]), -len(exacto[k][1])):
            exacto[k] = (url, name)
        if quiero not in _regiones(name) or rank(name) < 0:
            continue
        propio.setdefault(k, []).append((url, name, rank(name)))
        base = re.split(r"\s+-\s+", re.sub(r"\s*\([^)]*\)", "", name), 1)[0]
        b = core(base)
        if b != k:
            sinsub.setdefault(b, []).append((url, name, rank(name)))
    return {"exacto": exacto, "propio": propio, "sinsub": sinsub, "quiero": quiero}


def _literal(s: str) -> str:
    """El texto en minúsculas con la puntuación vuelta espacio, **sin aplanarla del todo**."""
    s = "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def _empieza(nombre: str, texto: str) -> bool:
    """Si el archivo empieza literalmente por [texto], conservando los límites de palabra.

    `core()` borra toda la puntuación, y eso hace colisionar títulos que no tienen nada que ver:
    `Ys I & II` y `Ys III` colapsan los dos a `ysiii`. Al buscar por título recortado eso le ponía a
    *Ys III* la tapa de *Ys I & II*. Comparando con los espacios puestos, `ys i ii ...` ya no empieza
    por `ys iii` y el falso positivo desaparece.
    """
    return _literal(nombre).startswith(_literal(texto))


def elegir(covers: dict, titulo: str):
    """URL de la carátula para [titulo], prefiriendo la región pedida sobre el calce exacto.

    El calce exacto manda **solo si el archivo es de nuestra región**. Si no, se buscan alternativas
    propias antes de conformarse con la extranjera: el mismo título, el título con subtítulo, o el
    título recortado en los dos puntos (el catálogo dice `Ys III: Wanderers from Ys` y el archivo
    japonés es `Ys III (Japan)`, o sea al revés: acá el largo es el nuestro).
    """
    k = core(titulo)
    hit = covers["exacto"].get(k)
    if hit and covers["quiero"] in _regiones(hit[1]):
        return hit[0]
    corto = titulo.split(":")[0]
    for candidatos, exigir in ((covers["propio"].get(k), titulo),
                               (covers["sinsub"].get(k), titulo),
                               (covers["propio"].get(core(corto)), corto)):
        # De los que de verdad empiezan por el título buscado, el mejor rankeado; el desempate va
        # por nombre más corto, que es el lanzamiento base y no una reedición.
        validos = [c for c in (candidatos or []) if _empieza(c[1], exigir)]
        if validos:
            return max(validos, key=lambda c: (c[2], -len(c[1])))[0]
    return hit[0] if hit else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("repo")
    ap.add_argument("--prefer", required=True)
    ap.add_argument("--overwrite", action="store_true",
                    help="reemplazar también las que ya tienen carátula, si la de esta región es "
                         "mejor. Necesario después de arreglar el ranking: las que quedaron mal no "
                         "se corrigen solas porque el campo ya está lleno.")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    cat = json.load(open(a.catalog, encoding="utf-8"))
    covers = cover_index(a.repo, a.prefer)
    filled = changed = 0
    ejemplos = []
    for e in cat:
        url = elegir(covers, e["title"])
        if not url:
            continue
        actual = str(e.get("coverUrl") or "").strip()
        if not actual:
            e["coverUrl"] = url
            filled += 1
        elif a.overwrite and actual != url:
            if len(ejemplos) < 8:
                nombre = lambda u: urllib.parse.unquote(u.rsplit("/", 1)[-1])[:-4]
                ejemplos.append((e["title"], nombre(actual), nombre(url)))
            e["coverUrl"] = url
            changed += 1
    print(f"{os.path.basename(a.catalog)}: +{filled} carátulas"
          + (f" · {changed} corregidas de región" if changed else ""))
    for t, viejo, nuevo in ejemplos:
        print(f"    {t[:30]:<32} {viejo[:34]:<36} → {nuevo[:34]}")
    if a.dry_run:
        print("   (dry-run: no se escribió)")
        return
    write_catalog(a.catalog, cat)


if __name__ == "__main__":
    main()
