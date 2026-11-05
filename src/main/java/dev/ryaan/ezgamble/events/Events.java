package dev.ryaan.ezgamble.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.hud.Banners;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.vip.Vip;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Timed events the host runs, one at a time, 5 to 60 minutes:
 *   Wager race   whoever wagers the most with his chips during the event ranks on a live leaderboard; a prize pool the
 *                host pays 50 / 30 / 20 to the top three, one Pay click per winner (Payouts.send), never on its own
 *   Double XP    VIP XP counts twice for every bet placed during the event
 *   Happy hour   +1% cashback on every tier for every bet placed during the event
 * Double XP and Happy hour are reproducible from history: Vip asks xpMultiplierAt(t) / rateBonusAt(t) for every bet it
 * folds, and those look at the saved events. Everything is in config/ezgamble/events.json (the running event survives a
 * restart, the past events keep their top three and who was paid).
 */
public final class Events {
    private Events() {}

    public enum Kind {
        RACE("race", "Wager race", 0xFFF2C14E), XP("xp", "Double XP", 0xFF8FE3FF), HOUR("hour", "Happy hour", 0xFF4BE07A);
        public final String id, label; public final int color;
        Kind(String id, String label, int color) { this.id = id; this.label = label; this.color = color; }
        /** "race", "xp" or "hour" (a label works too); null when unknown. */
        public static Kind of(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(java.util.Locale.ROOT);
            for (Kind v : values()) if (v.id.equals(k) || v.label.toLowerCase(java.util.Locale.ROOT).equals(k)) return v;
            return k.startsWith("double") ? XP : k.startsWith("happy") ? HOUR : null;
        }
    }

    /** The shares of the prize pool, top to bottom. */
    public static final double[] SPLIT = {0.5, 0.3, 0.2};
    public static final int[] MINUTES = {5, 15, 30, 60};
    public static final int GOLD = 0xFFF2C14E, SILVER = 0xFFC7CCD6, BRONZE = 0xFFCD8B5A;
    public static final int[] MEDAL = {GOLD, SILVER, BRONZE};

    /** One event (Gson). top is filled when it ends: the three biggest wagers of a race, with their share and whether it was paid. */
    public static final class Event {
        public String kind; public long start, end, prize; public boolean cancelled;
        public List<Winner> top = new ArrayList<>();
        public Kind kind() { Kind k = Kind.of(kind); return k == null ? Kind.RACE : k; }
        public boolean live(long t) { return t >= start && t <= end; }
        public long minutes() { return Math.round((end - start) / 60000.0); }
    }
    /** payAt / payAmount: the Pay click (0 = never clicked); paid: the server confirmed that /pay. */
    public static final class Winner { public String name; public long wagered, share, payAt, payAmount; public boolean paid; }
    /** A live leaderboard line. */
    public record Entry(String name, long wagered, int bets) {}
    public record PayState(String label, boolean active, boolean done, boolean retry) {}

