package dev.ryaan.ezgamble.games.rain;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Fair;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.P;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rain (as on Donut Surge): the host sets a ticket price and a goal, players /pay the price (any multiple = that many
 * tickets), and once the pot reaches the goal a reel of every ticket spins: one player takes the pot, minus the host's
 * fee. The draw is provably fair (Fair round "Rain": the number picks a ticket, tickets in the order they were bought).
 * The prize goes into the winner's chips, so the Pay button sends it like any other win; a cancelled rain turns every
 * ticket back into chips, refunded from the Chips view.
 */
public final class RainTable {
    public static final RainTable RN = new RainTable();
    public static final int CELL = 40, WIN_AT = 60;
    public static final long SPIN_MS = 6000;

    public String stage = "setup";   // setup, open (collecting), spin, done
    public long entry = 1_000_000, goal = 50_000_000;
    public double fee = 5;   // percent kept by the host
    /** Tickets by player, in the order of their first ticket. */
    public final Map<String, Integer> tickets = new LinkedHashMap<>();
    /** Every ticket's owner, in the order bought: the provably fair number picks one of these. */
    public final List<String> order = new ArrayList<>();
    public Round round;

    public static final class Round extends Widgets.Round {
        public String winner; public int ticket, total; public long pot, prize, cut;
        public List<String> strip; public double land; public long at, landedAt; public int lastCell = -1; public long tickAt; public boolean applied;
    }

    public int count() { return order.size(); }
    public long pot() { return entry * order.size(); }

    public String warn; public long warnUntil;
    private boolean fail(String w) { warn = w; warnUntil = P.now() + 2200; P.play("fail", 1, 0.5); return false; }

    public boolean start() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (entry <= 0) return fail("Set a ticket price");
        if (goal < 2 * entry) return fail("Goal: 2 tickets at least");
        if (fee < 0 || fee > 50) return fail("Fee: 0 to 50%");
        tickets.clear(); order.clear(); round = null;
        stage = "open";
        P.play("ui_kcards_open", 1, 0.7);
        return true;
    }

    /** A /pay received while the rain is open: a multiple of the ticket price buys that many tickets (true = taken). */
    public boolean take(String name, long amount) {
        if (!stage.equals("open") || entry <= 0 || amount <= 0 || amount % entry != 0) return false;
        int n = (int) Math.min(1000, amount / entry);
        tickets.merge(name, n, Integer::sum);
        for (int i = 0; i < n; i++) order.add(name);
        EzGambleMod.LOG.info("[ezgamble] rain: {} bought {} ticket(s), pot {}", name, n, pot());
        if (pot() >= goal) spin();
        return true;
    }

    /** The goal is reached: draw the winning ticket and start the reel. */
    void spin() {
        var fr = Fair.round("Rain");
        double u = fr.next();
        Round r = new Round();
        r.total = order.size(); r.ticket = Math.min(r.total - 1, (int) Math.floor(u * r.total)); r.winner = order.get(r.ticket);
        r.pot = pot(); r.cut = Math.round(r.pot * fee / 100.0); r.prize = r.pot - r.cut;
        fr.result("ticket " + (r.ticket + 1) + "/" + r.total + " " + r.winner);
        r.strip = new ArrayList<>();
        for (int i = 0; i < WIN_AT + 6; i++) r.strip.add(i == WIN_AT ? r.winner : order.get((int) Math.floor(Widgets.rand01() * order.size())));   // every ticket as likely as it really is
        r.land = WIN_AT * CELL + CELL / 2.0 + (Widgets.rand01() - 0.5) * (CELL - 10);
        r.at = P.now();
        round = r;
        stage = "spin";
        P.play("mc_bell", 1.2, 0.5);
    }

    public double pos(long t) {
        double f = Math.min(1, (double) (t - round.at) / SPIN_MS);
        return 2 * CELL + (round.land - 2 * CELL) * (1 - Math.pow(1 - f, 4));
    }

    public void tick() {
        Round r = round;
        if (r == null || !stage.equals("spin")) return;
        long t = P.now();
        int cell = (int) Math.floor(pos(t) / CELL);
        if (cell != r.lastCell) {
            if (r.lastCell >= 0 && t - r.tickAt >= 45) { P.play("ui_tick_close", 1 + 0.15 * Widgets.rand01(), 0.35); r.tickAt = t; }
            r.lastCell = cell;
        }
        if (t - r.at >= SPIN_MS) {
            stage = "done"; r.landedAt = t; settle();
            P.play("cash", 1, 0.8); P.play("mc_totem", 1.2, 0.35);
        }
    }

    /** The prize into the winner's chips, the fee as the host's profit. Idempotent. */
    public void settle() {
        Round r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        var s = Payouts.session();
        if (s != null) s.addWon(r.winner, r.prize);
        GambleData.record(new Txn(P.now(), "Rain", r.pot, r.cut, r.winner, "Rain " + Money.format(r.pot) + " won by " + r.winner + " (" + r.total + " tickets)"));
    }

    /** Stop collecting: every ticket goes back into its owner's chips (refund them from the Chips view). */
    public void cancel() {
        if (!stage.equals("open")) return;
        var s = Payouts.session();
        if (s != null) for (var e : tickets.entrySet()) s.addWon(e.getKey(), e.getValue() * entry);
        tickets.clear(); order.clear();
        stage = "setup";
        P.play("ui_kcards_close", 1, 0.6);
    }

    public void again() { if (stage.equals("done")) { settle(); stage = "setup"; round = null; tickets.clear(); order.clear(); } }

    public Widgets.PayState payState() {
        Round r = round;
        if (r == null || !stage.equals("done")) return null;
        return Widgets.payStateOf(r, r.winner, Math.min(r.prize, Payouts.balance(r.winner)), r.landedAt);
    }

    public void pay() { Widgets.payOut(round, round.winner, payState(), this::settle); }

    // ---- what the host pastes in chat (never sent by the mod: a bot-like message is what Ryan does not want)

    private static String me() { return MinecraftClient.getInstance().getSession().getUsername(); }
    private String feeText() { return fee == Math.rint(fee) ? String.valueOf((long) fee) : String.valueOf(fee); }

    public String announceText() {
        return "Rain is on! /pay " + me() + " " + Money.format(entry) + " per ticket, goal " + Money.format(goal)
                + ". One ticket takes the whole pot (" + feeText() + "% fee). More tickets, more chances.";
    }

    public String progressText() {
        return "Rain: " + Money.format(pot()) + " / " + Money.format(goal) + ", " + count() + " ticket" + (count() == 1 ? "" : "s") + ". /pay " + me() + " " + Money.format(entry) + " to get in!";
    }

    public String winnerText() {
        Round r = round;
        return r == null ? "" : "Rain winner: " + r.winner + " takes " + Money.format(r.prize) + " (ticket " + (r.ticket + 1) + " of " + r.total + "). Check it: " + Fair.VERIFY_URL;
    }

    public static void copy(String text) { Fair.copy(text); P.play("click"); }
}
