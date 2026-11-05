package dev.ryaan.ezgamble.jackpot;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The jackpot counter of the HUD's persistent row: a glass pill, "JACKPOT $12.4M" in gradient text. The shown amount
 * springs toward the real pot (GambleHud's lvl spring), a golden pulse rings it when the pot grows. hud.Banners places
 * it (x, y) next to the event banner and hides it under the rain cloud and the auction card; its width is the pot's
 * target width, so the centred pair does not jitter while the number springs.
 */
public final class JackpotHud {
    private JackpotHud() {}

    private static final double H = 17;
    private static final long PULSE_MS = 650;
    private static double shown = -1, vel;
    private static long lastFrame, lastPot = -1, pulseAt = -1;

    public static boolean showing() { return Jackpot.enabled(); }

    public static int width() { return 10 + F.width("JACKPOT") + 6 + F.width(Money.format(Jackpot.pot())) + 10; }

    /** The spring step and the pulse detection, in real time (same feel at any frame rate). */
    private static void tick() {
        long t = now(), pot = Jackpot.pot();
        if (lastPot >= 0 && pot > lastPot) pulseAt = t;   // the pot grew: a golden pulse
        lastPot = pot;
        // a drop (a hit) lands at once, the finale tells the story
        double dt = lastFrame == 0 ? 0 : Math.min(0.05, (t - lastFrame) / 1000.0);
        lastFrame = t;
        if (shown < 0 || pot < shown) { shown = pot; vel = 0; }
        vel += (pot - shown) * 60 * dt; vel *= Math.exp(-9 * dt); shown += vel * dt;
        if (Math.abs(pot - shown) < 0.5) shown = pot;
    }

    public static void draw(G g, double x, double y) {
        tick();
        long t = now();
        String a = "JACKPOT", b = Money.format(Math.max(0, Math.round(shown)));
        double w = width();
        double f = pulseAt < 0 ? 1 : Math.min(1, (t - pulseAt) / (double) PULSE_MS), pop = 1 + 0.05 * (1 - easeOut(f));
        g.push();
        g.translate(x + w / 2, y + H / 2); g.scale(pop, pop); g.translate(-(x + w / 2), -(y + H / 2));
        glass(g, x, y, w, H, 8.5, 0.75, 0.6);
        ring(g, x, y, w, H, 8.5, alpha(Jackpot.GOLD, 0.3 + 0.6 * (1 - f)), 1);   // faint gold rim, bright on the pulse
        g.drawText(a, x + 10, y + 5, alpha(Jackpot.GOLD, 0.9), true);
        gradientText(g, b, x + 10 + F.width(a) + 6, y + 5, true);
        if (f < 1) {   // the pulse: a ring swelling out of the pill and fading
            double k = easeOut(f), grow = 6 * k;
            ring(g, x - grow, y - grow, w + 2 * grow, H + 2 * grow, 8.5 + grow, alpha(Jackpot.GOLD, 0.7 * (1 - k)), 1.2);
        }
        g.pop();
    }
}
