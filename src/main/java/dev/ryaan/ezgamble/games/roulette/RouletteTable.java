package dev.ryaan.ezgamble.games.roulette;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * European roulette, one zero. The gambler composes an amount with the chips (or types it), then clicks the cells of
 * the board to put that amount there: several bets per spin, all for the same player. Spin: the number is drawn BEFORE
 * the wheel moves (crypto RNG), a reel of the wheel's real order slows down onto it. Standard European payouts (a number
 * 35:1, a dozen 2:1, red, black, even, odd, 1-18, 19-36 1:1), the casino's 2.7 % edge, the same for every bet. Said, not hidden.
 */
public final class RouletteTable extends Host {
    /** The wheel, clockwise from the zero. */
    public static final int[] ORDER = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    public static final int CELL = 30, LAPS = 4, SPIN_MS = 6500;
    public static final long MAX_WIN = 500_000_000L;   // the most one bet may return (host setting, like Crates)

    public static String color(int n) { return n == 0 ? "green" : RED.contains(n) ? "red" : "black"; }

    /** Never flat casino red on the glass: a tint of the menu's own red / green, the number in that colour; black = the felt, a little darker, white digits. */
    public static final int RED_C = 0xFFFF8A8A, BLACK_C = 0xFF0C0814, GREEN_C = 0xFF4BE07A;
    public static int fill(int n) { return switch (color(n)) { case "green" -> GREEN_C; case "red" -> RED_C; default -> BLACK_C; }; }
    public static int ink(int n) { return switch (color(n)) { case "green" -> GREEN_C; case "red" -> RED_C; default -> P.WHITE; }; }
    public static int tint(int n, double k) { return color(n).equals("black") ? P.alpha(BLACK_C, Math.min(1, 0.45 * k)) : P.alpha(fill(n), Math.min(1, 0.15 * k)); }

