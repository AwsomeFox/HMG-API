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
 * off by default): the head unit joins the
 * networks saved in the stock Wi-Fi settings (/data/misc/wifi/wpa_supplicant.conf) on a
 * second interface, wlan1, while wlan0 keeps the projection hotspot (the BCM4359 runs a
 * station and an AP together, on two channels at most). The stock client feature
 * (android.hardware.wifi) comes on with it, so the stock settings page can add networks.
 * Only mods that ask ("wifi": {"home": true}) use it: their traffic is marked
 * (MARK) and routed through table TABLE; the car's own stays on cellular. The keeper
 * (state/wifi.sh, service "hmgapi wifi") runs wlan1's wpa_supplicant and DHCP, the
 * table, netd's DNS servers for wlan1, and the DNS redirect for mods' services. It never
 * takes wlan1 down while running: the hotspot shares the radio, and a projection session
 * dropped when it did (2026-11-17, US EV6).
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

    /**
     * The owner's switch as a change set of HMG-API's own (id "hmgapi"): the stock client
     * feature, as a mod's "wifi": {"client": true} has it. Its files live in state/core.
     */
    static Engine.Mod coreMod(Engine e) {
        if (!on(e)) return null;
        Engine.Mod m = new Engine.Mod();
        m.id = "hmgapi";
        m.dir = new File(e.stateDir, "core");
        m.dir.mkdirs();
        m.json = new LinkedHashMap<String, Object>();
        m.json.put("id", "hmgapi");
        Map<String, Object> wifi = new LinkedHashMap<String, Object>();
        wifi.put("client", Boolean.TRUE);
        m.json.put("wifi", wifi);
        m.patches = new LinkedHashMap<String, Object>();
        m.version = Engine.HMGAPI_VERSION;
        m.set = "";
        return m;
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
        out.add("home Wi-Fi  " + (home ? "on: the networks saved in Wi-Fi settings, on wlan1 next to the hotspot" : "off"));
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
     * Android is up; mksh and the unit's toolbox only. hmgapid wifi-if makes wlan1,
     * hmgapid wpa talks to its wpa_supplicant.
     */
    static final String SCRIPT = Network.lines(
        "# HMG-API's home Wi-Fi keeper (written by the engine: hmgapi.engine.HomeWifi). hmgapid runs it",
        "# as root (service \"hmgapi wifi\"). wlan1 joins the networks saved in the stock Wi-Fi settings",
        "# next to the projection hotspot on wlan0; only the mods that asked are routed through it.",
        "#   sh wifi.sh        keep (the service)",
        "#   sh wifi.sh down   everything out (the uninstaller)",
        "set -f",
        "S=${0%/*}",
        "X=${HMGAPI_TEST_ROOT:-}   # off-car tests only",
        "W=$X/data/hmgapi/wifi C=$X/data/hmgapi/wifi/ctrl I=" + IFACE + " T=" + TABLE,
        "STOCK=$X/data/misc/wifi/wpa_supplicant.conf",
        "PUB=$X/data/hmgapi/public/wifi.json",
        "say() { echo \"$(date '+%m-%d %H:%M:%S') $*\"; }",
        "wpa() { hmgapid wpa $C/$I \"$1\" 2>/dev/null; }",
        "field() { while IFS== read -r k v; do [ \"$k\" = \"$1\" ] && { echo \"$v\"; return; }; done; }",
        "status() { echo \"{\\\"state\\\": \\\"$1\\\", \\\"rssi\\\": ${2:-0}, \\\"freq\\\": ${3:-0}}\" > $PUB.tmp && chmod 644 $PUB.tmp && mv $PUB.tmp $PUB; }",
        "# the saved networks' blocks, nothing else (no p2p or global settings)",
        "networks() {",
        "    in=",
        "    while IFS= read -r l; do",
        "        case $l in network=*) in=1 ;; esac",
        "        [ -n \"$in\" ] && echo \"$l\"",
        "        case $l in \"}\") in= ;; esac",
        "    done < $STOCK",
        "}",
        "conf() {",
        "    n=$(networks 2>/dev/null)",
        "    [ -n \"$n\" ] || return 1",
        "    echo \"ctrl_interface=DIR=$C GROUP=wifi\"",
        "    echo 'update_config=0'",
        "    echo 'p2p_disabled=1'",
        "    echo \"$n\"",
        "}",
        "pid_of() { p=$(cat $W/wpa.pid 2>/dev/null) && [ -n \"$p\" ] && [ -d /proc/$p ] && echo $p; }",
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
        "# stock client mode (the Wi-Fi settings page in P): the stock supplicant owns the radio",
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
        "    : > $W/dnsroutes",
        "    # netd's lookups for wlan1 leave by it, and the kernel would send them there without the gateway",
        "    # (a server off the subnet: no answer): through the gateway, unless the server is the gateway",
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
        "    tab=$'\\t'",
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
        "    wpa \"SET_NETWORK $bid freq_list $best\" >/dev/null && wpa \"SELECT_NETWORK $bid\" >/dev/null && wpa ENABLE_NETWORK\\ all >/dev/null",
        "    say \"pinned to $best MHz, the strongest access point of a saved network\"",
        "}",
        "dhcp_running() { p=$(cat $X/data/misc/dhcp/dhcpcd-$I.pid 2>/dev/null) && [ -n \"$p\" ] && [ -d /proc/$p ]; }",
        "keep() {",
        "    if stock_client; then",
        "        [ -n \"$(pid_of)\" ] && { client_down; say 'stock Wi-Fi client mode on (Wi-Fi settings): home Wi-Fi waits'; }",
        "        status stock; return",
        "    fi",
        "    [ -d $X/sys/class/net/wlan0 ] || { status nochip; return; }",
        "    c=$(conf) || { [ -n \"$(pid_of)\" ] && { client_down; say 'no saved networks: home Wi-Fi off'; }; status nonetworks; return; }",
        "    mkdir -p $W $C; chown wifi:wifi $W $C 2>/dev/null; chmod 770 $W $C",
        "    if [ \"$c\" != \"$(cat $W/run.conf 2>/dev/null)\" ]; then",
        "        echo \"$c\" > $W/run.conf; chown wifi:wifi $W/run.conf 2>/dev/null; chmod 600 $W/run.conf; rm -f $W/clash",
        "        [ -n \"$(pid_of)\" ] && { wpa RECONFIGURE >/dev/null; say 'saved networks changed'; }",
        "    fi",
        "    hmgapid wifi-if add $I || { status noiface; return; }",
        "    ifconfig $I up 2>/dev/null",
        "    if [ -z \"$(pid_of)\" ]; then",
        "        wpa_supplicant -B -P $W/wpa.pid -i $I -D nl80211 -c $W/run.conf >/dev/null 2>&1 || { say 'wpa_supplicant didn'\"'\"'t start'; status error; return; }",
        "        say 'looking for the saved networks'; fails=0",
        "    fi",
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
        "        [ -f $W/lease ] && { lease_down; say 'home Wi-Fi lost'; }",
        "        fails=$((fails + 1)); [ $fails = 3 ] && pin",
        "        # this build's wpa_supplicant doesn't scan on its own (the framework asks it to): every 30 s",
        "        [ $((fails % 3)) = 1 ] && wpa SCAN >/dev/null",
        "        if [ -f $W/clash ]; then status clash; else status searching; fi",
        "    fi",
        "}",
        "down() { client_down; status off; }",
        "umask 077",
        "if [ \"$1\" = down ]; then down; exit 0; fi",
        "# stopped: the client and the lease go; wlan1 stays (taking it down drops a projection session)",
        "trap 'down; say stopped; exit 0' TERM",
        "fails=0",
        "while :; do",
        "    keep",
        "    sleep 10",
        "done");
}
