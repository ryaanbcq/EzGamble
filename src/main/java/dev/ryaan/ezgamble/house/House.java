package dev.ryaan.ezgamble.house;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Balance;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.proto.P;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The house's health: can the gambler pay what he owes? His bankroll is his own server balance (the sidebar, the last
 * /bal: Balance.value()), what he owes is every player's chips on the table (Payouts) plus whatever other modules
 * register (addOwed). Tables ask canCover(maxWin) before a round: a bet the house could not pay if it hit its top
 * multiplier is refused, so a lucky player is never owed money that is not there. Nothing is written in chat.
 */
public final class House {
    private House() {}

    public enum State { UNKNOWN, HEALTHY, TIGHT, UNDER }

    /** One round never risks more than this share of the free bankroll (safeMax). */
    public static final double SAFE_SHARE = 0.25;
    /** A new state must hold this long before it shows: the sidebar is read every second and rounds its figure. */
    private static final long SETTLE_MS = 2000;
    public static final int GOLD = 0xFFF2C14E;

    private static final List<LongSupplier> extraOwed = new ArrayList<>();
    private static State state = State.UNKNOWN, candidate = State.UNKNOWN;
    private static long since, candidateSince;

    /** Another module owes money for the house too (cashback...): counted in owed(). */
    public static void addOwed(LongSupplier s) { extraOwed.add(s); }

    /** The gambler's own money, -1 while the server has not shown it. */
    public static long bankroll() { return Balance.value(); }

    /** Every chip on the table, plus what other modules registered. Never negative. Summed at most every 50 ms: the HUD, the menu and the tick all ask. */
    private static long owedCache, owedAt = -1;
    public static long owed() {
        long t = P.now();
        if (owedAt >= 0 && t - owedAt < 50) return owedCache;
        owedAt = t; owedCache = sumOwed();
        return owedCache;
    }

    private static long sumOwed() {
        long o = 0;
        for (Payouts.Row r : Payouts.rows()) o += r.bal();
        for (LongSupplier s : extraOwed) o += Math.max(0, s.getAsLong());
        return o;
    }

    /** bankroll - owed, or -1 while the bankroll is unknown (may be negative: under-covered). */
    public static long free() { long b = bankroll(); return b < 0 ? -1 : b - owed(); }

    /** bankroll / owed; NaN when nothing is owed or the bankroll is unknown. */
    public static double coverage() {
        long b = bankroll(), o = owed();
        if (b < 0 || o <= 0) return Double.NaN;
        return b / (double) o;
    }

    /** The state as the figures say right now (the shown one, state(), waits for it to settle). */
    public static State compute() {
        long b = bankroll();
        if (b < 0) return State.UNKNOWN;
        long o = owed();
        if (o <= 0) return State.HEALTHY;
        double c = b / (double) o;
        return c >= 2 ? State.HEALTHY : c >= 1 ? State.TIGHT : State.UNDER;
    }

    public static State state() { return state; }
    /** When the current state began. */
    public static long since() { return since; }

    /** Can the house pay this win on top of what it owes? Unknown bankroll = yes (never block blindly). */
    public static boolean canCover(long maxWin) {
        long f = free();
        return bankroll() < 0 || maxWin <= f;
    }

    /** The refusal shown on a 110 px main button (the DANGER tint says it is a refusal): "Can't cover $123.45M" is 106 px at most. */
    public static String reason(long maxWin) { return "Can't cover " + Money.format(maxWin); }

    /** The biggest bet whose top win (bet x mult) stays under a quarter of the free bankroll; -1 unknown, 0 none. */
    public static long safeMax(double mult) {
        long f = free();
        if (f < 0) return -1;
        if (mult <= 0) return 0;
        return Math.max(0, (long) Math.floor(f * SAFE_SHARE / mult));
    }

    public static String word(State s) {
        return switch (s) { case HEALTHY -> "Healthy"; case TIGHT -> "Tight"; case UNDER -> "Under-covered"; default -> "Unknown balance"; };
    }

    public static int color(State s) {
        return switch (s) { case HEALTHY -> P.GREEN; case TIGHT -> GOLD; case UNDER -> P.RED; default -> P.DIM; };
    }

    /** "3.2x", "-" when unknown. */
    public static String coverText() {
        double c = coverage();
        if (Double.isNaN(c)) return "-";
        if (c >= 100) return "99x+";
        return String.format(java.util.Locale.ROOT, "%.1fx", c);
    }

    /** Client tick: the debounced state, the alarm when the house goes under. */
    public static void tick() {
        long t = P.now();
        State now = compute();
        if (now != candidate) { candidate = now; candidateSince = t; }
        if (candidate == state) return;
        if (since == 0 || t - candidateSince >= SETTLE_MS) {
            boolean alarm = candidate == State.UNDER && since != 0;   // not at startup: the first reading is not news
            state = candidate; since = t;
            if (alarm) {
                P.play("mc_thunder", 0.6, 0.5);   // a low rumble (an already registered vanilla event: nothing unverified against MC)
                P.play("mc_bell", 0.6, 0.5);
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(
                        Text.literal("House under-covered: you owe " + Money.format(Math.max(0, -free())) + " more than you have").withColor(0xFF5C5C), false);
            }
        }
    }

    /** Debug: the figures in one line. */
    public static String describe() {
        return "bankroll " + bankroll() + " owed " + owed() + " free " + free() + " coverage " + coverText() + " state " + state + " (now " + compute() + ")";
    }
}
