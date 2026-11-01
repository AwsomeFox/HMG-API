package example.helloapi.impl;

import example.helloapi.api.Greeter;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/** Publishes a Greeter for the mods that require hello-api (its impl package isn't visible to them). */
public final class HelloApi extends ModEntry {
    @Override
    public void zygoteInit(ModContext mod) {
        mod.publish("greeter", new Greeter() {
            @Override
            public String greet(String who) {
                return "hello, " + who + " (from hello-api " + mod.version + ")";
            }
        });
    }
}
