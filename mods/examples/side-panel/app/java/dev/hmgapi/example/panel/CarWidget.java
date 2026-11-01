package dev.hmgapi.example.panel;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import hmgapi.client.HmgApi;

import java.util.Map;

/**
 * HMG-API example: a side panel page. It's a plain Android app widget; HMG-API shows
 * it in the right-hand panel because mod.json lists it under provides.panel. The
 * readings come from HMG-API (mod.json "vehicle"), through hmgapi-client.jar.
 * Tap it to refresh.
 */
public class CarWidget extends AppWidgetProvider {
    static final String REFRESH = "dev.hmgapi.example.panel.REFRESH";

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (REFRESH.equals(i.getAction())) refresh(c);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        refresh(c);
    }

    static void refresh(final Context c) {
        final Context app = c.getApplicationContext();
        new Thread() {   // hmgapid calls block: off the main thread
            @Override
            public void run() {
                String soc = "-", details;
                try {
                    Map<String, String> v = HmgApi.connect().vehicle("hv.soc", "battery12v.soc", "speed", "gear.park");
                    soc = v.get("hv.soc") + "%";
                    details = "12V " + v.get("battery12v.soc") + "%  ·  "
                            + ("1".equals(v.get("gear.park")) ? "Park" : v.get("speed") + " km/h");
                } catch (Exception e) {
                    details = "HMG-API: " + e.getMessage();
                }
                RemoteViews r = new RemoteViews(app.getPackageName(), R.layout.car_widget);
                r.setTextViewText(R.id.soc, soc);
                r.setTextViewText(R.id.details, details);
                r.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(app, 0,
                        new Intent(REFRESH).setClass(app, CarWidget.class), PendingIntent.FLAG_UPDATE_CURRENT));
                AppWidgetManager.getInstance(app).updateAppWidget(new ComponentName(app, CarWidget.class), r);
            }
        }.start();
    }
}
