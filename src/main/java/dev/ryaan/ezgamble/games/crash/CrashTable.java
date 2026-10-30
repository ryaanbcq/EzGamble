package dev.ryaan.ezgamble.games.crash;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.Locale;

/**
 * Crash: a multiplier climbs, cash out before it crashes.
 * The multiplier starts at x1.00 and climbs faster and faster; it crashes at a point drawn BEFORE the launch (crypto
 * RNG). Cash out before and the bet is paid x the multiplier of that moment; after, it is lost.
 * Odds: P(crash >= x) = 0.96 / x, so cashing out at ANY multiplier returns 96 % (4 % of rounds crash at x1.00 at
 * once: that is the house's edge, said, not hidden).
 * Whispers take a second or two to arrive: the player can set an AUTO cash out before the launch (exact, no lag), and
 * a "cash" whisper is honoured at the multiplier of the moment it ARRIVES.
 */
public final class CrashTable extends Host {
    public static final double CRASH_EDGE = 0.04, CRASH_MAX_X = 1000, CRASH_MAX_WIN = 500e6, CRASH_RATE = Math.log(2) / 6.5;   // x2 after 6.5 s, x10 after 21.6 s, x100 after 43 s
    public static final double[] CRASH_TARGETS = {1.5, 2, 3, 5, 10, 100};   // 0 = no auto cash out
    public static final long CRASH_WAIT_MS = 1200;   // the countdown before the launch

    /** The multiplier shown and paid, 2 decimals rounded down. */
    public static double crashAt(double ms) { return Math.floor(Math.exp(CRASH_RATE * Math.max(0, ms) / 1000) * 100) / 100; }
    public static double crashMsOf(double x) { return (Math.log(x) / CRASH_RATE) * 1000; }
    public static String crashText(double m) { return String.format(Locale.ROOT, "x%.2f", m); }

    /** "2", "2x", "x2.5", "auto 3", "off": an auto cash out target (0 = off), or null (NaN) when it is not one. */
    public static Double parseAuto(String s) {
        String t = s.toLowerCase(Locale.ROOT).replaceFirst("^auto\\s*", "").trim();
        if (t.equals("off") || t.equals("none") || t.equals("0")) return 0.0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^x?\\s?(\\d+(?:[.,]\\d+)?)\\s?x?$").matcher(t);
        if (!m.find()) return null;
        double v = Math.floor(Double.parseDouble(m.group(1).replace(',', '.')) * 100) / 100;
        return v >= 1.01 && v <= CRASH_MAX_X ? v : null;
    }

    public static final class Rnd extends Widgets.Round {
        public long bet; public String backer; public double auto, crash;
        public long at, launch, cashAt, crashedAt, tickAt; public double crashMs, cashX;
        public boolean crashed, applied; public int lastStep;
    }

    public static final CrashTable CX = new CrashTable();

    public String stage = "bet";
    public double auto = 2;
    public Rnd round;

    private CrashTable() {}

    public boolean start() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (bet <= 0) return fail("Place a bet");
        if (bet > balance()) return fail("Not enough chips");
        settle();
        var fr = dev.ryaan.ezgamble.data.Fair.round("Crash");
        double c = (1 - CRASH_EDGE) / (1 - fr.next()), crash = Math.min(CRASH_MAX_X, Math.max(1, Math.floor(c * 100) / 100));
        fr.result("x" + crash);
        Rnd r = new Rnd();
        r.bet = bet; r.backer = backer; r.auto = auto; r.crash = crash; r.at = P.now(); r.launch = P.now() + CRASH_WAIT_MS; r.crashMs = crashMsOf(crash);
        round = r;
        Host.lastBacker = backer;
        stage = "play";
        P.play("ui_kcards_open", 1, 0.7);
        return true;
    }

    /** "again" replays the same bet and the same auto cash out. */
    public boolean open() { return start(); }

    public boolean again() {
        fitBet();
        if (bet <= 0) return fail("No chips left");
        return open();
    }

    /** The live multiplier (x1.00 during the countdown, frozen at the crash). */
    public double mult(long t) {
        Rnd r = round; if (r == null) return 1;
        double ms = Math.min(t - r.launch, r.crashMs);
        return t < r.launch ? 1 : Math.min(r.crash, crashAt(ms));
    }
    public double mult() { return mult(P.now()); }

    public long value(double x) { return (long) Math.min(CRASH_MAX_WIN, Math.floor(round.bet * x)); }

    /** Cash out now (button, whisper): paid at the multiplier of this very moment. */
    public boolean cashout() {
        Rnd r = round; long t = P.now();
        if (r == null || !stage.equals("play") || r.cashX != 0 || t < r.launch || r.crashed) return false;
        return cashAt(mult(t), t);
    }

    private boolean cashAt(double x, long t) {
        Rnd r = round; r.cashX = x; r.cashAt = t; stage = "done"; settle();   // done for HIM: the curve flies on to the crash, for the show
        P.play("cash", 1, 0.7); P.play("mc_orb", 1.2, 0.4);
        return true;
    }

    private static boolean screenOpen() {
        return GambleScreen.current() != null && GambleScreen.tab.equals("games") && "crash".equals(GambleScreen.sub);
    }

    public void tick() {
        Rnd r = round; if (r == null || r.crashed) return;
        long t = P.now();
        if (r.cashX != 0 && !screenOpen()) { r.crashed = true; r.crashedAt = t; return; }   // he cashed out and the host left the screen: it is over, the rest of the flight stops (silently)
        if (t < r.launch) return;
        double x = mult(t);
        if (r.cashX == 0 && r.auto != 0 && x >= r.auto) cashAt(r.auto, r.launch + (long) crashMsOf(r.auto));   // the auto cash out: exactly its target, never the frame after
        if (r.cashX == 0 && value(x) >= CRASH_MAX_WIN) cashAt(x, t);
        int step = (int) Math.floor(Math.log(x) / Math.log(1.1));   // a tick every +10 %, a little higher each time
        if (step > r.lastStep) { r.lastStep = step; if (t - r.tickAt > 90) { P.play("ui_tick_close", Math.min(2, 1 + step * 0.03), 0.3); r.tickAt = t; } }
        if (t - r.launch >= r.crashMs) {
            r.crashed = true; r.crashedAt = t;
            if (stage.equals("play")) { stage = "done"; settle(); }
            P.play("mc_explode", 1.1, r.cashX != 0 ? 0.25 : 0.5); if (r.cashX == 0) P.play("fail", 1, 0.35);
        }
    }

    /** His chips: value - bet on a cash out, - bet on a crash; the house, the other side. Idempotent. */
    @Override
    public void settle() {
        Rnd r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        Session s = Payouts.session();
        long net = (r.cashX != 0 ? value(r.cashX) : 0) - r.bet;
        if (s != null) s.addWon(r.backer, net);
        GambleData.record(new Txn(P.now(), "Crash", r.bet, -net, r.backer,
                "Crash " + (r.cashX != 0 ? "cashed " + crashText(r.cashX) : "crashed " + crashText(r.crash)) + " for " + r.backer));
    }

    public Widgets.PayState payState() {
        Rnd r = round;
        if (r == null || !stage.equals("done") || r.cashX == 0) return null;
        long v = value(r.cashX);
        return Widgets.payStateOf(r, r.backer, v > r.bet ? Math.min(v, balance()) : 0, r.cashAt);
    }

    public void pay() { Widgets.payOut(round, round.backer, payState(), this::settle); }
}
