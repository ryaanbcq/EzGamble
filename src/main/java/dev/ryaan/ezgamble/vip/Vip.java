package dev.ryaan.ezgamble.vip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.proto.Items2;
import dev.ryaan.ezgamble.proto.P;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loyalty tiers. A player's XP is what he wagered at the mod's tables, every session (derived from the transactions,
 * never stored: one source of truth; a player removed from the database restarts from zero). The XP puts him in a tier,
 * each tier gives a cashback rate the host owes on every bet placed while in that tier. Only what was PAID as cashback
 * is saved (config/ezgamble/vip.json), owed = earned - paid. Crossing a tier is celebrated (sound, overlay, HUD banner).
 */
public final class Vip {
    private Vip() {}

    public record Tier(int rank, String name, int color, double rate, long from, String perk) {
        public String rateText() { return rate == 0 ? "no cashback" : (rate * 100 == Math.rint(rate * 100) ? String.valueOf((long) (rate * 100)) : String.valueOf(rate * 100)) + "% cashback"; }
    }

    public static final Tier[] TIERS = {
            new Tier(0, "Bronze", 0xFFCD8B5A, 0, 0, "No perks yet"),
            new Tier(1, "Silver", 0xFFC7CCD6, 0.005, 25_000_000L, "0.5% cashback"),
            new Tier(2, "Gold", 0xFFF2C14E, 0.01, 100_000_000L, "1% cashback"),
            new Tier(3, "Platinum", 0xFF8FE3FF, 0.015, 500_000_000L, "1.5% cashback"),
            new Tier(4, "Diamond", 0xFFC77DFF, 0.02, 2_000_000_000L, "2% cashback")};

    /** One player's standing: wagered (XP), cashback earned so far, and his display name. */
    public static final class Standing { public String name; public long xp, earned; }
    /** A cashback /pay sent, confirmed later by the server. */
    public record Awaiting(String name, long amount, long at) {}
    public record TierUp(String name, Tier tier, long at) {}

    private static Map<String, Standing> standings;   // norm -> standing, rebuilt when dirty
    private static boolean dirty = true;
    private static TierUp lastTierUp;
    private static Awaiting awaiting;
    public static int ver;

