package dev.ryaan.ezgamble.vip;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;

/** The tier gem next to a name: a 7 x 7 pixel diamond in the tier's colour, a highlight on top, a darker base. */
public final class VipBadge {
    private VipBadge() {}

    /** Room the badge takes before a name (badge + 2 px), 0 when he has no tier. */
    public static int w(String name) { return Vip.tier(name) == null ? 0 : 9; }

    public static void draw(G g, String name, double x, double y) {
        Vip.Tier t = Vip.tier(name);
        if (t != null) draw(g, t, x, y);
    }

    /** Rows of 3, 5, 7, 5, 3, 1 (a cut gem), 1 px each; the top row lit, the lower rows shaded. */
    public static void draw(G g, Vip.Tier t, double x, double y) {
        int c = t.color(), hi = P.lerp(c, 0xFFFFFFFF, 0.55), lo = P.shade(c, 0.6);
        g.fill(x + 2, y, x + 5, y + 1, hi);
        g.fill(x + 1, y + 1, x + 6, y + 2, c);
        g.fill(x, y + 2, x + 7, y + 3, c);
        g.fill(x + 1, y + 3, x + 6, y + 4, lo);
        g.fill(x + 2, y + 4, x + 5, y + 5, lo);
        g.fill(x + 3, y + 5, x + 4, y + 6, P.shade(c, 0.4));
        g.fill(x + 2, y + 1, x + 3, y + 2, hi);   // a facet catching the light
        if (t.rank() == 4 && (P.now() / 250) % 2 == 0) { g.fill(x + 6, y - 1, x + 7, y, 0xFFFFFFFF); g.fill(x + 5, y, x + 6, y + 1, 0xFFFFFFFF); }   // Diamond twinkles
    }
}
