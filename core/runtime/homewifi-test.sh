#!/bin/sh
# Off-car test of home Wi-Fi and mods kept off the car's cellular link (HomeWifi):
# what the engine makes of the owner's switch, mod.json "wifi": {"home": true} and
# overrides.allow_cellular (state/mods-net.txt, service-gids.txt, the keepers' lines,
# the plan), then the network keeper's mods section (net.sh) and the home Wi-Fi keeper
# (wifi.sh) against fake tools that keep their state in files.
# UNIT_SH: the shell to run the keepers with (mksh, the unit's, when it's installed).
set -e
cd "$(dirname "$0")/../.."
case "$UNIT_SH" in *busybox*) UNIT_SH= ;; esac
UNIT_SH=${UNIT_SH:-$(command -v mksh || command -v bash)}
CP=$(mktemp -d)
sh tools/engine-classes.sh "$CP"
T=$(mktemp -d)
cleanup() { [ -f "$T/fake/fw/wpa.sleep" ] && kill "$(cat "$T/fake/fw/wpa.sleep")" 2>/dev/null || true; rm -rf "$T" "$CP"; }
trap cleanup EXIT
R=$T/root
D=$R/data/hmgapi
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
hasnt() { case "$3" in *"$2"*) echo "FAIL  $1: [$2] in [$3]"; fail=1 ;; *) echo "ok    $1" ;; esac; }
eng() { set +e; out=$(HMGAPI_ROOT=$R HMGAPI_HOST=1 HMGAPI_TEST_ANY_CAR=1 HMGAPI_PRODUCT=CVEV.USA.0000.V015 java -cp "$CP" hmgapi.engine.Engine "$@" 2>&1); rc=$?; set -e; }

mkdir -p "$D/mods/dl" "$D/mods/quiet" "$D/mods/cell" "$D/settings" "$D/state"
echo '{"id": "dl", "version": "1.0.0", "wifi": {"home": true}, "apps": [{"package": "com.example.dl", "apk": "dl.apk"}]}' > "$D/mods/dl/mod.json"
echo '{"id": "quiet", "version": "1.0.0", "apps": [{"package": "com.example.quiet", "apk": "q.apk"}]}' > "$D/mods/quiet/mod.json"
echo '{"id": "cell", "version": "1.0.0", "wifi": {"home": true}}' > "$D/mods/cell/mod.json"
: > "$D/mods/dl/dl.apk"; : > "$D/mods/quiet/q.apk"

# ---- the engine: home Wi-Fi off (the default)
eng publish-state
check "publish runs" 0 $rc
check "every mod kept off cellular" "block cell
block dl
block quiet" "$(cat "$D/state/mods-net.txt")"
check "their services' groups" "cell 2950
dl 2950
quiet 2950" "$(cat "$D/state/service-gids.txt")"
has "a mod that asks, home Wi-Fi off: told" "dl: asks for home Wi-Fi, which is off" "$out"
has "the network keeper runs for it" "hmgapi net " "$(cat "$D/state/services.txt")"
hasnt "no home Wi-Fi keeper while it's off" "hmgapi wifi " "$(cat "$D/state/services.txt")"

# ---- the owner's switch on (the app's file), one mod allowed cellular
mkdir -p "$D/settings/allow"; : > "$D/settings/allow/home_wifi"
echo '{"overrides": {"allow_cellular": ["cell"]}}' > "$D/settings/hmgapi.json"
eng publish-state
check "home for the mod that asked, block for the one that didn't, allow for the override" "allow cell
home dl
block quiet
homewifi on" "$(cat "$D/state/mods-net.txt")"
check "groups: none for the allowed one, home and plain" "cell 0
dl 2951
quiet 2950" "$(cat "$D/state/service-gids.txt")"
has "allowing cellular warns" "WARNING: cell: may use the car's cellular link (overrides.allow_cellular): at your own risk" "$out"
has "the home Wi-Fi keeper runs" "hmgapi wifi root android yes script /data/hmgapi/state/wifi.sh" "$(cat "$D/state/services.txt")"
check "its script written" yes "$([ -s "$D/state/wifi.sh" ] && echo yes || echo no)"
eng plan
has "the plan: home Wi-Fi on" "home Wi-Fi  on" "$out"
has "the plan: home Wi-Fi only for the mod that asked" "network  home Wi-Fi only (no cellular) [dl]" "$out"
has "the plan: the allowed one" "may use cellular (overrides.allow_cellular, at your own risk) [cell]" "$out"
hasnt "the plan: nothing in /system for it (no stock client feature, no restart)" "android.hardware.wifi" "$out"
has "the vehicle service runs for it (parked, power)" "hmgapi vehicle " "$(cat "$D/state/services.txt")"
echo '{"id": "bad", "version": "1.0.0", "wifi": {"home": "yes"}}' > "$D/mods/cell/mod.json"
eng plan
has "a bad \"wifi\" ask refused" "\"home\": true}" "$out"
echo '{"id": "cell", "version": "1.0.0", "wifi": {"home": true}}' > "$D/mods/cell/mod.json"

