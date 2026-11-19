package hmgapi.engine;

import java.lang.reflect.Method;

/**
 * The projection hotspot, through Android's own Wi-Fi service, as the projection apps start it:
 * app_process /system/bin hmgapi.engine.WifiAp restart | stop | start <mode> (root; CLASSPATH
 * hmgapi-hook.jar). Home Wi-Fi's keeper turns it off while the car is parked (the phones off it,
 * home Wi-Fi alone on the radio) and back on when the car is on, and restarts it when a phone can't finish joining the
 * hotspot (seen 2026-11-17 on a US EV6: with the radio shared, the firmware moved the
 * hotspot's channel, and phones then never completed the key handshake until it restarted).
 * The same mode as before: the projection AP (apMode 2) or the plain hotspot.
 * Through IWifiManager by reflection (hidden API on 4.4); no Context under app_process.
 */
public final class WifiAp {
    private WifiAp() {}

    static final int AP_ENABLED = 13;

    /**
     * restart: off and on again, the same mode. stop: off, printing the mode it had ("2" the projection
     * AP, "1" the plain hotspot, "0" it wasn't on). start <mode>: on again in that mode.
     */
    public static void main(String[] args) throws Exception {
        String v = args.length > 0 ? args[0] : "";
        if (!(("restart".equals(v) || "stop".equals(v)) && args.length == 1) && !("start".equals(v) && args.length == 2)) {
            System.out.println("usage: WifiAp restart | stop | start <1|2>");
            System.exit(2);
        }
        Object b = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wifi");
        if (b == null) {
            System.out.println("no Wi-Fi service");
            System.exit(1);
        }
        Class<?> i = Class.forName("android.net.wifi.IWifiManager");
        Object wifi = Class.forName("android.net.wifi.IWifiManager$Stub").getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, b);
        Class<?> conf = Class.forName("android.net.wifi.WifiConfiguration");
        int state = (Integer) i.getMethod("getWifiApEnabledState").invoke(wifi);
        int mode;
        if ("start".equals(v)) {
            mode = "2".equals(args[1]) ? 2 : 1;
        } else {
            try {
                mode = (Integer) i.getMethod("getWifiApMode").invoke(wifi) == 2 ? 2 : 1;
            } catch (NoSuchMethodException e) {
                mode = 1;
            }
        }
        Method set = mode == 2 ? i.getMethod("setWifiApEnabledForConnectivity", conf, boolean.class) : i.getMethod("setWifiApEnabled", conf, boolean.class);
        if ("stop".equals(v) || "restart".equals(v)) {
            if (state != AP_ENABLED && state != 12) {   // 12: enabling
                if ("stop".equals(v)) {
                    System.out.println("0");
                    System.exit(0);
                }
            } else {
                set.invoke(wifi, null, false);
                for (int t = 0; t < 40 && (Integer) i.getMethod("getWifiApEnabledState").invoke(wifi) != 11; t++) Thread.sleep(250);   // 11: disabled
            }
            if ("stop".equals(v)) {
                System.out.println(mode);
                System.exit(0);
            }
        }
        set.invoke(wifi, null, true);
        for (int t = 0; t < 60 && (Integer) i.getMethod("getWifiApEnabledState").invoke(wifi) != AP_ENABLED; t++) Thread.sleep(250);
        boolean up = (Integer) i.getMethod("getWifiApEnabledState").invoke(wifi) == AP_ENABLED;
        System.out.println((mode == 2 ? "projection AP" : "hotspot") + (up ? " on" : " didn't come on"));
        System.exit(up ? 0 : 1);
    }
}
