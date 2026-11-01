package example.hello;

import hmgapi.hook.LoadedApp;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/** Example mod: shows where a mod's code runs and how it reads its settings. */
public final class Hello extends ModEntry {
    @Override
    public void zygoteInit(ModContext mod) {
        mod.log("zygote: " + mod.settingString("greeting", "?") + " (version " + mod.version + ")");
    }

    @Override
    public void appLoaded(ModContext mod, LoadedApp app) {
        if ("com.android.launcher3".equals(app.packageName)) {
            mod.log("launcher started: " + mod.settingString("greeting", "?"));
        }
    }
}