# ---- the network keeper's mods section, against fake tools
F=$T/fake
mkdir -p "$F/bin" "$F/fw"
cat > "$F/bin/iptables" <<'S'
#!/bin/bash
# a fake iptables: one file per chain ($FW/<v>/<table>/<chain>), rules as given
v=4; [ "${0##*/}" = ip6tables ] && v=6
t=filter; a=()
while [ $# -gt 0 ]; do if [ "$1" = -t ]; then t=$2; shift 2; else a+=("$1"); shift; fi; done
set -- "${a[@]}"
[ $v = 6 ] && [ $t = nat ] && { echo "ip6tables: can't initialize table nat" >&2; exit 3; }
d=$FW/$v/$t; mkdir -p "$d"
stock() { case $1 in INPUT|OUTPUT|FORWARD|PREROUTING|POSTROUTING) return 0 ;; esac; return 1; }
op=$1 c=$2; shift 2
f=$d/$c
if ! stock "$c" && [ "$op" != -N ] && [ ! -f "$f" ]; then echo "iptables: No chain/target/match by that name." >&2; exit 1; fi
case $op in
    -S) if stock "$c"; then echo "-P $c ACCEPT"; else echo "-N $c"; fi
        [ -f "$f" ] && while IFS= read -r l; do echo "-A $c $l"; done < "$f"; exit 0 ;;
    -N) [ -f "$f" ] && { echo "iptables: Chain already exists." >&2; exit 1; }; : > "$f" ;;
    -F) : > "$f" ;;
    -X) grep -qx -- "-j $c" "$d"/* 2>/dev/null && { echo "iptables: Too many links." >&2; exit 1; }; rm -f "$f" ;;
    -C) grep -qx -- "$*" "$f" 2>/dev/null ;;
    -D) grep -qx -- "$*" "$f" 2>/dev/null || exit 1
        awk -v r="$*" '!done && $0 == r { done = 1; next } { print }' "$f" > "$f.new"; mv "$f.new" "$f" ;;
    -I) shift; { echo "$*"; cat "$f" 2>/dev/null; } > "$f.new"; mv "$f.new" "$f" ;;
    -A) echo "$*" >> "$f" ;;
esac
S
cp "$F/bin/iptables" "$F/bin/ip6tables"
cat > "$F/bin/ip" <<'S'
#!/bin/bash
# a fake ip: routes as lines in ip4.route ("<route> table <t>"; main has no "table"), rules as "<prio>:\t<rest>"
v=4; case $1 in -4) shift ;; -6) v=6; shift ;; esac
o=$1 c=$2; shift 2
f=$FW/ip$v.$o; touch "$f"
case $o.$c in
    route.show) if [ "$1" = dev ]; then grep -- " dev $2 .*table main\$" "$f" | sed 's/ table main$//' || true; else grep -- " table ${3:-main}\$" "$f" || true; fi ;;
    route.replace) n=$#; if [ "${@:$((n-1)):1}" = table ]; then tb=${!n}; set -- "${@:1:$((n-2))}"; else tb=main; fi
        grep -v -- "^$1 .*table $tb\$" "$f" > "$f.new"; mv "$f.new" "$f"; echo "$* table $tb" >> "$f" ;;
    route.del) grep -q -- "^$1 .*dev $3 .*table main\$\|^$1 dev $3 .*table main\$" "$f" || exit 2
        grep -v -m1 -- "^$1 .*dev $3" "$f" > "$f.new"; mv "$f.new" "$f" ;;
    route.flush) grep -v -- " table $2\$" "$f" > "$f.new"; mv "$f.new" "$f" ;;
    route.get) [ "$2" = oif ] && grep -q "^10\.1\." <<< "$1" && echo "$1 dev $3" ;;
    addr.flush) : ;;
    rule.show) echo "0:	from all lookup local"; cat "$f" ;;
    rule.add) p=$2; shift 2; printf '%s:\t%s\n' "$p" "$*" >> "$f" ;;
    rule.del) grep -q "^$2:" "$f" || exit 2; grep -v "^$2:" "$f" > "$f.new"; mv "$f.new" "$f" ;;
esac
S
cat > "$F/bin/getprop" <<'S'
#!/bin/sh
cat "$FW/props/$1" 2>/dev/null
S
cat > "$F/bin/setprop" <<'S'
#!/bin/sh
mkdir -p "$FW/props"; printf '%s' "$2" > "$FW/props/$1"
S
printf '#!/bin/sh\necho "$*" >> "$FW/ndc.log"\n' > "$F/bin/ndc"
chmod +x "$F/bin/"*
export FW=$F/fw
keeper() { HMGAPI_PACKAGES=$F/packages.list PATH=$F/bin:$PATH $UNIT_SH "$D/state/net.sh" "$@" 2>&1; }
ipt() { PATH=$F/bin:$PATH iptables "$@"; }
printf 'com.example.dl 10071 0 /data/data/com.example.dl default 3003\ncom.example.quiet 10072 0 /data/data/com.example.quiet default 3003\n' > "$F/packages.list"
printf 'com.example.dl dl\ncom.example.quiet quiet\n' > "$D/state/apps.txt"

log=$(keeper once)
has "mods kept off cellular" "mods kept off cellular: 2 app(s) and every mod service, home Wi-Fi routing in" "$log"
check "the cellular chain first in OUTPUT" "-A OUTPUT -j HMGAPI_CELL" "$(ipt -S OUTPUT | grep -m1 '^-A')"
check "its rules: both services' groups and both apps' uids" "-o rmnet+ -m owner --gid-owner 2950 -j REJECT
-o rmnet+ -m owner --gid-owner 2951 -j REJECT
-o rmnet+ -m owner --uid-owner 10071 -j REJECT
-o rmnet+ -m owner --uid-owner 10072 -j REJECT" "$(cat "$F/fw/4/filter/HMGAPI_CELL")"
check "IPv6 too" 4 "$(grep -c REJECT "$F/fw/6/filter/HMGAPI_CELL")"
check "only the mod that asked marked for home Wi-Fi (and the home group)" "-m owner --gid-owner 2951 -j MARK --set-mark 0x2951
-m owner --uid-owner 10071 -j MARK --set-mark 0x2951" "$(cat "$F/fw/4/mangle/HMGAPI_HOME")"
check "masqueraded on wlan1" "-o wlan1 -m mark --mark 0x2951 -j MASQUERADE" "$(cat "$F/fw/4/nat/HMGAPI_HOMEOUT")"
has "marked traffic to table 95" "900:	fwmark 0x2951 table 95" "$(cat "$F/fw/ip4.rule")"
check "both mods' apps look names up through wlan1, never cellular DNS" "resolver setifaceforuidrange wlan1 10071 10071
resolver setifaceforuidrange wlan1 10072 10072" "$(cat "$F/fw/ndc.log")"
check "all there: a check is quiet" "" "$(keeper once)"
ipt -I OUTPUT 1 -o rmnet+ -j ACCEPT   # the stock firewall puts an accept first
has "moved: put back first" "mods' cellular block put back" "$(keeper once)"
check "... first again" "-A OUTPUT -j HMGAPI_CELL" "$(ipt -S OUTPUT | grep -m1 '^-A')"
printf 'com.example.dl 10091 0 /data/data/com.example.dl default 3003\ncom.example.quiet 10072 0 /data/data/com.example.quiet default 3003\n' > "$F/packages.list"
keeper once >/dev/null
has "a reinstalled app's new uid" "--uid-owner 10091 -j REJECT" "$(cat "$F/fw/4/filter/HMGAPI_CELL")"
hasnt "... the old one gone" "10071" "$(cat "$F/fw/4/filter/HMGAPI_CELL")"
rm "$D/settings/allow/home_wifi"
eng publish-state
keeper once >/dev/null
check "home Wi-Fi off: no marks" no "$([ -f "$F/fw/4/mangle/HMGAPI_HOME" ] && echo yes || echo no)"
hasnt "... no routing rule" "900:" "$(cat "$F/fw/ip4.rule")"
check "... still off cellular" 4 "$(grep -c REJECT "$F/fw/4/filter/HMGAPI_CELL")"
keeper down >/dev/null
hasnt "down: the cellular chain gone" "HMGAPI_CELL" "$(ipt -S OUTPUT)"
: > "$D/settings/allow/home_wifi"
eng publish-state

# ---- the home Wi-Fi keeper, against fake tools
X=$T/unit
mkdir -p "$X/sys/class/net/wlan0" "$X/data/misc/wifi/hostapd" "$X/data/misc/dhcp" "$X/data/hmgapi/public" "$X/proc"
echo "1000.00 2000.00" > "$X/proc/uptime"
mkdir -p "$X/proc/sys/kernel/random"; echo boot-1 > "$X/proc/sys/kernel/random/boot_id"
cat > "$F/bin/hmgapid" <<'S'
#!/bin/bash
case $1 in
    wifi-if) [ "$2" = add ] && { mkdir -p "$FW/wlan1"; echo "add $3" >> "$FW/ifs.log"; }; exit 0 ;;
    vehicle) v=$(cat "$FW/veh.$2" 2>/dev/null); [ -n "$v" ] && echo "$2 $v"; exit 0 ;;
    wpa) sock=$2 c=$3
        case $sock in
            */hostapd/*)   # the hotspot: $FW/phones, one "<mac> <flags>" line each
                case $c in
                    STA-FIRST) l=$(sed -n 1p "$FW/phones" 2>/dev/null) ;;
                    "STA-NEXT "*) m=${c#STA-NEXT }; l=$(grep -A1 "^$m " "$FW/phones" 2>/dev/null | sed -n 2p) ;;
                esac
                [ -n "$l" ] && printf '%s\nflags=%s\n' "${l%% *}" "${l#* }"
                exit 0 ;;
        esac
        echo "$c" >> "$FW/wpa.log"
        case $c in
            STATUS) cat "$FW/wpa.status" ;;
            SIGNAL_POLL) echo "RSSI=-61" ;;
            PING) echo PONG ;;
            LIST_NETWORKS) printf 'network id / ssid / bssid / flags\n0\tCascadia\tany\t\n1\tWillows\tany\t[CURRENT]\n' ;;
            SCAN_RESULTS) printf 'bssid / frequency / signal level / flags / ssid\naa:aa\t2462\t-79\t[WPA2]\tWillows\nbb:bb\t2437\t-61\t[WPA2]\tWillows\ncc:cc\t2437\t-60\t[WPA2]\tOther\n' ;;
            *) echo OK ;;
        esac ;;
esac
S
cat > "$F/bin/wpa_supplicant" <<'S'
#!/bin/bash
while [ $# -gt 0 ]; do case $1 in -P) pf=$2; shift ;; -c) cf=$2; shift ;; esac; shift; done
sleep 300 & echo $! > "$pf"; echo $! > "$FW/wpa.sleep"; cp "$cf" "$FW/wpa.conf"
S
cat > "$F/bin/dhcpcd" <<'S'
#!/bin/bash
echo "$*" >> "$FW/dhcpcd.log"
case "$*" in -x*) exit 0 ;; esac
mkdir -p "$FW/props"
printf '%s' "$(cat "$FW/lease.ip")" > "$FW/props/dhcp.wlan1.ipaddress"; printf 10.1.0.1 > "$FW/props/dhcp.wlan1.gateway"
printf 1.1.1.1 > "$FW/props/dhcp.wlan1.dns1"; printf ok > "$FW/props/dhcp.wlan1.result"
ip route replace default via 10.1.0.1 dev wlan1 metric 1000
ip route replace 10.1.0.0/16 dev wlan1 proto kernel
S
printf '#!/bin/sh\necho "app_process $*" >> "$FW/apps.log"; echo "projection AP restarted"\n' > "$F/bin/app_process"
printf '#!/bin/sh\n:\n' > "$F/bin/ifconfig"
printf '#!/bin/sh\n:\n' > "$F/bin/chown"
chmod +x "$F/bin/"*
# the keeper's loop replaced by N keeps (its state carries across them)
sed -e 's/^while :; do$/for _k in $(seq 1 ${KEEPS:-1}); do keep; done; exit 0/' "$D/state/wifi.sh" > "$D/state/wifi.sh.n"
wk() { HMGAPI_TEST_ROOT=$X KEEPS=${1:-1} PATH=$F/bin:$PATH $UNIT_SH "$D/state/wifi.sh.n" 2>&1; }
verb() { HMGAPI_TEST_ROOT=$X PATH=$F/bin:$PATH $UNIT_SH "$D/state/wifi.sh" "$@" 2>&1; }
pub() { cat "$X/data/hmgapi/public/wifi.json" 2>/dev/null; }
state() { pub | sed 's/.*"state": "\([a-z]*\)".*/\1/'; }
NET=$X/data/hmgapi/wifi/networks.conf

