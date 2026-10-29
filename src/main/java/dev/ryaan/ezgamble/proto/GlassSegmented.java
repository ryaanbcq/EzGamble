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
    // jelly: squash/stretch amount and its speed, lean toward the motion (-1..1)
    private double q, qv, lean;

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

    private boolean lensOn() { return lens && dev.ryaan.ezgamble.data.GambleData.lens && dev.ryaan.ezgamble.ui.LensGlass.ok(); }

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
        qv -= 3.5;   // let go: it springs back taller, then settles
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
        // spring on render time, in sub-steps so a slow frame stays stable: soft and a little late while it
        // follows the pointer, softer and a little bouncy when it settles
        int n = items.length;
        double lo = centre(0), hi = centre(n - 1);
        double target = held ? Math.max(lo, Math.min(hi, dragX)) : centre(sel);
        double k = held ? 620 : 420, z = held ? 0.72 : 0.55, c = 2 * z * Math.sqrt(k);
        for (double left = dts; left > 1e-6; left -= 1 / 240.0) {
            double st = Math.min(1 / 240.0, left);
            lv += (k * (target - lx) - c * lv) * st;
            lx += lv * st;
        }
        grow = held ? Math.min(1, grow + dts / 0.12) : Math.max(0, grow - dts / 0.12);
        // jelly: the shape has its own soft, under-damped spring driven by the speed: stretched along the motion and
        // squashed across while it travels, a wobble when it stops; the front swells (lean) like a blob being dragged
        double want = Math.min(0.6, Math.abs(lv) / 520), jk = 150, jc = 2 * 0.2 * Math.sqrt(jk);
        for (double left = dts; left > 1e-6; left -= 1 / 240.0) {
            double st = Math.min(1 / 240.0, left);
            qv += (jk * (want - q) - jc * qv) * st;
            q += qv * st;
        }
        lean += (Math.max(-1, Math.min(1, lv / 700)) - lean) * (1 - Math.exp(-dts / 0.05));
        int segNow = nearest(lx);
        if (segNow != lastSeg) { lastSeg = segNow; if (held) P.play("click", 1.25, 0.35); }   // a tick for each tab crossed
        double pr = P.easeOut(grow), sc = 1 + 0.15 * pr, mag = 1.1 + 0.1 * pr;
        double qq = Math.max(-0.3, Math.min(0.6, q)), base = h * 1.35;
        double LH = base * sc * (1 - 0.45 * qq), LW = Math.max(base * 1.4, (widthAt(lx) - 12) * mag + base * 0.75) * sc * (1 + qq);
        double cy = y + h / 2;
        P.capsule(g, x, y, w, h, "ghost", false);
        boolean ov = over(mx, my);
        labels(g, mx, ov, false);
        // the glass: a shader over what is already drawn (the row and its labels), one draw
        // fixed area around the row (the lens, its stretch and its shadow stay inside): cached layer bounds
        dev.ryaan.ezgamble.ui.LensGlass.draw(g, lx, cy, LW, LH, pr, mag, lean * Math.min(1, Math.abs(qq) * 2.5),
                x - base * 2.5, y - base * 1.6, x + w + base * 2.5, y + h + base * 1.6);
        // then the labels again, crisp and magnified exactly like the shader's centre, only in the flat middle of the lens
        double bw = LH * 0.25 * (1 + 0.3 * pr);
        g.enableScissor(lx - LW / 2 + bw * 0.6, cy - LH / 2 + bw, lx + LW / 2 - bw * 0.6, cy + LH / 2 - bw);
        g.push(); g.translate(lx, cy); g.scale(mag, mag); g.translate(-lx, -cy);
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
        if (lensOn() && !Double.isNaN(lx)) { held = true; dragX = mx; qv += 5; return; }   // a press squishes it
        for (int i = 0; i < items.length; i++) {
            double[] s = seg(i);
            if (mx >= s[0] && mx < s[0] + s[1] && !items[i][0].equals(get.get())) { P.play("click"); set.accept(items[i][0]); }
        }
    }
}
