#!/bin/bash
# Compila whisper.cpp + ggml (CPU) para iOS desde las fuentes vendoreadas y deja una estatica
# combinada por plataforma en whisper/lib/<sdk>/libwhisper_all.a. Lo corre el pre-build de Xcode
# (solo si falta el .a), asi el proyecto se buildea sin pasos manuales.
set -eo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

build_sdk() {
  sdk="$1"
  out="$DIR/lib/$sdk/libwhisper_all.a"
  if [ -f "$out" ]; then echo "whisper: $sdk ya construido"; return; fi
  bdir="$DIR/build/$sdk"
  echo "whisper: construyendo $sdk..."
  cmake -B "$bdir" -S "$DIR" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$sdk" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=15.0 \
    -DCMAKE_BUILD_TYPE=Release >/dev/null
  cmake --build "$bdir" --config Release -j >/dev/null
  mkdir -p "$DIR/lib/$sdk"
  libtool -static -o "$out" $(find "$bdir" -name '*.a') 2>/dev/null
  echo "whisper: $sdk listo -> $out"
}

build_sdk iphonesimulator
build_sdk iphoneos