echo "wpa_state=SCANNING" > "$F/fw/wpa.status"
wk >/dev/null
check "no saved networks, none in the car's settings: nothing to join" nonetworks "$(state)"
check "... its client runs anyway (for the app's scans)" "add wlan1" "$(cat "$F/fw/ifs.log")"
rm -rf "$X/data/hmgapi/wifi"
cat > "$X/data/misc/wifi/wpa_supplicant.conf" <<'C'
ctrl_interface=wlan0
update_config=1
p2p_disabled=1

network={
	ssid="Willows"
	psk="secret-not-shown"
	key_mgmt=WPA-PSK
}
C
log=$(wk)
has "the car's own saved networks imported once" "1 network(s) from the car's own Wi-Fi settings" "$log"
has "... into HMG-API's own list" 'ssid="Willows"' "$(cat "$NET")"
check "... owner only" "-rw-------" "$(ls -l "$NET" | cut -c1-10)"
has "its client's config: the networks" 'ssid="Willows"' "$(cat "$F/fw/wpa.conf")"
hasnt "... not the stock global settings" "ctrl_interface=wlan0" "$(cat "$F/fw/wpa.conf")"
check "searching" searching "$(state)"
has "it asks for a scan (this build's supplicant doesn't scan by itself)" "SCAN" "$(cat "$F/fw/wpa.log")"
hasnt "the key never in the log" "secret" "$log"

