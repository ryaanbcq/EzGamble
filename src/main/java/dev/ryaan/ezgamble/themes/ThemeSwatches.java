package dev.ryaan.ezgamble.themes;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.Liquid;

/** The HUD tab's theme row: one round two-tone swatch per preset (lav left, pink right), the current one ringed in white. */
public class ThemeSwatches extends Widget {
    private static final int SW = 10, PITCH = 12;
    /** The swatch under the pointer, -1 when none (the row's caption names it). */
    public int hover = -1;

    public ThemeSwatches(double x, double y) { super(x, y, width(), SW); }

    public static int width() { return Themes.ALL.size() * PITCH - 2; }

    /** The theme the pointer is on, else the current one: what the caption shows. */
    public Theme shown() { return hover >= 0 ? Themes.ALL.get(hover) : Themes.current(); }

    @Override
    public void render(G g, double mx, double my) {
        hover = -1;
        for (int i = 0; i < Themes.ALL.size(); i++) {
            Theme t = Themes.ALL.get(i);
            double sx = x + i * PITCH;
            boolean sel = t == Themes.current(), hov = over(mx, my) && mx >= sx && mx < sx + SW;
            if (hov) hover = i;
            // a hard split at the middle, not a gradient: the two accents read at a glance even at 10 px
            Liquid.Paint two = new Liquid.Paint() {
                public int at(float u, float v) { return u < 0.5f ? t.lav() : t.pink(); }
                public boolean horizontal() { return true; }
            };
            P.smooth(g, sx, y, SW, SW, SW / 2.0, two, 0x59FFFFFF);
            if (sel) P.ring(g, sx - 2, y - 2, SW + 4, SW + 4, SW / 2.0 + 2, P.WHITE, 1);
            else if (hov) P.ring(g, sx - 2, y - 2, SW + 4, SW + 4, SW / 2.0 + 2, 0x80FFFFFF, 1);
        }
    }

    @Override
    public void click(double mx, double my) {
        int i = (int) Math.floor((mx - x) / PITCH);
        if (i < 0 || i >= Themes.ALL.size() || mx - x - i * PITCH >= SW) return;   // the 2 px gap between two swatches
        P.play("click");
        GambleData.setTheme(Themes.ALL.get(i).id());   // applied at once, saved with the config
    }
}
