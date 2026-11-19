package hmgapi.app;

import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Logs > Record a problem: the problem log (hmgapid engine problem record), for an owner with
 * an issue to send to whoever helps them. Says first what it takes, then records while the
 * owner makes the problem happen, and saves a .txt on the USB stick and in HMG-API's folder.
 * Crash dumps are the owner's choice, off until they turn them on. The recording keeps going
 * when the owner leaves the page; coming back shows it.
 */
final class ProblemPage {
    static final int MAX_SECONDS = 300;
    static boolean recording, dumps;
    static long startedAt;
    static String result, error;

    final MainActivity a;
    TextView status;
    final Handler tick = new Handler();

    ProblemPage(MainActivity a) {
        this.a = a;
    }

    View view() {
        LinearLayout root = Ui.row(a);
        LinearLayout list = Ui.scrolling(root, 1, 0);
        list.setPadding(Ui.dp(a, 36), Ui.dp(a, 16), Ui.dp(a, 36), Ui.dp(a, 24));
        Ui.button(list, "‹ Back to the log", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogsTab.problem = false;
                a.show(3);
            }
        });
        Ui.text(list, "Record a problem", 28, true, Ui.TEXT);
        Ui.text(list, "For when something goes wrong (an app crashes, CarPlay stops, no sound) and someone is helping you "
                + "find out why. Start recording, make the problem happen, then stop. It records for at most 5 minutes.", 17, false, Ui.TEXT);
        Ui.text(list, "What the file holds", 20, true, Ui.TEXT);
        Ui.text(list, "• The head unit: model, region, firmware, platform, and HMG-API's version.\n"
                + "• Log lines from crashes, CarPlay, Android Auto, audio, the Wi-Fi hotspot, HMG-API and its mods, "
                + "only while it records. Lines from every other app (phone, contacts, messages, navigation, radio) "
                + "are left out, only counted.\n"
                + "• Your installed mods, and checksums of the system files they patch.\n"
                + "• HMG-API's recent errors.\n"
                + "Device addresses, phone numbers, VIN, serial numbers, device ids and GPS positions are removed from "
                + "every line. CarPlay and Android Auto lines can still name your phone or what's playing: open the "
                + "file and look before you send it.", 16, false, Ui.GREY);
        final Switch sw = Ui.switchRow(list, "Include crash dumps");
        sw.setChecked(dumps);
        sw.setEnabled(!recording);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                dumps = on;
            }
        });
        Ui.text(list, "Crash dumps are the head unit's own records of the last crashes (in /data/tombstones). With this on, "
                + "the file gets the newest 5, cut down to which program crashed, how, and where in its code. The memory "
                + "contents and log lines a dump also holds are left out.", 16, false, Ui.GREY);
        LinearLayout buttons = Ui.row(a);
        list.addView(buttons);
        final Button go = Ui.button(buttons, recording ? "Stop" : "Start recording", true, null);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) stop(go);
                else start();
            }
        });
        status = Ui.text(list, "", 18, false, Ui.WARN);
        draw();
        return root;
    }

    void start() {
        recording = true;
        startedAt = System.currentTimeMillis();
        result = error = null;
        final boolean withDumps = dumps;
        new Ui.Job<String>() {
            @Override
            String work() throws Exception {
                Hmgapid.Result r = withDumps
                        ? Hmgapid.call((MAX_SECONDS + 120) * 1000, "engine", "problem", "record", "--usb", "--crash-dumps")
                        : Hmgapid.call((MAX_SECONDS + 120) * 1000, "engine", "problem", "record", "--usb");
                if (r.code != 0) throw new Exception(r.output.trim());
                return r.output;
            }

            @Override
            void done(String r, Exception e) {
                recording = false;
                result = r;
                error = e == null ? null : String.valueOf(e.getMessage());
                if (LogsTab.problem && a.current == 3) a.show(3);   // redraw, if the page is still open
            }
        }.start(a);
        a.show(3);
    }

    void stop(final Button go) {
        go.setEnabled(false);
        go.setText("Stopping…");
        new Ui.Job<String>() {
            @Override
            String work() throws Exception {
                return Hmgapid.call("engine", "problem", "stop").output;
            }

            @Override
            void done(String r, Exception e) {
                if (e != null) status.setText("Couldn't stop it: " + e.getMessage() + " (it stops by itself after 5 minutes)");
            }
        }.start(a);
    }

    void draw() {
        if (recording) {
            long s = (System.currentTimeMillis() - startedAt) / 1000;
            status.setTextColor(Ui.WARN);
            status.setText("Recording: " + s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60
                    + " (stops by itself at 5:00). Make the problem happen now, then tap Stop.");
            tick.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (status.isShown()) draw();
                }
            }, 1000);
            return;
        }
        if (error != null) {
            status.setTextColor(Ui.ERROR);
            status.setText("Couldn't record: " + error);
            return;
        }
        if (result == null) {
            status.setText("");
            return;
        }
        String name = null, kept = null;
        StringBuilder usb = new StringBuilder();
        for (String l : result.split("\n")) {
            if (l.startsWith("problem: ")) name = l.substring(9).trim();
            else if (l.startsWith("kept: ")) kept = l.substring(6).trim();
            else if (l.startsWith("usb: ")) usb.append(l.substring(5).trim()).append('\n');
        }
        status.setTextColor(Ui.TEXT);
        status.setText("Saved: " + name + ".txt (" + kept + ")\n\n"
                + "USB stick: " + (usb.length() > 0 ? usb.toString() : "none\n")
                + "adb (from a computer):\n    adb pull /data/hmgapi/public/reports/" + name + ".txt\n\n"
                + "Look through it, then send it to whoever is helping you (attach it to the GitHub issue).");
    }
}
