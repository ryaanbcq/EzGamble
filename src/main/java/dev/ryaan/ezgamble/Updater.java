package dev.ryaan.ezgamble;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Updates from the public GitHub repo: update/latest.json names the newest jar and its sha256.
 * The new jar is written next to the old one, the old one is removed when the game closes
 * (on Windows by a small cmd that waits for the game to release the file).
 */
public final class Updater {
    private Updater() {}

    private static final String BASE = "https://raw.githubusercontent.com/ryaanbcq/EzGamble/main/update/";
    /**
     * latest.json through the GitHub API: at most a minute old. The raw file above sits in GitHub's cache for 5 minutes
     * whatever the query string (checked: x-cache HIT with ?t=), so a new version took up to 15 minutes to show.
     * The API allows 60 requests an hour per address: one every 2 minutes, plus menu openings, and the raw file when it says no.
     */
    private static final String API = "https://api.github.com/repos/ryaanbcq/EzGamble/contents/update/latest.json";
    private static volatile String etag;
    private static volatile String lastBody;
    private static volatile long apiBlockedUntil;
    public enum State { CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

    private static volatile State state = State.CHECKING;
    private static volatile String latest = "", file = "", sha256 = "", error = "";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static State state() { return state; }
    public static String latest() { return latest; }
    public static String error() { return error; }

    public static String current() {
        return FabricLoader.getInstance().getModContainer("ezgamble")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }

    private static volatile long checkedAt;

    /** Opening the menu: look again if the last answer is more than 30 s old (a version published a minute ago shows). */
    public static void recheck() { recheck(30_000); }

    /** Every tick: a look every 2 minutes while the game runs, so an update shows up without opening anything. */
    public static void tick() { recheck(120_000); }

    private static void recheck(long olderThan) {
        if ((state == State.UP_TO_DATE || state == State.FAILED || state == State.AVAILABLE) && System.currentTimeMillis() - checkedAt > olderThan) check();
    }

    /** latest.json: the API when it answers (a 304 keeps the last body), else the raw file. */
    private static String fetchLatest() throws Exception {
        if (System.currentTimeMillis() >= apiBlockedUntil) {
            try {
                var b = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/vnd.github.raw+json").header("User-Agent", "EzGamble/" + current());
                if (etag != null && lastBody != null) b.header("If-None-Match", etag);
                HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 304 && lastBody != null) return lastBody;
                if (r.statusCode() == 200) { etag = r.headers().firstValue("etag").orElse(null); lastBody = r.body(); return lastBody; }
                apiBlockedUntil = System.currentTimeMillis() + 15 * 60_000;   // rate limited (403/429) or down: the raw file for a while
                EzGambleMod.LOG.info("[ezgamble] update check: GitHub API {}, raw file for 15 min", r.statusCode());
            } catch (Exception e) { apiBlockedUntil = System.currentTimeMillis() + 15 * 60_000; }
        }
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "latest.json"))
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return r.body();
    }

    /** Called at startup and by recheck(), off the main thread. */
    public static void check() {
        checkedAt = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            try {
                JsonObject o = JsonParser.parseString(fetchLatest()).getAsJsonObject();
                latest = o.get("version").getAsString();
                file = o.get("file").getAsString();
                sha256 = o.get("sha256").getAsString().toLowerCase(Locale.ROOT);
                if (!file.matches("ezgamble-[0-9A-Za-z.+-]+\\.jar")) throw new IllegalStateException("bad file name");
                if (state == State.DOWNLOADING || state == State.READY) return;   // a download started meanwhile: leave it alone
                State was = state;
                state = newer(latest, current()) ? State.AVAILABLE : State.UP_TO_DATE;
                if (was != state || state == State.AVAILABLE) EzGambleMod.LOG.info("[ezgamble] update check: current {}, latest {}", current(), latest);
                if (state == State.AVAILABLE && dev.ryaan.ezgamble.data.GambleData.autoUpdate) download();   // option "Auto update"
            } catch (Exception e) {
                error = e.getMessage();
                state = State.FAILED;
                EzGambleMod.LOG.warn("[ezgamble] update check failed: {}", e.toString());
            }
        }, "ezgamble-update");
        t.setDaemon(true);
        t.start();
    }

    /** Button "Update": download, verify, swap on exit. */
    public static void download() {
        if (state != State.AVAILABLE) return;
        state = State.DOWNLOADING;
        Thread t = new Thread(() -> {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + file))
                        .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
                byte[] jar = r.body();
                String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
                if (!got.equals(sha256)) throw new IllegalStateException("checksum mismatch");
                Path old = ownJar();
                Path mods = old != null ? old.getParent() : FabricLoader.getInstance().getGameDir().resolve("mods");
                Path dest = mods.resolve(file);
                Path tmp = mods.resolve(file + ".part");
                Files.write(tmp, jar);
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                if (old != null && !old.equals(dest)) removeOnExit(old);
                state = State.READY;
                EzGambleMod.LOG.info("[ezgamble] downloaded {} ({} bytes)", file, jar.length);
            } catch (Exception e) {
                error = e.getMessage();
                state = State.FAILED;
                EzGambleMod.LOG.warn("[ezgamble] update download failed: {}", e.toString());
            }
        }, "ezgamble-update");
        t.setDaemon(true);
        t.start();
    }

    private static Path ownJar() {
        return FabricLoader.getInstance().getModContainer("ezgamble")
                .flatMap(m -> m.getOrigin().getPaths().stream().findFirst())
                .filter(p -> p.toString().endsWith(".jar")).orElse(null);   // null in the dev environment
    }

    private static void removeOnExit(Path old) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows) {
            try { Files.deleteIfExists(old); return; } catch (Exception ignored) {}
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (windows) {
                    // retry for ~30 s until the game process has let go of the jar
                    String cmd = "for /l %i in (1,1,30) do (del /f /q \"" + old + "\" 2>nul & if not exist \"" + old + "\" exit & ping -n 2 127.0.0.1 >nul)";
                    new ProcessBuilder("cmd", "/c", cmd).start();
                } else Files.deleteIfExists(old);
            } catch (Exception ignored) {}
        }));
    }

    /** "0.1.10" > "0.1.9"; anything after a '-' or '+' is ignored. */
    static boolean newer(String a, String b) {
        String[] x = a.split("[-+]")[0].split("\\."), y = b.split("[-+]")[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }
}