    // ---- persistence: only what was paid as cashback

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("vip.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Payment(String name, long amount, long t) {}
    private static final class State { Map<String, Long> paid = new LinkedHashMap<>(); List<Payment> payments = new ArrayList<>(); }
    private static State st;

    private static State st() {
        if (st != null) return st;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] vip.json unreadable: {}", e.toString()); }
        if (st == null) st = new State();
        if (st.paid == null) st.paid = new LinkedHashMap<>();
        if (st.payments == null) st.payments = new ArrayList<>();
        return st;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st()), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] vip.json not saved: {}", e.toString()); }
    }

    // ---- the model

    public static Tier tierOf(long xp) {
        Tier t = null;
        for (Tier k : TIERS) if (xp >= k.from()) t = k;
        return t;
    }

    /** Every wager in time order folds into XP and the cashback earned at the rate of the tier he was in BEFORE the bet. */
    private static Map<String, Standing> standings() {
        if (standings != null && !dirty) return standings;
        Map<String, Standing> m = new LinkedHashMap<>();
        List<Txn> all = GambleData.all();   // sessions are sorted by start, each session's list is chronological
        for (Txn t : all) {
            if (t.player() == null || t.bet() == null || t.bet() <= 0 || t.t() <= GambleData.cutoff(t.player())) continue;
            Standing s = m.computeIfAbsent(Payouts.norm(t.player()), k -> { Standing n = new Standing(); n.name = t.player(); return n; });
            Tier before = tierOf(s.xp);
            s.earned += (long) Math.floor(t.bet() * before.rate());
            s.xp += t.bet();
        }
        standings = m; dirty = false;
        return m;
    }

    public static void invalidate() { dirty = true; ver++; }

    private static Standing of(String name) { return name == null ? null : standings().get(Payouts.norm(name)); }

    /** His tier, or null when he never wagered. */
    public static Tier tier(String name) { Standing s = of(name); return s == null ? null : tierOf(s.xp); }
    public static long xp(String name) { Standing s = of(name); return s == null ? 0 : s.xp; }
    public static Tier next(String name) { Tier t = tier(name); return t == null || t.rank() + 1 >= TIERS.length ? null : TIERS[t.rank() + 1]; }

    /** 0..1 inside the current tier (1 at Diamond). */
    public static double progress(String name) {
        Tier t = tier(name), n = next(name);
        if (t == null) return 0;
        if (n == null) return 1;
        return Math.max(0, Math.min(1, (xp(name) - t.from()) / (double) (n.from() - t.from())));
    }

    public static long paidCashback(String name) { return name == null ? 0 : st().paid.getOrDefault(Payouts.norm(name), 0L); }
    public static long owed(String name) { Standing s = of(name); return s == null ? 0 : Math.max(0, s.earned - paidCashback(name)); }

    /** Everyone with a tier, Diamond first then by XP. */
    public static List<Standing> ladder() {
        List<Standing> l = new ArrayList<>(standings().values());
        l.sort(Comparator.comparingLong((Standing s) -> -s.xp));
        return l;
    }

    public static long totalOwed() { long o = 0; for (Standing s : standings().values()) o += owed(s.name); return o; }

    // ---- cashback payments

    public static Awaiting awaiting() { return awaiting; }
    public static boolean paying(String name) { return awaiting != null && Payouts.same(awaiting.name(), name); }

    /** One /pay of everything owed: counted as paid only once the server confirms it (tick). Never touches his chips. */
    public static boolean pay(String name) {
        long o = owed(name);
        if (o <= 0 || awaiting != null) return false;
        if (Payouts.pending.stream().anyMatch(p -> Payouts.same(p.name(), name))) return false;
        awaiting = new Awaiting(name, o, P.now());
        Payouts.send(name, o, true);
        ver++;
        return true;
    }

    /** Debug / tests: as if the server had confirmed a cashback. */
    public static void markPaid(String name, long amount) {
        st().paid.merge(Payouts.norm(name), amount, Long::sum);
        st.payments.add(new Payment(name, amount, P.now()));
        if (st.payments.size() > 500) st.payments.remove(0);
        save(); ver++;
    }

    public static void tick() {
        Awaiting a = awaiting;
        if (a != null) {
            Payouts.Recent r = Payouts.recent.get(a.name());
            boolean pend = Payouts.pending.stream().anyMatch(p -> Payouts.same(p.name(), a.name()));
            if (r != null && r.at() >= a.at() && !pend) {   // answered: paid for real, or refused (nothing changes)
                if (r.ok()) markPaid(a.name(), a.amount());
                awaiting = null; ver++;
            } else if (!pend && P.now() - a.at() > 20_000) { awaiting = null; ver++; }
        }
        if (lastTierUp != null && P.now() - lastTierUp.at() > 3200) lastTierUp = null;
    }

    // ---- tier-ups

    /** Called by GambleData.record once the bet is in the session: it may have lifted its player into the next tier. */
    public static void onTxn(Txn t) {
        invalidate();
        if (t.player() == null || t.bet() == null || t.bet() <= 0 || t.t() <= GambleData.cutoff(t.player())) return;
        long xpAfter = xp(t.player()), xpBefore = xpAfter - t.bet();   // the transaction is already counted
        Tier after = tierOf(xpAfter);
        if (xpBefore <= 0) return;   // his very first bet: Bronze, no fanfare
        if (after.rank() <= tierOf(xpBefore).rank()) return;
        celebrate(t.player(), after);
    }

    private static void celebrate(String name, Tier tier) {
        lastTierUp = new TierUp(name, tier, P.now());
        P.play("mc_levelup", 1, 0.8);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(name + " is now " + tier.name()).withColor(tier.color() & 0xFFFFFF), false);
        if (mc.currentScreen == null && mc.gameRenderer != null) mc.gameRenderer.showFloatingItem(P.shiny(Items2.stack("nether_star")));
    }

    public static TierUp lastTierUp() { return lastTierUp; }

    public static String describe(String name) {
        Tier t = tier(name);
        return t == null ? "no tier" : t.name() + " xp=" + xp(name) + " progress=" + String.format(java.util.Locale.ROOT, "%.2f", progress(name)) + " owed=" + owed(name) + " paid=" + paidCashback(name);
    }
}
