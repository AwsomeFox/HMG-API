package hmgapi.hook;

import java.util.ArrayList;
import java.util.List;

/** Drives KeyPresses with a fake clock: what fires, and what the car gets back. */
public final class KeyPressesTest {
    static final class Clock implements KeyPresses.Out {
        long now;
        final List<Object[]> timers = new ArrayList<Object[]>();   // {at, runnable}
        final List<String> log = new ArrayList<String>();

        public void fire(String press) { log.add("fire " + press); }

        public void replay(List<Object> events) { log.add("car " + events); }

        public void later(Runnable r, long ms) { timers.add(new Object[] { now + ms, r }); }

        public void cancel(Runnable r) {
            for (int i = timers.size() - 1; i >= 0; i--) if (timers.get(i)[1] == r) timers.remove(i);
        }

        void advance(long ms) {
            long end = now + ms;
            while (true) {
                Object[] next = null;
                for (Object[] t : timers) if ((Long) t[0] <= end && (next == null || (Long) t[0] < (Long) next[0])) next = t;
                if (next == null) break;
                timers.remove(next);
                now = (Long) next[0];
                ((Runnable) next[1]).run();
            }
            now = end;
        }
    }

    static int fail;

    static void check(String what, String want, List<String> got) {
        String g = String.join(" | ", got);
        if (want.equals(g)) System.out.println("ok    " + what);
        else {
            System.out.println("FAIL  " + what + ": wanted [" + want + "], got [" + g + "]");
            fail = 1;
        }
    }

    static KeyPresses k(Clock c, String... binds) {
        KeyPresses k = new KeyPresses(c);
        for (String b : binds) k.bind(b.replace("!", ""), b.endsWith("!"));   // "x3!": replaces the car's own action
        return k;
    }

    static void tap(Clock c, KeyPresses k, String name) {
        k.down(0, name + "d");
        c.advance(100);
        k.up(name + "u");
        c.advance(100);
    }

    public static void main(String[] a) {
        Clock c = new Clock();
        KeyPresses k = k(c, "short");
        tap(c, k, "1");
        check("keep short: fires at release, no wait; the car keeps the press", "fire short", c.log);

        c = new Clock(); k = k(c, "short!");
        tap(c, k, "1"); c.advance(1000);
        check("replace short: fires, the car gets nothing", "fire short", c.log);

        c = new Clock(); k = k(c, "short!");
        tap(c, k, "1"); tap(c, k, "2");
        check("two quick presses are two short presses (no multi-press)", "fire short | fire short", c.log);

        c = new Clock(); k = k(c, "hold!");
        k.down(0, "1d"); c.advance(900); k.down(1, "1r"); k.up("1u"); c.advance(1000);
        check("hold: fires at 0.8 s, the car never sees the press", "fire hold | fire release", c.log);

        c = new Clock(); k = k(c, "hold!");
        tap(c, k, "1");
        check("hold, a tap: the car gets the tap at release, no other wait", "car [1d, 1u]", c.log);

        c = new Clock(); k = k(c, "hold!");
        tap(c, k, "1"); tap(c, k, "2");
        check("hold, two taps: each reaches the car at its release", "car [1d, 1u] | car [2d, 2u]", c.log);

        c = new Clock(); k = k(c, "hold!", "short");
        tap(c, k, "1");
        check("hold and a keeping short on one key: the tap fires short, then reaches the car", "fire short | car [1d, 1u]", c.log);

        c = new Clock(); k = k(c, "short!");
        k.down(0, "1d"); c.advance(900); k.down(1, "1r"); k.up("1u"); c.advance(1000);
        check("replace short, a long press: not the short press; the car gets it at 0.8 s, then the rest as it comes",
                "car [1d] | car [1r] | car [1u]", c.log);

        c = new Clock(); k = k(c, "short!");
        tap(c, k, "1"); k.down(0, "2d"); c.advance(900); k.up("2u");
        check("... a tap and then a long press", "fire short | car [2d] | car [2u]", c.log);

        c = new Clock(); k = k(c, "short");
        k.down(0, "1d"); c.advance(900); k.down(1, "1r"); k.up("1u"); c.advance(1000);
        check("keep short, a long press: not a short press, nothing fires", "", c.log);

        c = new Clock(); k = k(c, "hold!");
        k.down(0, "1d"); c.advance(900); k.up("1u");
        check("a hold's end is told (a binding's release)", "fire hold | fire release", c.log);

        c = new Clock(); k = k(c, "short!");
        k.down(0, "1d"); c.advance(400); k.up("1u");
        check("taken short: the long press goes on at 0.3 s", "car [1d] | car [1u]", c.log);

        c = new Clock(); k = k(c, "down");
        k.down(0, "1d"); k.down(1, "1r"); k.down(2, "1r"); k.up("1u"); c.advance(1000);
        check("down: every press and repeat", "fire down | fire down | fire down", c.log);

        c = new Clock(); k = k(c, "hold!");
        k.down(0, "1d"); c.advance(300); k.up("1u"); c.advance(100); k.down(0, "2d"); c.advance(900); k.up("2u"); c.advance(1000);
        check("a hold after a tap: the tap reached the car, the hold is taken", "car [1d, 1u] | fire hold | fire release", c.log);
        System.exit(fail);
    }
}
