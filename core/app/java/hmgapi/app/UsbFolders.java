package hmgapi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

/**
 * Tells mods' apps when a USB stick with their folder (mod.json "usb") comes and
 * goes: hmgapi.intent.action.USB_FOLDER, sent to each app of that mod, with
 * "folder", "path" (e.g. /storage/usb0/Dashcam) and "mounted". hmgapid (`usb`)
 * looks at the stick as root, so mods' apps need no storage permission to hear
 * about it; the mods and their apps come from the public state.
 */
public final class UsbFolders extends BroadcastReceiver {
    static final String TAG = "HMG-API";
    public static final String ACTION = "hmgapi.intent.action.USB_FOLDER";

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        final boolean mounted = Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction());
        final String mount = intent.getData() == null ? null : intent.getData().getPath();
        if (mount == null) return;
        final PendingResult done = goAsync();
        new Thread("hmgapi-usb") {
            @Override
            public void run() {
                try {
                    announce(ctx, mount, mounted);
                } catch (Exception e) {
                    Log.w(TAG, "usb folders: " + e);
                } finally {
                    done.finish();
                }
            }
        }.start();
    }

    static void announce(Context ctx, String mount, boolean mounted) throws Exception {
        JSONObject mods = new JSONObject(read(new File("/data/hmgapi/public/state.json"))).optJSONObject("mods");
        if (mods == null) return;
        String stick = mount.replace("/mnt/media_rw/", "/storage/");
        if (mounted) {
            Hmgapid.Result r = Hmgapid.call("usb");
            for (String line : r.output.split("\n")) {
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                String mod = line.substring(0, sp), path = line.substring(sp + 1);
                if (!path.startsWith(stick + "/")) continue;
                send(ctx, mods.optJSONObject(mod), mod, path.substring(stick.length() + 1), path, true);
            }
            return;
        }
        for (Iterator<?> it = mods.keys(); it.hasNext();) {
            String mod = (String) it.next();
            JSONObject m = mods.optJSONObject(mod);
            JSONArray usb = m == null ? null : m.optJSONArray("usb");
            for (int i = 0; usb != null && i < usb.length(); i++) send(ctx, m, mod, usb.getString(i), stick + "/" + usb.getString(i), false);
        }
    }

    static void send(Context ctx, JSONObject m, String mod, String folder, String path, boolean mounted) {
        JSONArray apps = m == null || !m.optBoolean("enabled") ? null : m.optJSONArray("apps");
        for (int i = 0; apps != null && i < apps.length(); i++) {
            ctx.sendBroadcast(new Intent(ACTION).setPackage(apps.optString(i))
                    .putExtra("mod", mod).putExtra("folder", folder).putExtra("path", path).putExtra("mounted", mounted));
        }
        Log.i(TAG, "usb folders: " + mod + " " + path + (mounted ? " in" : " out"));
    }

    static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int n = 0, k;
            while (n < b.length && (k = in.read(b, n, b.length - n)) > 0) n += k;
            return new String(b, 0, n, "UTF-8");
        } finally {
            in.close();
        }
    }
}
