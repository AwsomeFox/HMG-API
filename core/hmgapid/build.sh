#!/bin/sh
# Build hmgapid for the head unit (ARMv7 Thumb, static, no libc).
# OUT: output folder (default ../../out). Extra arguments go to clang (tests use -D...).
set -e
cd "$(dirname "$0")"
. ../../tools/env.sh
OUT=${OUT:-../../out}
mkdir -p "$OUT"
clang $ARM_STATIC -Wall -Wno-unused-function "$@" -o "$OUT/hmgapid" hmgapid.c
md5sum "$OUT/hmgapid"
