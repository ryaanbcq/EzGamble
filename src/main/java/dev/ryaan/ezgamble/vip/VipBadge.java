package dev.ryaan.ezgamble.vip;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The tier gem next to a name: a 7 x 7 pixel diamond in the tier's colour, a highlight on top, a darker base. Drawn
 * through Vec, one quad per gem: as fills it was 7 to 8 draws per name in every list (a Chips list of 8 rows, 64 fills
 * for five possible pictures). Tier colours are semantic, a theme switch just repaints (Themes.ver is in Vec's key).
 */
public final class VipBadge {
    private VipBadge() {}

    /** Room the badge takes before a name (badge + 2 px), 0 when he has no tier. */
    public static int w(String name) { return Vip.tier(name) == null ? 0 : 9; }

    public static void draw(G g, String name, double x, double y) {
        Vip.Tier t = Vip.tier(name);
        if (t != null) draw(g, t, x, y);
    }

    /** Rows of 3, 5, 7, 5, 3, 1 (a cut gem), 1 px each; the top row lit, the lower rows shaded. The sprite starts one row above y: the Diamond's twinkle. */
    public static void draw(G g, Vip.Tier t, double x, double y) {
        boolean tw = t.rank() == 4 && (P.now() / 250) % 2 == 0;   // Diamond twinkles
        Vec.draw(g, "gem/" + t.rank() + (tw ? "/tw" : ""), x, y - 1, 7, 7, 0, g2 -> paint(g2, t, tw));
    }

    private static void paint(Graphics2D g2, Vip.Tier t, boolean tw) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);   // pixel art: crisp rows
        int c = t.color(), hi = P.lerp(c, 0xFFFFFFFF, 0.55), lo = P.shade(c, 0.6);
        px(g2, 2, 1, 3, 1, hi);
        px(g2, 1, 2, 5, 1, c);
        px(g2, 0, 3, 7, 1, c);
        px(g2, 1, 4, 5, 1, lo);
        px(g2, 2, 5, 3, 1, lo);
        px(g2, 3, 6, 1, 1, P.shade(c, 0.4));
        px(g2, 2, 2, 1, 1, hi);   // a facet catching the light
        if (tw) { px(g2, 6, 0, 1, 1, 0xFFFFFFFF); px(g2, 5, 1, 1, 1, 0xFFFFFFFF); }
    }

    private static void px(Graphics2D g2, int x, int y, int w, int h, int c) {
        g2.setColor(Vec.c(c));
        g2.fillRect(x, y, w, h);
    }
}
