#!/usr/bin/env python3
"""Dibuja el glifo de la Nintendo 3DS en el estilo de Controllercons (solid, viewBox 64x64).

La silueta es la consola **abierta**, que es como se la reconoce: dos paneles con sus pantallas, el
de arriba más ancho. Los detalles van en negativo (huecos), igual que el resto del paquete.

Sin arcos: todo se emite con cúbicas. `A` está soportado por VectorDrawable pero las curvas de Bézier
funcionan en cualquier renderer sin depender de eso.
"""
K = 0.5523  # kappa: cuánto vale el tirador de una cúbica que aproxima un cuarto de círculo


def rrect(x, y, w, h, r):
    """Rectángulo redondeado, en sentido horario."""
    x2, y2 = x + w, y + h
    c = r * K
    return (
        f"M{x + r},{y} "
        f"L{x2 - r},{y} C{x2 - r + c},{y} {x2},{y + r - c} {x2},{y + r} "
        f"L{x2},{y2 - r} C{x2},{y2 - r + c} {x2 - r + c},{y2} {x2 - r},{y2} "
        f"L{x + r},{y2} C{x + r - c},{y2} {x},{y2 - r + c} {x},{y2 - r} "
        f"L{x},{y + r} C{x},{y + r - c} {x + r - c},{y} {x + r},{y} Z"
    )


def circ(cx, cy, r):
    """Círculo en sentido **antihorario**, para que `evenOdd` lo recorte del cuerpo."""
    c = r * K
    return (
        f"M{cx},{cy - r} "
        f"C{cx - c},{cy - r} {cx - r},{cy - c} {cx - r},{cy} "
        f"C{cx - r},{cy + c} {cx - c},{cy + r} {cx},{cy + r} "
        f"C{cx + c},{cy + r} {cx + r},{cy + c} {cx + r},{cy} "
        f"C{cx + r},{cy - c} {cx + c},{cy - r} {cx},{cy - r} Z"
    )


def glifo():
    p = []
    # Panel de arriba y su pantalla ancha (la 3D es 16:9).
    p.append(rrect(9, 5, 46, 27, 3.5))
    p.append(rrect(14.5, 9.5, 35, 18, 1.5))
    # Panel de abajo, un poco más alto: ahí van los controles.
    p.append(rrect(9, 34, 46, 25, 3.5))
    # Pantalla táctil, centrada y 4:3.
    p.append(rrect(25, 38, 14, 11, 1.5))
    # Circle Pad y cruceta, a la izquierda.
    p.append(circ(16.5, 41.5, 3.2))
    p.append(circ(16.5, 50.5, 2.0))
    # Cuatro botones en rombo, a la derecha.
    p.append(circ(47.5, 41.5, 1.9))
    p.append(circ(43.6, 45.4, 1.9))
    p.append(circ(51.4, 45.4, 1.9))
    p.append(circ(47.5, 49.3, 1.9))
    return " ".join(p)


if __name__ == "__main__":
    import pathlib, sys
    d = glifo()
    S = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    (S / "3ds.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">'
        f'<path fill="#e8e4dc" fill-rule="evenodd" d="{d}"/></svg>'
    )
    (S / "3ds.path").write_text(d)
    print(f"  {len(d)} chars de path")
