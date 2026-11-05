package dev.ryaan.ezgamble.achievements;

import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;

import java.awt.RenderingHints;
import java.util.List;

/**
 * The 9 x 9 pixel badge icons. Each icon is painted once into a Vec sprite (cached by id, scale and lit / dim) so a row of
 * 24 badges costs 24 texture draws, not 24 x 30 fills. Also the profile's badges row and the glass tooltip both views use.
 */
public final class BadgeIcons {
    private BadgeIcons() {}

    public static final int SIZE = 9;

    /** The icon at (x, y), `scale` GUI px per cell (1 in rows, 2 in the grid); lit = earned, dim = locked (faint, greyed). */
    public static void draw(G g, Achievement a, double x, double y, int scale, boolean lit) {
        double s = SIZE * scale;
        Vec.draw(g, "badge/" + a.id() + "/" + scale + (lit ? "" : "/dim"), x, y, s, s, 0, g2 -> {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);   // pixel art: crisp cells
            int main = a.main(), second = a.second();
            int[] pal = {main, P.shade(main, 0.6), P.lerp(main, 0xFFFFFFFF, 0.55), second, P.shade(second, 0.6)};
            if (!lit) for (int i = 0; i < pal.length; i++) pal[i] = P.alpha(P.lerp(pal[i], P.DIM, 0.6), 0.4);   // locked: greyed toward the theme's dim (Vec's key carries Themes.ver)
            String[] px = a.px();
            for (int r = 0; r < SIZE && r < px.length; r++) {
                String row = px[r];
                for (int c = 0; c < SIZE && c < row.length(); c++) {
                    char ch = row.charAt(c);
                    if (ch < 'A' || ch > 'E') continue;
                    int run = 1;   // one rect per run of the same colour
                    while (c + run < row.length() && row.charAt(c + run) == ch) run++;
                    g2.setColor(Vec.c(pal[ch - 'A']));
                    g2.fillRect(c * scale, r * scale, run * scale, scale);
                    c += run - 1;
                }
            }
        });
    }

    /** The profile's badges row at y (16 px tall): "Badges N / 24", then the 24 icons, earned lit, locked faint. @return the hovered badge or null. */
    public static Achievement row(G g, String name, double lx, double rx, double y, double mx, double my) {
        List<Achievements.Earned> earned = Achievements.of(name);
        boolean hover = mx >= lx && mx < rx && my >= y && my < y + 16;
        if (hover) P.smooth(g, lx, y - 1, rx - lx, 17, 6, 0x0FFFFFFF, 0x30FFFFFF);   // the row is a link to the catalogue
        g.drawText("Badges " + earned.size() + " / " + Achievements.TOTAL, lx + 8, y + 3, hover ? P.WHITE : P.TEXT, false);
        List<Achievement> cat = Achievements.catalogue();
        double x0 = rx - 7 - cat.size() * 10 + 1;   // 9 px icons, 1 px apart, right-aligned (the label never runs under them)
        Achievement hit = null;
        for (int i = 0; i < cat.size(); i++) {
            Achievement a = cat.get(i);
            boolean lit = false;
            for (Achievements.Earned e : earned) if (e.achievement() == a) { lit = true; break; }
            double x = x0 + i * 10;
            draw(g, a, x, y + 3, 1, lit);
            if (mx >= x - 1 && mx < x + 10 && my >= y && my < y + 16) hit = a;
        }
        return hit;
    }

    /** When he earned it, or null. */
    public static Long earnedAt(String name, Achievement a) {
        for (Achievements.Earned e : Achievements.of(name)) if (e.achievement() == a) return e.at();
        return null;
    }

    /** A glass tooltip above the pointer: name in the rarity colour, the rarity, the description, the unlock date or "Locked". Kept inside [lx, rx]. */
    public static void tooltip(G g, Achievement a, Long at, double mx, double my, double lx, double rx, double top) {
        String l1 = a.name(), l1b = a.rarityName(), l2 = a.desc(), l3 = at != null ? "Unlocked " + P.DATE(at) : "Locked";
        int w = Math.max(F.width(l1) + 6 + F.width(l1b), Math.max(F.width(l2), F.width(l3))) + 12, h = 36;
        double x = Math.min(Math.max(lx, mx - w / 2.0), rx - w), y = my - h - 6;
        if (y < top) y = my + 12;   // no room above: below the pointer
        P.glass(g, x, y, w, h, 6, 0.85, 0.8);
        P.ring(g, x, y, w, h, 6, P.alpha(a.color(), 0.5), 1);
        g.drawText(l1, x + 6, y + 5, a.color(), false);
        g.drawText(l1b, x + 6 + F.width(l1) + 6, y + 5, P.DIM, false);
        g.drawText(l2, x + 6, y + 15, P.TEXT, false);
        g.drawText(l3, x + 6, y + 25, at != null ? P.GREEN : P.DIM, false);
    }
}
