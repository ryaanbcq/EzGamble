package dev.ryaan.ezgamble.games;

/** Game clocks that run with the menu closed (a Crash flight goes on, its auto cash out fires). Each port adds its line. */
public final class Ticks {
    private Ticks() {}
    public static void tick() {
        dev.ryaan.ezgamble.games.crash.CrashTable.CX.tick();   // a Crash flight goes on (its auto cash out fires, it crashes) even with the menu closed
    }
}