    /** A chip seen from above, centre (x, y), radius r (GUI px): what sits on a cell of the board. */
    public static void chipTop(G g, double x, double y, double r, dev.ryaan.ezgamble.games.Chips.Chip chip) {
        double pad = 2, d = 2 * r;
        Vec.draw(g, "rl/chip/" + chip + "/" + r, x - r, y - r, d, d, pad, g2 -> {
            g2.setColor(new java.awt.Color(4, 2, 10, 60));   // the soft drop shadow, one px down
            g2.fill(new java.awt.geom.Ellipse2D.Double(-0.3, 0.4, d + 0.6, d + 0.6));
            g2.setColor(new java.awt.Color(4, 2, 10, 50));
            g2.fill(new java.awt.geom.Ellipse2D.Double(-0.8, 0.2, d + 1.6, d + 1.6));
            g2.setColor(Vec.c(chip.face()));
            g2.fill(new java.awt.geom.Ellipse2D.Double(0, 0, d, d));
            g2.setColor(Vec.c(P.alpha(chip.edge(), 0.95 * ((chip.edge() >>> 24) / 255.0))));
            g2.setStroke(new java.awt.BasicStroke((float) (r * 0.28), java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10, new float[]{(float) (r * 0.45), (float) (r * 0.42)}, 0));
            double q = r * 0.82;
            g2.draw(new java.awt.geom.Ellipse2D.Double(r - q, r - q, 2 * q, 2 * q));
            g2.setColor(Vec.c(P.alpha(chip.ink(), 0.35 * ((chip.ink() >>> 24) / 255.0))));
            g2.setStroke(new java.awt.BasicStroke((float) Math.max(0.35, r * 0.1)));
            q = r * 0.5;
            g2.draw(new java.awt.geom.Ellipse2D.Double(r - q, r - q, 2 * q, 2 * q));
            g2.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(255, 255, 255, 102), 0, (float) (d * 0.55), new java.awt.Color(255, 255, 255, 0)));
            g2.fill(new java.awt.geom.Ellipse2D.Double(0, 0, d, d));
        });
    }

    /** One bet of the board: its label, what it returns per 1 bet (stake included), whether number n wins it. */
    public record Bet(String label, int ret, IntPredicate wins) {}
    public static final Map<String, Bet> BETS = new LinkedHashMap<>();
    static {
        for (int n = 0; n <= 36; n++) { final int m = n; BETS.put("n" + n, new Bet(String.valueOf(n), 36, x -> x == m)); }
        BETS.put("red", new Bet("Red", 2, RED::contains));
        BETS.put("black", new Bet("Black", 2, x -> x > 0 && !RED.contains(x)));
        BETS.put("even", new Bet("Even", 2, x -> x > 0 && x % 2 == 0));
        BETS.put("odd", new Bet("Odd", 2, x -> x % 2 == 1));
        BETS.put("low", new Bet("1-18", 2, x -> x >= 1 && x <= 18));
        BETS.put("high", new Bet("19-36", 2, x -> x >= 19));
        BETS.put("d1", new Bet("1st 12", 3, x -> x >= 1 && x <= 12));
        BETS.put("d2", new Bet("2nd 12", 3, x -> x >= 13 && x <= 24));
        BETS.put("d3", new Bet("3rd 12", 3, x -> x >= 25));
    }

    /** "red", "17", "1st 12", "dozen 2", "19-36"... -> a bet key, or null. */
    public static String parseBet(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
        Map<String, String> w = new java.util.HashMap<>();
        for (String[] p : new String[][]{{"red", "red"}, {"rouge", "red"}, {"black", "black"}, {"noir", "black"}, {"even", "even"}, {"pair", "even"}, {"odd", "odd"}, {"impair", "odd"},
                {"low", "low"}, {"1-18", "low"}, {"high", "high"}, {"19-36", "high"},
                {"1st 12", "d1"}, {"first 12", "d1"}, {"dozen 1", "d1"}, {"d1", "d1"}, {"1-12", "d1"}, {"2nd 12", "d2"}, {"second 12", "d2"}, {"dozen 2", "d2"}, {"d2", "d2"}, {"13-24", "d2"},
                {"3rd 12", "d3"}, {"third 12", "d3"}, {"dozen 3", "d3"}, {"d3", "d3"}, {"25-36", "d3"}, {"zero", "n0"}, {"green", "n0"}}) w.put(p[0], p[1]);
        if (w.containsKey(t)) return w.get(t);
        if (t.matches("^\\d{1,2}$") && Integer.parseInt(t) <= 36) return "n" + Integer.parseInt(t);
        return null;
    }

    /** One spin in play or finished. */
    public static final class Spin extends Widgets.Round {
        public final int n;
        public final Map<String, Long> bets;
        public final long total, at;
        public final String backer;
        public final double land;
        public int lastCell = -1;
        public long landedAt, tickAt;
        public boolean applied;

        Spin(int n, Map<String, Long> bets, long total, String backer, long at, double land) {
            this.n = n; this.bets = bets; this.total = total; this.backer = backer; this.at = at; this.land = land;
        }
    }

    public static final RouletteTable RL = new RouletteTable();

    public final Map<String, Long> bets = new LinkedHashMap<>();
    public final List<Map.Entry<String, Long>> order = new ArrayList<>();
    public Map<String, Long> lastBets;
    public String stage = "bet";   // bet | spin | done
    public Spin round;
    public final List<Integer> lastResults = new ArrayList<>();

    public long placed() { long a = 0; for (long v : bets.values()) a += v; return a; }

    /** Put `amount` (the composed one by default) on a cell. */
    public boolean place(String key) { return place(key, bet); }
    public boolean place(String key, long amount) {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (!stage.equals("bet")) return false;
        if (!(amount > 0)) return fail("Pick an amount first");
        if (placed() + amount > balance()) return fail("Not enough chips");
        Bet b = BETS.get(key); if (b == null) return false;
        if ((bets.getOrDefault(key, 0L) + amount) * b.ret() > MAX_WIN) return fail("Max " + Money.format(MAX_WIN / b.ret()) + " on " + b.label());
        bets.merge(key, amount, Long::sum); order.add(Map.entry(key, amount));
        P.play("ui_kcollide_open", 1, 0.5);
        return true;
    }

    public void undo() {
        if (order.isEmpty()) return;
        Map.Entry<String, Long> last = order.remove(order.size() - 1);
        long left = bets.get(last.getKey()) - last.getValue();
        if (left <= 0) bets.remove(last.getKey()); else bets.put(last.getKey(), left);
        P.play("ui_kcollide_close", 1, 0.5);
    }

    public void clearTable() { if (!order.isEmpty()) P.play("ui_kcollide_close", 1, 0.5); bets.clear(); order.clear(); }

    /** Draw the number, build the reel around it, start the spin. */
    public boolean spin() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        long total = placed();
        if (total <= 0) return fail("Click a cell to bet");
        if (total > balance()) return fail("Not enough chips");
        settle();
        var fr = dev.ryaan.ezgamble.data.Fair.round("Roulette");   // provably fair: the number (the landing jitter stays cosmetic)
        int n = (int) Math.floor(fr.next() * 37), idx = LAPS * 37 + indexOf(n);
        fr.result(String.valueOf(n));
        double land = idx * CELL + CELL / 2.0 + (Widgets.rand01() - 0.5) * (CELL - 8);
        round = new Spin(n, new LinkedHashMap<>(bets), total, backer, P.now(), land);
        lastBets = new LinkedHashMap<>(bets); bets.clear(); order.clear();
        Host.lastBacker = backer;
        stage = "spin";
        P.play("ui_kcards_open", 1, 0.7);
        return true;
    }

    static int indexOf(int n) { for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == n) return i; return 0; }

    /** The same bets again (cut to what he can still cover: nothing is placed if he cannot cover them all). */
    public boolean again() {
        if (stage.equals("spin")) return false;
        Map<String, Long> prev = lastBets == null ? Map.of() : lastBets;
        long total = 0; for (long v : prev.values()) total += v;
        settle(); stage = "bet";
        if (total == 0) return fail("No bets to repeat");
        if (total > balance()) return fail("Not enough chips");
        bets.clear(); bets.putAll(prev); order.clear(); order.addAll(prev.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList());
        return spin();
    }

    public double pos(long t) { Spin r = round; double f = Math.min(1, (t - r.at) / (double) SPIN_MS); return 2 * CELL + (r.land - 2 * CELL) * (1 - Math.pow(1 - f, 4)); }

    public long payoutOf(Spin r) {
        long a = 0;
        for (var e : r.bets.entrySet()) { Bet b = BETS.get(e.getKey()); if (b.wins().test(r.n)) a += e.getValue() * b.ret(); }
        return a;
    }

    public void tick() {
        Spin r = round; if (r == null || !stage.equals("spin")) return;
        long t = P.now();
        int cell = (int) Math.floor(pos(t) / CELL);
        if (cell != r.lastCell) {
            if (r.lastCell >= 0 && t - r.tickAt >= 45) { P.play("ui_tick_close", 1.1 + 0.15 * Widgets.rand01(), 0.3); r.tickAt = t; }
            r.lastCell = cell;
        }
        if (t - r.at >= SPIN_MS) {
            stage = "done"; r.landedAt = t; settle();
            lastResults.add(r.n); if (lastResults.size() > 12) lastResults.remove(0);
            long pay = payoutOf(r);
            if (pay > r.total) { P.play("pay", 1, 0.6); if (pay >= r.total * 10) P.play("mc_totem", 1.2, 0.35); }
            else P.play("fail", 1.2, 0.35);
        }
    }

    @Override
    public void settle() {
        Spin r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        Session s = Payouts.session();
        long net = payoutOf(r) - r.total;
        if (s != null) s.addWon(r.backer, net);
        GambleData.record(new Txn(P.now(), "Roulette", r.total, -net, r.backer, "Roulette " + r.n + " " + color(r.n) + " for " + r.backer));
    }

    public Widgets.PayState payState() {
        Spin r = round; if (r == null || !stage.equals("done")) return null;
        long p = payoutOf(r);
        return Widgets.payStateOf(r, r.backer, p > r.total ? Math.min(p, balance()) : 0, r.landedAt);
    }

    public void pay() { Spin r = round; Widgets.payOut(r, r.backer, payState(), this::settle); }
}