# the app's verbs
check "saved: the names" "Willows" "$(verb saved)"
out=$(HMGAPI_WIFI_PSK=short verb join "Cascadia" || true)
check "join: a key under 8 characters refused" "a Wi-Fi key is 8 to 63 characters" "$out"
out=$(HMGAPI_WIFI_PSK='has"quote' verb join "Cascadia" || true)
has "join: a key that could break the config refused" "can't be used" "$out"
out=$(HMGAPI_WIFI_PSK= verb join 'Bad"Name' || true)
has "join: a name that could break the config refused" "can't be used" "$out"
check "join: saved" "saved: Cascadia" "$(HMGAPI_WIFI_PSK=cascadia-key verb join Cascadia)"
check "... both now" "Willows
Cascadia" "$(verb saved)"
has "... its key in the list, not in anything printed" 'psk="cascadia-key"' "$(cat "$NET")"
HMGAPI_WIFI_PSK=new-willows-key verb join Willows >/dev/null
check "join again: the network replaced, not doubled" 1 "$(grep -c 'ssid="Willows"' "$NET")"
has "... with its new key" 'psk="new-willows-key"' "$(cat "$NET")"
check "an open network" "saved: Cafe" "$(HMGAPI_WIFI_PSK= verb join Cafe)"
has "... no key" "key_mgmt=NONE" "$(cat "$NET")"
check "forget" "forgotten: Cafe" "$(verb forget Cafe)"
check "... gone, the others kept" "Cascadia
Willows" "$(verb saved | sort)"
has "scan: what the client sees" "2437	-61	[WPA2]	Willows" "$(verb scan)"
log=$(wk)
has "the keeper takes the changed list" "saved networks changed" "$log"
has "... its client reconfigured" "RECONFIGURE" "$(cat "$F/fw/wpa.log")"

