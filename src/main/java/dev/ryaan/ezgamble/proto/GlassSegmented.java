package dev.ryaan.ezgamble.proto;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Segmented control: one clear capsule, a brighter pill that slides to the selected segment and stretches as it moves. */
public class GlassSegmented extends Widget {
    public final String[][] items;   // {id, label}
    public final int[] widths;
    public final Supplier<String> get;
    public final Consumer<String> set;
    public Double ix, iw, px;
    private long last = P.now();

    public GlassSegmented(double x, double y, double h, String[][] items, Supplier<String> get, Consumer<String> set) {
        super(x, y, 0, h);
        this.items = items; this.get = get; this.set = set;
        widths = new int[items.length];
        int t = 4;
        for (int i = 0; i < items.length; i++) { widths[i] = F.width(items[i][1]) + 12; t += widths[i]; }
        w = t;
    }

    /** Moves it; the pill follows without a jump (prototype: s2.ix += x - px). */
    public void place(double nx, double ny) { if (ix != null) ix += nx - x; x = nx; y = ny; px = nx; }

    public double[] seg(int i) { double sx = x + 2; for (int j = 0; j < i; j++) sx += widths[j]; return new double[]{sx, widths[i]}; }

    @Override
    public void render(G g, double mx, double my) {
        String cur = get.get();
        int sel = -1;
        for (int i = 0; i < items.length; i++) if (items[i][0].equals(cur)) sel = i;
        long t = P.now(), dt = Math.min(64, t - last); last = t;
        P.capsule(g, x, y, w, h, "ghost", false);
        if (sel >= 0) {
            double[] s = seg(sel);
            if (ix == null) { ix = s[0]; iw = s[1]; }
            double f = 1 - Math.exp(-dt / 70.0), moving = Math.abs(s[0] - ix);
            ix += (s[0] - ix) * f; iw += (s[1] - iw) * f;
            double stretch = Math.min(10, moving * 0.35);
            P.capsule(g, ix - stretch / 2, y + 2, iw + stretch, h - 4, "selected", over(mx, my));
        } else ix = null;
        for (int i = 0; i < items.length; i++) {
            double[] s = seg(i);
            boolean hov = mx >= s[0] && mx < s[0] + s[1] && over(mx, my);
            g.drawText(items[i][1], s[0] + (int) ((s[1] - F.width(items[i][1])) / 2), y + (int) ((h - 8) / 2), i == sel || hov ? P.WHITE : P.TEXT, true);
        }
    }

    @Override
    public void click(double mx, double my) {
        for (int i = 0; i < items.length; i++) {
            double[] s = seg(i);
            if (mx >= s[0] && mx < s[0] + s[1] && !items[i][0].equals(get.get())) { P.play("click"); set.accept(items[i][0]); }
        }
    }
}
