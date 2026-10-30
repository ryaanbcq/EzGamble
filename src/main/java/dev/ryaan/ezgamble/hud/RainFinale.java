package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.games.rain.RainTable;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;

import static dev.ryaan.ezgamble.hud.HudConfig.sh;
import static dev.ryaan.ezgamble.hud.HudConfig.sw;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The rain's finale, over the whole screen, once the cloud is full (gen_rain_finale.py for its sprites). One scene:
 *   charge    the full cloud trembles and swells, the screen dims around it, a pastel lightning flash and thunder
 *   sky       the cloud rises and grows over the top of the screen
 *   downpour  iridescent drops on three depths over the whole screen, splashing at the bottom, gold coins spinning
 *             down among them; the cloud empties as it rains, the pot counts up in the middle
 *   draw      the players' heads run through, slowing down, in step with the menu's reel (same strip, same clock),
 *             and stop on the winner: a second flash, a fountain of coins, a rainbow drawn behind the name
 * Times are from `start` (the burst, after the last drop has landed) except the draw, tied to the round's own clock.
 */
final class RainFinale {
    private RainFinale() {}

    private static final long CHARGE = 450, RISE = 600, RAIN0 = 900, RAIN1 = 3900, HOLD = 2600, FADE = 700;
    private static final int DROPS = 720, DRIZZLE = 160, COINS = 110, FOUNTAIN = 40;
    private static final int[] TINTS = {0xFFFFFFFF, 0xFFB9A4FF, 0xFFFF9FD8, 0xFF9FD0FF, 0xFFD9C9FF};
    private static long soundsOf;
    private static int soundsPlayed;

    static long end(RainTable.Round r) { return r.at + RainTable.SPIN_MS + HOLD + FADE; }

