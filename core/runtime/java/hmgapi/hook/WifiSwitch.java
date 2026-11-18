package hmgapi.hook;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;

import java.util.List;

/**
 * Home Wi-Fi (hmgapi.json "home_wifi"): the stock Wi-Fi settings page adds networks in P.
 *
 * The stock Wi-Fi stack runs the client or the hotspot, never both, and its WifiController
 * ignores "Wi-Fi on" while the hotspot is up: the page's switch does nothing on a car that
 * runs wireless CarPlay / Android Auto. In system_server, with home Wi-Fi on:
 *
 *   switch on, hotspot up, gear in P   the hotspot goes off first (remembered: the projection
 *                                      AP, apMode 2, or the plain hotspot; the stock page turns
 *                                      it off itself just before, which counts too), then the client
 *                                      comes on as stock; the networks the owner joins are
 *                                      saved the stock way (wpa_supplicant.conf), and HMG-API's
 *                                      home Wi-Fi keeper (state/wifi.sh) uses them on wlan1
 *   switch on, not in P                refused, with a notice
 *   switch off                         the client off, the hotspot back as it was
 *   out of P, or the page left         the same, checked every 5 s
 *
 * What it switched is in Settings.Global "hmgapi_wifi_switched" (1 hotspot, 2 projection AP),
 * so a restart with the page open still brings the hotspot back.
 */
final class WifiSwitch {
    private WifiSwitch() {}

    static final String SVC = "framework.wifi_service";
    static final String ENABLE = SVC + ".set_wifi_enabled", AP = SVC + ".set_wifi_ap_enabled",
            AP_CONNECTIVITY = SVC + ".set_wifi_ap_enabled_for_connectivity", AP_STATE = SVC + ".get_wifi_ap_enabled_state",
            AP_MODE = SVC + ".get_wifi_ap_mode", START = SVC + ".check_and_start_wifi", CONTEXT = SVC + ".context";
    static final String[] GEAR = { "framework.car_services.system", "framework.service_manager.get_service",
            "framework.system_service_stub.as_interface", "framework.system_service.gear_p_status" };
    static final String FLAG = "hmgapi_wifi_switched", PAGE = "com.hkmc.wifisettings";
    static final int AP_ENABLING = 12, AP_ENABLED = 13;

    static volatile Object svc;
    static volatile long offAt;
    static volatile int offMode;
    static Handler handler;
    static int away;
    static final ThreadLocal<Integer> SWITCHED = new ThreadLocal<Integer>();
    static final ThreadLocal<Boolean> OURS = new ThreadLocal<Boolean>();

    static boolean on() {
        return Mods.hmgapiSettings != null && Mods.hmgapiSettings.optBoolean("home_wifi", false);
    }

