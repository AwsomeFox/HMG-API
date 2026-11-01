package hmgapi.app;

import android.app.Activity;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One mod's settings, drawn from its settings.json (toggle, text, number, choice)
 * into a column (the Mods tab). Save writes the owner's values with hmgapid
 * setting-set; "Apply changes" then rebuilds what they affect.
 */
final class SettingsForm {
    private final Activity a;
    private final LinearLayout list;
    private final String id;
    private final Runnable saved;
    private final Map<String, View> inputs = new LinkedHashMap<String, View>();
    private final Map<String, String> types = new LinkedHashMap<String, String>();
    private TextView message;

    SettingsForm(Activity a, LinearLayout list, String id, Runnable saved) {
        this.a = a;
        this.list = list;
        this.id = id;
        this.saved = saved;
    }

    void load() {
        message = Ui.text(list, "Loading its settings…", 16, false, Ui.GREY);
        new Ui.Job<JSONObject[]>() {
            @Override
            JSONObject[] work() throws Exception {
                Hmgapid.Result s = Hmgapid.call("mod-file", id, "settings.json");
                if (s.code != 0) return null;
                String v = Hmgapid.call("setting-get", id).output.trim();
                return new JSONObject[] { new JSONObject(s.output), new JSONObject(v.isEmpty() ? "{}" : v) };
            }

            @Override
            void done(JSONObject[] r, Exception e) {
                if (e != null) {
                    message.setText("Can't read its settings: " + e.getMessage());
                    message.setTextColor(Ui.ERROR);
                    return;
                }
                JSONArray settings = r == null ? null : r[0].optJSONArray("settings");
                if (settings == null || settings.length() == 0) {
                    message.setText("No settings.");
                    return;
                }
                list.removeView(message);
                draw(settings, r[1]);
            }
        }.start(a);
    }

    private void draw(JSONArray settings, JSONObject values) {
        for (int i = 0; i < settings.length(); i++) {
            JSONObject s = settings.optJSONObject(i);
            if (s == null || s.optBoolean("private")) continue;
            String key = s.optString("key"), type = s.optString("type", "text");
            Object value = values.has(key) ? values.opt(key) : s.opt("default");
            if ("toggle".equals(type)) {
                Switch sw = Ui.switchRow(list, s.optString("title", key));
                sw.setChecked(Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value)));
                inputs.put(key, sw);
            } else if ("choice".equals(type)) {
                Ui.text(list, s.optString("title", key), 19, false, Ui.TEXT);
                RadioGroup g = new RadioGroup(a);
                JSONArray opts = s.optJSONArray("options");
                for (int k = 0; opts != null && k < opts.length(); k++) {
                    JSONObject o = opts.optJSONObject(k);
                    RadioButton rb = new RadioButton(a);
                    rb.setText(o.optString("title", o.optString("value")));
                    rb.setTextColor(Ui.TEXT);
                    rb.setTag(o.optString("value"));
                    rb.setId(1000 + k);
                    g.addView(rb);
                    if (o.optString("value").equals(String.valueOf(value))) g.check(rb.getId());
                }
                list.addView(g);
                inputs.put(key, g);
            } else {
                Ui.text(list, s.optString("title", key), 19, false, Ui.TEXT);
                EditText t = new EditText(a);
                t.setText(value == null || value == JSONObject.NULL ? "" : String.valueOf(value));
                t.setTextColor(Ui.TEXT);
                if ("number".equals(type)) t.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                list.addView(t);
                inputs.put(key, t);
            }
            types.put(key, type);
            if (s.optString("description").length() > 0) Ui.text(list, s.optString("description"), 15, false, Ui.GREY);
            if (s.optString("warning").length() > 0) Ui.text(list, s.optString("warning"), 15, false, Ui.WARN);
        }
        Ui.button(list, "Save settings", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        message = Ui.text(list, "", 16, false, Color.GRAY);
    }

    private void save() {
        final JSONObject out = new JSONObject();
        try {
            for (Map.Entry<String, View> e : inputs.entrySet()) {
                String type = types.get(e.getKey());
                View v = e.getValue();
                if ("toggle".equals(type)) {
                    out.put(e.getKey(), ((Switch) v).isChecked());
                } else if ("choice".equals(type)) {
                    RadioGroup g = (RadioGroup) v;
                    View sel = g.findViewById(g.getCheckedRadioButtonId());
                    if (sel != null) out.put(e.getKey(), sel.getTag());
                } else if ("number".equals(type)) {
                    String t = ((EditText) v).getText().toString().trim();
                    if (!t.isEmpty()) out.put(e.getKey(), Long.parseLong(t));
                } else {
                    out.put(e.getKey(), ((EditText) v).getText().toString());
                }
            }
        } catch (Exception e) {
            message.setText("Not saved: " + e.getMessage());
            message.setTextColor(Ui.ERROR);
            return;
        }
        new Ui.Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call("setting-set", id, out.toString());
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                if (e != null || r.code != 0) {
                    message.setText("Not saved: " + (e != null ? e.getMessage() : r.output.trim()));
                    message.setTextColor(Ui.ERROR);
                    return;
                }
                message.setText("Saved. Apply changes to update the head unit.");
                message.setTextColor(Ui.GREEN);
                if (saved != null) saved.run();
            }
        }.start(a);
    }
}
