package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A game's own 32 px icon drawn at `size`; a game without one (Slots, Buckshot) keeps its item. Events, Books and Jackpot
 * are painted in Java2D through Vec (one quad each, repainted on a theme switch): as fills they were 41, 23 and 63 draws
 * per frame each, 127 for the Games grid alone, and hardcoded the Lavender palette.
 */
public final class Icons {
    private Icons() {}
    private static final Set<String> OWN = Set.of("blackjack", "auction", "mines", "crates", "roulette", "crash", "coinflip", "rain");
    private static final Map<String, String> ICON = Map.of("Roulette", "clock", "Blackjack", "paper", "Coinflip", "gold_nugget", "Crash", "firework_rocket",
            "Slots", "emerald", "Buckshot", "crossbow", "Crates", "chest_minecart");

    public static void drawGameIcon(G g, String game, double x, double y, double size) {
        String k = String.valueOf(game).toLowerCase(Locale.ROOT);
        if (k.equals("events")) { drawEvents(g, x, y, size); return; }   // painted through Vec, no sprite file
        if (k.equals("books")) { drawBooks(g, x, y, size); return; }   // the Books tile: Vec, no sprite file
        if (k.equals("jackpot")) { drawJackpot(g, x, y, size); return; }   // the Jackpot tile and its rows: Vec, no sprite file
        if (OWN.contains(k)) g.drawSprite("game/" + k, x, y, size, size);
        else g.drawItem(ICON.getOrDefault(game, "paper"), x + (size - 16) / 2, y + (size - 16) / 2);
    }

    /** Events (events.Events): a gold stopwatch on a 32 px grid, its hand near the top of the hour, a pin in the theme's pink. */
    private static void drawEvents(G g, double x, double y, double size) {
        Vec.draw(g, "icon/events/" + Math.round(size), x, y, size, size, 1, g2 -> paintEvents(g2, size));
    }

    /** Books: a ledger (cover in the theme's lavender, dark spine, a pale page) with three rising bars on the page, 32 px art scaled to `size`. */
    public static void drawBooks(G g, double x, double y, double size) {
        Vec.draw(g, "icon/books/" + Math.round(size), x, y, size, size, 1, g2 -> paintBooks(g2, size));
    }

    /** Jackpot: a big gold coin (rim, face, a "$") over two coins lying behind it, a sparkle top right. 32 px art scaled to `size`. */
    public static void drawJackpot(G g, double x, double y, double size) {
        Vec.draw(g, "icon/jackpot/" + Math.round(size), x, y, size, size, 1, g2 -> paintJackpot(g2, size));
    }

    // ---- the Java2D painters: the same shapes at the same 32-unit coordinates, u = one icon pixel. Gold, greens and the
    // dial greys stay literal (semantic); the lavender, pink and text greys come from the theme (Vec's key carries Themes.ver).

    private static void paintEvents(Graphics2D g2, double size) {
        double u = size / 32;
        int gold = 0xFFF2C14E, dark = 0xFFC8902E, face = 0x2EF2C14E;
        rect(g2, 13 * u, 1 * u, 6 * u, 2 * u, gold);   // the crown button and its stem
        rect(g2, 14.5 * u, 3 * u, 3 * u, 3 * u, dark);
        rect(g2, 23 * u, 5 * u, 3 * u, 2 * u, dark);   // the side button
        disc(g2, 4 * u, 6 * u, 24 * u, face, gold);   // the case
        disc(g2, 7 * u, 9 * u, 18 * u, 0x30000000, 0x40FFFFFF);   // the dial
        int tick = P.TEXT;
        rect(g2, 15.5 * u, 10 * u, 1 * u, 2 * u, tick); rect(g2, 15.5 * u, 24 * u, 1 * u, 2 * u, tick);   // 12 and 6
        rect(g2, 8 * u, 17.5 * u, 2 * u, 1 * u, tick); rect(g2, 22 * u, 17.5 * u, 2 * u, 1 * u, tick);   // 9 and 3
        rect(g2, 15.5 * u, 12 * u, 1 * u, 6 * u, 0xFFFFFFFF);   // the big hand, up
        rect(g2, 16 * u, 17.5 * u, 5 * u, 1 * u, 0xFFFFFFFF);   // the small hand, to 3
        rect(g2, 15 * u, 17 * u, 2 * u, 2 * u, P.PINK);   // the pin
    }

