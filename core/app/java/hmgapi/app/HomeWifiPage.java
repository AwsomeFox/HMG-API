package hmgapi.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings > Home Wi-Fi: the owner's switch, the networks HMG-API joins (its own list, the car's
 * Wi-Fi settings' networks imported once), a scan, join and forget. Everything goes through
 * hmgapid wifi (the keeper's script, as root); a key is only ever sent to it, never shown or
 * logged. Joining with a key needs the car in P (typing). The scan runs on HMG-API's own client
 * (wlan1), next to the hotspot: wireless CarPlay and Android Auto keep running.
 */
final class HomeWifiPage {
    final MainActivity a;
    final LinearLayout page;
    LinearLayout saved, found;

    HomeWifiPage(MainActivity a, LinearLayout page) {
        this.a = a;
        this.page = page;
    }

    void show(final SettingsTab tab) {
        Ui.text(page, "Home Wi-Fi", 21, true, Ui.TEXT);
        Ui.text(page, "On: the head unit also joins your home Wi-Fi, on a second connection next to the one wireless "
                + "CarPlay and Android Auto use. They come first: while a phone is on the car's Wi-Fi, home Wi-Fi waits, and "
                + "it's back once the car is parked or the phone has been gone 2 minutes. Only mods that ask use it; the car's "
                + "own services stay on its mobile data.", 16, false, Ui.GREY);
        android.widget.Switch sw = Ui.switchRow(page, "Use home Wi-Fi");
        Ui.allowSwitch(a, sw, "home_wifi", false, new Runnable() {
            @Override
            public void run() {
                a.plan();   // the keeper starts (or stops) once applied; no restart
            }
        });
        Ui.text(page, "Now: " + now(), 16, false, Ui.TEXT);
        Ui.line(page);
        Ui.text(page, "Saved networks", 21, true, Ui.TEXT);
        saved = Ui.column(a);
        page.addView(saved);
        Ui.button(page, "Look for networks", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan();
            }
        });
        found = Ui.column(a);
        page.addView(found);
        Ui.line(page);
        Ui.text(page, "Mobile data", 21, true, Ui.TEXT);
        Ui.text(page, "Mods never use the car's mobile data, DNS lookups included: only home Wi-Fi, for the mods that ask. "
                + "To let one mod use it anyway, at your own risk, list it in hmgapi.json overrides.allow_cellular.", 16, false, Ui.GREY);
        loadSaved();
    }

    static String now() {
        try {
            JSONObject w = new JSONObject(UsbFolders.read(new File("/data/hmgapi/public/wifi.json")));
            String s = w.optString("state");
            if ("up".equals(s)) return "connected, " + w.optInt("rssi") + " dBm, " + w.optInt("freq") + " MHz";
            if ("yield".equals(s)) return "waiting: a phone is on the car's Wi-Fi";
            if ("searching".equals(s)) return "looking for a saved network";
            if ("nonetworks".equals(s)) return "no saved networks yet";
            if ("nolease".equals(s)) return "joined, waiting for an address";
            if ("stock".equals(s)) return "the car's own Wi-Fi settings have the Wi-Fi";
            if ("clash".equals(s)) return "a network uses 192.168.0.x, the car Wi-Fi's own addresses: it can't be used";
            if ("off".equals(s)) return "off";
            return s;
        } catch (Exception e) {
            return "off";
        }
    }

    void loadSaved() {
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call("wifi", "saved");
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                saved.removeAllViews();
                if (e != null || r.code != 0) {
                    Ui.text(saved, e != null ? "HMG-API isn't answering." : r.output.trim(), 16, false, Ui.GREY);
                    return;
                }
                boolean any = false;
                for (final String name : r.output.split("\n")) {
                    if (name.trim().isEmpty()) continue;
                    any = true;
                    LinearLayout row = Ui.row(a);
                    saved.addView(row);
                    Ui.text(row, name, 18, false, Ui.TEXT);
                    Ui.button(row, "Forget", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            forget(name);
                        }
                    });
                }
                if (!any) Ui.text(saved, "None yet: look for networks below.", 16, false, Ui.GREY);
            }
        }.start(a);
    }

    void scan() {
        found.removeAllViews();
        Ui.text(found, "Looking…", 16, false, Ui.GREY);
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call(30000, "wifi", "scan");
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                found.removeAllViews();
                if (e != null || r.code != 0) {
                    Ui.text(found, e != null ? "HMG-API isn't answering." : r.output.trim() + " (turn home Wi-Fi on and apply first)", 16, false, Ui.GREY);
                    return;
                }
                // strongest per name; "<MHz>\t<dBm>\t<flags>\t<name>"
                Map<String, String[]> best = new LinkedHashMap<String, String[]>();
                for (String l : r.output.split("\n")) {
                    String[] f = l.split("\t", 4);
                    if (f.length < 4 || f[3].isEmpty()) continue;
                    String[] was = best.get(f[3]);
                    if (was == null || Integer.parseInt(f[1]) > Integer.parseInt(was[1])) best.put(f[3], f);
                }
                List<String[]> list = new ArrayList<String[]>(best.values());
                java.util.Collections.sort(list, new java.util.Comparator<String[]>() {
                    @Override
                    public int compare(String[] x, String[] y) {
                        return Integer.parseInt(y[1]) - Integer.parseInt(x[1]);
                    }
                });
                if (list.isEmpty()) Ui.text(found, "No networks in range.", 16, false, Ui.GREY);
                for (final String[] f : list) {
                    final boolean open = !f[2].contains("WPA") && !f[2].contains("WEP");
                    final boolean usable = !f[2].contains("WEP") && !f[2].contains("EAP");
                    LinearLayout row = Ui.row(a);
                    found.addView(row);
                    int mhz = Integer.parseInt(f[0]);
                    Ui.text(row, f[3] + "   " + f[1] + " dBm, " + (mhz < 3000 ? "2.4" : "5") + " GHz" + (open ? ", open" : ""), 18, false,
                            usable ? Ui.TEXT : Ui.GREY);
                    if (!usable) continue;
                    Ui.button(row, "Join", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            join(f[3], open);
                        }
                    });
                }
            }
        }.start(a);
    }

    void join(final String name, final boolean open) {
        if (open) {
            save(name, "");
            return;
        }
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call("vehicle", "gear.park");
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                boolean parked = e == null && r.code == 0 && r.output.trim().endsWith(" 1");
                if (!parked) {
                    a.message("Home Wi-Fi", "Shift to P to type a Wi-Fi key.");
                    return;
                }
                final EditText key = new EditText(a);
                key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                key.setHint("Key");
                new AlertDialog.Builder(a).setTitle(name).setView(key)
                        .setPositiveButton("Join", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                save(name, key.getText().toString());
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        }.start(a);
    }

    void save(final String name, final String key) {
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return key.isEmpty() ? Hmgapid.call("wifi", "join", name) : Hmgapid.call("wifi", "join", name, key);
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                if (e != null || r.code != 0) {
                    a.message("Home Wi-Fi", e != null ? "HMG-API isn't answering." : r.output.trim());
                    return;
                }
                a.message("Home Wi-Fi", "Saved. HMG-API joins it within a few seconds when no phone is on the car's Wi-Fi.");
                loadSaved();
            }
        }.start(a);
    }

    void forget(final String name) {
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call("wifi", "forget", name);
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                loadSaved();
            }
        }.start(a);
    }
}
