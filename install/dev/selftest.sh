#!/system/bin/sh
# HMG-API hook test: run the hook runtime's self-test in a one-off app_process,
# away from the zygote (nothing on /system changes). Needs root for the Dalvik
# cache of the test jar, which it removes afterwards:
#   adb shell /data/local/tmp/nemesis-d root -c 'sh /data/local/tmp/hmgapi/dev/selftest.sh'
# (no sed on the unit, so the folder is fixed and so is its Dalvik cache name)
HERE=/data/local/tmp/hmgapi
JAR=$HERE/out/hmgapi-hook.jar
C=/data/dalvik-cache/data@local@tmp@hmgapi@out@hmgapi-hook.jar@classes.dex
LD_PRELOAD=$HERE/out/libhmgapi_hook.so HMGAPI_SELFTEST=$JAR CLASSPATH=$JAR \
    app_process /system/bin hmgapi.hook.SelfTest
rc=$?
rm -f "$C"
echo "app_process exit $rc"
