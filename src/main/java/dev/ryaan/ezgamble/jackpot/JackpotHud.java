package dev.ryaan.ezgamble.jackpot;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.games.rain.RainTable;
import dev.ryaan.ezgamble.hud.HudConfig;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The jackpot counter of the HUD: a glass pill at the top centre of the screen (under the banner spot, y 30),
 * "JACKPOT $12.4M" in gradient text. The shown amount springs toward the real pot (GambleHud's lvl spring), a golden
 * pulse rings it when the pot grows. Drawn only with the jackpot enabled and gambling mode on (GambleHud.render),
 * never while the rain cloud hangs at the same spot.
 */
public final class JackpotHud {
    private JackpotHud() {}

    private static final double Y = 30, H = 17;
    private static final long PULSE_MS = 650;
    private static double shown = -1, vel;
    private static long lastFrame, lastPot = -1, pulseAt = -1;

    public static void draw(G g) {
        if (!Jackpot.enabled()) return;
        if (!RainTable.RN.stage.equals("setup")) return;   // the rain's cloud lives at the top centre while a rain runs
        long t = now(), pot = Jackpot.pot();
        if (lastPot >= 0 && pot > lastPot) pulseAt = t;   // the pot grew: a golden pulse
        lastPot = pot;
        // spring toward the pot (real time, same feel at any frame rate); a drop (a hit) lands at once, the finale tells the story
        double dt = lastFrame == 0 ? 0 : Math.min(0.05, (t - lastFrame) / 1000.0);
        lastFrame = t;
        if (shown < 0 || pot < shown) { shown = pot; vel = 0; }
        vel += (pot - shown) * 60 * dt; vel *= Math.exp(-9 * dt); shown += vel * dt;
        if (Math.abs(pot - shown) < 0.5) shown = pot;
        String a = "JACKPOT", b = Money.format(Math.max(0, Math.round(shown)));
        double w = 10 + F.width(a) + 6 + F.width(b) + 10, x = Math.round((HudConfig.sw() - w) / 2.0);
        double f = pulseAt < 0 ? 1 : Math.min(1, (t - pulseAt) / (double) PULSE_MS), pop = 1 + 0.05 * (1 - easeOut(f));
        g.push();
        g.translate(x + w / 2, Y + H / 2); g.scale(pop, pop); g.translate(-(x + w / 2), -(Y + H / 2));
        glass(g, x, Y, w, H, 8.5, 0.75, 0.6);
        ring(g, x, Y, w, H, 8.5, alpha(Jackpot.GOLD, 0.3 + 0.6 * (1 - f)), 1);   // faint gold rim, bright on the pulse
        g.drawText(a, x + 10, Y + 5, alpha(Jackpot.GOLD, 0.9), true);
        gradientText(g, b, x + 10 + F.width(a) + 6, Y + 5, true);
        if (f < 1) {   // the pulse: a ring swelling out of the pill and fading
            double k = easeOut(f), grow = 6 * k;
            ring(g, x - grow, Y - grow, w + 2 * grow, H + 2 * grow, 8.5 + grow, alpha(Jackpot.GOLD, 0.7 * (1 - k)), 1.2);
        }
        g.pop();
    }
}
