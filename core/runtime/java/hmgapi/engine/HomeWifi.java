package hmgapi.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home Wi-Fi, and mods kept off the car's cellular link.
 *
 * Cellular: no mod's traffic goes over the car's cellular link (rmnet), DNS included. Its
 * apps (by uid) and its services (by group: hmgapid runs them with SERVICE_GID or
 * HOME_GID as their effective group, state/service-gids.txt) are refused there by the
 * network keeper (net.sh, chain HMGAPI_CELL), and their apps' DNS goes through wlan1 in
 * netd, which has no servers unless home Wi-Fi is up. The owner can let one mod use
 * cellular, at their own risk (hmgapi.json overrides.allow_cellular).
 *
 * Home Wi-Fi (the HMG-API app's switch, settings/allow/home_wifi, or hmgapi.json "home_wifi": true;
 * off by default): the head unit joins HMG-API's saved networks (/data/hmgapi/wifi/networks.conf,
 * root only; the HMG-API app adds and forgets them through hmgapid wifi, and the car's own Wi-Fi
 * settings' networks are imported once) on a second interface, wlan1, next to the projection
 * hotspot on wlan0. The BCM4359's firmware here can't keep both on two channels for long (a US
 * EV6, 2026-11-18: about ten minutes in, it moved the hotspot's channel twice and the phone could
 * no longer finish joining), so the hotspot comes first: while a phone is on it (hostapd's list),
 * home Wi-Fi is disconnected, wlan1 and its client kept; it's back when the car is parked (car
 * events, #305) or 2 minutes after the last phone left. A phone that can't finish joining for
 * 30 s gets the hotspot restarted (WifiAp). Only mods that ask ("wifi": {"home": true}) use it:
 * their traffic is marked (MARK) and routed through table TABLE; the car's own stays on
 * cellular. The keeper (state/wifi.sh, service "hmgapi wifi") runs wlan1's wpa_supplicant and
 * DHCP, the table, netd's DNS servers for wlan1, and the DNS redirect for mods' services.
 */
final class HomeWifi {
    private HomeWifi() {}

    static final int SERVICE_GID = 2950, HOME_GID = 2951, TABLE = 95, PRIORITY = 900;
    static final String MARK = "0x2951", IFACE = "wlan1";
    static final String SCRIPT_PATH = "/data/hmgapi/state/wifi.sh";

    /** the owner's switch: the HMG-API app (settings/allow/home_wifi), or hmgapi.json "home_wifi" */
    static boolean on(Engine e) {
        return new File(e.data, "settings/allow/home_wifi").isFile() || (e.hmgapi != null && Boolean.TRUE.equals(e.hmgapi.get("home_wifi")));
    }

    @SuppressWarnings("unchecked")
    static List<Object> allowed(Map<String, Object> hmgapi) {
        Object ov = hmgapi == null ? null : hmgapi.get("overrides");
        Object l = ov instanceof Map ? ((Map<String, Object>) ov).get("allow_cellular") : null;
        return l instanceof List ? (List<Object>) l : new ArrayList<Object>();
    }

    /** mod.json "wifi": {"home": true} */
    @SuppressWarnings("unchecked")
    static boolean asks(Map<String, Object> mj) {
        Object w = mj == null ? null : mj.get("wifi");
        return w instanceof Map && Boolean.TRUE.equals(((Map<String, Object>) w).get("home"));
    }

    /** what each enabled mod gets: "home", "block" or "allow" */
    static Map<String, String> modes(Engine e, Map<String, Map<String, Object>> json, Set<String> on) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        List<Object> allow = allowed(e.hmgapi);
        boolean home = on(e);
        for (String id : on) {
            if (allow.contains(id)) out.put(id, "allow");
            else out.put(id, home && asks(json.get(id)) ? "home" : "block");
        }
        return out;
    }

    /**
     * state/mods-net.txt (the network keeper's "mods" lines) and state/service-gids.txt
     * (hmgapid: each mod's services' group); true when the network keeper has to run for it.
     */
    static boolean publish(Engine e, Map<String, Map<String, Object>> json, Set<String> on) throws Exception {
        Map<String, String> modes = modes(e, json, on);
        StringBuilder net = new StringBuilder(), gids = new StringBuilder();
        boolean home = on(e);
        for (Map.Entry<String, String> m : modes.entrySet()) {
            String id = m.getKey(), mode = m.getValue();
            net.append(mode).append(' ').append(id).append('\n');
            gids.append(id).append(' ').append("allow".equals(mode) ? 0 : "home".equals(mode) ? HOME_GID : SERVICE_GID).append('\n');
            if ("allow".equals(mode)) {
                e.out.add("WARNING: " + id + ": may use the car's cellular link (overrides.allow_cellular): at your own risk, as its"
                        + " traffic and DNS lookups then go over the car's mobile data");
            } else if (!home && asks(json.get(id))) {
                e.out.add("note     " + id + ": asks for home Wi-Fi, which is off (HMG-API settings): it has no internet until it's on");
            }
        }
        if (home) net.append("homewifi on\n");
        ModRuntime.writeIfChanged(new File(e.stateDir, "mods-net.txt"), net.toString());
        ModRuntime.writeIfChanged(new File(e.stateDir, "service-gids.txt"), gids.toString());
        return net.length() > 0 || new File(e.stateDir, "mods-net.applied").isFile();
    }

    /** the home Wi-Fi keeper's services.txt line, or null when it's off (its script written) */
    static String service(Engine e) throws Exception {
        File script = new File(e.stateDir, "wifi.sh");
        if (!on(e)) return null;
        if (!(script.isFile() && Engine.read(script).equals(SCRIPT))) {
            File tmp = new File(e.stateDir, ".wifi.sh.tmp");
            Engine.write(tmp, SCRIPT);
            if (!tmp.renameTo(script)) throw new java.io.IOException("can't write wifi.sh");
        }
        return "hmgapi wifi root android yes script " + SCRIPT_PATH + " - " + Integer.toHexString(SCRIPT.hashCode());
    }

    /** the plan's lines */
    static List<String> describe(Engine e, Map<String, Map<String, Object>> json, Set<String> on) {
        List<String> out = new ArrayList<String>();
        boolean home = on(e);
        out.add("home Wi-Fi  " + (home ? "on: HMG-API's saved networks, on wlan1 next to the hotspot, giving way while a phone is on it" : "off"));
        for (Map.Entry<String, String> m : modes(e, json, on).entrySet()) {
            String mode = m.getValue();
            out.add("network  " + ("home".equals(mode) ? "home Wi-Fi only (no cellular)" : "allow".equals(mode)
                    ? "may use cellular (overrides.allow_cellular, at your own risk)" : "no cellular" + (asks(json.get(m.getKey())) ? " (home Wi-Fi is off)" : ""))
                    + " [" + m.getKey() + "]");
        }
        return out;
    }

    /**
     * HMG-API's home Wi-Fi keeper, run by hmgapid as root (service "hmgapi wifi") once
     * Android is up; mksh and the unit's toolbox only. hmgapid wifi-if makes wlan1, hmgapid
     * wpa talks to its wpa_supplicant and to the hotspot's hostapd, hmgapid vehicle reads
     * "parked" and "power" (car events, #305), WifiAp restarts the hotspot.
     */
    static final String SCRIPT = Network.lines(
        "# HMG-API's home Wi-Fi keeper (written by the engine: hmgapi.engine.HomeWifi). hmgapid runs it",
        "# as root (service \"hmgapi wifi\"). wlan1 joins HMG-API's saved networks next to the projection",
        "# hotspot on wlan0, and gives way to it: while a phone is on the hotspot, home Wi-Fi stays off the",
        "# air (the radio can't share two channels for long). Only the mods that asked go through it.",
        "#   sh wifi.sh                 keep (the service)",
        "#   sh wifi.sh down            everything out (the uninstaller)",
        "#   sh wifi.sh saved | scan | join <network> | forget <network>   for HMG-API's app (hmgapid wifi);",
        "#                              join's key comes in HMGAPI_WIFI_PSK, never on the command line",
        "set -f",
        "X=${HMGAPI_TEST_ROOT:-}   # off-car tests only",
        "W=$X/data/hmgapi/wifi C=$X/data/hmgapi/wifi/ctrl I=" + IFACE + " T=" + TABLE + " AP=wlan0",
        "NET=$W/networks.conf",
        "STOCK=$X/data/misc/wifi/wpa_supplicant.conf",
        "HAPD=$X/data/misc/wifi/hostapd/wlan0",
        "PUB=$X/data/hmgapi/public/wifi.json",
        "JAR=/system/framework/hmgapi-hook.jar",
        "nl='",
        "'",
        "tab='\t'",
        "say() { echo \"$(date '+%m-%d %H:%M:%S') $*\"; }",
        "wpa() { hmgapid wpa $C/$I \"$1\" 2>/dev/null; }",
        "field() { while IFS== read -r k v; do [ \"$k\" = \"$1\" ] && { echo \"$v\"; return; }; done; }",
        "status() { echo \"{\\\"state\\\": \\\"$1\\\", \\\"rssi\\\": ${2:-0}, \\\"freq\\\": ${3:-0}}\" > $PUB.tmp && chmod 644 $PUB.tmp && mv $PUB.tmp $PUB; }",
        "now() { read -r u rest < $X/proc/uptime; echo ${u%.*}; }",
        "boot() { cat $X/proc/sys/kernel/random/boot_id 2>/dev/null; }   # times are since boot: only this boot's count",
        "reading() { hmgapid vehicle \"$1\" 2>/dev/null | while read -r k v; do [ \"$k\" = \"$1\" ] && echo \"$v\"; done; }",
        "",
        "# ---- the saved networks: HMG-API's own (networks.conf, root only), the stock settings' imported once",
        "blocks() {   # the network={...} blocks of a wpa_supplicant config",
        "    in=",
        "    while IFS= read -r l; do",
        "        case $l in network=*) in=1 ;; esac",
        "        [ -n \"$in\" ] && echo \"$l\"",
        "        case $l in \"}\") in= ;; esac",
        "    done < \"$1\"",
        "}",
        "import_stock() {",
        "    [ -f $NET ] && return",
        "    mkdir -p $W",
        "    if [ -f $STOCK ]; then blocks $STOCK > $NET.tmp 2>/dev/null; else : > $NET.tmp; fi",
        "    chmod 600 $NET.tmp; mv $NET.tmp $NET",
        "    n=$(grep -c '^network=' $NET)",
        "    [ \"$n\" -gt 0 ] && say \"$n network(s) from the car's own Wi-Fi settings\"",
        "}",
        "ssid_of() { while IFS= read -r l; do case $l in *ssid=\\\"*) l=${l#*ssid=\\\"}; echo \"${l%\\\"}\"; return ;; esac; done; }",
        "saved() {   # each saved network's name",
        "    b=",
        "    [ -f $NET ] && while IFS= read -r l; do",
        "        case $l in network=*) b= ;; esac",
        "        b=\"$b$l$nl\"",
        "        case $l in \"}\") echo \"$b\" | ssid_of ;; esac",
        "    done < $NET",
        "}",
        "without() {   # networks.conf without the network named $1",
        "    b= keep=1",
        "    while IFS= read -r l; do",
        "        case $l in network=*) b= keep=1 ;; esac",
        "        case $l in *ssid=\\\"*) [ \"$(echo \"$l\" | ssid_of)\" = \"$1\" ] && keep= ;; esac",
        "        b=\"$b$l$nl\"",
        "        case $l in \"}\") [ -n \"$keep\" ] && printf_ \"$b\"; b= ;; esac",
        "    done < $NET",
        "}",
        "printf_() { echo -n \"$1\"; }",
        "safe() {   # a name or key that can't break the config: no quotes, backslashes or line breaks",
        "    case $1 in *\\\"*|*\\\\*|*\"$nl\"*) return 1 ;; esac",
        "    return 0",
        "}",
        "join() {",
        "    s=$1 k=$HMGAPI_WIFI_PSK",
        "    [ -n \"$s\" ] && [ ${#s} -le 32 ] && safe \"$s\" || { echo \"that network name can't be used\"; return 2; }",
        "    safe \"$k\" || { echo \"that key can't be used (quotes, backslashes)\"; return 2; }",
        "    if [ -n \"$k\" ] && [ ${#k} -lt 8 -o ${#k} -gt 63 ]; then echo \"a Wi-Fi key is 8 to 63 characters\"; return 2; fi",
        "    import_stock",
        "    umask 077",
        "    without \"$s\" > $NET.tmp",
        "    { echo 'network={'; echo \"\tssid=\\\"$s\\\"\"",
        "      if [ -n \"$k\" ]; then echo \"\tpsk=\\\"$k\\\"\"; echo '\tkey_mgmt=WPA-PSK'; else echo '\tkey_mgmt=NONE'; fi",
        "      echo '\tscan_ssid=1'; echo '}'; } >> $NET.tmp",
        "    chmod 600 $NET.tmp; mv $NET.tmp $NET",
        "    echo \"saved: $s\"",
        "}",
        "forget() {",
        "    import_stock",
        "    umask 077",
        "    without \"$1\" > $NET.tmp; chmod 600 $NET.tmp; mv $NET.tmp $NET",
        "    echo \"forgotten: $1\"",
        "}",
        "scan() {   # \"<MHz>\\t<dBm>\\t<flags>\\t<name>\", strongest first per name; the keeper's own client does it",
        "    wpa PING >/dev/null || { echo \"home Wi-Fi isn't running\"; return 1; }",
        "    wpa SCAN >/dev/null; sleep 4",
        "    wpa SCAN_RESULTS | while IFS=$tab read -r b f sig fl name; do",
        "        case $f in [0-9]*) [ -n \"$name\" ] && echo \"$f$tab$sig$tab$fl$tab$name\" ;; esac",
        "    done",
        "}",
        "",
        "# ---- the client on wlan1",
        "conf() {",
        "    echo \"ctrl_interface=DIR=$C GROUP=wifi\"",
        "    echo 'update_config=0'",
        "    echo 'p2p_disabled=1'",
        "    cat $NET 2>/dev/null",
        "}",
        "pid_of() { p=$(cat $W/wpa.pid 2>/dev/null) && [ -n \"$p\" ] && [ -d /proc/$p ] && echo $p; }",
        "dhcp_running() { p=$(cat $X/data/misc/dhcp/dhcpcd-$I.pid 2>/dev/null) && [ -n \"$p\" ] && [ -d /proc/$p ]; }",
        "lease_down() {",
        "    dhcpcd -x $I >/dev/null 2>&1",
        "    ip route flush table $T 2>/dev/null",
        "    ip -4 addr flush dev $I 2>/dev/null",
        "    iptables -t nat -F HMGAPI_HOMEDNS 2>/dev/null",
        "    for r in $(cat $W/dnsroutes 2>/dev/null); do ip route del $r dev $I 2>/dev/null; done; rm -f $W/dnsroutes",
        "    for k in ipaddress gateway dns1 dns2 result; do setprop dhcp.$I.$k ''; done   # no stale lease next time",
        "    rm -f $W/lease",
        "}",
        "client_down() { lease_down; wpa TERMINATE >/dev/null; p=$(pid_of) && kill $p 2>/dev/null; rm -f $W/wpa.pid $W/run.conf; }",
        "# stock client mode (the car's own Wi-Fi settings): the stock supplicant owns the radio",
        "stock_client() { case $(getprop init.svc.p2p_supplicant)$(getprop init.svc.wpa_supplicant) in *running*) return 0 ;; esac; return 1; }",
        "dns_nat() {",
        "    iptables -t nat -N HMGAPI_HOMEDNS 2>/dev/null; iptables -t nat -F HMGAPI_HOMEDNS",
        "    iptables -t nat -C OUTPUT -j HMGAPI_HOMEDNS 2>/dev/null || iptables -t nat -I OUTPUT 1 -j HMGAPI_HOMEDNS",
        "    for p in udp tcp; do iptables -t nat -A HMGAPI_HOMEDNS -m owner --gid-owner " + HOME_GID + " -p $p --dport 53 -j DNAT --to-destination $1; done",
        "}",
        "# a lease: its own table, netd's DNS servers for wlan1, nothing in the main table's default",
        "lease_up() {",
        "    ip=$(getprop dhcp.$I.ipaddress) gw=$(getprop dhcp.$I.gateway) d1=$(getprop dhcp.$I.dns1) d2=$(getprop dhcp.$I.dns2)",
        "    [ -n \"$ip\" ] && [ -n \"$gw\" ] || return 1",
        "    case $ip in 192.168.0.*)   # the hotspot's own range: this network can't be used; the others still can",
        "        id=$(wpa STATUS | field id)",
        "        say \"a saved network gives 192.168.0.x, the hotspot's own range: not used (another network, or change the router's range)\"",
        "        if [ -n \"$id\" ]; then wpa \"DISABLE_NETWORK $id\" >/dev/null; else wpa DISCONNECT >/dev/null; fi",
        "        lease_down; : > $W/clash; status clash; return 2 ;;",
        "    esac",
        "    rm -f $W/clash",
        "    for _ in 1 2 3 4 5 6 7 8; do ip route del default dev $I 2>/dev/null || break; done",
        "    ip route replace default via $gw dev $I table $T",
        "    for r in $(ip route show dev $I 2>/dev/null | while read -r n rest; do case $n in */*) echo $n ;; esac; done); do",
        "        ip route replace $r dev $I table $T",
        "    done",
        "    [ -n \"$d1\" ] || d1=$gw",
        "    ndc resolver setifdns $I '' $d1 $d2 >/dev/null 2>&1",
        "    ndc resolver flushif $I >/dev/null 2>&1",
        "    # netd's lookups for wlan1 leave by it, and the kernel would send them there without the gateway",
        "    # (a server off the subnet: no answer): through the gateway, unless the server is the gateway",
        "    : > $W/dnsroutes",
        "    for d in $d1 $d2; do",
        "        [ \"$d\" = \"$gw\" ] && continue",
        "        ip route replace $d/32 via $gw dev $I metric 1000 && echo $d/32 >> $W/dnsroutes",
        "    done",
        "    dns_nat $d1",
        "    echo \"$ip $gw $d1\" > $W/lease",
        "    say \"home Wi-Fi up: an address, the mods that asked go through it\"",
        "}",
        "# can't join for a while: the strongest access point of a saved network seen gets its channel",
        "# pinned (a mesh on several channels: the client kept trying the weak one)",
        "pin() {",
        "    wpa LIST_NETWORKS > $W/saved || return",
        "    wpa SCAN_RESULTS > $W/seen || return",
        "    best= bs=-200 bid=",
        "    while IFS=$tab read -r id name rest; do",
        "        case $id in [0-9]*) ;; *) continue ;; esac",
        "        while IFS=$tab read -r b f sig fl seen; do",
        "            [ \"$seen\" = \"$name\" ] && [ \"$sig\" -gt $bs ] 2>/dev/null && { bs=$sig; best=$f; bid=$id; }",
        "        done < $W/seen",
        "    done < $W/saved",
        "    rm -f $W/saved $W/seen",
        "    [ -n \"$best\" ] || return",
        "    wpa \"SET_NETWORK $bid freq_list $best\" >/dev/null && wpa \"SELECT_NETWORK $bid\" >/dev/null && wpa \"ENABLE_NETWORK all\" >/dev/null",
        "    say \"pinned to $best MHz, the strongest access point of a saved network\"",
        "}",
        "",
        "# ---- the hotspot comes first",
        "# phones on it: \"<associated> <joined>\" (hostapd's own list; joined: the key handshake finished)",
        "phones() {",
        "    n=0 a=0",
        "    m=$(hmgapid wpa $HAPD STA-FIRST 2>/dev/null)",
        "    while [ -n \"$m\" ] && [ $n -lt 16 ]; do",
        "        mac=${m%%$nl*}",
        "        case $mac in *:*:*:*:*:*) ;; *) break ;; esac",
        "        n=$((n + 1)); case $m in *AUTHORIZED*) a=$((a + 1)) ;; esac",
        "        m=$(hmgapid wpa $HAPD \"STA-NEXT $mac\" 2>/dev/null)",
        "    done",
        "    echo \"$n $a\"",
        "}",
        "yield() {   # off the air for the hotspot: disconnected, wlan1 and the client kept (taking them down drops a session)",
        "    st=$(wpa STATUS | field wpa_state)",
        "    case $st in DISCONNECTED|INACTIVE|INTERFACE_DISABLED|\"\") ;; *)",
        "        lease_down; wpa DISCONNECT >/dev/null; say \"$1: home Wi-Fi gives way\" ;;",
        "    esac",
        "    boot > $W/yielded   # in a file: a restarted keeper still knows (this boot only)",
        "}",
        "ap_restart() {   # through Android's Wi-Fi service; the driver reloads, so wlan1 and its client come back next round",
        "    say \"$1\"",
        "    lease_down",
        "    CLASSPATH=$JAR app_process /system/bin hmgapi.engine.WifiAp restart 2>&1 | while read -r l; do say \"$l\"; done",
        "}",
        "",
        "# ---- parked: the hotspot under a name and key no phone knows, on the home network's channel (the",
        "# startAccessPoint hook reads hmgapi.wifi.parked), so the phones are off it and home Wi-Fi has the",
        "# radio on one channel; as it was again once the car is on",
        "channel_of() {   # MHz -> a channel the hotspot may use (2.4 GHz, or 5 GHz without radar checks), or nothing",
        "    f=$1",
        "    if [ \"$f\" -ge 2412 ] 2>/dev/null && [ \"$f\" -le 2472 ]; then echo $(((f - 2407) / 5)); return; fi",
        "    c=$(((f - 5000) / 5))",
        "    if [ \"$c\" -ge 36 ] 2>/dev/null && [ \"$c\" -le 48 ] || [ \"$c\" -ge 149 ] && [ \"$c\" -le 165 ]; then echo $c; fi",
        "}",
        "home_channel() {   # the strongest saved network the client sees",
        "    wpa SCAN >/dev/null; sleep 4",
        "    saved > $W/names",
        "    best= bs=-200",
        "    wpa SCAN_RESULTS | while IFS=$tab read -r b f sig fl name; do",
        "        case $f in [0-9]*) ;; *) continue ;; esac",
        "        while IFS= read -r s; do [ \"$s\" = \"$name\" ] && echo \"$sig $f\"; done < $W/names",
        "    done > $W/seen",
        "    while read -r sig f; do [ \"$sig\" -gt $bs ] 2>/dev/null && { bs=$sig; best=$f; }; done < $W/seen",
        "    rm -f $W/names $W/seen",
        "    [ -n \"$best\" ] && channel_of $best",
        "}",
        "park() {",
        "    pk=$(getprop hmgapi.wifi.parked) p=$(reading parked) pw=$(reading power)",
        "    if [ -n \"$pk\" ]; then",
        "        if [ \"$pw\" = on ] || [ \"$p\" != 1 ]; then",
        "            setprop hmgapi.wifi.parked ''",
        "            ap_restart \"the car is on: the hotspot back as it was\"",
        "        fi",
        "    elif [ \"$p\" = 1 ] && [ \"$pw\" != on ] && [ -n \"$(saved)\" ]; then",
        "        ch=$(home_channel)",
        "        if [ -n \"$ch\" ]; then",
        "            setprop hmgapi.wifi.parked $ch",
        "            ap_restart \"parked: the hotspot on channel $ch under a name no phone knows (phones off it); home Wi-Fi has the radio\"",
        "        elif [ ! -f $W/parked.none ]; then",
        "            : > $W/parked.none",
        "            say \"parked, but no saved network in range on a channel the hotspot can use: the hotspot stays as it is\"",
        "        fi",
        "    elif [ \"$p\" != 1 ]; then",
        "        rm -f $W/parked.none",
        "    fi",
        "}",
        "",
        "keep() {",
        "    if stock_client; then",
        "        [ -n \"$(pid_of)\" ] && { client_down; say \"the car's own Wi-Fi client is on (its Wi-Fi settings): home Wi-Fi waits\"; }",
        "        status stock; return",
        "    fi",
        "    [ -d $X/sys/class/net/$AP ] || { status nochip; return; }",
        "    case $(reading power) in preparing_off|off)",
        "        [ -f $W/lease ] && { yield \"the car is switching off\"; }",
        "        status off; return ;;",
        "    esac",
        "    import_stock",
        "    mkdir -p $W $C; chown wifi:wifi $W $C 2>/dev/null; chmod 770 $W $C",
        "    c=$(conf)",
        "    if [ \"$c\" != \"$(cat $W/run.conf 2>/dev/null)\" ]; then",
        "        echo \"$c\" > $W/run.conf; chown wifi:wifi $W/run.conf 2>/dev/null; chmod 600 $W/run.conf; rm -f $W/clash",
        "        [ -n \"$(pid_of)\" ] && { wpa RECONFIGURE >/dev/null; say \"saved networks changed\"; [ -f $W/yielded ] && wpa DISCONNECT >/dev/null; }",
        "    fi",
        "    hmgapid wifi-if add $I || { status noiface; return; }",
        "    ifconfig $I up 2>/dev/null",
        "    if [ -z \"$(pid_of)\" ]; then",
        "        wpa_supplicant -B -P $W/wpa.pid -i $I -D nl80211 -c $W/run.conf >/dev/null 2>&1 || { say \"wpa_supplicant didn't start\"; status error; return; }",
        "        fails=0",
        "        [ -f $W/yielded ] && wpa DISCONNECT >/dev/null",
        "    fi",
        "    park",
        "    t=$(now) b=$(boot) last=0",
        "    [ -f $W/lastphone ] && read -r lb last < $W/lastphone && [ \"$lb\" != \"$b\" ] && last=0",
        "    [ -f $W/yielded ] && [ \"$(cat $W/yielded)\" != \"$b\" ] && rm -f $W/yielded   # from an earlier boot",
        "    set -- $(phones); n=${1:-0} joined=${2:-0}",
        "    [ \"$n\" -gt 0 ] && { last=$t; echo \"$b $t\" > $W/lastphone; }",
        "    # a phone that associates and never finishes its handshake (the state the shared radio left the",
        "    # hotspot in): after 30 s, with home Wi-Fi off the air, the hotspot restarts (once in 5 minutes)",
        "    if [ \"$n\" -gt \"$joined\" ]; then stuck=$((stuck + 1)); else stuck=0; fi",
        "    if [ $stuck -ge 3 ] && [ $((t - restarted)) -ge 300 ]; then",
        "        yield \"a phone joining\"; ap_restart \"a phone can't finish joining the hotspot: the hotspot restarted\"; restarted=$t; stuck=0",
        "    fi",
        "    if [ \"$n\" -gt 0 ]; then yield \"a phone on the hotspot\"; status yield; return; fi",
        "    # no phone: back on home Wi-Fi when parked (the owner's rule), or 2 minutes after the last phone left",
        "    if [ -f $W/yielded ]; then",
        "        if [ \"$(reading parked)\" != 1 ] && [ $((t - ${last:-0})) -lt 120 ]; then status yield; return; fi",
        "        rm -f $W/yielded; wpa RECONNECT >/dev/null; fails=0; say \"no phone on the hotspot: home Wi-Fi again\"",
        "    fi",
        "    [ -n \"$(saved)\" ] || { status nonetworks; return; }",
        "    st=$(wpa STATUS) || { status error; return; }",
        "    state=$(echo \"$st\" | field wpa_state) freq=$(echo \"$st\" | field freq)",
        "    if [ \"$state\" = COMPLETED ]; then",
        "        fails=0",
        "        rssi=$(wpa SIGNAL_POLL | field RSSI)",
        "        if [ ! -f $W/lease ]; then",
        "            dhcp_running || dhcpcd -m 1000 -t 30 $I >/dev/null 2>&1",
        "            lease_up; r=$?",
        "            [ $r = 0 ] || { [ $r = 2 ] || status nolease \"$rssi\" \"$freq\"; return; }",
        "        fi",
        "        for _ in 1 2 3 4 5 6 7 8; do ip route del default dev $I 2>/dev/null || break; done   # a renewal puts it back in main",
        "        status up \"$rssi\" \"$freq\"",
        "    else",
        "        [ -f $W/lease ] && { lease_down; say \"home Wi-Fi lost\"; }",
        "        fails=$((fails + 1)); [ $fails = 3 ] && pin",
        "        # this build's wpa_supplicant doesn't scan on its own (the framework asks it to): every 30 s",
        "        [ $((fails % 3)) = 1 ] && wpa SCAN >/dev/null",
        "        if [ -f $W/clash ]; then status clash; else status searching; fi",
        "    fi",
        "}",
        "down() {   # never leave the hotspot under the parked name",
        "    if [ -n \"$(getprop hmgapi.wifi.parked)\" ]; then setprop hmgapi.wifi.parked ''; ap_restart \"stopping: the hotspot back as it was\"; fi",
        "    client_down; status off",
        "}",
        "umask 077",
        "case $1 in",
        "    down) down; exit 0 ;;",
        "    saved) saved; exit 0 ;;",
        "    scan) scan; exit $? ;;",
        "    join) join \"$2\"; exit $? ;;",
        "    forget) forget \"$2\"; exit $? ;;",
        "esac",
        "trap 'down; say stopped; exit 0' TERM",
        "fails=0 stuck=0 restarted=-300",
        "while :; do",
        "    keep",
        "    sleep 10",
        "done");
}
