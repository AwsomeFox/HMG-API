package dev.hmgapi.example.carcheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import hmgapi.client.HmgApi;

/** HMG-API test mod: what HMG-API delivers, on screen (the car's notice) and in the log (tag CarCheck). */
public class Events extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String text;
        if (HmgApi.ACTION_PROJECTION.equals(i.getAction())) {
            byte[] data = i.getByteArrayExtra("data");
            text = "Projection: " + i.getStringExtra("event")
                    + (data != null ? " type 0x" + Integer.toHexString(i.getIntExtra("type", 0)) + ", " + data.length + " bytes"
                    : " " + i.getIntExtra("value", 0));
        } else {
            text = "Button: " + i.getStringExtra("hmgapi.key") + " " + i.getStringExtra("hmgapi.press");
        }
        Log.i("CarCheck", text);
        HmgApi.toast(c, text);
    }
}
