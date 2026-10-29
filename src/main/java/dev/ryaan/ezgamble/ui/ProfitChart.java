package dev.ryaan.ezgamble.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Cumulative profit curve, drawn in REAL pixels (scale 1 / gui scale): monotone cubic through the points,
 * green/red area fading to the zero line, gradient line with a halo, pulsing dot at the live end, hover marker.
 * deltas = per-transaction profit, oldest first; reveal 0..1 = left-to-right drawing animation.
 */
public final class ProfitChart {
    private ProfitChart() {}

    /** hover = 1..n or -1, px = GUI x of each point (0..n), cum = running total (0..n). */
    public record Result(int hover, int[] px, long[] cum) {}

    public static Result draw(DrawContext g, long[] deltas, int cx, int cy, int cw, int ch, int mx, int my,
                              boolean mini, int lineColor, boolean gradient, float reveal) {
        int n = deltas.length;
        float k = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
        long[] cum = new long[n + 1];
        for (int i = 0; i < n; i++) cum[i + 1] = cum[i] + deltas[i];
        double min = 0, max = 0;
        for (long v : cum) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max == min) max = min + 1;
        int m = mini ? 3 : 10, mv = mini ? 4 : 14;
        double top = (cy + mv) * k, bottom = (cy + ch - mv) * k, left = (cx + m) * k, right = (cx + cw - m) * k;
        double[] xs = new double[n + 1], ys = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            xs[i] = left + i * (right - left) / Math.max(1, n);
            ys[i] = bottom - (cum[i] - min) / (max - min) * (bottom - top);
        }
        Monotone curve = new Monotone(xs, ys);
        int zero = (int) Math.round(bottom - (0 - min) / (max - min) * (bottom - top));
        int end = (int) Math.round(left + (right - left) * reveal);
        int ik = Math.max(1, Math.round(k));
        int hover = -1;
        g.getMatrices().pushMatrix();
        try {
            g.getMatrices().scale(1 / k, 1 / k);
            g.fill((int) Math.round(left) - (mini ? 0 : 4 * ik), zero, (int) Math.round(right) + (mini ? 0 : 4 * ik), zero + ik, 0x40FFFFFF);
            int th = 2 * ik, halo = 4 * ik;
            int prev = (int) Math.round(curve.at(left));
            for (int x = (int) Math.round(left); x < end; x++) {
                int y = (int) Math.round(curve.at(x + 0.5));
                if (y < zero) g.fillGradient(x, y, x + 1, zero, 0x664BE07A, 0x064BE07A);
                else if (y > zero) g.fillGradient(x, zero, x + 1, y, 0x06FF5C5C, 0x66FF5C5C);
                float t = (float) ((x - left) / Math.max(1, right - left));
                int col = gradient ? Glass.lerp(Glass.LAV, Glass.PINK, t) : lineColor;
                int y1 = Math.min(prev, y), y2 = Math.max(prev, y);
                g.fill(x, y1 - halo / 2, x + 1, y2 + halo / 2, Glass.alpha(col, 0.18f));
                g.fill(x, y1 - th / 2, x + 1, y2 + th / 2, col);
                prev = y;
            }
            if (n > 0 && reveal >= 1) {
                int ex = (int) Math.round(xs[n]), ey = (int) Math.round(ys[n]), last = deltas[n - 1] >= 0 ? Glass.GREEN : Glass.RED;
                disk(g, ex, ey, (mini ? 3 : 4.5f) * k * (0.8f + 0.4f * Ui.pulse(System.currentTimeMillis() * 2)), Glass.alpha(last, 0.25f));
                disk(g, ex, ey, (mini ? 1.6f : 2.2f) * k, last);
            }
            if (!mini && n > 0 && reveal >= 1 && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
                hover = (int) Math.max(1, Math.min(n, Math.round((mx * k - left) / Math.max(1, (right - left) / n))));
                int hx = (int) Math.round(xs[hover]), hy = (int) Math.round(ys[hover]);
                g.fill(hx, Math.round((cy + 4) * k), hx + ik, Math.round((cy + ch - 4) * k), 0x40FFFFFF);
                disk(g, hx, hy, 3.5f * k, Glass.INK);
                disk(g, hx, hy, 2.2f * k, deltas[hover - 1] >= 0 ? Glass.GREEN : Glass.RED);
            }
        } finally {
            g.getMatrices().popMatrix();
        }
        int[] px = new int[n + 1];
        for (int i = 0; i <= n; i++) px[i] = (int) Math.round(xs[i] / k);
        return new Result(hover, px, cum);
    }

    /** Filled disk, one fill per row (real pixels). */
    private static void disk(DrawContext g, int x, int y, float r, int color) {
        int rr = (int) Math.ceil(r);
        for (int dy = -rr; dy <= rr; dy++) {
            double w = Math.sqrt(Math.max(0, r * r - dy * dy));
            if (w > 0) g.fill((int) Math.round(x - w), y + dy, (int) Math.round(x + w), y + dy + 1, color);
        }
    }

    /** Two-line tooltip above the point, each line with an optional right part, kept inside [lx, rx]. */
    public static void tooltip(DrawContext g, String l1, String l2, int c2, int px, int cy, int lx, int rx, String r1, String r2) {
        var f = Ui.font();
        int tw = Math.max(f.getWidth(l1) + (r1.isEmpty() ? 0 : 12 + f.getWidth(r1)), f.getWidth(l2) + (r2.isEmpty() ? 0 : 12 + f.getWidth(r2))) + 12;
        int tx = Math.min(Math.max(lx, px - tw / 2), rx - tw), ty = cy - 2;
        Liquid.glass(g, tx, ty - 24, tw, 26, 7, 0.8f, 0.8f);
        g.drawText(f, l1, tx + 6, ty - 20, Glass.WHITE, false);
        g.drawText(f, l2, tx + 6, ty - 9, c2, false);
        if (!r1.isEmpty()) g.drawText(f, r1, tx + tw - 6 - f.getWidth(r1), ty - 20, Glass.DIM, false);
        if (!r2.isEmpty()) g.drawText(f, r2, tx + tw - 6 - f.getWidth(r2), ty - 9, Glass.MUTE, false);
    }

    /** Monotone cubic (Fritsch-Carlson): never overshoots the real points. */
    private static final class Monotone {
        private final double[] xs, ys, t;

        Monotone(double[] xs, double[] ys) {
            this.xs = xs; this.ys = ys;
            int n = xs.length;
            t = new double[n];
            if (n < 2) return;
            double[] d = new double[n - 1];
            for (int i = 0; i < n - 1; i++) d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
            t[0] = d[0]; t[n - 1] = d[n - 2];
            for (int i = 1; i < n - 1; i++) t[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
            for (int i = 0; i < n - 1; i++) {
                if (d[i] == 0) { t[i] = 0; t[i + 1] = 0; continue; }
                double a = t[i] / d[i], b = t[i + 1] / d[i], s = a * a + b * b;
                if (s > 9) { double tau = 3 / Math.sqrt(s); t[i] = tau * a * d[i]; t[i + 1] = tau * b * d[i]; }
            }
        }

        double at(double x) {
            int n = xs.length;
            if (n < 2) return n == 1 ? ys[0] : 0;
            int i = 0, lo = 0, hi = n - 2;
            while (lo <= hi) { int mid = (lo + hi) >>> 1; if (xs[mid] <= x) { i = mid; lo = mid + 1; } else hi = mid - 1; }
            i = Math.max(0, Math.min(n - 2, i));
            double h = xs[i + 1] - xs[i], u = Math.max(0, Math.min(1, (x - xs[i]) / h));
            double h00 = 2 * u * u * u - 3 * u * u + 1, h10 = u * u * u - 2 * u * u + u, h01 = -2 * u * u * u + 3 * u * u, h11 = u * u * u - u * u;
            return h00 * ys[i] + h10 * h * t[i] + h01 * ys[i + 1] + h11 * h * t[i + 1];
        }
    }
}
