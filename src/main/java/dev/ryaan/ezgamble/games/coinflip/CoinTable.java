package dev.ryaan.ezgamble.games.coinflip;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.P;

import java.util.ArrayList;
import java.util.List;

/**
 * Coinflip: heads or tails. The player picks a side and a bet, the coin is flipped: drawn BEFORE it leaves the hand
 * (crypto RNG), a fair 50/50. The right side pays x1.94 (bet back + 94 %): the house keeps 3 % on average, said on
 * screen, the same for both sides.
 */
public final class CoinTable extends Host {
    public static final double CF_PAY = 1.94, CF_MAX_WIN = 500e6;
    public static final long CF_MS = 2400;

    public static String cfName(String side) { return side.equals("heads") ? "Heads" : "Tails"; }

    public static final class Rnd extends Widgets.Round {
        public long bet, at, landedAt, tickAt; public String backer, side, result; public int halves, lastHalf; public boolean applied;
    }

    public static final CoinTable CF = new CoinTable();

    public String stage = "bet", side = "heads";
    public final List<String> results = new ArrayList<>();
    public Rnd round;

    private CoinTable() {}

    public boolean start() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (bet <= 0) return fail("Place a bet");
        if (bet > balance()) return fail("Not enough chips");
        if (bet * CF_PAY > CF_MAX_WIN) return fail("Max bet " + Money.format((long) Math.floor(CF_MAX_WIN / CF_PAY)));
        settle();
        var fr = dev.ryaan.ezgamble.data.Fair.round("Coinflip");
        String result = fr.next() < 0.5 ? "heads" : "tails";
        fr.result(result);
        // the coin starts heads up and turns a whole number of half turns, 5 or 6, so it lands on the drawn side. Not more: at
        // 9-10 half turns it moved almost half a turn between two frames at 30 FPS and looked frozen and jumpy (wagon-wheel effect)
        Rnd r = new Rnd();
        r.bet = bet; r.backer = backer; r.side = side; r.result = result; r.halves = 5 + (result.equals("heads") ? 1 : 0); r.at = P.now();
        round = r;
        Host.lastBacker = backer;
        stage = "spin";
        P.play("ui_coin_open", 1, 0.7);
        return true;
    }

    /** "again" = same side, same bet. */
    public boolean open() { return start(); }

    public boolean again() {
        fitBet();
        if (bet <= 0) return fail("No chips left");
        return open();
    }

    public boolean won(Rnd r) { return r.side.equals(r.result); }
    public long payoutOf(Rnd r) { return won(r) ? (long) Math.floor(r.bet * CF_PAY) : 0; }

    /** The coin's turn (radians) at time t: fast off the thumb, slowing to land flat. */
    public double angle(long t) {
        Rnd r = round; double f = Math.min(1, (double) (t - r.at) / CF_MS);
        return r.halves * Math.PI * (1 - Math.pow(1 - f, 2));   // quadratic ease: top speed ~5 half turns a second, a sixth of a turn per frame at 30 FPS
    }

    public void tick() {
        Rnd r = round; if (r == null || !stage.equals("spin")) return;
        long t = P.now(); int half = (int) Math.floor(angle(t) / Math.PI);
        if (half > r.lastHalf) { r.lastHalf = half; if (t - r.tickAt > 60) { P.play("ui_tick_close", 1.6 + 0.1 * Widgets.rand01(), 0.2); r.tickAt = t; } }   // a glint each time it shows a face
        if (t - r.at >= CF_MS) {
            stage = "done"; r.landedAt = t; settle();
            results.add(r.result); if (results.size() > 12) results.remove(0);
            P.play("ui_coin_close", 1, 0.7);
            if (won(r)) P.play("pay", 1, 0.6); else P.play("fail", 1.2, 0.35);
        }
    }

    @Override
    public void settle() {
        Rnd r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        Session s = Payouts.session();
        long net = payoutOf(r) - r.bet;
        if (s != null) s.addWon(r.backer, net);
        GambleData.record(new Txn(P.now(), "Coinflip", r.bet, -net, r.backer, "Coinflip " + r.result + " for " + r.backer + " on " + r.side));
    }

    public Widgets.PayState payState() {
        Rnd r = round;
        if (r == null || !stage.equals("done") || !won(r)) return null;
        return Widgets.payStateOf(r, r.backer, Math.min(payoutOf(r), balance()), r.landedAt);
    }

    public void pay() { Widgets.payOut(round, round.backer, payState(), this::settle); }
}
