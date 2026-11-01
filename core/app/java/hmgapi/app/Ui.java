package hmgapi.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** The few views HMG-API's screens are made of (plain code, no layouts: the app stays tiny). */
final class Ui {
    static final int BG = Color.rgb(16, 20, 18);
    static final int PANEL = Color.rgb(26, 32, 29);
    static final int PANEL2 = Color.rgb(34, 42, 38);
    static final int LINE = Color.rgb(52, 62, 57);
    static final int ACCENT = Color.rgb(65, 174, 71);    // the logo's green
    static final int GREEN = Color.rgb(150, 220, 160);
    static final int WARN = Color.rgb(230, 196, 90);
    static final int ERROR = Color.rgb(232, 96, 84);
    static final int GREY = Color.rgb(150, 158, 153);
    static final int OFF = Color.rgb(95, 102, 98);
    static final int TEXT = Color.rgb(235, 240, 236);

    private Ui() {}

    /** A scrolling page; returns the column to add views to. */
    static LinearLayout page(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(BG);
        LinearLayout list = column(a);
        int pad = dp(a, 32);
        list.setPadding(pad, dp(a, 24), pad, dp(a, 24));
        scroll.addView(list);
        a.setContentView(scroll);
        return list;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** a scrolling column inside a parent, filling it */
    static LinearLayout scrolling(LinearLayout parent, float weight, int width) {
        ScrollView s = new ScrollView(parent.getContext());
        LinearLayout col = column(parent.getContext());
        s.addView(col);
        parent.addView(s, new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT, weight));
        return col;
    }

    static TextView text(LinearLayout list, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(list.getContext());
        t.setText(s);
        size(t, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(list.getContext(), 4), 0, dp(list.getContext(), 4));
        list.addView(t);
        return t;
    }

    static Button button(LinearLayout list, String label, View.OnClickListener click) {
        return button(list, label, false, click);
    }

    static Button button(LinearLayout list, String label, boolean primary, View.OnClickListener click) {
        Context c = list.getContext();
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        size(b, 20);
        b.setTextColor(primary ? Color.rgb(10, 20, 12) : TEXT);
        if (primary) b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(c, primary ? ACCENT : PANEL2, primary ? 0 : LINE, 10));
        int p = dp(c, 20);
        b.setPadding(p, dp(c, 10), p, dp(c, 10));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), dp(c, 12), dp(c, 6));
        list.addView(b, lp);
        return b;
    }

    /** a rounded box: fill, optional border (0: none), radius in dp */
    static GradientDrawable box(Context c, int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(dp(c, 2), border);
        return d;
    }

    /** a status pill: a coloured dot and a short text, outlined in the same colour */
    static TextView pill(LinearLayout list, String s, int color) {
        Context c = list.getContext();
        TextView t = new TextView(c);
        t.setText("●  " + s);
        size(t, 17);
        t.setTextColor(TEXT);
        t.setBackground(box(c, PANEL2, color, 18));
        t.setPadding(dp(c, 14), dp(c, 6), dp(c, 16), dp(c, 6));
        android.text.SpannableString sp = new android.text.SpannableString(t.getText());
        sp.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, 0);
        t.setText(sp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 4), dp(c, 10), dp(c, 4));
        list.addView(t, lp);
        return t;
    }

    /** a small round dot */
    static View dot(Context c, int color, int sizeDp) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.setMargins(dp(c, 4), 0, dp(c, 16), 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** a line: label on the left, a switch on the right */
    static Switch switchRow(LinearLayout list, String label) {
        Context c = list.getContext();
        LinearLayout r = row(c);
        TextView t = new TextView(c);
        t.setText(label);
        size(t, 19);
        t.setTextColor(TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(c);
        r.addView(sw);
        r.setPadding(0, dp(c, 6), 0, dp(c, 6));
        list.addView(r);
        return sw;
    }

    /** monospace text on a dark box (logs) */
    static TextView mono(LinearLayout list) {
        Context c = list.getContext();
        TextView t = new TextView(c);
        t.setTypeface(Typeface.MONOSPACE);
        size(t, 15);
        t.setTextColor(Color.rgb(190, 200, 194));
        t.setBackground(box(c, Color.rgb(10, 13, 12), LINE, 8));
        int p = dp(c, 14);
        t.setPadding(p, p, p, p);
        t.setTextIsSelectable(true);
        list.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    static void line(LinearLayout list) {
        View v = new View(list.getContext());
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, dp(list.getContext(), 12), 0, dp(list.getContext(), 12));
        list.addView(v, lp);
    }

    static void gap(LinearLayout list, int dp) {
        View g = new View(list.getContext());
        g.setMinimumHeight(dp(list.getContext(), dp));
        list.addView(g);
    }

    /**
     * Sizes are for the head unit's wide screen (1920x720 at density 1.5): the numbers
     * in this app are scaled by K so the screens fit it as designed.
     */
    static final float K = 0.66f;

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density * K);
    }

    /** text size in "sp" as written in this app, scaled like dp */
    static void size(TextView t, float sp) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * t.getResources().getDisplayMetrics().scaledDensity * K);
    }

    /** Run off the UI thread, then show the result on it. */
    abstract static class Job<T> {
        abstract T work() throws Exception;

        abstract void done(T result, Exception error);

        void start(final Activity a) {
            new Thread("hmgapi-job") {
                @Override
                public void run() {
                    T r = null;
                    Exception err = null;
                    try {
                        r = work();
                    } catch (Exception e) {
                        err = e;
                    }
                    final T fr = r;
                    final Exception fe = err;
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!a.isFinishing()) done(fr, fe);
                        }
                    });
                }
            }.start();
        }
    }

    /** an owner switch kept by hmgapid (allow <name> on|off); inverted: the switch shows the opposite */
    static void allowSwitch(final Activity a, final Switch sw, final String name, final boolean inverted, final Runnable changed) {
        sw.setEnabled(false);
        new Job<Hmgapid.Result>() {
            @Override
            Hmgapid.Result work() throws Exception {
                return Hmgapid.call("allow", name);
            }

            @Override
            void done(Hmgapid.Result r, Exception e) {
                boolean on = e == null && r.code == 0 && r.output.trim().equals("on");
                sw.setChecked(inverted ? !on : on);
                sw.setEnabled(true);
                sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, final boolean checked) {
                        new Job<Hmgapid.Result>() {
                            @Override
                            Hmgapid.Result work() throws Exception {
                                return Hmgapid.call("allow", name, (checked ^ inverted) ? "on" : "off");
                            }

                            @Override
                            void done(Hmgapid.Result r, Exception e) {
                                if (e != null || r.code != 0) {
                                    android.widget.Toast.makeText(a, "Couldn't save it", android.widget.Toast.LENGTH_SHORT).show();
                                } else if (changed != null) {
                                    changed.run();
                                }
                            }
                        }.start(a);
                    }
                });
            }
        }.start(a);
    }
}
