package hmgapi.engine;

/** Exercises the boot keeper's radio checks without depending on interface addresses. */
public final class WifiApTest {
    public static final class Wifi {
        int ap, client, mode, starts;
        int nextAp = -1, startMode = 2;
        boolean savedClient;

        Wifi(int ap, int client, int mode) {
            this.ap = ap;
            this.client = client;
            this.mode = mode;
        }

        public int getWifiApEnabledState() {
            int state = ap;
            if (nextAp != -1) {
                ap = nextAp;
                nextAp = -1;
            }
            return state;
        }

        public int getWifiApMode() { return mode; }
        public int getWifiEnabledState() { return client; }
        public boolean getPersistedWifiEnabled() { return savedClient; }

        public void setWifiApEnabledForConnectivity(Object config, boolean enabled) {
            if (!enabled) throw new AssertionError("boot keeper stopped an AP");
            starts++;
            ap = 13;
            mode = startMode;
        }
    }

    private static void check(String name, Wifi wifi, int status, int starts) throws Exception {
        int result = WifiAp.ensureProjection(Wifi.class, wifi, Object.class);
        if (result != status || wifi.starts != starts) {
            throw new AssertionError(name + ": status " + result + ", starts " + wifi.starts);
        }
        System.out.println("ok    " + name);
    }

    public static void main(String[] args) throws Exception {
        check("personal hotspot with no IP address is left alone", new Wifi(13, 1, 1), 0, 0);
        check("projection AP already on is left alone", new Wifi(13, 1, 2), 0, 0);
        check("AP enabling is left to finish", new Wifi(12, 1, 0), 1, 0);
        check("AP disabling is left to finish", new Wifi(10, 1, 1), 1, 0);
        check("active Wi-Fi client keeps the radio", new Wifi(11, 3, 0), 0, 0);
        check("client enabling is left to finish", new Wifi(11, 2, 0), 1, 0);
        Wifi restoringClient = new Wifi(11, 1, 0);
        restoringClient.savedClient = true;
        check("saved Wi-Fi client can restore at boot", restoringClient, 0, 0);
        Wifi startingAp = new Wifi(11, 1, 0);
        startingAp.nextAp = 12;
        check("factory AP start between reads is left alone", startingAp, 1, 0);
        check("idle radio starts mode 2 once", new Wifi(11, 1, 0), 0, 1);
        Wifi wrongMode = new Wifi(11, 1, 0);
        wrongMode.startMode = 1;
        check("start only succeeds when mode 2 comes up", wrongMode, 1, 1);
    }
}
