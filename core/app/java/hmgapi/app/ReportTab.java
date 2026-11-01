package hmgapi.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Car Report: checks how HMG-API fits this car and makes a report for its maintainers
 * (hmgapid engine verify-map when there's a map, then engine report --usb: the car
 * mapped from its own files, mods, errors; model-level facts only). Shows the summary
 * and a QR code that opens a pre-filled GitHub issue on the owner's phone; the file
 * to attach is on the USB stick ("HMG-API reports"). Works on any car, mapped or not.
 */
final class ReportTab {
    final MainActivity a;
    private TextView message, result;
    private ImageView qr;
    private Button go;
    static String last;   // the last report's output, kept while the app runs

    ReportTab(MainActivity a) {
        this.a = a;
    }

    View view() {
        LinearLayout root = Ui.row(a);
        root.setGravity(0);
        LinearLayout list = Ui.scrolling(root, 1, 0);
        list.setPadding(Ui.dp(a, 36), Ui.dp(a, 16), Ui.dp(a, 24), Ui.dp(a, 24));
        Ui.text(list, "Car Report", 28, true, Ui.TEXT);
        Ui.text(list, "Checks how HMG-API fits this car and makes a report for HMG-API's maintainers, so what's missing "
                + "for your car can be added. It holds what the head unit is (model, region, firmware), which hook points "
                + "this car has, which mods load, and recent HMG-API errors. Nothing personal: no VIN, serial numbers, "
                + "phone numbers or account details. Plug in a USB stick first to get the file to attach.", 16, false, Ui.GREY);
        go = Ui.button(list, "Make report", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                make();
            }
        });
        message = Ui.text(list, "", 18, false, Ui.WARN);
        result = Ui.text(list, "", 18, false, Ui.TEXT);
        qr = new ImageView(a);
        qr.setVisibility(View.GONE);
        int px = Ui.dp(a, 380);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(Ui.dp(a, 24), 0, Ui.dp(a, 40), 0);
        root.addView(qr, lp);
        if (last != null) show(last.substring(last.indexOf('\n') + 1), last.substring(0, last.indexOf('\n')));
        return root;
    }

    private void make() {
        go.setEnabled(false);
        qr.setVisibility(View.GONE);
        result.setText("");
        message.setText("Checking the map and reading this car's apps (about a minute)…");
        new Ui.Job<String[]>() {
            @Override
            String[] work() throws Exception {
                Hmgapid.Result map = Hmgapid.call(60000, "engine", "map");
                String check = "";
                if (map.output.contains(" for ")) {   // a map for this car: do its names resolve here?
                    Hmgapid.Result v = Hmgapid.call(600000, "engine", "verify-map");
                    for (String l : v.output.split("\n")) if (l.startsWith("map ") && l.contains("resolve")) check = l.trim().replaceFirst("^map \\S+: ", "");
                }
                Hmgapid.Result r = Hmgapid.call(600000, "engine", "report", "--usb");
                if (r.code != 0) throw new Exception(r.output.trim());
                return new String[] { r.output, check };
            }

            @Override
            void done(String[] r, Exception e) {
                go.setEnabled(true);
                go.setText("Make it again");
                if (e != null) {
                    message.setText("Couldn't make the report: " + e.getMessage());
                    return;
                }
                last = r[1] + "\n" + r[0];
                show(r[0], r[1]);
            }
        }.start(a);
    }

    private void show(String out, String check) {
        go.setText("Make it again");
        String url = null, file = null, car = null, map = null, found = null;
        StringBuilder usb = new StringBuilder();
        for (String l : out.split("\n")) {
            if (l.startsWith("issue-url: ")) url = l.substring(11).trim();
            else if (l.startsWith("report: ")) file = l.substring(8).trim();
            else if (l.startsWith("car: ")) car = l.substring(5).trim();
            else if (l.startsWith("hook map: ")) map = l.trim();
            else if (l.startsWith("map-car: ")) found = l.substring(9).trim();
            else if (l.startsWith("usb: /")) usb.append(l.substring(5).trim()).append('\n');
        }
        StringBuilder s = new StringBuilder();
        s.append("Car: ").append(car).append("\n\n").append(map).append("\n");
        if (!check.isEmpty()) s.append("Map check: ").append(check).append("\n");
        s.append("Found from this car's own files: ").append(found).append("\n\n");
        s.append("Report file: ").append(file).append(".json\n");
        message.setText("Done. Scan the code with your phone: it opens a GitHub issue with this summary. Attach the report "
                + "file to it, from the USB stick or over adb (below), and send it.");
        if (usb.length() > 0) s.append("USB stick: ").append(usb).append("folder \"HMG-API reports\" on the stick\n");
        else s.append("USB stick: none found (plug one in and make the report again)\n");
        s.append("adb (from a computer, the head unit's adb on):\n    adb pull /data/hmgapi/public/reports/").append(file).append(".json\n");
        result.setText(s.toString());
        if (url != null) {
            try {
                qr.setImageBitmap(bitmap(QrCode.encode(url.getBytes("UTF-8")), Ui.dp(a, 380)));
                qr.setVisibility(View.VISIBLE);
            } catch (Exception x) {
                result.append("\n(no QR code: " + x.getMessage() + ")");
            }
        }
    }

    /** the code, black on white, with the 4-module quiet zone scanners need */
    static Bitmap bitmap(QrCode q, int px) {
        int n = q.size + 8, scale = Math.max(1, px / n), w = n * scale;
        int[] pixels = new int[w * w];
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                int mx = x / scale - 4, my = y / scale - 4;
                boolean dark = mx >= 0 && my >= 0 && mx < q.size && my < q.size && q.modules[my][mx];
                pixels[y * w + x] = dark ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, w, w, Bitmap.Config.ARGB_8888);
    }
}
