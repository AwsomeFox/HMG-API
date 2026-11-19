package hmgapi.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * engine problem record [--crash-dumps] [--usb] [--seconds N] | stop: the problem log, for an
 * owner with an issue (a crash, a feature that stops) to send to whoever is helping them.
 * Separate from the Car Report: the owner starts it, makes the problem happen, stops it.
 *
 * What it keeps, and nothing else (the app's "Record a problem" page says the same):
 *  - the head unit (model, region, firmware, platform) and HMG-API's version;
 *  - logcat lines (main and system buffers, never radio or events) from the tags in
 *    TAGS/PREFIXES only: crashes, CarPlay, Android Auto, audio, Bluetooth connections, Wi-Fi hotspot, HMG-API
 *    and its mods. Everything else is read and dropped, only counted. Every kept line is
 *    scrubbed (Report.scrub + PHONE_IDS);
 *  - each installed mod (id, version, on, whether it supports this car) and the md5 of
 *    the system libraries its native patches are for (stock files, model-level);
 *  - HMG-API's recent errors (as in the Car Report);
 *  - with --crash-dumps: the newest crash dumps in /data/tombstones (an exception to
 *    "nothing from /data", which the owner turns on knowingly): per dump only the
 *    crashed process, signal, registers, backtraces and the library map near the fault.
 *    The raw memory (stack, "memory near", "code around") and the process's own log
 *    lines in the dump are dropped.
 *
 * Stops on "engine problem stop" (state/problem.stop), after --seconds (default and most
 * MAX_SECONDS), or at MAX_BYTES of kept lines. Writes public/reports/problem-*.txt (and
 * "HMG-API reports" on USB sticks with --usb); prints "problem: <name>", "kept: ...",
 * "usb: ..." lines.
 */
final class ProblemLog {
    private ProblemLog() {}

    static final int MAX_SECONDS = 1800;   // 30 min: a session can drop seconds or minutes in
    static final int MAX_BYTES = 16 << 20;
    static final int MAX_DUMPS = 5;
    static final int MAX_DUMP_LINES = 600;

    /** whole tags kept as they are (after scrubbing) */
    static final String[] TAGS = {
        "AndroidRuntime", "DEBUG", "libc", "dalvikvm", "HMG-API", "LeafiaAAC",
        "TinyALSA", "AudioFlinger", "AudioTrack", "AudioPolicyManagerBase", "audio_hw_primary", "AudioMixer",
        "MicomAudioService", "MicomAudioManager", "MediaFocusControl",
        "CODEC_IF", "CODEC_AACDEC", "ACodec", "OMXCodec", "OMXNodeInstance",
        "PhoneProjection", "GalReceiver", "hostapd", "dhcpcd", "WifiStateMachine", "SoftapController",
    };
    /** tags starting with these */
    static final String[] PREFIXES = {
        "CarPlay", "TccCarPlay", "DiPO", "iAP2", "Iap2", "AAProjection", "Gpm", "Gal", "TCC_OMX", "TCCCDK",
        "AudioService", "AudioManager", "BTCM",   // BTCMService, BTCMStateMachine: Bluetooth connects and drops
    };
    /** ActivityManager logs intents (with their data) too: only its lines about processes dying */
    static final Pattern AM = Pattern.compile("(?i)(has died|crash|anr in|not responding|force finishing|killing|start proc|low memory|died)");
    static final Pattern LINE = Pattern.compile("^\\S+ \\S+\\s+\\d+\\s+\\d+ ([VDIWEF]) (.*?)\\s*: ");

    /** what Report.scrub doesn't catch that phones send: device ids, UUIDs, GPS positions */
    static final Pattern[] PHONE_IDS = {
        Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"),   // UUIDs
        Pattern.compile("(?i)\\b0000[0-9a-f]{4}-[0-9a-f]{16}\\b"),                                // newer iPhone UDIDs
        Pattern.compile("(?i)\\b[0-9a-f]{24,}\\b"),                                                // older UDIDs, tokens
        Pattern.compile("-?\\b\\d{1,3}\\.\\d{4,}\\s*[,/ ]\\s*-?\\d{1,3}\\.\\d{4,}\\b"),           // latitude, longitude
    };

    static String scrub(String s) {
        for (Pattern p : PHONE_IDS) s = p.matcher(s).replaceAll("[removed]");   // first: Report's number patterns would split ids
        return Report.scrub(s);
    }

    /** a logcat line with its message scrubbed (the date, pids and tag are logcat's own, and look like numbers) */
    static String scrubLine(String l, Matcher m) {
        return l.substring(0, m.end()) + scrub(l.substring(m.end()));
    }

    /** a dump's own format: registers, backtrace frames, map ranges (hex numbers only, nothing to scrub) */
    static final Pattern DUMP_FORMAT = Pattern.compile("^\\s+((r\\d+|ip|sp|lr|pc|cpsr|fp|sl|d\\d+|scr)\\s+[0-9a-f]{8}\\b.*|#\\d+\\s+pc\\s+[0-9a-f]{8}\\s.*|[0-9a-f]{8}-[0-9a-f]{8}\\s.*|\\(no map.*)$");

    /** the tag a line is kept under, or null */
    static boolean keep(String tag, String line) {
        for (String t : TAGS) if (t.equals(tag)) return true;
        for (String p : PREFIXES) if (tag.startsWith(p)) return true;
        return "ActivityManager".equals(tag) && AM.matcher(line).find();
    }

    static int run(Engine e, String[] args) throws Exception {
        String sub = args.length > 1 ? args[1] : "";
        File stop = new File(e.stateDir, "problem.stop");
        if ("stop".equals(sub)) {
            Engine.write(stop, "stop\n");
            e.out.add("stopping");
            return 0;
        }
        if (!"record".equals(sub)) {
            e.out.add("usage: engine problem record [--crash-dumps] [--usb] [--seconds N] | stop");
            return 2;
        }
        boolean dumps = false, usb = false;
        int seconds = MAX_SECONDS;
        for (int i = 2; i < args.length; i++) {
            if ("--crash-dumps".equals(args[i])) dumps = true;
            else if ("--usb".equals(args[i])) usb = true;
            else if ("--seconds".equals(args[i]) && i + 1 < args.length) seconds = Math.max(1, Math.min(MAX_SECONDS, Integer.parseInt(args[++i])));
        }
        stop.delete();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

        // logcat: read everything, keep only the allowlisted tags (what's dropped is never stored)
        final StringBuilder kept = new StringBuilder();
        final Map<String, Integer> perTag = new TreeMap<String, Integer>();
        final long[] counts = new long[2];   // seen, kept
        String logcat = e.root + "/system/bin/logcat";
        final Process p = new ProcessBuilder(logcat, "-v", "threadtime", "-b", "main", "-b", "system").redirectErrorStream(true).start();
        Thread reader = new Thread() {
            @Override
            public void run() {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                    String l;
                    while ((l = in.readLine()) != null) {
                        Matcher m = LINE.matcher(l);
                        synchronized (kept) {
                            counts[0]++;
                            if (!m.find() || !keep(m.group(2), l)) continue;
                            if (kept.length() >= MAX_BYTES) continue;
                            counts[1]++;
                            Integer n = perTag.get(m.group(2));
                            perTag.put(m.group(2), n == null ? 1 : n + 1);
                            kept.append(scrubLine(l, m)).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                    // logcat ended
                }
            }
        };
        reader.setDaemon(true);
        reader.start();
        long start = System.currentTimeMillis(), end = start + seconds * 1000L;
        String why = "time limit (" + seconds + " s)";
        while (System.currentTimeMillis() < end) {
            if (stop.exists()) { why = "stopped"; break; }
            synchronized (kept) {
                if (kept.length() >= MAX_BYTES) { why = "size limit"; break; }
            }
            if (!reader.isAlive()) { why = "logcat ended"; break; }
            Thread.sleep(250);
        }
        p.destroy();
        reader.join(2000);
        stop.delete();
        long took = (System.currentTimeMillis() - start) / 1000;

        StringBuilder s = new StringBuilder();
        s.append("HMG-API problem log ").append(stamp).append("\n");
        s.append("HMG-API ").append(Engine.HMGAPI_VERSION);
        String build = Report.installedBuild(e);
        if (build != null) s.append(" (build ").append(build).append(")");
        s.append("\nunit: ");
        for (String k : new String[] {"product", "model", "region", "firmware", "platform", "android"}) s.append(k).append('=').append(e.facts.get(k)).append(' ');
        s.append("\nrecorded: ").append(took).append(" s, ended by ").append(why).append("\n");
        s.append("crash dumps: ").append(dumps ? "included (trimmed: no memory contents)" : "not included").append("\n");
        s.append("\nThis file holds only what's listed above and below: logcat lines from crash, CarPlay, Android Auto,\n"
                + "audio, Wi-Fi hotspot and HMG-API tags (scrubbed of addresses, numbers and device ids), installed mods,\n"
                + "library checksums, HMG-API's errors").append(dumps ? ", and trimmed crash dumps" : "").append(".\n");

        s.append("\n== Mods\n");
        mods(e, s);
        s.append("\n== HMG-API errors\n");
        for (Object o : Report.errors(e)) s.append(o).append('\n');
        if (dumps) {
            s.append("\n== Crash dumps\n");
            crashDumps(e, s);
        }
        s.append("\n== Log (").append(counts[1]).append(" of ").append(counts[0]).append(" lines kept)\n");
        for (Map.Entry<String, Integer> t : perTag.entrySet()) s.append("  ").append(t.getKey()).append(": ").append(t.getValue()).append('\n');
        s.append('\n');
        synchronized (kept) {
            s.append(kept);
        }

        String name = "problem-" + Report.safe(e.facts.get("model")) + "-" + Report.safe(e.facts.get("region")) + "-"
                + Report.safe(e.facts.get("firmware")) + "-" + stamp;
        File dir = new File(e.data, "public/reports");
        dir.mkdirs();
        dir.setReadable(true, false);
        dir.setExecutable(true, false);
        File f = new File(dir, name + ".txt");
        Engine.write(f, s.toString());
        f.setReadable(true, false);
        e.out.add("problem: " + name);
        e.out.add("kept: " + counts[1] + " of " + counts[0] + " log lines, " + took + " s, ended by " + why);
        e.out.add("saved: " + f.getPath());
        if (usb) {
            boolean any = false;
            for (String u : Report.USB) {
                File root = new File(e.root + u);
                if (!root.isDirectory() || root.list() == null) continue;
                File d = new File(root, "HMG-API reports");
                if (!d.isDirectory() && !d.mkdirs()) continue;
                Engine.write(new File(d, name + ".txt"), s.toString());
                e.out.add("usb: " + u + "/HMG-API reports/" + name + ".txt");
                any = true;
            }
            if (!any) e.out.add("usb: no USB stick found (adb pull works too)");
        }
        return 0;
    }

    /** installed mods, and the md5 of each system library their native patches are for */
    @SuppressWarnings("unchecked")
    static void mods(Engine e, StringBuilder s) {
        File[] ds = new File(e.data, "mods").listFiles();
        if (ds == null) { s.append("(none)\n"); return; }
        Arrays.sort(ds);
        for (File d : ds) {
            File mf = new File(d, "mod.json");
            if (!mf.isFile()) continue;
            try {
                Map<String, Object> raw = Json.object(Engine.read(mf));
                String sys = raw.containsKey("system") ? SystemCheck.check(raw.get("system"), e.facts, SystemCheck.asksHotspot(raw)) : null;
                s.append(raw.get("id")).append(' ').append(raw.get("version")).append(new File(d, ".disabled").exists() ? " (off)" : " (on)")
                        .append(sys == null ? "" : ", not for this car (installed anyway)").append('\n');
                Object hooks = raw.get("hooks");
                Object nat = hooks instanceof Map ? ((Map<String, Object>) hooks).get("native") : null;
                if (nat instanceof Map) {
                    for (String lib : new TreeMap<String, Object>((Map<String, Object>) nat).keySet()) {
                        File l = new File(e.root + "/system/lib/" + new File(lib).getName());
                        s.append("  ").append(lib).append(' ').append(l.isFile() ? CarMap.md5(l) : "absent").append('\n');
                    }
                }
            } catch (Exception x) {
                s.append(d.getName()).append(": ").append(scrub(String.valueOf(x))).append('\n');
            }
        }
    }

    /** the newest tombstones, trimmed to what says where it crashed */
    static void crashDumps(Engine e, StringBuilder s) {
        File[] ts = new File(e.root + "/data/tombstones").listFiles();
        List<File> list = new ArrayList<File>();
        if (ts != null) for (File t : ts) if (t.isFile() && t.getName().startsWith("tombstone_")) list.add(t);
        if (list.isEmpty()) { s.append("(none on this unit)\n"); return; }
        java.util.Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = 0; i < list.size() && i < MAX_DUMPS; i++) {
            File t = list.get(i);
            s.append("--- ").append(t.getName()).append(" (").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(t.lastModified()))).append(")\n");
            try {
                s.append(trimDump(Engine.read(t)));
            } catch (Exception x) {
                s.append("(unreadable: ").append(scrub(String.valueOf(x))).append(")\n");
            }
        }
    }

    /** a tombstone without its raw memory and log lines: header, registers, backtraces, maps near the fault */
    static String trimDump(String text) {
        StringBuilder o = new StringBuilder();
        boolean on = true;
        int n = 0;
        for (String l : text.split("\n")) {
            String x = l.trim();
            if (x.startsWith("stack:") || x.startsWith("memory near") || x.startsWith("code around") || x.startsWith("---------")) on = false;
            else if (x.startsWith("backtrace:") || x.startsWith("memory map") || x.startsWith("--- --- ---") || x.startsWith("pid: ")
                    || x.startsWith("*** ") || x.startsWith("Build fingerprint") || x.startsWith("signal ")) on = true;
            if (!on) continue;
            if (++n > MAX_DUMP_LINES) { o.append("(cut at ").append(MAX_DUMP_LINES).append(" lines)\n"); break; }
            o.append(DUMP_FORMAT.matcher(l).matches() ? l : scrub(l)).append('\n');
        }
        return o.toString();
    }
}
