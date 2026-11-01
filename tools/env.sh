# Shared build settings, sourced by build.sh and the tests.
SDK=${ANDROID_HOME:-$HOME/Android}
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | tail -1)
D8="$BT/d8"
ANDROID_JAR=$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | tail -1)   # compile only; code uses API 19
# the head unit's own /system/lib, to link the loader against (no NDK)
SYSLIB=${SYSLIB:-$HOME/hmg-private/stock/ev6-sysimg/lib}
# freestanding static ARM (no libc): hmgapid, the zygote wrapper, lpatch
ARM_STATIC="--target=armv7a-linux-gnueabi -mthumb -march=armv7-a -mfloat-abi=soft -Os \
  -ffreestanding -fno-builtin -nostdlib -static -fno-stack-protector -fuse-ld=lld \
  -Wl,-e,_start -Wl,--build-id=none"
APP_KEY=${HMGAPI_KEY:-$HOME/hmg-private/keys/leafia-app.p12}