    private static final class State { Event current; List<Event> past = new ArrayList<>(); }
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("events.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static State st;
    /** Bumps on every change the views should redraw for (start, end, cancel, a Pay click, a confirmation). */
    public static int ver;
    /** When the last event ended (the HUD says "X is over" for a few seconds), and the race whose finale waits for a free screen. */
    public static long endedAt; public static Event lastEnded;
    private static Event pendingFinale;
    private static List<Entry> board; private static long boardAt;
    private static int payVer = -1;

    static {   // Vip folds every bet through these: Double XP and Happy hour are part of the history, not a live bonus
        Vip.xpHook = Events::xpMultiplierAt;
        Vip.rateHook = Events::rateBonusAt;
        Vip.invalidate();
    }

    // ---- persistence

    private static State st() {
        if (st != null) return st;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] events.json unreadable: {}", e.toString()); }
        if (st == null) st = new State();
        if (st.past == null) st.past = new ArrayList<>();
        for (Event e : st.past) if (e.top == null) e.top = new ArrayList<>();
        if (st.current != null && st.current.top == null) st.current.top = new ArrayList<>();
        return st;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st()), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] events.json not saved: {}", e.toString()); }
    }

    // ---- the model

    /** The running event, or null. */
    public static Event current() { return st().current; }
    /** Past events, the latest first. */
    public static List<Event> past() { List<Event> l = new ArrayList<>(st().past); java.util.Collections.reverse(l); return l; }
    /** Milliseconds left, 0 when nothing runs. */
    public static long left() { Event e = current(); return e == null ? 0 : Math.max(0, e.end - System.currentTimeMillis()); }

    /** Starts an event now; false when one is running, the duration is not 5 / 15 / 30 / 60 or a race has no valid prize. */
    public static boolean start(Kind kind, int minutes, long prize) {
        if (kind == null || current() != null) return false;
        boolean okMin = false;
        for (int m : MINUTES) okMin |= m == minutes;
        if (!okMin || (kind == Kind.RACE && prize <= 0)) return false;
        Event e = new Event();
        e.kind = kind.id; e.start = System.currentTimeMillis(); e.end = e.start + minutes * 60_000L; e.prize = kind == Kind.RACE ? prize : 0;
        st().current = e; board = null; save(); ver++;
        Vip.invalidate();
        P.play("mc_chime", 1, 0.8);
        overlay(kind.label + " started: " + minutes + " min" + (kind == Kind.RACE ? ", " + Money.format(prize) + " prize pool" : ""), kind.color);
        Banners.show(new Banners.Toast(null, List.of(Banners.seg(kind.label, kind.color), Banners.seg(" started, " + minutes + " min", P.WHITE)), kind.color, 3000, null));
        return true;
    }

    /** The host cancels: the event ends now with no winners and stays in the history as cancelled. */
    public static boolean cancel() {
        Event e = current();
        if (e == null) return false;
        e.end = System.currentTimeMillis(); e.cancelled = true;
        st().current = null; st().past.add(e); board = null; save(); ver++;
        Vip.invalidate();
        P.play("fail", 1, 0.5);
        overlay(e.kind().label + " cancelled", P.RED);
        Banners.show(new Banners.Toast(null, List.of(Banners.seg(e.kind().label + " cancelled", P.WHITE)), P.RED, 3000, null));
        return true;
    }

    /** Debug: the running event ends right now, as if its time were up. */
    public static void endNow() { Event e = current(); if (e != null) { e.end = System.currentTimeMillis(); finish(e); } }

    /** GambleData.record: a bet during the event changes the leaderboard at once. */
    public static void onTxn(Txn t) {
        Event e = current();
        if (e != null && t.player() != null && e.live(t.t())) board = null;
    }

    /** Client tick: the end of the event, the race finale once no screen is open, the Pay confirmations of past races. */
    public static void tick() {
        Event e = current();
        if (e != null && System.currentTimeMillis() >= e.end) finish(e);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (pendingFinale != null && mc.currentScreen == null && mc.player != null) { mc.setScreen(new RaceFinaleScreen(pendingFinale)); pendingFinale = null; }
        if (payVer != Payouts.ver) { payVer = Payouts.ver; confirmPayments(); }
    }

    private static void finish(Event e) {
        e.top.clear();
        List<Entry> b = fold(e);
        for (int i = 0; i < Math.min(3, b.size()); i++) {
            Winner w = new Winner(); w.name = b.get(i).name(); w.wagered = b.get(i).wagered();
            w.share = e.kind() == Kind.RACE ? (long) Math.floor(e.prize * SPLIT[i]) : 0;   // a share only for a rank somebody holds: the rest stays with the house
            e.top.add(w);
        }
        st().current = null; st().past.add(e); board = null; save(); ver++;
        Vip.invalidate();
        endedAt = P.now(); lastEnded = e;
        // the HUD's toast column: "Wager race is over, X wins" (the name trimmed: the pill must fit a 640 px screen)
        Banners.show(new Banners.Toast(null, List.of(Banners.seg(e.kind().label, e.kind().color),
                Banners.seg(" is over" + (e.kind() == Kind.RACE && !e.top.isEmpty() ? ", " + F.trimToWidth(e.top.get(0).name, 80) + " wins" : ""), P.WHITE)),
                e.kind().color, 3000, null));
        if (e.kind() == Kind.RACE && !e.top.isEmpty()) pendingFinale = e;   // the podium opens when no screen is up (the menu closing included)
        else { P.play("mc_chime", 0.7, 0.8); overlay(e.kind().label + " is over" + (e.kind() == Kind.RACE ? ", nobody wagered" : ""), e.kind().color); }
    }

    private static void overlay(String s, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(s).withColor(color & 0xFFFFFF), false);
    }

    // ---- the leaderboard

    /** Wagers placed with a player's chips inside the event, biggest first (cached 500 ms, refreshed by every bet). */
    public static List<Entry> board() {
        Event e = current();
        if (e == null) return List.of();
        long t = P.now();
        if (board != null && t - boardAt < 500) return board;
        board = fold(e); boardAt = t;
        return board;
    }

    private static List<Entry> fold(Event e) {
        Map<String, Object[]> m = new LinkedHashMap<>();   // norm -> {name, wagered, bets}
        for (Txn t : GambleData.all()) {
            if (t.player() == null || t.bet() == null || t.bet() <= 0 || !e.live(t.t()) || t.t() <= GambleData.cutoff(t.player())) continue;
            Object[] r = m.computeIfAbsent(Payouts.norm(t.player()), k -> new Object[]{t.player(), 0L, 0});
            r[1] = (long) r[1] + t.bet(); r[2] = (int) r[2] + 1;
        }
        List<Entry> out = new ArrayList<>();
        for (Object[] r : m.values()) out.add(new Entry((String) r[0], (long) r[1], (int) r[2]));
        out.sort(Comparator.comparingLong((Entry x) -> -x.wagered()));
        return out;
    }

    // ---- Vip hooks: the history replays the same way, event or not

    // the past events as sorted intervals per kind, rebuilt when ver moves: Vip asks for every bet it folds, thousands at a time
    private static int ivVer = -1;
    private static long[][] ivStart, ivEnd;

    private static void intervals() {
        if (ivVer == ver && ivStart != null) return;
        ivVer = ver;
        Kind[] kinds = Kind.values();
        ivStart = new long[kinds.length][]; ivEnd = new long[kinds.length][];
        for (Kind k : kinds) {
            List<Event> l = new ArrayList<>();
            for (Event e : st().past) if (e.kind() == k) l.add(e);
            l.sort(Comparator.comparingLong(e -> e.start));   // one event at a time: sorted by start, they never overlap
            long[] a = new long[l.size()], b = new long[l.size()];
            for (int i = 0; i < a.length; i++) { a[i] = l.get(i).start; b[i] = l.get(i).end; }
            ivStart[k.ordinal()] = a; ivEnd[k.ordinal()] = b;
        }
    }

    private static boolean covers(Kind k, long t) {
        Event c = current();
        if (c != null && c.kind() == k && c.live(t)) return true;
        intervals();
        long[] a = ivStart[k.ordinal()];
        int i = Arrays.binarySearch(a, t);
        if (i < 0) i = -i - 2;   // the last event of that kind started at or before t
        return i >= 0 && t <= ivEnd[k.ordinal()][i];   // a cancelled event ends at the cancel: it still covered the bets before it
    }

    /** 2 during a Double XP, else 1. */
    public static double xpMultiplierAt(long t) { return covers(Kind.XP, t) ? 2 : 1; }
    /** +1% during a Happy hour, else 0. */
    public static double rateBonusAt(long t) { return covers(Kind.HOUR, t) ? 0.01 : 0; }

    // ---- paying a race winner (one /pay per winner, by hand)

    /** Payouts' timeout: past it with no answer the button offers a retry (also right after a restart, when nothing is remembered). */
    private static final long TIMEOUT = 8000;

    /** What the Pay button of a winner shows, mirroring Widgets.payStateOf; null when he has no share. */
    public static PayState payState(Winner w) {
        if (w.share <= 0) return null;
        if (w.paid) return new PayState("Paid " + Money.format(w.payAmount), false, true, false);
        if (w.payAt == 0) return new PayState("Pay " + Money.format(w.share), true, false, false);
        if (Payouts.pending.stream().anyMatch(p -> Payouts.same(p.name(), w.name))) return new PayState("Sending...", false, false, false);
        Payouts.Recent r = Payouts.recent.get(w.name);
        if (r != null && r.at() >= w.payAt && !r.ok()) return new PayState((r.why() != null ? r.why() : "Not confirmed") + ", retry", true, false, true);
        if (r == null && P.now() - w.payAt > TIMEOUT) return new PayState("Not confirmed, retry", true, false, true);
        return new PayState("Sending...", false, false, false);
    }

    /**
     * The prize first becomes his chips (session.addWon, like a table win), then one /pay sends them back: both sides of
     * the chips model move by the same amount, and a /pay that is never confirmed leaves the chips owed in the Chips view.
     * @return false when nothing was sent (another /pay to him is on its way, or the button is not active).
     */
    public static boolean pay(Winner w) {
        PayState s = payState(w);
        if (s == null || !s.active()) return false;
        Session sess = GambleData.last();
        if (!s.retry() && sess != null) sess.addWon(w.name, w.share);
        if (!Payouts.send(w.name, w.share)) { if (!s.retry() && sess != null) sess.addWon(w.name, -w.share); return false; }
        w.payAt = P.now(); w.payAmount = w.share;   // P.now like Payouts.Recent.at, the two are compared (the demo recorder freezes that clock)
        save(); ver++;
        return true;   // no click here: the GlassButton that calls this already played it
    }

    /** Payouts changed: a winner's /pay the server confirmed is marked paid. */
    private static void confirmPayments() {
        boolean changed = false;
        for (Event e : st().past) for (Winner w : e.top) {
            if (w.paid || w.payAt == 0) continue;
            Payouts.Recent r = Payouts.recent.get(w.name);
            if (r != null && r.ok() && r.at() >= w.payAt && Payouts.pending.stream().noneMatch(p -> Payouts.same(p.name(), w.name))) { w.paid = true; changed = true; }
        }
        if (changed) { save(); ver++; }
    }

    /** Unpaid race shares across the past events (what the host still owes to winners). */
    public static long unpaid() {
        long o = 0;
        for (Event e : st().past) for (Winner w : e.top) if (!w.paid) o += w.share;
        return o;
    }

    public static String describe() {
        Event e = current();
        StringBuilder sb = new StringBuilder();
        if (e == null) sb.append("none");
        else {
            sb.append(e.kind().label).append(" left=").append(P.clock(left())).append(" prize=").append(e.prize).append(" board=");
            for (Entry x : board()) sb.append(x.name()).append(':').append(x.wagered()).append(' ');
        }
        sb.append(" past=").append(st().past.size());
        for (Event p : past()) {
            sb.append(" [").append(p.kind().label).append(p.cancelled ? " cancelled" : "");
            for (Winner w : p.top) sb.append(' ').append(w.name).append(':').append(w.wagered).append('/').append(w.share).append(w.paid ? " paid" : w.payAt > 0 ? " sent" : "");
            sb.append(']');
        }
        return sb.toString();
    }
}