    /** A fixed random number in [0, 1) per particle and use: the scene is the same at every frame. */
    private static double h(int i, int k) {
        long x = i * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31; x *= 0x94D049BB133111EBL; x ^= x >>> 29; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 32;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smoothstep(double v) { v = clamp01(v); return v * v * (3 - 2 * v); }

    private static int mix(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int gg = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bb = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    /** Text in the Outmind gradient, centred on cx, `sc` times the font size, with its shadow. */
    private static void gradText(G g, String s, double cx, double y, double sc, double a) {
        double w = F.width(s) * sc, x = 0;
        g.push(); g.translate(cx - w / 2, y); g.scale(sc, sc);
        int n = s.length();
        for (int i = 0; i < n; i++) {
            String ch = s.substring(i, i + 1);
            g.drawText(ch, x, 0, alpha(mix(0xFFC7B3FF, 0xFFFFC6EC, n == 1 ? 0 : i / (double) (n - 1)), a), true);
            x += F.width(ch);
        }
        g.pop();
    }

    private static void centred(G g, String s, double cx, double y, double sc, int color) {
        g.push(); g.translate(cx - F.width(s) * sc / 2, y); g.scale(sc, sc); g.drawText(s, 0, 0, color, true); g.pop();
    }

    private static void sounds(long start, long e, long sinceLand) {
        if (soundsOf != start) { soundsOf = start; soundsPlayed = 0; }
        if (soundsPlayed == 0 && e >= 300) { soundsPlayed++; play("mc_thunder", 1.2, 0.55); }
        if (soundsPlayed == 1 && e >= RAIN0) { soundsPlayed++; play("mc_rain", 1.0, 0.9); }
        if (soundsPlayed == 2 && e >= RAIN0 + 1400) { soundsPlayed++; play("mc_rain", 0.9, 0.8); }
        if (soundsPlayed == 3 && e >= 2700) { soundsPlayed++; play("mc_chime", 1.2, 0.9); }
        if (soundsPlayed == 4 && sinceLand >= 0) { soundsPlayed++; play("mc_chime", 0.8, 1); play("mc_chime", 1.6, 0.8); }
    }

    static void draw(G g, RainTable rn, long start, long t) {
        RainTable.Round r = rn.round;
        if (r == null) return;
        double W = sw(), H = sh();
        long e = t - start, land = r.at + RainTable.SPIN_MS, sinceLand = t - land, endT = end(r);
        double gf = clamp01((endT - t) / (double) FADE);                   // everything fades out at the very end
        sounds(start, e, sinceLand);

        // ---- the screen dims around the scene
        double v = smoothstep(e / 500.0) * gf;
        g.fill(0, 0, W, H, alpha(0xFF1A0C2E, 0.28 * v));
        g.drawSprite("rain/vignette", 0, 0, W, H, alpha(0xFFFFFFFF, 0.95 * v));

        // ---- the cloud: charge at its HUD place, then up into the sky, emptying as it rains, then gone
        double big = Math.min(W * 0.62 / 76, H * 0.48 / 74), skyY = H * 0.2;
        double cx = W / 2, cy = 43, sc = 1, ca = 1, k = 1;
        if (e < CHARGE) {
            double c = e / (double) CHARGE;
            cx += Math.sin(e * 0.09) * 1.4 * c; cy += Math.cos(e * 0.13) * 0.9 * c;
            sc = 1 + 0.12 * easeOut(c) + 0.03 * Math.sin(e / 38.0);
        } else {
            double p = smoothstep((e - CHARGE) / (double) RISE);
            cy = 43 + (skyY - 43) * p; sc = 1.12 + (big - 1.12) * p; ca = 1 - 0.1 * p;
        }
        if (e > RAIN0) k = 1 - clamp01((e - RAIN0) / (double) (RAIN1 - 400 - RAIN0));
        if (e > RAIN1 - 200) { double d = clamp01((e - (RAIN1 - 200)) / 700.0); ca *= 1 - d; sc *= 1 + 0.15 * easeOut(d); }
        if (e < CHARGE + RISE) {   // a glow gathering behind the cloud
            double gl = Math.sin(clamp01(e / (double) (CHARGE + RISE)) * Math.PI);
            double gs = 130 * sc;
            g.drawSprite("rain/glow", cx - gs / 2, cy - gs / 2, gs, gs, alpha(0xFFFFE8FA, 0.55 * gl));
        }
        if (ca > 0.01) {
            g.push(); g.translate(cx, cy); g.scale(sc, sc); g.translate(-38, -37);
            GambleHud.drawCloudBody(g, k, ca * gf);
            g.pop();
        }
        double cloudHalf = 38 * big, cloudBottom = skyY + 28 * big;

        // ---- lightning: one at the charge, one when the winner lands
        if (e >= 300 && e < 560) g.fill(0, 0, W, H, alpha(0xFFFFF4FD, 0.5 * (1 - (e - 300) / 260.0)));
        if (sinceLand >= 0 && sinceLand < 320) g.fill(0, 0, W, H, alpha(0xFFFFFFFF, 0.42 * (1 - sinceLand / 320.0)));

        // ---- the downpour: three depths (far = thin, slow, landing high on screen), a little wind, a crown where
        // each lands; then a drizzle until the winner is shown
        long drawEnd = land - start + 1200;
        for (int i = 0; i < DROPS + DRIZZLE; i++) {
            boolean drizzle = i >= DROPS;
            double d = h(i, 1);
            double t0 = drizzle ? RAIN1 - 400 + h(i, 3) * Math.max(0, drawEnd - RAIN1) : RAIN0 + h(i, 3) * (RAIN1 - RAIN0 - 400), el = e - t0;
            if (el < 0) continue;
            double x = h(i, 2) * W;
            double y0 = !drizzle && Math.abs(x - W / 2) < cloudHalf * 0.85 && e < RAIN1 ? cloudBottom - h(i, 4) * 26 : -14 - h(i, 4) * 50;
            double speed = H * (0.7 + 0.9 * d), y = y0 + speed * el / 1000, ground = H * (0.78 + 0.19 * d);
            x += el / 1000 * W * 0.04;
            int tint = TINTS[(int) (h(i, 5) * TINTS.length)];
            double a = (0.5 + 0.5 * d) * gf * (drizzle ? 0.7 : 1);
            if (y < ground) {
                double len = 8 + 26 * d, w = d < 0.4 ? 1 : d < 0.8 ? 2 : 3;
                g.drawSprite("rain/streak", x - w / 2, y - len, w, len, alpha(tint, a));
            } else {
                double since = (y - ground) / speed * 1000;
                if (since > 280) continue;
                double f = since / 280, sw2 = (6 + 14 * d) * (0.55 + 0.7 * f);
                g.drawSprite("rain/splash", x - sw2 / 2, ground - sw2 / 2, sw2, sw2 / 2, alpha(tint, a * (1 - f)));
            }
        }
        // gold coins falling out of the cloud, spinning
        for (int j = 0; j < COINS; j++) {
            double t0 = RAIN0 + 200 + h(j, 13) * (RAIN1 - RAIN0 - 900), el = (e - t0) / 1000.0;
            if (el < 0) continue;
            double x = W / 2 + (h(j, 12) - 0.5) * cloudHalf * 1.8 + Math.sin(el * 3 + j) * 5;
            double y = cloudBottom - 6 + 18 * el + 0.5 * H * 1.1 * el * el;
            if (y > H + 10) continue;
            double size = 7 + 6 * h(j, 14), spin = Math.cos(el * 9 + j), w = size * Math.max(0.12, Math.abs(spin));
            g.drawSprite("rain/coin", x - w / 2, y - size / 2, w, size, alpha(spin < 0 ? 0xFFD8C8A8 : 0xFFFFFFFF, gf));
        }

        // ---- the pot, counting up while it rains
        long slotStart = Math.max(start + 3700, land - 2300);
        double potA = clamp01((e - 1200) / 250.0) * (1 - clamp01((t - slotStart) / 300.0));
        if (potA > 0) {
            double c = easeOut(clamp01((e - 1200) / 1500.0));
            String amt = Money.format(Math.round(r.pot * c));
            double s = Math.min(4, W * 0.55 / Math.max(1, F.width(Money.format(r.pot))));
            s *= 1 + 0.3 * (1 - easeOut(clamp01((e - 1200) / 260.0))) + (c >= 1 ? 0.06 * Math.max(0, 1 - (e - 2700) / 300.0) : 0);
            double gs = W * 0.6;
            g.drawSprite("rain/glow", W / 2 - gs / 2, H * 0.57 - gs * 0.22, gs, gs * 0.44, alpha(0xFF2A1450, 0.55 * potA));
            gradText(g, amt, W / 2, H * 0.57 - 4.5 * s, s, potA * gf);
            int players = (int) r.strip.stream().distinct().count();
            String sub = "It's raining on " + rn.tickets.size() + " player" + (rn.tickets.size() == 1 ? "" : "s");
            centred(g, players > 0 ? sub : "", W / 2, H * 0.57 + 4.5 * s + 6, 1.25, alpha(0xFFF4EEFF, potA * gf));
        }

        // ---- the draw: heads running through with the reel, then the winner
        if (t >= slotStart) {
            double hy = H * 0.44, size = 40;
            double sa = clamp01((t - slotStart) / 250.0) * gf;
            String name;
            double pop = 1;
            if (sinceLand < 0) {
                // the menu's reel, on screen: heads sliding by under a marker, slowing down
                double pos = rn.pos(t);
                int first = (int) Math.floor((pos - W / 2) / RainTable.CELL) - 1, last = (int) Math.ceil((pos + W / 2) / RainTable.CELL) + 1;
                for (int c = Math.max(0, first); c <= Math.min(r.strip.size() - 1, last); c++) {
                    double x = W / 2 + (c * RainTable.CELL + RainTable.CELL / 2.0 - pos) * 1.15;
                    double edge = clamp01(1 - Math.abs(x - W / 2) / (W * 0.42));
                    if (edge <= 0) continue;
                    double hs = 26 + 6 * edge;
                    g.drawHead(r.strip.get(c), x - hs / 2, hy - hs / 2, hs);
                    g.fill(x - hs / 2, hy - hs / 2, x + hs / 2, hy + hs / 2, alpha(0xFF1A0C2E, (1 - edge) * 0.8));
                }
                // the marker: a glowing pink line through the middle
                g.drawSprite("rain/glow", W / 2 - 30, hy - 40, 60, 80, alpha(0xFFFFB8E4, 0.45 * sa));
                g.fill(W / 2 - 0.5, hy - 26, W / 2 + 0.5, hy + 26, alpha(0xFFFFC6EC, sa));
                centred(g, "Drawing the winner", W / 2, hy - 44, 1.5, alpha(0xFFF4EEFF, sa));
                name = null;
            } else {
                name = r.winner;
                double p = clamp01(sinceLand / 420.0);
                pop = 1 + 0.45 * Math.sin(p * Math.PI) * (1 - p) + 0.0;
                // the rainbow, drawn left to right behind the winner
                double rw = Math.min(W * 0.9, H * 1.4), rh = rw / 2, rx = W / 2 - rw / 2, ry = hy + 26 - rh;
                double sweep = smoothstep(sinceLand / 900.0);
                g.enableScissor(rx, ry, rx + rw * sweep, ry + rh);
                g.drawSprite("rain/rainbow", rx, ry, rw, rh, alpha(0xFFFFFFFF, 0.9 * gf));
                g.disableScissor();
                double gs = 120;
                g.drawSprite("rain/glow", W / 2 - gs / 2, hy - gs / 2, gs, gs, alpha(0xFFFFF0FA, 0.5 * gf * (1 - 0.5 * p)));
                // a fountain of coins out of the winner
                for (int j = 0; j < FOUNTAIN; j++) {
                    double el = (sinceLand - h(j, 21) * 300) / 1000.0;
                    if (el < 0) continue;
                    double vx = (h(j, 22) - 0.5) * W * 0.5, vy = -(H * 0.55 + h(j, 23) * H * 0.35);
                    double x = W / 2 + vx * el, y = hy + vy * el + 0.5 * H * 1.6 * el * el;
                    if (y > H + 10) continue;
                    double s2 = 6 + 4 * h(j, 24), spin = Math.cos(el * 10 + j), w = s2 * Math.max(0.12, Math.abs(spin));
                    g.drawSprite("rain/coin", x - w / 2, y - s2 / 2, w, s2, alpha(spin < 0 ? 0xFFD8C8A8 : 0xFFFFFFFF, gf));
                }
                double ta = clamp01((sinceLand - 150) / 250.0) * gf;
                gradText(g, name, W / 2, hy + size / 2 + 10, 3, ta);
                centred(g, "takes " + Money.format(r.prize), W / 2, hy + size / 2 + 10 + 30, 1.5, alpha(0xFFFFFFFF, ta));
            }
            if (name != null) {
                g.push(); g.translate(W / 2, hy); g.scale(pop, pop);
                smooth(g, -size / 2 - 3, -size / 2 - 3, size + 6, size + 6, 7, alpha(0xFFFFFFFF, 0.9 * sa));
                g.drawHead(name, -size / 2, -size / 2, size);
                g.pop();
            }
        }
    }
}