printf 'wpa_state=COMPLETED\nfreq=2437\n' > "$F/fw/wpa.status"
echo 10.1.174.175 > "$F/fw/lease.ip"
log=$(wk)
has "joined: an address" "home Wi-Fi up" "$log"
check "up, with signal and channel, no network name" '{"state": "up", "rssi": -61, "freq": 2437}' "$(pub)"
has "table 95: the default route" "default via 10.1.0.1 dev wlan1 table 95" "$(cat "$F/fw/ip4.route")"
has "table 95: the subnet" "10.1.0.0/16 dev wlan1 table 95" "$(cat "$F/fw/ip4.route")"
hasnt "nothing in the main table's default" "default via 10.1.0.1 dev wlan1 metric 1000 table main" "$(cat "$F/fw/ip4.route")"
has "netd: wlan1's DNS servers (no search domains)" "resolver setifdns wlan1  1.1.1.1" "$(cat "$F/fw/ndc.log")"
has "a DNS server that isn't the gateway: through the gateway" "1.1.1.1/32 via 10.1.0.1 dev wlan1 metric 1000" "$(cat "$F/fw/ip4.route")"
check "mods' services' DNS to the home router's" "-m owner --gid-owner 2951 -p udp --dport 53 -j DNAT --to-destination 1.1.1.1
-m owner --gid-owner 2951 -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1" "$(cat "$F/fw/4/nat/HMGAPI_HOMEDNS")"
FW=$F/fw PATH=$F/bin:$PATH ip route replace default via 10.1.0.1 dev wlan1 metric 1000   # a renewal puts it back
wk >/dev/null
check "a renewal's main default taken out again" 0 "$(grep "^default .*dev wlan1" "$F/fw/ip4.route" | grep -c "table main" || true)"

