#!/usr/bin/env python3
"""
Publica una versión: changelog, APK firmado y release en GitHub, de un comando.

    python3 tools/release.py 0.2            # pregunta antes de publicar
    python3 tools/release.py 0.2 --yes      # sin preguntar
    python3 tools/release.py 0.2 --dry-run  # arma todo y no publica nada

Hace, en este orden:

    1. Verifica que se pueda publicar (rama, árbol limpio, sincronizado con origin)
    2. Corre **todo**: lint de datos, conteos, manifest, tests de Kotlin
    3. Sube `versionCode` y fija `versionName`
    4. Compila el APK de release **sin la API key de SteamGridDB**
    5. Verifica el APK: firmado, sin secretos adentro
    6. Escribe `CHANGELOG.md` desde los commits
    7. Commitea, etiqueta, pushea y crea el release con el APK

## Por qué cada paso está donde está

**Las verificaciones van antes de tocar nada.** Un release a medias —tag puesto, APK sin subir— es
más molesto de limpiar que uno que no empezó.

**Los tests corren antes de compilar el APK**, no después: compilar release tarda un minuto y no
tiene sentido gastarlo si el lint de datos ya falla.

**El APK se compila con `-PsteamGridDbKey=` vacío.** Esa clave se hornea en `BuildConfig`, o sea que
termina en el dex y **cualquiera que descomprima el APK puede leerla**. En una build para uno mismo
da igual; en una que se publica en un repo público, es regalar la credencial. Ya nos pasó una vez.

**El APK se audita antes de subirlo**, no se confía en que el paso anterior salió bien: se busca la
clave en los dex y se comprueba que esté firmado.

**`versionCode` siempre sube.** Es lo único que mira Android para decidir si un APK es una
actualización; si no sube, no se puede instalar encima del anterior.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from datetime import date
from pathlib import Path

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(RAIZ, "app", "build.gradle.kts")
CHANGELOG = os.path.join(RAIZ, "CHANGELOG.md")
APK = os.path.join(RAIZ, "app", "build", "outputs", "apk", "release", "app-release.apk")

# Prefijo de commit -> título en el changelog. Los que no están acá no se listan: `chore`, `ci`,
# `build` y `test` son ruido para quien lee qué cambió en la app.
# Los títulos van en **inglés** porque se publican: son los encabezados del release en GitHub y de
# CHANGELOG.md. Los comentarios de este archivo siguen en español, que es su idioma.
SECCIONES = {
    "feat": "What's new",
    "fix": "Fixes",
    "style": "Interface",
    "perf": "Performance",
    "refactor": "Under the hood",
    "docs": "Documentation",
}


def corre(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=RAIZ, capture_output=True, text=True, **kw)


# Lo que hay que deshacer si algo falla después de haber tocado archivos. Un intento fallido no
# puede dejar el árbol con la versión a medio subir: el siguiente arrancaría desde un número que
# nunca se publicó, y a la larga los `versionCode` dejan de significar nada.
_deshacer: list[tuple[str, str]] = []


def salir(motivo: str) -> None:
    for ruta, contenido in reversed(_deshacer):
        Path(ruta).write_text(contenido, encoding="utf-8") if contenido is not None else \
            Path(ruta).unlink(missing_ok=True)
    if _deshacer:
        print("    (se restauró el árbol a como estaba)")
    print(f"\n  ✗ {motivo}")
    sys.exit(1)


def paso(texto: str) -> None:
    print(f"  → {texto}")


def verificar_repo(permitir_sucio: bool) -> None:
    rama = corre(["git", "branch", "--show-current"]).stdout.strip()
    if rama != "main":
        salir(f"estás en '{rama}', no en main")
    if not permitir_sucio and corre(["git", "status", "--porcelain"]).stdout.strip():
        salir("hay cambios sin commitear: un release tiene que salir de un árbol limpio")
    corre(["git", "fetch", "origin", "--quiet"])
    detras = corre(["git", "rev-list", "--count", "HEAD..origin/main"]).stdout.strip()
    if detras not in ("", "0"):
        salir(f"el remoto tiene {detras} commits que no tenés: hacé pull primero")


def verificar_todo() -> None:
    """Datos y código. Un release no puede llevar catálogos rotos ni tests en rojo."""
    for nombre, cmd in [
        ("lint de catálogos", ["python3", "tools/catalog_lint.py"]),
        ("conteos por consola", ["python3", "tools/platform_counts.py", "--check"]),
        ("manifest al día", ["python3", "tools/catalog_manifest.py", "--check"]),
        ("tests de datos", ["python3", "tools/test_catalog_lint.py"]),
        ("tests de Kotlin", ["./gradlew", "-q", ":shared:testDebugUnitTest"]),
    ]:
        paso(f"verificando {nombre}…")
        if corre(cmd).returncode != 0:
            salir(f"falló: {nombre}")


def version_actual() -> tuple[int, str]:
    t = open(GRADLE, encoding="utf-8").read()
    return (
        int(re.search(r"versionCode = (\d+)", t).group(1)),
        re.search(r'versionName = "([^"]+)"', t).group(1),
    )


def escribir_version(code: int, name: str) -> None:
    t = open(GRADLE, encoding="utf-8").read()
    _deshacer.append((GRADLE, t))
    t = re.sub(r"versionCode = \d+", f"versionCode = {code}", t, count=1)
    t = re.sub(r'versionName = "[^"]+"', f'versionName = "{name}"', t, count=1)
    open(GRADLE, "w", encoding="utf-8").write(t)


def compilar() -> None:
    paso("compilando el APK de release (sin la API key de SteamGridDB)…")
    r = corre(["./gradlew", "-q", ":app:assembleRelease", "-PsteamGridDbKey="])
    if r.returncode != 0 or not os.path.exists(APK):
        salir("no compiló el APK de release" + (f"\n{r.stderr[-600:]}" if r.stderr else ""))


def auditar_apk() -> None:
    """Que no lleve secretos y que esté firmado. No se confía en el paso anterior."""
    paso("auditando el APK…")
    clave = ""
    props = os.path.join(RAIZ, "local.properties")
    if os.path.exists(props):
        for linea in open(props, encoding="utf-8"):
            if linea.startswith("STEAMGRIDDB_API_KEY="):
                clave = linea.split("=", 1)[1].strip()
    if clave:
        # `grep -a` porque el dex es binario; sin eso grep lo saltea y "no encontrar nada" mentiría.
        hallada = subprocess.run(
            ["grep", "-a", "-c", clave, APK], capture_output=True, text=True,
            env={**os.environ, "LC_ALL": "C"},
        ).stdout.strip()
        if hallada not in ("", "0"):
            salir(f"¡el APK contiene la API key de SteamGridDB ({hallada} veces)! No se publica.")
    # Con `apksigner`, no mirando el zip: desde el esquema de firma v2 la firma vive en el **APK
    # Signing Block**, fuera de las entradas del archivo, así que buscar `META-INF/*.RSA` da
    # "sin firmar" en un APK perfectamente firmado.
    firmador = apksigner()
    if not firmador:
        print("    (sin apksigner a mano: no se pudo verificar la firma)")
        return
    r = subprocess.run([firmador, "verify", "--print-certs", APK], capture_output=True, text=True)
    if r.returncode != 0 or "Signer #1" not in r.stdout:
        salir("el APK no está firmado: revisá keystore.properties")
    dn = next((x for x in r.stdout.splitlines() if "certificate DN" in x), "")
    print(f"    firmado · {dn.split(':', 1)[-1].strip()}")


def apksigner() -> str | None:
    """La copia más nueva del SDK. La ruta sale de `local.properties`, como la usa Gradle."""
    props = os.path.join(RAIZ, "local.properties")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    if os.path.exists(props):
        for linea in open(props, encoding="utf-8"):
            if linea.startswith("sdk.dir="):
                sdk = linea.split("=", 1)[1].strip()
    base = os.path.join(sdk, "build-tools")
    if not os.path.isdir(base):
        return None
    for v in sorted(os.listdir(base), reverse=True):
        ruta = os.path.join(base, v, "apksigner")
        if os.path.exists(ruta):
            return ruta
    return None


def changelog(desde: str, version: str) -> str:
    """Las novedades desde el último tag, agrupadas por tipo de commit."""
    rango = f"{desde}..HEAD" if desde else "HEAD"
    lineas = corre(["git", "log", rango, "--format=%s", "--no-merges"]).stdout.strip().split("\n")
    grupos: dict[str, list[str]] = {}
    for linea in lineas:
        m = re.match(r"^([a-z]+)(?:\([^)]*\))?:\s*(.+)$", linea.strip())
        if not m:
            continue
        titulo = SECCIONES.get(m.group(1))
        if titulo:
            grupos.setdefault(titulo, []).append(m.group(2).strip())
    if not grupos:
        return "No listed changes.\n"
    partes = [f"## v{version} — {date.today().isoformat()}\n"]
    for titulo in SECCIONES.values():
        if titulo in grupos:
            partes.append(f"### {titulo}\n")
            partes += [f"- {x}" for x in grupos[titulo]]
            partes.append("")
    return "\n".join(partes) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("version", help='sin la "v": 0.2')
    ap.add_argument("--yes", action="store_true", help="no preguntar antes de publicar")
    ap.add_argument("--dry-run", action="store_true", help="arma todo y no publica nada")
    ap.add_argument("--allow-dirty", action="store_true", help="permitir árbol sucio (para probar)")
    a = ap.parse_args()

    version = a.version.lstrip("v")
    tag = f"v{version}"
    if corre(["git", "tag", "-l", tag]).stdout.strip():
        salir(f"el tag {tag} ya existe")

    verificar_repo(a.allow_dirty)
    verificar_todo()

    code, nombre_previo = version_actual()
    anterior = corre(["git", "describe", "--tags", "--abbrev=0"]).stdout.strip()
    notas = changelog(anterior, version)

    escribir_version(code + 1, version)
    compilar()
    auditar_apk()

    previo = open(CHANGELOG, encoding="utf-8").read() if os.path.exists(CHANGELOG) else ""
    _deshacer.append((CHANGELOG, previo or None))
    cabecera = "# Changelog\n\n"
    open(CHANGELOG, "w", encoding="utf-8").write(
        cabecera + notas + "\n" + previo.removeprefix(cabecera).lstrip("\n"))

    print(f"\n  {tag} · versionCode {code} → {code + 1} · versionName {nombre_previo} → {version}")
    print(f"  APK: {os.path.getsize(APK):,} bytes")
    print("  " + "-" * 58)
    print("\n".join("  " + x for x in notas.strip().split("\n")))
    print("  " + "-" * 58)

    if a.dry_run:
        print("\n  (dry-run: no se publicó nada; `git checkout` para descartar los cambios)")
        return 0
    if not a.yes:
        # Publicar es difícil de deshacer: la gente ya puede haber descargado el APK.
        if input("\n  ¿Publicar? [s/N] ").strip().lower() not in ("s", "si", "sí", "y"):
            print("  cancelado (los cambios de versión y changelog quedan sin commitear)")
            return 1

    paso("commiteando y etiquetando…")
    corre(["git", "add", "app/build.gradle.kts", "CHANGELOG.md"])
    corre(["git", "commit", "-q", "-m", f"chore: {tag} (versionCode {code + 1})"])
    corre(["git", "tag", "-a", tag, "-m", f"fullset {tag}"])
    for cmd in (["git", "push", "origin", "main"], ["git", "push", "origin", tag]):
        if corre(cmd).returncode != 0:
            salir(f"falló: {' '.join(cmd)}")

    paso("creando el release…")
    destino = os.path.join(RAIZ, "app", "build", "outputs", "apk", "release", f"fullset-{tag}.apk")
    os.replace(APK, destino)
    r = corre(["gh", "release", "create", tag, destino, "--title", f"fullset {tag}",
               "--notes", notas])
    if r.returncode != 0:
        salir(f"falló gh release: {r.stderr[-400:]}")
    print(f"\n  ✓ {r.stdout.strip()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