    /** In the zygote: system_server, the only caller, inherits the hooks. */
    static void zygote() {
        if (!on()) return;
        String[] need = { ENABLE, AP, AP_CONNECTIVITY, AP_STATE, AP_MODE, START, CONTEXT, GEAR[0], GEAR[1], GEAR[2], GEAR[3] };
        if (!Points.needAll("home Wi-Fi: the Wi-Fi settings page in P", need)) return;
        Points.hook(null, START, new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                svc = p.thisObject;
                if (flag() != 0) watch(30000);   // a restart with the page open: the hotspot comes back
            }
        });
        // The stock page turns the hotspot off itself before it turns the client on (WifiHotspot,
        // WifiClientSettings: setWifiApEnabled(config, false), then setWifiEnabled(true)): which
        // hotspot it was, and when, so the client toggle that follows knows what to bring back.
        MethodHook apOff = new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (Boolean.TRUE.equals(OURS.get()) || Boolean.TRUE.equals(p.args[1])) return;
                try {
                    svc = p.thisObject;
                    int ap = num(Points.call(null, AP_STATE, svc));
                    if (ap != AP_ENABLING && ap != AP_ENABLED) return;
                    offMode = num(Points.call(null, AP_MODE, svc)) == 2 ? 2 : 1;
                    offAt = android.os.SystemClock.elapsedRealtime();
                } catch (Throwable t) {
                    HmgApi.e("home Wi-Fi: hotspot off", t);
                }
            }
        };
        Points.hook(null, AP, apOff);
        Points.hook(null, AP_CONNECTIVITY, apOff);
        Points.hook(null, ENABLE, new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                SWITCHED.set(0);
                if (Boolean.TRUE.equals(OURS.get())) return;
                try {
                    svc = p.thisObject;
                    boolean enable = (Boolean) p.args[0];
                    if (!enable) {
                        SWITCHED.set(flag());
                        return;
                    }
                    int ap = num(Points.call(null, AP_STATE, svc));
                    boolean up = ap == AP_ENABLING || ap == AP_ENABLED;
                    boolean justOff = !up && offAt > 0 && android.os.SystemClock.elapsedRealtime() - offAt < 15000;
                    if (!up && !justOff) return;   // no hotspot: as stock
                    int mode = up ? (num(Points.call(null, AP_MODE, svc)) == 2 ? 2 : 1) : offMode;
                    offAt = 0;
                    if (!parked()) {
                        toast("Shift to P to add or change Wi-Fi networks. Wireless CarPlay and Android Auto use the Wi-Fi until then.");
                        p.setResult(Boolean.FALSE);
                        if (justOff) hotspotOn(mode);   // the page already turned it off: back on
                        HmgApi.i("home Wi-Fi: Wi-Fi settings: the client refused (not in P)");
                        return;
                    }
                    Settings.Global.putInt(resolver(), FLAG, mode);
                    if (up) {
                        OURS.set(Boolean.TRUE);
                        try {
                            Points.call(null, AP, svc, null, Boolean.FALSE);   // the controller then takes the client toggle
                        } finally {
                            OURS.set(Boolean.FALSE);
                        }
                    }
                    HmgApi.i("home Wi-Fi: Wi-Fi settings in P: the " + (mode == 2 ? "projection AP" : "hotspot") + " off, the client on");
                    watch(5000);
                } catch (Throwable t) {
                    HmgApi.e("home Wi-Fi: settings switch", t);
                }
            }

            @Override
            protected void afterHookedMethod(Param p) {
                Integer was = SWITCHED.get();
                if (was != null && was != 0) back(was, "the client turned off");
            }
        });
    }

    static ContentResolver resolver() {
        return ((Context) Points.get(null, CONTEXT, svc)).getContentResolver();
    }

    static int flag() {
        try {
            return Settings.Global.getInt(resolver(), FLAG, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : -1;
    }

    /** gear in P; anything unknown is "not in P" */
    static boolean parked() {
        try {
            String name = (String) Points.get(null, GEAR[0], null);
            IBinder b = (IBinder) Points.call(null, GEAR[1], null, name);
            if (b == null) return false;
            Object sys = Points.call(null, GEAR[2], null, b);
            return sys != null && num(Points.call(sys.getClass().getClassLoader(), GEAR[3], sys)) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean pageOnTop() {
        try {
            Context c = (Context) Points.get(null, CONTEXT, svc);
            List<ActivityManager.RunningTaskInfo> t = ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(1);
            return t != null && !t.isEmpty() && t.get(0).topActivity != null && PAGE.equals(t.get(0).topActivity.getPackageName());
        } catch (Throwable x) {
            return true;   // can't tell: the gear still decides
        }
    }

    static void toast(String text) {
        try {
            Context c = (Context) Points.get(null, CONTEXT, svc);
            Intent i = new Intent("com.hkmc.intent.action.TOAST");
            i.putExtra("com.hkmc.intent.extra.TOAST_MESSAGE", text);
            i.putExtra("com.hkmc.intent.extra.TOAST_POPUPSHOWING", true);
            c.sendBroadcast(i);
        } catch (Throwable t) {
            HmgApi.e("home Wi-Fi: notice", t);
        }
    }

    /** the client off (if it's on) and the hotspot back as it was */
    static synchronized void back(int was, String why) {
        try {
            if (flag() == 0) return;
            Settings.Global.putInt(resolver(), FLAG, 0);
            OURS.set(Boolean.TRUE);
            try {
                Points.call(null, ENABLE, svc, Boolean.FALSE);
            } finally {
                OURS.set(Boolean.FALSE);
            }
            hotspotOn(was);
            HmgApi.i("home Wi-Fi: " + why + ": the " + (was == 2 ? "projection AP" : "hotspot") + " back on");
        } catch (Throwable t) {
            HmgApi.e("home Wi-Fi: hotspot back", t);
        }
    }

    static void hotspotOn(int mode) {
        OURS.set(Boolean.TRUE);
        try {
            Points.call(null, mode == 2 ? AP_CONNECTIVITY : AP, svc, null, Boolean.TRUE);
        } finally {
            OURS.set(Boolean.FALSE);
        }
    }

    /** every 5 s while switched: out of P, or away from the page twice in a row, switches back */
    static synchronized void watch(long first) {
        if (handler == null) {
            HandlerThread t = new HandlerThread("hmgapi-wifi-switch");
            t.start();
            handler = new Handler(t.getLooper());
        }
        away = 0;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int was = flag();
                if (was == 0 || svc == null) return;
                boolean p = parked(), top = pageOnTop();
                away = top ? 0 : away + 1;
                if (!p || away >= 2) {
                    back(was, !p ? "out of P" : "the Wi-Fi settings page left");
                    return;
                }
                handler.postDelayed(this, 5000);
            }
        }, first);
    }
}