# the hotspot comes first
echo "aa:bb:cc:dd:ee:01 [AUTH][ASSOC][AUTHORIZED]" > "$F/fw/phones"
: > "$F/fw/wpa.log"
log=$(wk)
has "a phone on the hotspot: home Wi-Fi gives way" "a phone on the hotspot: home Wi-Fi gives way" "$log"
has "... disconnected" "DISCONNECT" "$(cat "$F/fw/wpa.log")"
hasnt "... the client kept running (not taken down)" "TERMINATE" "$(cat "$F/fw/wpa.log")"
check "... wlan1 kept" "" "$(grep del "$F/fw/ifs.log" || true)"
check "... its lease gone" "" "$(grep 'table 95' "$F/fw/ip4.route" || true)"
check "says so" yield "$(state)"
echo "wpa_state=DISCONNECTED" > "$F/fw/wpa.status"
: > "$F/fw/phones"
echo "1060.00 2000.00" > "$X/proc/uptime"
: > "$F/fw/wpa.log"
wk >/dev/null
check "the phone gone a minute, not parked: still waiting" yield "$(state)"
hasnt "... not reconnected yet" "RECONNECT" "$(cat "$F/fw/wpa.log")"
echo 1 > "$F/fw/veh.parked"
: > "$F/fw/apps.log"
log=$(wk)
has "parked (the car events' rule): back at once" "no phone on the hotspot: home Wi-Fi again" "$log"
has "... reconnected" "RECONNECT" "$(cat "$F/fw/wpa.log")"
has "parked: the hotspot moves to the home network's channel, under a name no phone knows" "parked: the hotspot on channel 6 under a name no phone knows" "$log"
check "... the channel for the startAccessPoint hook" 6 "$(cat "$F/fw/props/hmgapi.wifi.parked")"
has "... the hotspot restarted through Android's Wi-Fi service" "hmgapi.engine.WifiAp restart" "$(cat "$F/fw/apps.log")"
: > "$F/fw/apps.log"
check "still parked: left as it is" "" "$(wk | grep -i hotspot || true)"
check "... no second restart" "" "$(cat "$F/fw/apps.log")"
echo on > "$F/fw/veh.power"
has "the car on: the hotspot back as it was" "the car is on: the hotspot back as it was" "$(wk)"
check "... the channel cleared" "" "$(cat "$F/fw/props/hmgapi.wifi.parked")"
rm "$F/fw/veh.power"
D=$D HMGAPI_TEST_ROOT=$X PATH=$F/bin:$PATH $UNIT_SH -c ". /dev/stdin" <<'C' > "$F/fw/ch.txt"
eval "$(sed -n '/^channel_of() {/,/^}/p' "$D/state/wifi.sh")"
for f in 2437 2462 5180 5240 5260 5500 5745 5825; do echo "$f:$(channel_of $f)"; done
C
check "channels the hotspot may take: 2.4 GHz, 5 GHz 36-48 and 149-165, not the radar ones" "2437:6 2462:11 5180:36 5240:48 5260: 5500: 5745:149 5825:165" "$(cat "$F/fw/ch.txt" | xargs)"
rm "$F/fw/veh.parked"
echo "1200.00 2000.00" > "$X/proc/uptime"
echo "aa:bb:cc:dd:ee:01 [AUTH][ASSOC][AUTHORIZED]" > "$F/fw/phones"; wk >/dev/null
: > "$F/fw/phones"; echo "1250.00 2000.00" > "$X/proc/uptime"
check "not parked, the phone gone under 2 minutes: waits" yield "$(wk >/dev/null; state)"
echo "1330.00 2000.00" > "$X/proc/uptime"
has "not parked: back 2 minutes after the last phone left" "home Wi-Fi again" "$(wk)"