    private static void paintBooks(Graphics2D g2, double size) {
        double u = size / 32;
        g2.setPaint(new GradientPaint(0, (float) (3 * u), Vec.c(P.shade(P.LAV, 0.66)), 0, (float) (29 * u), Vec.c(P.shade(P.LAV, 0.46))));   // the cover
        g2.fill(new RoundRectangle2D.Double(6 * u, 3 * u, 21 * u, 26 * u, 6 * u, 6 * u));
        outline(g2, new RoundRectangle2D.Double(6 * u, 3 * u, 21 * u, 26 * u, 6 * u, 6 * u), P.alpha(P.PINK, 0.4), lw(g2, 0.6));
        rect(g2, 6 * u, 5 * u, 3 * u, 22 * u, P.shade(P.LAV, 0.3));   // the spine
        rect(g2, 11 * u, 6 * u, 14 * u, 20 * u, P.lerp(P.PINK, P.WHITE, 0.6));   // the page
        for (int i = 0; i < 3; i++) rect(g2, 13 * u, (9 + i * 3) * u, 10 * u, 1 * u, 0x30403060);   // ruled lines
        int[] hs = {4, 7, 10};   // three bars rising, the last one the mod's green
        for (int i = 0; i < 3; i++) rect(g2, (13 + i * 4) * u, (24 - hs[i]) * u, 3 * u, hs[i] * u, i == 2 ? 0xFF4BE07A : 0xFF3BBE63);
    }

    private static void paintJackpot(Graphics2D g2, double size) {
        double u = size / 32;
        int gold = 0xFFF2C14E, dark = 0xFF9A6A1C, light = 0xFFFFF0B0;
        round(g2, 2 * u, 22 * u, 18 * u, 8 * u, 4 * u, 0xFFC8902E, dark);    // the coins lying behind, edge on
        round(g2, 12 * u, 24 * u, 18 * u, 8 * u, 4 * u, 0xFFD9A62E, dark);
        g2.setPaint(new GradientPaint(0, (float) (3 * u), Vec.c(light), 0, (float) (25 * u), Vec.c(gold)));   // the big coin: rim and face
        g2.fill(new Ellipse2D.Double(5 * u, 3 * u, 22 * u, 22 * u));
        outline(g2, new Ellipse2D.Double(5 * u, 3 * u, 22 * u, 22 * u), dark, lw(g2, 0.6));
        outline(g2, new Ellipse2D.Double(8 * u, 6 * u, 16 * u, 16 * u), 0x80B8821E, Math.max(1, u) * 0.75);   // the inner ring of the face (P.ring's width)
        rect(g2, 15 * u, 9 * u, 2 * u, 10 * u, dark);   // a "$" stroke: the bar and its two bowls
        rect(g2, 12 * u, 10 * u, 8 * u, 2 * u, dark); rect(g2, 12 * u, 16 * u, 8 * u, 2 * u, dark);
        rect(g2, 12 * u, 12 * u, 2 * u, 2 * u, dark); rect(g2, 18 * u, 14 * u, 2 * u, 2 * u, dark);
        rect(g2, 12 * u, 13 * u, 8 * u, 2 * u, dark);
        rect(g2, 26 * u, 1 * u, 2 * u, 6 * u, light); rect(g2, 24 * u, 3 * u, 6 * u, 2 * u, light);   // the sparkle
    }

    private static void rect(Graphics2D g2, double x, double y, double w, double h, int c) {
        g2.setColor(Vec.c(c));
        g2.fill(new Rectangle2D.Double(x, y, w, h));
    }

    /** P.smooth's look: a rounded rect filled, its stroke just inside the edge. */
    private static void round(Graphics2D g2, double x, double y, double w, double h, double r, int fill, int stroke) {
        g2.setColor(Vec.c(fill));
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r));
        if (stroke != 0) outline(g2, new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r), stroke, lw(g2, 0.6));
    }

    private static void disc(Graphics2D g2, double x, double y, double d, int fill, int stroke) {
        g2.setColor(Vec.c(fill));
        g2.fill(new Ellipse2D.Double(x, y, d, d));
        if (stroke != 0) outline(g2, new Ellipse2D.Double(x, y, d, d), stroke, lw(g2, 0.6));
    }

    /** A stroke of width lw (GUI px) drawn inside the shape's edge, as Liquid's strokes are. */
    private static void outline(Graphics2D g2, java.awt.geom.RectangularShape s, int c, double lw) {
        java.awt.geom.RectangularShape in = (java.awt.geom.RectangularShape) s.clone();
        in.setFrame(s.getX() + lw / 2, s.getY() + lw / 2, s.getWidth() - lw, s.getHeight() - lw);
        g2.setColor(Vec.c(c));
        g2.setStroke(new BasicStroke((float) lw));
        g2.draw(in);
    }

    /** Liquid's line width: 0.6 GUI px, at least one real pixel (the Graphics2D is scaled by the real pixel density). */
    private static double lw(Graphics2D g2, double gui) { return Math.max(gui, 1 / Math.max(1e-6, g2.getTransform().getScaleX())); }
}
