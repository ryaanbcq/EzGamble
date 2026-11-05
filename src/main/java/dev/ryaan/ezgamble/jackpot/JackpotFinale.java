package dev.ryaan.ezgamble.jackpot;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.hud.HudConfig;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The jackpot's finale, over the whole screen for 7 s after a hit: the screen dims, "JACKPOT" in gradient at scale 4
 * shakes in, the winner's head and name, the amount counting up, a fountain of gold coins (small ellipses, each on a
 * fixed per-particle hash like hud.RainFinale.h, so the scene is the same at every frame). Sounds: the level-up, then
 * the chime. Drawn by the HUD (drawHud) or, while the menu is open, on top of the menu (drawOver), never both.
 */
public final class JackpotFinale {
    private JackpotFinale() {}

    public static final long SHOW = 7000;
    private static final long FADE = 600;
    private static final int COINS = 56;
    private static Jackpot.Hit hit;
    private static long at = -1;
    private static int soundsPlayed;

    static void start(Jackpot.Hit h) { hit = h; at = now(); soundsPlayed = 0; }

    public static boolean active() { return at >= 0 && now() - at < SHOW; }

    /** From GambleHud.render: the HUD's copy, unless the menu is open (it draws the finale on top of itself). */
    public static void drawHud(G g) {
        if (!active() || MinecraftClient.getInstance().currentScreen instanceof GambleScreen) return;
        draw(g);
    }

    /** From GambleScreen.renderFg, under the menu's view transform: undone here so the scene covers the real screen. */
    public static void drawOver(G g, GambleScreen s) {
        if (!active()) return;
        g.push();
        g.scale(1 / s.viewZ, 1 / s.viewZ); g.translate(-s.viewX, -s.viewY);
        try { draw(g); } finally { g.pop(); }
    }

    /** A fixed random number in [0, 1) per particle and use (hud.RainFinale.h). */
    private static double h(int i, int k) {
        long x = i * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31; x *= 0x94D049BB133111EBL; x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smoothstep(double v) { v = clamp01(v); return v * v * (3 - 2 * v); }

    /** Text in the mod's gradient, centred on cx, `sc` times the font size. */
    private static void gradText(G g, String s, double cx, double y, double sc, double a) {
        g.push(); g.translate(cx - F.width(s) * sc / 2, y); g.scale(sc, sc); gradientText(g, s, 0, 0, true, a); g.pop();
    }

    private static void centred(G g, String s, double cx, double y, double sc, int color) {
        g.push(); g.translate(cx - F.width(s) * sc / 2, y); g.scale(sc, sc); g.drawText(s, 0, 0, color, true); g.pop();
    }

    private static void sounds(long e) {
        if (soundsPlayed == 0 && e >= 0) { soundsPlayed++; play("mc_levelup", 1, 1); }
        if (soundsPlayed == 1 && e >= 700) { soundsPlayed++; play("mc_chime", 1.2, 0.9); play("mc_chime", 1.8, 0.7); }
        if (soundsPlayed == 2 && e >= 1500) { soundsPlayed++; play("mc_chime", 0.9, 0.8); }
    }

    private static void draw(G g) {
        Jackpot.Hit r = hit;
        if (r == null) return;
        double W = HudConfig.sw(), H = HudConfig.sh();
        long e = now() - at;
        sounds(e);
        double gf = clamp01((SHOW - e) / (double) FADE);   // everything fades out at the very end
        double in = smoothstep(e / 400.0);

        // ---- the dim, then a warm glow in the middle
        g.fill(0, 0, W, H, alpha(0xFF1A0C2E, 0.62 * in * gf));
        double gw = W * 0.7, gh = H * 0.5;
        smooth(g, W / 2 - gw / 2, H * 0.42 - gh / 2, gw, gh, Math.min(gw, gh) / 2, alpha(0xFF6A4A10, 0.22 * in * gf));

        // ---- "JACKPOT": pops in at scale 4 with a shake that dies down
        double ta = clamp01(e / 250.0) * gf;
        double shake = 3 * (1 - clamp01(e / 900.0)), sx = Math.sin(e * 0.9) * shake, sy = Math.cos(e * 1.3) * shake;
        double sc = 4 * (1 + 0.35 * (1 - easeOut(clamp01(e / 350.0))));
        double titleY = H * 0.2;
        g.push(); g.translate(W / 2 + sx, titleY + sy); g.scale(sc, sc);
        g.drawText("JACKPOT", -F.width("JACKPOT") / 2.0 + 0.5, -3.5, alpha(0xFF3A2200, 0.6 * ta), false);   // a warm shadow under the gradient
        gradientText(g, "JACKPOT", -F.width("JACKPOT") / 2.0, -4, false, ta);
        g.pop();

        // ---- the winner: his head in a white frame, his name under it, the pot counting up
        double hy = H * 0.47, size = 40;
        double wa = clamp01((e - 300) / 300.0) * gf;
        if (wa > 0) {
            double pop = 1 + 0.4 * Math.sin(clamp01((e - 300) / 420.0) * Math.PI) * (1 - clamp01((e - 300) / 420.0));
            g.push(); g.translate(W / 2, hy); g.scale(pop, pop);
            smooth(g, -size / 2 - 3, -size / 2 - 3, size + 6, size + 6, 7, alpha(0xFFFFFFFF, 0.9 * wa));
            ring(g, -size / 2 - 3, -size / 2 - 3, size + 6, size + 6, 7, alpha(Jackpot.GOLD, wa), 1.6);
            g.drawHead(skinOf(r.name()), -size / 2, -size / 2, size);
            g.pop();
            gradText(g, r.name(), W / 2, hy + size / 2 + 10, 2, wa);
        }
        double ca = clamp01((e - 600) / 250.0) * gf;
        if (ca > 0) {
            double c = easeOut(clamp01((e - 600) / 1600.0));
            String amt = Money.format(Math.round(r.amount() * c));
            double s = Math.min(3, W * 0.5 / Math.max(1, F.width(Money.format(r.amount()))));
            s *= 1 + (c >= 1 ? 0.08 * Math.max(0, 1 - (e - 2200) / 300.0) : 0);   // a beat when the count lands
            gradText(g, amt, W / 2, hy + size / 2 + 36, s, ca);
            centred(g, "goes to " + r.name() + "'s chips", W / 2, hy + size / 2 + 36 + 9 * s + 6, 1, alpha(0xFFF4EEFF, ca));
        }

        // ---- the fountain: coins thrown up out of the winner over three seconds, falling under gravity, spinning
        for (int j = 0; j < COINS; j++) {
            double el = (e - 350 - h(j, 1) * 3000) / 1000.0;
            if (el < 0) continue;
            double vx = (h(j, 2) - 0.5) * W * 0.6, vy = -(H * 0.55 + h(j, 3) * H * 0.4);
            double x = W / 2 + vx * el, y = hy + vy * el + 0.5 * H * 1.5 * el * el;
            if (y > H + 10) continue;
            double s2 = 5 + 4 * h(j, 4), spin = Math.cos(el * 10 + j), w = s2 * Math.max(0.15, Math.abs(spin));
            int col = spin < 0 ? 0xFFD9A62E : h(j, 5) < 0.3 ? 0xFFFFF0B0 : Jackpot.GOLD;
            smooth(g, x - w / 2, y - s2 / 2, w, s2, Math.min(w, s2) / 2, alpha(col, gf), alpha(0xFF8A5A14, 0.6 * gf));
        }
    }
}
