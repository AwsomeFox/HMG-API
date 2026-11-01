#!/bin/sh
# Build hmgapi-adbd: AOSP 4.4.4's adbd (third_party/aosp-4.4.4), with the NDK
# for API 19, linked against the head unit's own libc/liblog/libdl (its static
# libc from a newer NDK can't read 4.4's property area). Output: $1/files/hmgapi-adbd
set -e
cd "$(dirname "$0")/../.."
OUTDIR=${1:?usage: build.sh <mod output dir>}
NDK=${NDK:-$HOME/Android/ndk/21.4.7075529}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
[ -x "$TC/armv7a-linux-androideabi19-clang" ] || { echo "no NDK r21 at $NDK (sdkmanager \"ndk;21.4.7075529\")"; exit 1; }
A=third_party/aosp-4.4.4
mkdir -p "$OUTDIR/files"
"$TC/armv7a-linux-androideabi19-clang" -O2 -DHAVE_ANDROID_OS -DADB_HOST=0 -D_GNU_SOURCE -DHAVE_PTHREADS -DHAVE_SYS_UIO_H \
    -Ithird_party/hmgapi-compat -I$A/include -I$A/adb -w -fPIE -pie -Wl,--hash-style=sysv -Wl,--build-id=none \
    -o "$OUTDIR/files/hmgapi-adbd" $A/adb/*.c $A/libcutils/*.c $A/libmincrypt/*.c -llog
"$TC/llvm-strip" "$OUTDIR/files/hmgapi-adbd"
md5sum "$OUTDIR/files/hmgapi-adbd"
