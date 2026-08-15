#!/usr/bin/env python3
"""
Genera el icono de la app a partir del logotipo de la landing: **full** arriba en crema y **set**
abajo en ámbar, los dos con el **mismo ancho**, sobre el fondo oscuro del sitio.

Por qué un script y no un PNG dibujado a mano: el icono adaptativo de Android es un
`VectorDrawable`, que **no sabe dibujar texto** — hay que darle contornos. Acá se sacan los
contornos reales de la fuente con fontTools y se emite el XML, así que si mañana cambia el color o
la proporción se vuelve a correr en vez de reabrir un editor.

    pip install fonttools                     # única dependencia de terceros del repo
    python3 tools/make_app_icon.py            # escribe los drawables + una vista previa
    python3 tools/make_app_icon.py --preview  # solo la vista previa, no toca el proyecto

Es el **único** script del repo que necesita algo que no venga con Python, y por eso no está en CI:
el icono se regenera a mano cuando cambia, no en cada push.

**Los dos anchos.** `full` mide 1.633 em y `set` 1.533 em en Noto Sans Bold, así que igualarlos pide
estirar `set` un 1.07× — imperceptible, muy lejos de la deformación que se nota. Se hace escalando
horizontalmente el contorno, no separando las letras: el tracking abierto en una palabra de tres
letras deja huecos que se leen como un error de espaciado.

**La zona segura manda sobre el parecido.** El lienzo del icono adaptativo es 108x108, pero el
launcher recorta con la máscara que quiera —círculo, squircle, cuadrado redondeado— y solo garantiza
lo que entra en un **círculo de 66 de diámetro** centrado. El logotipo de la landing llena el cuadro;
acá no puede, o en un launcher con máscara circular se comería los bordes de la `f` y la `t`. El
bloque se dimensiona por su **diagonal**, que es lo que toca la máscara, no por su ancho.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

try:
    from fontTools.pens.boundsPen import BoundsPen
except ModuleNotFoundError:  # noqa: F401 — mensaje útil en vez de un ImportError pelado
    raise SystemExit("falta fonttools: pip install fonttools")
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

RAIZ = Path(__file__).resolve().parent.parent
RES = RAIZ / "app" / "src" / "main" / "res"

# Los mismos tres colores del `:root` de docs/index.html. Si cambian allá, cambian acá.
FONDO = "#100e0a"
CREMA = "#f4efe4"
AMBAR = "#ffc400"

LIENZO = 108.0        # el lienzo del icono adaptativo, en dp
DIAMETRO_SEGURO = 66.0  # lo que Android garantiza visible con cualquier máscara
SEPARACION = 0.045     # hueco entre las dos palabras, en fracción del ancho del bloque
# El mismo `letter-spacing: -0.045em` del `h1` de la landing. Sin esto las letras respiran más que
# en el logo del sitio y el icono deja de ser la misma pieza.
TRACKING = -0.045


def fuente() -> str:
    """La Noto Sans Bold del sistema — es la que resuelve `system-ui`, o sea la de la landing."""
    r = subprocess.run(["fc-match", "-f", "%{file}", "Noto Sans:style=Bold"],
                       capture_output=True, text=True)
    if not r.stdout.strip():
        raise SystemExit("no se encontró Noto Sans Bold; instalá noto-fonts")
    return r.stdout.strip()


def contorno(tt: TTFont, texto: str):
    """Los contornos de [texto] como (path SVG, ancho, alto), en unidades de la fuente y con Y ya
    dada vuelta (la fuente tiene Y hacia arriba; SVG y VectorDrawable, hacia abajo).

    Se mide la **tinta**, no las métricas: para centrar un logotipo importa dónde empieza y termina
    el trazo, no el alto de línea ni el espacio lateral que la fuente reserve alrededor.
    """
    glifos, cmap, hmtx = tt.getGlyphSet(), tt.getBestCmap(), tt["hmtx"]

    # Primero la caja real de la tinta, avanzando letra por letra.
    paso = TRACKING * tt["head"].unitsPerEm
    caja = BoundsPen(glifos)
    x = 0.0
    for c in texto:
        g = cmap[ord(c)]
        glifos[g].draw(TransformPen(caja, (1, 0, 0, 1, x, 0)))
        x += hmtx[g][0] + paso
    x0, y0, x1, y1 = caja.bounds

    # Ahora los trazos, trasladados al origen y con Y invertida.
    lapiz = SVGPathPen(glifos)
    x = 0.0
    for c in texto:
        g = cmap[ord(c)]
        # (xx, xy, yx, yy, dx, dy): yy = -1 da vuelta la Y, y el `+y1` deja el tope de la tinta en 0.
        glifos[g].draw(TransformPen(lapiz, (1, 0, 0, -1, x - x0, y1)))
        x += hmtx[g][0] + paso
    return lapiz.getCommands(), x1 - x0, y1 - y0


def transformar(path: str, escala_x: float, escala_y: float, dx: float, dy: float) -> str:
    """Aplica escala y traslado a un path SVG ya emitido, redondeando a 3 decimales.

    Se hace sobre el texto del path en vez de con otro pen porque `SVGPathPen` ya resolvió las
    curvas: solo quedan números, y transformarlos es una multiplicación por coordenada.
    """
    # Se parte en comandos (letras) y números, conservando el orden.
    tokens, i, n = [], 0, len(path)
    while i < n:
        c = path[i]
        if c.isalpha():
            tokens.append(c)
            i += 1
        elif c in " ,":
            i += 1
        else:
            j = i + 1 if c in "+-" else i
            while j < n and (path[j].isdigit() or path[j] == "."):
                j += 1
            tokens.append(float(path[i:j]))
            i = j

    # `M`, `L`, `Q` y `Z` llevan sus números en pares (x, y) y basta con alternar. Pero SVGPathPen
    # también emite **`H` y `V`**, que llevan UN solo número —la coordenada que cambia—: tratarlos
    # como pares desincroniza el resto del path y el contorno colapsa. Por eso hay que saber en qué
    # comando se está, no solo alternar.
    salida, cmd, esperando_x, vx = [], "", True, 0.0
    for tok in tokens:
        if isinstance(tok, str):
            salida.append(tok)
            cmd, esperando_x = tok, True
        elif cmd in ("H", "h"):
            salida.append(f"{tok * escala_x + dx:.3f} ")
        elif cmd in ("V", "v"):
            salida.append(f"{tok * escala_y + dy:.3f} ")
        elif esperando_x:
            vx = tok * escala_x + dx
            esperando_x = False
        else:
            salida.append(f"{vx:.3f},{tok * escala_y + dy:.3f} ")
            esperando_x = True
    return "".join(salida).strip()


def bloque():
    """Las dos palabras colocadas en el lienzo de 108, devueltas como (path_full, path_set)."""
    tt = TTFont(fuente())
    p_full, w_full, h_full = contorno(tt, "full")
    p_set, w_set, h_set = contorno(tt, "set")

    # Las dos al mismo ancho: el bloque se define por su ancho unitario 1 y su alto proporcional.
    alto_full = h_full / w_full          # alto de `full` si su ancho fuera 1
    alto_set = h_set / w_set
    alto_total = alto_full + SEPARACION + alto_set

    # El bloque se dimensiona por la DIAGONAL, que es lo que recorta una máscara circular.
    diagonal_unitaria = (1 + alto_total ** 2) ** 0.5
    ancho = DIAMETRO_SEGURO / diagonal_unitaria
    alto = ancho * alto_total

    x0 = (LIENZO - ancho) / 2
    y0 = (LIENZO - alto) / 2

    esc_full = ancho / w_full
    esc_set = ancho / w_set
    return (
        transformar(p_full, esc_full, esc_full, x0, y0),
        transformar(p_set, esc_set, esc_set, x0, y0 + (alto_full + SEPARACION) * ancho),
        ancho, alto,
    )


PLANTILLA_FG = '''<?xml version="1.0" encoding="utf-8"?>
<!-- GENERADO por tools/make_app_icon.py — no editar a mano, volver a correr el script. -->
<!-- El logotipo de la landing: `full` en crema arriba, `set` en ámbar abajo, mismo ancho los dos. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="{crema}" android:pathData="{full}" />
    <path android:fillColor="{ambar}" android:pathData="{set}" />
</vector>
'''

PLANTILLA_BG = '''<?xml version="1.0" encoding="utf-8"?>
<!-- GENERADO por tools/make_app_icon.py — no editar a mano. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="{fondo}" android:pathData="M0,0h108v108h-108z" />
</vector>
'''


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--preview", action="store_true", help="solo la vista previa")
    a = ap.parse_args()

    full, set_, ancho, alto = bloque()
    print(f"  bloque {ancho:.1f} x {alto:.1f} dp · diagonal "
          f"{(ancho**2 + alto**2) ** 0.5:.1f} (límite seguro {DIAMETRO_SEGURO})")

    if not a.preview:
        (RES / "drawable" / "ic_launcher_foreground.xml").write_text(
            PLANTILLA_FG.format(crema=CREMA, ambar=AMBAR, full=full, set=set_), encoding="utf-8")
        (RES / "drawable" / "ic_launcher_background.xml").write_text(
            PLANTILLA_BG.format(fondo=FONDO), encoding="utf-8")
        print(f"  escritos {RES / 'drawable'}/ic_launcher_{{foreground,background}}.xml")

    # Vista previa: el mismo dibujo en SVG, con las máscaras que usan los launchers reales.
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{LIENZO*3}" height="{LIENZO}" viewBox="0 0 324 108">
  <defs>
    <clipPath id="circulo"><circle cx="54" cy="54" r="54"/></clipPath>
    <clipPath id="squircle"><path d="M54,0 C87,0 108,21 108,54 C108,87 87,108 54,108 C21,108 0,87 0,54 C0,21 21,0 54,0 Z"/></clipPath>
  </defs>
  <g><rect width="108" height="108" fill="{FONDO}"/>
     <path fill="{CREMA}" d="{full}"/><path fill="{AMBAR}" d="{set_}"/></g>
  <g transform="translate(108,0)" clip-path="url(#squircle)"><rect width="108" height="108" fill="{FONDO}"/>
     <path fill="{CREMA}" d="{full}"/><path fill="{AMBAR}" d="{set_}"/></g>
  <g transform="translate(216,0)" clip-path="url(#circulo)"><rect width="108" height="108" fill="{FONDO}"/>
     <path fill="{CREMA}" d="{full}"/><path fill="{AMBAR}" d="{set_}"/></g>
</svg>'''
    destino = Path(sys.argv[0]).resolve().parent / "icono-preview.svg"
    destino.write_text(svg, encoding="utf-8")
    print(f"  vista previa: {destino}  (sin máscara · squircle · círculo)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
