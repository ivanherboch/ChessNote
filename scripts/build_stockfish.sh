#!/usr/bin/env bash
# Compile Stockfish 11 (sources dans stockfish/src) en exécutables Android,
# empaquetés comme "libstockfish.so" dans jniLibs pour être lancés hors-ligne via UCI.
set -euo pipefail
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/opt/android-sdk}/ndk/27.2.12479018}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/stockfish/src"
OUT="$ROOT/app/src/main/jniLibs"
build() { # abi triple flags...
  local abi="$1" triple="$2"; shift 2
  mkdir -p "$OUT/$abi"
  "$TC/${triple}26-clang++" -O3 -std=c++11 -DNDEBUG -fno-exceptions -fno-rtti -flto \
    -fPIE -pie -static-libstdc++ -Wl,-z,max-page-size=16384 "$@" \
    "$SRC"/*.cpp "$SRC"/syzygy/*.cpp -o "$OUT/$abi/libstockfish.so"
  "$TC/llvm-strip" "$OUT/$abi/libstockfish.so"
  echo "OK $abi"
}
build arm64-v8a   aarch64-linux-android   -DIS_64BIT -DUSE_POPCNT
build armeabi-v7a armv7a-linux-androideabi -mfpu=neon
build x86_64      x86_64-linux-android    -DIS_64BIT -DUSE_POPCNT -msse4.2 -mpopcnt