echo "aa:bb:cc:dd:ee:01 [AUTH][ASSOC][AUTHORIZED]" > "$F/fw/phones"; wk >/dev/null; : > "$F/fw/phones"
echo "30.00 60.00" > "$X/proc/uptime"; echo boot-2 > "$X/proc/sys/kernel/random/boot_id"   # a restart: the clock starts again
check "a last phone from before a restart doesn't hold home Wi-Fi back" "" "$(wk | grep 'gives way' || true)$(state | grep yield || true)"
echo "1330.00 2000.00" > "$X/proc/uptime"

# a phone that can't finish joining
echo "aa:bb:cc:dd:ee:02 [AUTH][ASSOC]" > "$F/fw/phones"
: > "$F/fw/apps.log"
wk 2 >/dev/null
check "associated, not joined, 20 s: nothing yet" "" "$(cat "$F/fw/apps.log")"
log=$(wk 3)
has "30 s: the hotspot restarted" "a phone can't finish joining the hotspot: the hotspot restarted" "$log"
has "... through Android's Wi-Fi service" "hmgapi.engine.WifiAp restart" "$(cat "$F/fw/apps.log")"
check "... once in 5 minutes" 1 "$(grep -c WifiAp "$F/fw/apps.log")"
: > "$F/fw/phones"

# switching off
printf 'wpa_state=COMPLETED\nfreq=2437\n' > "$F/fw/wpa.status"
echo "2000.00 2000.00" > "$X/proc/uptime"
echo 1 > "$F/fw/veh.parked"; wk >/dev/null; rm "$F/fw/veh.parked"
check "up again before the off" up "$(state)"
echo preparing_off > "$F/fw/veh.power"
has "the car switching off: let go" "the car is switching off: home Wi-Fi gives way" "$(wk)"
check "... says so" off "$(state)"
rm "$F/fw/veh.power"

# the network clash
echo 1 > "$F/fw/veh.parked"
printf 'wpa_state=COMPLETED\nfreq=2437\nid=1\n' > "$F/fw/wpa.status"
echo 192.168.0.40 > "$F/fw/lease.ip"
log=$(wk)
has "a network on the hotspot's range: not used" "192.168.0.x, the hotspot's own range" "$log"
check "... status says so" clash "$(state)"
has "... that network off, the others still usable" "DISABLE_NETWORK 1" "$(cat "$F/fw/wpa.log")"
echo "wpa_state=SCANNING" > "$F/fw/wpa.status"
wk >/dev/null
check "... still says so while searching" clash "$(state)"
: > "$F/fw/wpa.log"
wk 3 >/dev/null
has "can't join three times: the strongest access point's channel pinned" "SET_NETWORK 1 freq_list 2437" "$(cat "$F/fw/wpa.log")"

echo running > "$F/fw/props/init.svc.wpa_supplicant"
has "the car's own client on: ours stops" "home Wi-Fi waits" "$(wk)"
check "... and says why" stock "$(state)"
check "... wlan1 never taken down" "" "$(grep del "$F/fw/ifs.log" || true)"

rm -f "$F/fw/props/init.svc.wpa_supplicant"
echo 9 > "$F/fw/props/hmgapi.wifi.parked"; : > "$F/fw/apps.log"
verb down >/dev/null
check "stopping while parked: the hotspot never left under the parked name" "" "$(cat "$F/fw/props/hmgapi.wifi.parked")"
has "... restarted as it was" "WifiAp restart" "$(cat "$F/fw/apps.log")"

[ $fail = 0 ] && echo "all home Wi-Fi checks passed"
exit $fail
