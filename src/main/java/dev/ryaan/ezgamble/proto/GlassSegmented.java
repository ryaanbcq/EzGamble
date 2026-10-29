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
    /** Liquid Glass lens (tabs, dashboard period) when the "Liquid glass" option is on. */
    public boolean lens;
    // lens state: centre and speed (GUI px, px/s), held / pointer, grow animation, segment under it, frame clock
    private double lx = Double.NaN, lv, dragX, grow;
    private boolean held;
    private int lastSeg = -1;
    private long lastNs;

    public GlassSegmented(double x, double y, double h, String[][] items, Supplier<String> get, Consumer<String> set) {
        super(x, y, 0, h);
        this.items = items; this.get = get; this.set = set;
        widths = new int[items.length];
        int t = 4;
        for (int i = 0; i < items.length; i++) { widths[i] = F.width(items[i][1]) + 12; t += widths[i]; }
        w = t;
    }

    /** Moves it; the pill follows without a jump (prototype: s2.ix += x - px). */
    public void place(double nx, double ny) { if (ix != null) ix += nx - x; if (!Double.isNaN(lx)) { lx += nx - x; dragX += nx - x; } x = nx; y = ny; px = nx; }

    private boolean lensOn() { return lens && dev.ryaan.ezgamble.data.GambleData.lens; }

    public double[] seg(int i) { double sx = x + 2; for (int j = 0; j < i; j++) sx += widths[j]; return new double[]{sx, widths[i]}; }

    @Override
    public void render(G g, double mx, double my) {
        String cur = get.get();
        int sel = -1;
        for (int i = 0; i < items.length; i++) if (items[i][0].equals(cur)) sel = i;
        long t = P.now(), dt = Math.min(64, t - last); last = t;
        if (lensOn() && sel >= 0) { renderLens(g, mx, my, sel); return; }
        lx = Double.NaN; held = false;
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
    public boolean over(double mx, double my) {   // the lens takes a slightly larger hit zone than the labels
        if (!lensOn()) return super.over(mx, my);
        return visible && mx >= x - 4 && mx < x + w + 4 && my >= y - 3 && my < y + h + 3;
    }

    @Override
    public void drag(double mx, double my) { if (held) dragX = mx; }

    @Override
    public void release(double mx, double my) {
        if (!held) return;
        held = false;
        int i = nearest(mx);   // the segment under (or nearest to) the pointer: a plain click lands where it was clicked
        if (!items[i][0].equals(get.get())) { P.play("click"); set.accept(items[i][0]); }
    }

    private double centre(int i) { double[] s = seg(i); return s[0] + s[1] / 2; }

    private int nearest(double px) {
        int best = 0; double bd = Double.MAX_VALUE;
        for (int i = 0; i < items.length; i++) { double d = Math.abs(centre(i) - px); if (d < bd) { bd = d; best = i; } }
        return best;
    }

    /** Width under the lens: the segment widths, blended between the two segments it sits between. */
    private double widthAt(double px) {
        int n = items.length;
        if (px <= centre(0)) return widths[0];
        for (int i = 0; i < n - 1; i++) {
            double a = centre(i), b = centre(i + 1);
            if (px <= b) return widths[i] + (widths[i + 1] - widths[i]) * (px - a) / (b - a);
        }
        return widths[n - 1];
    }

    /**
     * Liquid Glass lens (prototype GlassSegmented.renderLens): a pixel pill that follows the pointer while held,
     * magnifies the labels under it x1.25, and springs onto the nearest segment when released.
     */
    private void renderLens(G g, double mx, double my, int sel) {
        long ns = System.nanoTime();
        double dts = lastNs == 0 ? 0 : Math.min(0.05, (ns - lastNs) / 1e9);
        lastNs = ns;
        if (Double.isNaN(lx)) { lx = centre(sel); lv = 0; grow = 0; lastSeg = sel; }
        // spring on render time, in sub-steps so a slow frame stays stable: stiff and critically damped while it
        // follows the pointer, softer and a little bouncy when it settles
        int n = items.length;
        double lo = centre(0), hi = centre(n - 1);
        double target = held ? Math.max(lo, Math.min(hi, dragX)) : centre(sel);
        double k = held ? 1400 : 520, z = held ? 1 : 0.62, c = 2 * z * Math.sqrt(k);
        for (double left = dts; left > 1e-6; left -= 1 / 240.0) {
            double st = Math.min(1 / 240.0, left);
            lv += (k * (target - lx) - c * lv) * st;
            lx += lv * st;
        }
        grow = held ? Math.min(1, grow + dts / 0.12) : Math.max(0, grow - dts / 0.12);
        int segNow = nearest(lx);
        if (segNow != lastSeg) { lastSeg = segNow; if (held) P.play("click", 1.25, 0.35); }   // a tick for each tab crossed
        double sc = 1 + 0.1 * P.easeOut(grow);
        int LW = (int) Math.round((widthAt(lx) - 12) * 1.25 * sc + 12), LH = (int) Math.round((h + 4) * sc);
        int LX = (int) Math.round(lx - LW / 2.0), LY = (int) Math.round(y + h / 2 - LH / 2.0);
        P.capsule(g, x, y, w, h, "ghost", false);
        // the row's labels outside the lens only (inside, the magnified copy replaces them)
        boolean ov = over(mx, my);
        g.enableScissor(x - 6, y - 6, LX, y + h + 6); labels(g, mx, ov, false); g.disableScissor();
        g.enableScissor(LX + LW, y - 6, x + w + 6, y + h + 6); labels(g, mx, ov, false); g.disableScissor();
        LensSprite.draw(g, LX, LY, LW, LH);
        // the loupe: the same row x1.25 around the lens centre, clipped to the lens
        double cy = y + h / 2;
        g.enableScissor(LX + 1, LY + 1, LX + LW - 1, LY + LH - 1);
        g.push(); g.translate(lx, cy); g.scale(1.25, 1.25); g.translate(-lx, -cy);
        labels(g, mx, ov, true);
        g.pop();
        g.disableScissor();
    }

    private void labels(G g, double mx, boolean ov, boolean white) {
        for (int i = 0; i < items.length; i++) {
            double[] s = seg(i);
            boolean hov = ov && mx >= s[0] && mx < s[0] + s[1];
            g.drawText(items[i][1], s[0] + (int) ((s[1] - F.width(items[i][1])) / 2), y + (int) ((h - 8) / 2), white || hov ? P.WHITE : P.TEXT, true);
        }
    }

    @Override
    public void click(double mx, double my) {
        if (lensOn() && !Double.isNaN(lx)) { held = true; dragX = mx; return; }
        for (int i = 0; i < items.length; i++) {
            double[] s = seg(i);
            if (mx >= s[0] && mx < s[0] + s[1] && !items[i][0].equals(get.get())) { P.play("click"); set.accept(items[i][0]); }
        }
    }
}
