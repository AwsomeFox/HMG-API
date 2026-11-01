#!/system/bin/sh
# HMG-API example service. hmgapid runs it (as "user", here system) once Android is
# up, restarts it if it stops, and stops it when the mod is turned off. Its output
# goes to /data/hmgapi/logs/hello-service/hello.log (hmgapid service-log).
#   $HMGAPI_MOD      hello-service
#   $HMGAPI_MOD_DIR  this mod's folder (read-only files from the package)
#   $HMGAPI_DATA     this service's own folder, for its state
# hmgapid knows this process is the mod's service, so it answers for this mod only:
# whoami, setting-get, usb, services, service-log, and the vehicle readings its
# mod.json lists ("vehicle": {"read": [...]}) ... (a Java service or app uses
# hmgapi-client.jar for the same).
echo "started as $(id), mod $HMGAPI_MOD ($(hmgapid whoami))"
n=$(cat "$HMGAPI_DATA/count" 2>/dev/null || echo 0)
while true; do
    # the head unit's shell is mksh with a small toolbox: no sed, tr, awk, head, tail, cut
    # (tools/check-scripts.sh); the shell's own ${var#x} and ${var%x} pick a value out
    s=$(hmgapid setting-get)   # {"every": 60}
    case $s in
        *'"every"'*) every=${s#*'"every"'}; every=${every#*:}; every=${every%%[,\}]*}; every=${every# } ;;
        *) every= ;;
    esac
    n=$((n + 1)); echo $n > "$HMGAPI_DATA/count"
    for d in $(hmgapid usb); do
        echo "hello #$n" >> "$d/hello.txt" 2>/dev/null && echo "wrote $d/hello.txt"
    done
    car=$(echo $(hmgapid vehicle gear.park speed battery12v.soc))   # unquoted: one line, "gear.park 1 speed 0 battery12v.soc 87"
    echo "hello #$n (every ${every:-60} s): $car"
    sleep "${every:-60}"
done
