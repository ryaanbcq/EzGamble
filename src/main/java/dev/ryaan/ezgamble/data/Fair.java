package dev.ryaan.ezgamble.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Provably fair, without a server: commit-reveal. A secret server seed is drawn first and only its SHA-256 is shown
 * (the host pastes it in chat himself, nothing is whispered for him). Every outcome then comes from
 * HMAC-SHA256(key = server seed, message = "clientSeed:nonce:cursor"), one round = one nonce, one number per cursor.
 * Revealing the seed lets anyone recompute every round and check the hash (verify.html). A new seed starts right after.
 * Only the outcomes use it (the drawn number, card order, bombs, crash point...); cosmetic jitter keeps its own RNG.
 */
public final class Fair {
    private Fair() {}

    public static final String VERIFY_URL = "https://ryaanbcq.github.io/EzGamble/verify.html";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("fair.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RNG = new SecureRandom();

    public record Draw(int nonce, String game, String result, long t) {}
    public record Spent(String seed, String hash, String client, int rounds, long at) {}

    /** What is saved: the live seed (secret until revealed), its rounds, and the seeds already revealed. */
    private static final class State {
        String seed, client; int nonce;
        List<Draw> draws = new ArrayList<>();
        List<Spent> past = new ArrayList<>();
    }

    private static State st;
    private static String hash;
    private static int ver;

    private static State st() {
        if (st != null) return st;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] fair.json unreadable, new seed: {}", e.toString()); }
        if (st == null || st.seed == null) { State old = st; st = new State(); if (old != null && old.past != null) st.past = old.past; st.seed = newSeed(); st.client = newClient(); }
        if (st.draws == null) st.draws = new ArrayList<>();
        if (st.past == null) st.past = new ArrayList<>();
        hash = sha256(st.seed);
        return st;
    }

    private static void save() {
        ver++;
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(st)); }
        catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] fair.json not saved: {}", e.toString()); }
    }

    /** Bumped on every change (the view rebuilds). */
    public static int ver() { return ver; }
    public static String hash() { st(); return hash; }
    public static String client() { return st().client; }
    public static int rounds() { return st().nonce; }
    public static List<Draw> draws() { return st().draws; }
    public static Spent lastSpent() { List<Spent> p = st().past; return p.isEmpty() ? null : p.get(p.size() - 1); }

    /** The client seed: what a viewer sends you to make sure you could not pick a seed that suits you. */
    public static void setClient(String c) {
        c = c == null ? "" : c.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
        if (c.isEmpty() || c.equals(st().client)) return;
        st.client = c.length() > 32 ? c.substring(0, 32) : c;
        save();
    }

    public static void randomClient() { st().client = newClient(); save(); }

    /** One round of a game: a nonce, then as many numbers as the game needs. */
    public static final class Round {
        public final int nonce; public final String game;
        private int cursor;
        private final String seed, client;
        Round(int nonce, String game, String seed, String client) { this.nonce = nonce; this.game = game; this.seed = seed; this.client = client; }
        /** The next number in [0, 1). */
        public double next() { return number(seed, client, nonce, cursor++); }
        /** The round's outcome, as the log and the verify page show it. */
        public void result(String r) { st().draws.add(new Draw(nonce, game, r, System.currentTimeMillis())); if (st.draws.size() > 500) st.draws.remove(0); save(); }
    }

    public static Round round(String game) { State s = st(); s.nonce++; return new Round(s.nonce, game, s.seed, s.client); }

    /** Reveals the live seed (returned, to paste in chat) and starts a new one. Do it between rounds. */
    public static Spent reveal() {
        State s = st();
        Spent sp = new Spent(s.seed, hash, s.client, s.nonce, System.currentTimeMillis());
        s.past.add(sp);
        if (s.past.size() > 50) s.past.remove(0);
        s.seed = newSeed(); s.nonce = 0; s.draws.clear();
        hash = sha256(s.seed);
        save();
        return sp;
    }

    // ---- the lines the host copies

    public static String commitText() { return "Provably fair: my next rolls use seed hash " + hash() + " with client seed " + client() + ". Send me a word to use as client seed, ask me for the reveal anytime."; }

    public static String revealText(Spent s) {
        String rounds = s.rounds() == 0 ? "no round" : s.rounds() == 1 ? "round 1" : "rounds 1-" + s.rounds();
        return "Provably fair reveal: seed " + s.seed() + " (hash " + s.hash() + "), client seed " + s.client() + ", " + rounds + ". Check: " + VERIFY_URL;
    }

    public static void copy(String text) { MinecraftClient.getInstance().keyboard.setClipboard(text); }

    // ---- the maths (verify.html does exactly the same)

    /** HMAC-SHA256(seed, "client:nonce:cursor"), the first 52 bits as a number in [0, 1). */
    public static double number(String seed, String client, int nonce, int cursor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((client + ":" + nonce + ":" + cursor).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 7; i++) v = (v << 8) | (h[i] & 0xFF);
            return (v >>> 4) / (double) (1L << 52);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String newSeed() { byte[] b = new byte[32]; RNG.nextBytes(b); return HexFormat.of().formatHex(b); }

    private static final String[] WORDS = {"lucky", "golden", "donut", "royal", "velvet", "neon", "silver", "wild", "midnight", "cherry", "diamond", "jade"};
    private static String newClient() { return WORDS[RNG.nextInt(WORDS.length)] + (1000 + RNG.nextInt(9000)); }
}
