package example.hellouser;

import example.helloapi.api.Greeter;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/** Uses hello-api's Greeter in the launcher, and again whenever its setting changes. */
public final class HelloUser extends ModEntry {
    @Override
    public void appLoaded(ModContext mod, LoadedApp app) {
        if ("com.android.launcher3".equals(app.packageName)) greet(mod, "launcher started");
    }

    @Override
    public void onSettingsChanged(ModContext mod) {
        greet(mod, "setting changed");
    }

    @Override
    public void onEnabled(ModContext mod, boolean enabled) {
        mod.log(enabled ? "turned on" : "turned off");
    }

    static void greet(ModContext mod, String why) {
        Greeter g = (Greeter) mod.use("hello-api", "greeter");
        mod.log(why + ": " + (g == null ? "hello-api published nothing" : g.greet(mod.settingString("who", "?"))));
    }
}
