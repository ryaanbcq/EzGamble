package dev.ryaan.ezgamble.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.ryaan.ezgamble.EzGambleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Sessions on disk (config/ezgamble/sessions/*.json, one per gambling mode ON) and the profile (config.json).
 * Writes are atomic and batched: 2 s after the last transaction, and when the mode turns off.
 */
public final class GambleData {
    private GambleData() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("ezgamble");
    private static final Path SESSIONS = DIR.resolve("sessions");

    public static final List<Session> sessions = new ArrayList<>();
    public static boolean mode;
    public static String name;
    public static boolean autoUpdate = false;   // "Auto update": download new versions by itself (off = the Update button asks)
    public static boolean lens = true;   // "Liquid glass": the lens on the tabs and the period selector (off = the sliding pill)   // what the mod calls the player (asked on the first opening)
    private static long dirtyAt;

    public static void load() {
        try {
            Path cfg = DIR.resolve("config.json");
            if (Files.exists(cfg)) {
                JsonObject o = GSON.fromJson(Files.readString(cfg), JsonObject.class);
                if (o.has("name")) name = o.get("name").getAsString();
                if (o.has("mode")) mode = o.get("mode").getAsBoolean();
                if (o.has("lens")) lens = o.get("lens").getAsBoolean();
                if (o.has("autoUpdate")) autoUpdate = o.get("autoUpdate").getAsBoolean();
            }
            if (Files.isDirectory(SESSIONS)) try (var files = Files.list(SESSIONS)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        Session s = GSON.fromJson(Files.readString(p), Session.class);
                        if (s.txns == null) s.txns = new ArrayList<>();
                        if (s.credit == null) s.credit = new java.util.LinkedHashMap<>();
                        if (s.sent == null) s.sent = new java.util.LinkedHashMap<>();
                        if (s.won == null) s.won = new java.util.LinkedHashMap<>();
                        if (s.payments == null) s.payments = new ArrayList<>();
                        if (s.sends == null) s.sends = new ArrayList<>();
                        s.file = p.getFileName().toString();
                        sessions.add(s);
                    } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] unreadable session {}", p); }
                }
            }
            sessions.sort(Comparator.comparingLong(s -> s.start));
            // the game closed while the mode was on: that session ended with its last bet
            for (Session s : sessions) if (s.end == null && s != current()) s.end = s.txns.isEmpty() ? s.start : s.txns.get(s.txns.size() - 1).t();
            if (mode && current() == null) newSession();
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] load failed: {}", e.toString());
        }
    }

    public static Session current() {
        if (sessions.isEmpty()) return null;
        Session s = sessions.get(sessions.size() - 1);
        return s.end == null ? s : null;
    }

    public static Session last() { return sessions.isEmpty() ? null : sessions.get(sessions.size() - 1); }

    public static List<Txn> all() {
        List<Txn> l = new ArrayList<>();
        for (Session s : sessions) l.addAll(s.txns);
        return l;
    }

    /** "session", "1h", "24h" or "all". */
    public static List<Txn> inRange(String range) {
        long t = System.currentTimeMillis();
        return switch (range) {
            case "session" -> last() == null ? List.of() : new ArrayList<>(last().txns);
            case "1h" -> all().stream().filter(x -> t - x.t() < 3_600_000L).toList();
            case "24h" -> all().stream().filter(x -> t - x.t() < 86_400_000L).toList();
            default -> all();
        };
    }

    /** When the last transaction was recorded (the HUD's toast). */
    public static long lastTxAt;

    public static void setMode(boolean on) {
        if (on == mode) return;
        mode = on;
        if (on) newSession();
        else { Session s = current(); if (s != null) { s.end = System.currentTimeMillis(); save(s); } }
        saveConfig();
        MinecraftClient mc = MinecraftClient.getInstance();
        dev.ryaan.ezgamble.proto.Snd.play(on ? "open" : "close", 0.8, 0.6);
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(on ? "Gambling mode ON" : "Gambling mode OFF")
                .withColor(on ? 0x4BE07A : 0xFF5C5C), false);
    }

    /** @return false when the mode is off: nothing is recorded. */
    public static boolean record(Txn tx) {
        Session s = current();
        if (!mode || s == null) return false;
        s.txns.add(tx);
        dirtyAt = System.currentTimeMillis();
        lastTxAt = System.currentTimeMillis();
        if (tx.delta() > 0) dev.ryaan.ezgamble.proto.Snd.play("pay", 1, 0.35);
        return true;
    }

    /** A session changed (credit, chips...): written 2 s later. */
    public static void touch() { dirtyAt = System.currentTimeMillis(); }

    /** Client tick: write the current session 2 s after its last change. */
    public static void tick() {
        if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt > 2000) {
            dirtyAt = 0;
            Session s = last();
            if (s != null) save(s);
        }
    }

    public static void setName(String n) { name = n; saveConfig(); }
    public static void setLens(boolean on) { lens = on; saveConfig(); }
    public static void setAutoUpdate(boolean on) { autoUpdate = on; saveConfig(); if (on) dev.ryaan.ezgamble.Updater.download(); }

    private static void newSession() {
        Session s = new Session();
        s.start = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        s.server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "";
        s.file = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(s.start))
                + (s.server.isEmpty() ? "" : "_" + s.server.replaceAll("[^A-Za-z0-9.-]", "_")) + ".json";
        sessions.add(s);
        save(s);
    }

    private static void save(Session s) { write(SESSIONS.resolve(s.file), GSON.toJson(s)); }

    private static void saveConfig() {
        JsonObject o = new JsonObject();
        if (name != null) o.addProperty("name", name);
        o.addProperty("mode", mode);
        o.addProperty("lens", lens);
        o.addProperty("autoUpdate", autoUpdate);
        write(DIR.resolve("config.json"), GSON.toJson(o));
    }

    private static void write(Path p, String json) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] write {} failed: {}", p, e.toString());
        }
    }
}
