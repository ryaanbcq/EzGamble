package dev.ryaan.ezgamble.games.blackjack;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.P;

import java.util.HashMap;
import java.util.Map;

/** Table state: backer, bet, timeline. Survives the menu (prototype BlackjackTable, singleton BJ). */
public final class BlackjackTable extends Host {
    public static final BlackjackTable BJ = new BlackjackTable();

    public static final int CARD_W = Widgets.CARD_W, CARD_H = Widgets.CARD_H, CARD_STEP = 18;
    public static final int BJ_DEAL_MS = 220, BJ_FLIP_MS = 320, BJ_FLY_MS = 450, BJ_SPLIT_MS = 260;

    /** When a card is dealt / flipped, and which of its sounds already played. */
    public static final class Timing {
        public long dealAt, revealAt;
        boolean s1, s2, s3;
        Timing(long dealAt) { this.dealAt = dealAt; }
    }

    /** The backer's chips counting up or down after the result. */
    public static final class BalAnim {
        public final long from, to, at;
        BalAnim(long from, long to, long at) { this.from = from; this.to = to; this.at = at; }
    }

    public final BlackjackGame.Shoe shoe = new BlackjackGame.Shoe(6);
    public BlackjackGame game;
    public String stage = "bet";
    public final Map<Integer, Timing> times = new HashMap<>();
    public int seen, page;
    public long lastEnd, resultAt, splitAt;
    public boolean applied = true, fx;
    // view-side animation state that must survive a rebuild
    public BlackjackGame offFor;
    public final Map<String, Double> off = new HashMap<>();
    public Long balShown;
    public String balFor;
    public BalAnim balAnim;

    private BlackjackTable() {}

    public long committed() { return game == null ? 0 : game.hands.stream().mapToLong(h -> h.bet).sum(); }
    public boolean locked() { return P.now() < lastEnd; }

    @Override public void settle() { apply(); }

    /** Settle a finished hand into the session: the backer's chips move (his point of view), one transaction per hand in the HOUSE's point of view (mode ON only). Idempotent. */
    public void apply() {
        BlackjackGame gm = game;
        if (gm == null || !gm.phase.equals("done") || applied) return;
        applied = true;
        Session s = Payouts.session();
        if (s != null) s.addWon(backer, gm.total());
        // the gambler is the house: a hand the backer loses is HIS profit (+bet), a hand the backer wins costs him (-gain)
        for (BlackjackGame.Result r : gm.results)
            GambleData.record(new Txn(P.now(), "Blackjack", r.bet(), -r.delta(), backer, "Blackjack " + r.outcome() + " for " + backer));
    }

    /** What a won hand gives back to the backer: every hand's bet + its result (a lost split hand counts 0). */
    public long payout() {
        BlackjackGame gm = game;
        if (gm == null || !gm.phase.equals("done") || gm.total() <= 0) return 0;
        return Math.min(balance(), gm.results.stream().mapToLong(r -> r.bet() + r.delta()).sum());
    }

    public Widgets.PayState payState() { return Widgets.payStateOf(game, backer, payout(), resultAt); }
    public void pay() { Widgets.payOut(game, backer, payState(), this::apply); }   // the hand's win is in his chips before the money leaves them

    public boolean deal() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (bet <= 0) return fail("Place a bet");
        if (bet > balance()) return fail("Not enough chips");
        apply();
        lastBacker = backer;
        game = new BlackjackGame(shoe);
        game.deal(bet);
        stage = "play"; times.clear(); seen = 0; lastEnd = P.now() + 80; resultAt = 0; applied = false; fx = false; splitAt = 0; warn = null;
        schedule();
        return true;
    }

    public boolean rebet() {
        fitBet();
        if (bet <= 0) return fail("No chips left");
        return deal();
    }

    public boolean canDo(String a) {
        BlackjackGame gm = game;
        if (gm == null || locked() || !gm.can(a)) return false;
        if (a.equals("double") || a.equals("split")) return committed() + gm.hand().bet <= balance();   // the extra bet must be covered
        return true;
    }

    public boolean act(String a) { if (!canDo(a)) return false; game.act(a); schedule(); return true; }

    /** Puts every new log entry on the clock: cards 230 ms apart, the dealer's draws slower, the flip in between. */
    void schedule() {
        BlackjackGame gm = game;
        long t = Math.max(P.now() + 60, lastEnd);
        boolean slow = false;
        for (int i = 0; i < seen; i++) if (gm.log.get(i).type.equals("reveal")) { slow = true; break; }
        for (; seen < gm.log.size(); seen++) {
            BlackjackGame.Entry e = gm.log.get(seen);
            if (e.type.equals("reveal")) { times.get(e.card.id).revealAt = t; t += BJ_FLIP_MS + 140; slow = true; }
            else if (e.type.equals("split")) { splitAt = t; t += BJ_SPLIT_MS; }
            else { times.put(e.card.id, new Timing(t)); t += slow ? 480 : 230; }
        }
        lastEnd = t;
        if (gm.phase.equals("done") && resultAt == 0) resultAt = t + 200;
    }

    /** Sounds on the timeline (a card leaving the shoe, landing, flipping; chips at the result), and the settlement. */
    public void tick() {
        long t = P.now();
        for (Timing tm : times.values()) {
            if (!tm.s1 && t >= tm.dealAt) { tm.s1 = true; P.play("ui_kcards_open", 1, 0.45); }
            if (!tm.s2 && t >= tm.dealAt + BJ_DEAL_MS) { tm.s2 = true; P.play("ui_kcards_close", 1, 0.45); }
            if (tm.revealAt > 0 && !tm.s3 && t >= tm.revealAt) { tm.s3 = true; P.play("ui_kcards_close", 1.1, 0.4); }
        }
        if (resultAt > 0 && !fx && t >= resultAt) {
            fx = true;
            long d = game.total();
            P.play(d > 0 ? "ui_kstack_open" : d < 0 ? "ui_kcollide_close" : "ui_kcollide_open", 1, 0.6);
            if (game.results.stream().anyMatch(r -> r.outcome().equals("blackjack"))) P.play("cash", 1, 0.5);
        }
        if (resultAt > 0 && !applied && t >= resultAt + BJ_FLY_MS) apply();
    }
}
