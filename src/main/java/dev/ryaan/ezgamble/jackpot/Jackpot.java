package dev.ryaan.ezgamble.jackpot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Fair;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.proto.P;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Progressive jackpot shared by every mod table. The house feeds it: a slice (1 percent by default) of every bet placed
 * with a player's chips goes into the pot, paid by the host on top of the chips (House.addOwed counts it), and every
 * such bet has one provably fair draw at the whole pot. On a hit the pot lands in the player's chips (session.won), the
 * loss is recorded as a "Jackpot" transaction, the pot restarts at the seed. Persisted in config/ezgamble/jackpot.json.
 *
 * Odds: chance = bet / 20B, capped at 2 percent, so a $1M bet is 1 in 20 000, $100M is 1 in 200, $400M and up 1 in 50.
 * Expected cost per $1B wagered: the slice is the only money the house puts in ($10M per $1B at 1 percent), plus the
 * seed once per hit (hits per $1B = 1B / 20B = 0.05, so $250K at a $5M seed). The odds never change what the jackpot
 * costs, only how often the pot is emptied: fed by $1M bets it is hit about every $20B wagered, $200M over the seed.
 */
public final class Jackpot {
    private Jackpot() {}

    /** Chance per dollar bet: 1 in 20 000 per $1M. Capped at 2 percent so a whale never buys the pot. */
    public static final double PER_DOLLAR = 1 / 20e9, MAX_CHANCE = 0.02;
    public static final long DEFAULT_SEED = 5_000_000L;
    public static final double[] SLICES = {0.005, 0.01, 0.02};
    public static final int GOLD = 0xFFF2C14E;

    public record Hit(String name, long amount, long t, int nonce) {}

    private static final class State {
        long pot = DEFAULT_SEED, seed = DEFAULT_SEED;
        double slice = 0.01;
        boolean enabled = true;
        List<Hit> hits = new ArrayList<>();
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("jackpot.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static State st;
    /** Bumped on every change (the view rebuilds, the HUD pulses). */
    public static int ver;
    /** The last hit (the finale, the HUD): when it landed, 0 = none this run. */
    private static Hit last;
    private static long lastAt;
    private static long dirtyAt;

    private static State st() {
        if (st != null) return st;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] jackpot.json unreadable: {}", e.toString()); }
        if (st == null) st = new State();
        if (st.hits == null) st.hits = new ArrayList<>();
        if (st.seed <= 0) st.seed = DEFAULT_SEED;
        if (st.pot < 0) st.pot = 0;
        boolean known = false;
        for (double s : SLICES) if (Math.abs(s - st.slice) < 1e-9) known = true;
        if (!known) st.slice = 0.01;   // an edited file: back to the default slice, never a made-up percentage
        return st;
    }

    /** A change: the views and the HUD redraw now, the file follows 2 s later (onTxn runs for every bet: never one write per bet). */
    private static void save() { ver++; dirtyAt = System.currentTimeMillis(); }

    /** Client tick: jackpot.json 2 s after its last change. */
    public static void tick() { if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt > 2000) { dirtyAt = 0; write(); } }

    /** Whatever waits goes to disk now (GambleData.setMode off: a session end never leaves the pot unsaved). */
    public static void flush() { if (dirtyAt != 0) { dirtyAt = 0; write(); } }

    private static void write() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st()), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] jackpot.json not saved: {}", e.toString()); }
    }

    // ---- the figures

    public static long pot() { return st().pot; }
    public static long seed() { return st().seed; }
    public static double slice() { return st().slice; }
    public static boolean enabled() { return st().enabled; }
    public static List<Hit> hits() { return st().hits; }
    public static Hit lastHit() { return last; }
    public static long lastHitAt() { return lastAt; }

    /** What the house owes on top of the chips: the pot, whoever hits it next (House.addOwed). */
    public static long owed() { return st().enabled ? st().pot : 0; }

    /** The chance of one bet, in [0, 0.02]. */
    public static double chance(long bet) { return Math.max(0, Math.min(MAX_CHANCE, bet * PER_DOLLAR)); }

    /** "1 in 20 000" for a bet. */
    public static String oddsText(long bet) {
        double p = chance(bet);
        if (p <= 0) return "no chance";
        return "1 in " + String.format(java.util.Locale.ROOT, "%,d", Math.round(1 / p)).replace(',', ' ');
    }

    /** "1%", "0.5%". */
    public static String sliceText(double s) { double pc = s * 100; return (pc == Math.rint(pc) ? String.valueOf((long) pc) : String.valueOf(pc)) + "%"; }
    public static String sliceText() { return sliceText(slice()); }

    // ---- the host's settings

    public static void setSeed(long v) { if (v <= 0) return; st().seed = v; if (st.pot < v) st.pot = v; save(); }   // the pot never shows less than what it restarts at
    public static void setSlice(double s) { st().slice = s; save(); }
    public static void setEnabled(boolean on) { st().enabled = on; save(); }
    /** Debug: the pot itself. */
    public static void setPot(long v) { st().pot = Math.max(0, v); save(); }

    // ---- the draw

    /**
     * GambleData.record, after the bet is in the session: only bets placed with a player's chips (player != null,
     * bet > 0) feed the pot and draw. The Jackpot transaction itself has bet 0, so it never comes back here.
     */
    public static void onTxn(Txn t) {
        if (t.player() == null || t.bet() == null || t.bet() <= 0 || !st().enabled) return;
        long bet = t.bet();
        st.pot += Math.round(bet * st.slice);   // the house's slice, from his edge, on top of the chips
        double p = chance(bet);
        var fr = Fair.round("Jackpot");   // one nonce, one number: the verify page recomputes u and compares it to the chance
        double u = fr.next();
        boolean hit = u < p;
        long won = st.pot;
        fr.result(hit ? "hit " + won : "miss");
        if (!hit) { save(); return; }
        award(t.player(), fr.nonce, t.t());
    }

    /** Debug / smoke test: a forced hit for NAME, logged as such in the Fair rounds. */
    public static void forceHit(String name) {
        if (name == null || name.isEmpty() || GambleData.current() == null) return;
        var fr = Fair.round("Jackpot");
        fr.next();
        fr.result("hit " + st().pot + " (forced)");
        award(name, fr.nonce, System.currentTimeMillis());
    }

    /** The pot into his chips, the loss on the books, the pot back to the seed, the hit in the history, the finale. */
    private static void award(String name, int nonce, long at) {
        long won = st().pot;
        if (won <= 0) return;
        Session s = GambleData.current();
        if (s == null) return;
        s.addWon(name, won);   // his chips: the only place the money moves to (Payouts sends it back like any win)
        st.pot = st.seed;
        Hit h = new Hit(name, won, P.now(), nonce);
        st.hits.add(h);
        if (st.hits.size() > 200) st.hits.remove(0);
        save();
        last = h; lastAt = P.now();
        // the house side: a loss of the pot, bet 0 so onTxn ignores it (no recursion), its VIP XP is nil
        GambleData.record(new Txn(at, "Jackpot", 0L, -won, name, "jackpot hit"));
        JackpotFinale.start(h);
        EzGambleMod.LOG.info("[ezgamble] jackpot: {} hits {} (round #{})", name, Money.format(won), nonce);
    }

    /** Debug: the figures in one line. */
    public static String describe() {
        return "pot " + pot() + " seed " + seed() + " slice " + sliceText() + " enabled " + enabled() + " hits " + hits().size()
                + " odds: $1M " + oddsText(1_000_000L) + ", $100M " + oddsText(100_000_000L) + ", $1B " + oddsText(1_000_000_000L);
    }
}
