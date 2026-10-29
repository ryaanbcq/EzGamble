package dev.ryaan.ezgamble.games.mines;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.P;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The player (or the gambler for him) picks how many mines hide in 25 tiles and the bet, then opens tiles one by one.
 * Every safe tile raises the multiplier; a mine loses the bet; Cash out pays bet x multiplier. The board is drawn
 * BEFORE the first pick (crypto RNG) and shown whole at the end: the mines never move.
 * Odds (DESIGN.md 36, scratch tool minessim): multiplier = 0.96 x C(25, k) / C(25 - m, k) after k safe tiles, so EVERY
 * cash-out point returns 96 %, whatever the player does: no strategy beats it, none is punished.
 */
public final class MinesTable extends Host {   // the player, the chips and the bet work like the crates
    public static final int MINES_N = 25;
    public static final double MINES_EDGE = 0.04;
    public static final int[] MINES_OPTIONS = {2, 3, 5, 10, 20};
    public static final double MINES_MAX_WIN = 500e6;   // the most one board may pay (host setting, like Crates)

    static double comb(int n, int k) { double r = 1; for (int i = 0; i < k; i++) r = (r * (n - i)) / (i + 1); return r; }
    /** Shown and paid at 2 decimals, rounded DOWN. */
    public static double minesMult(int k, int m) { return k <= 0 ? 1 : Math.floor((1 - MINES_EDGE) * (comb(MINES_N, k) / comb(MINES_N - m, k)) * 100) / 100; }
    /** x1.66, x2, x28.8: the multiplier exactly as it is paid (2 decimals, never rounded up for show). */
    public static String minesText(double m) { return "x" + BigDecimal.valueOf(Math.round(m * 100), 2).stripTrailingZeros().toPlainString(); }
    public static String tileName(int i) { return "ABCDE".charAt(i % 5) + String.valueOf(i / 5 + 1); }

    private static final Pattern LN = Pattern.compile("^([a-e])\\s?([1-5])$", Pattern.CASE_INSENSITIVE), NL = Pattern.compile("^([1-5])\\s?([a-e])$", Pattern.CASE_INSENSITIVE);
    /** "b3", "3b", "B 3" or 1-25 (row by row): the tile index, or -1. */
    public static int parseTile(String s) {
        Matcher m = LN.matcher(s);
        String l = null, n = null;
        if (m.matches()) { l = m.group(1); n = m.group(2); }
        else if ((m = NL.matcher(s)).matches()) { l = m.group(2); n = m.group(1); }
        if (l != null) return (Integer.parseInt(n) - 1) * 5 + "abcde".indexOf(l.toLowerCase());
        if (s.matches("^\\d{1,2}$")) { int v = Integer.parseInt(s); return v >= 1 && v <= MINES_N ? v - 1 : -1; }
        return -1;
    }

    public static final class Round extends Widgets.Round {
        public long bet; public String backer; public int mines; public Set<Integer> bombs = new HashSet<>();
        public List<Integer> picked = new ArrayList<>(); public long[] pickedAt = new long[MINES_N];
        public long at, endAt; public int boom = -1; public boolean cashed, applied;
    }

    public static final MinesTable MN = new MinesTable();

    public String stage = "bet";
    public int mines = 3;
    public Round round;

    /** Draw the board and start: the mines are placed now and never move. */
    public boolean start() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (bet <= 0) return fail("Place a bet");
        if (bet > balance()) return fail("Not enough chips");
        settle();
        int[] idx = new int[MINES_N];
        for (int i = 0; i < MINES_N; i++) idx[i] = i;
        for (int i = MINES_N - 1; i > 0; i--) { int j = (int) Math.floor(Widgets.rand01() * (i + 1)); int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp; }
        Round r = new Round();
        r.bet = bet; r.backer = backer; r.mines = mines; r.at = P.now();
        for (int i = 0; i < mines; i++) r.bombs.add(idx[i]);
        round = r;
        Host.lastBacker = backer;
        stage = "play";
        P.play("ui_kcards_open", 1, 0.7);
        return true;
    }

    public boolean open() { return start(); }

    /** "Play again" replays the same bet on a new board. */
    public boolean again() {
        if (bet > balance()) fitBet();
        if (bet <= 0) return fail("No chips left");
        return open();
    }

    public double mult() { return mult(round); }
    public double mult(Round r) { return minesMult(r.picked.size(), r.mines); }
    public long value() { return value(round); }
    public long value(Round r) { return (long) Math.min(MINES_MAX_WIN, Math.floor(r.bet * mult(r))); }
    public int safeLeft() { return safeLeft(round); }
    public int safeLeft(Round r) { return MINES_N - r.mines - r.picked.size(); }

    /** Open tile i: a mine ends the round, a safe tile raises the multiplier (the last safe tile, or the max win, cashes out). */
    public boolean pick(int i) {
        Round r = round;
        if (!stage.equals("play") || i < 0 || i >= MINES_N || r.picked.contains(i)) return false;
        r.picked.add(i); r.pickedAt[i] = P.now();
        if (r.bombs.contains(i)) {
            r.boom = i; stage = "done"; r.endAt = P.now(); settle();
            P.play("mc_explode", 1, 0.5); P.play("fail", 1, 0.35);
            return true;
        }
        P.play("mc_pling", 0.9 + 0.08 * r.picked.size(), 0.45);   // a note a little higher for every safe tile
        if (safeLeft(r) == 0 || value(r) >= MINES_MAX_WIN) cashout();
        return true;
    }

    /** A tile at random among the closed ones (the "random" whisper, the Random button). */
    public boolean pickRandom() {
        if (!stage.equals("play")) return false;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < MINES_N; i++) if (!round.picked.contains(i)) free.add(i);
        return pick(free.get((int) Math.floor(Widgets.rand01() * free.size())));
    }

    public boolean cashout() {
        Round r = round;
        if (!stage.equals("play") || r.picked.isEmpty()) return false;
        r.cashed = true; stage = "done"; r.endAt = P.now(); settle();
        P.play("cash", 1, 0.7); P.play("mc_orb", 1.2, 0.4);
        return true;
    }

    /** His chips: value - bet on a cash out, - bet on a mine; the house, the other side. Idempotent. */
    @Override
    public void settle() {
        Round r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        var s = Payouts.session();
        long net = (r.cashed ? value(r) : 0) - r.bet;
        if (s != null) s.addWon(r.backer, net);
        int k = r.picked.size() - (r.cashed ? 0 : 1);
        GambleData.record(new Txn(P.now(), "Mines", r.bet, -net, r.backer,
                "Mines " + r.mines + " mines, " + (r.cashed ? "cashed " + minesText(mult(r)) + " after " + k + " tiles" : "mine after " + k + " tiles") + " for " + r.backer));
    }

    public Widgets.PayState payState() {
        Round r = round;
        if (r == null || !stage.equals("done") || !r.cashed) return null;
        long v = value(r);
        return Widgets.payStateOf(r, r.backer, v > r.bet ? Math.min(v, balance()) : 0, r.endAt);
    }

    public void pay() { Widgets.payOut(round, round.backer, payState(), this::settle); }
}
