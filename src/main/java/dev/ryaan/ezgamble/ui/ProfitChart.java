package dev.ryaan.ezgamble.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

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
        int hover = -1;
        int ox = Math.round(cx * k), oy = Math.round(cy * k), iw = Math.round(cw * k), ih = Math.round(ch * k);
        Identifier img = image(deltas, cum, xs, ys, min, max, top, bottom, left, right, ox, oy, iw, ih, k, mini, gradient, lineColor);
        g.getMatrices().pushMatrix();
        try {
            g.getMatrices().scale(1 / k, 1 / k);
            int shown = reveal >= 1 ? iw : Math.max(0, Math.round((float) (left - ox + (right - left) * reveal)));
            if (img != null && shown > 0) g.drawTexture(RenderPipelines.GUI_TEXTURED, img, ox, oy, 0, 0, shown, ih, shown, ih, iw, ih);
            if (n > 0 && reveal >= 1) {
                int ex = (int) Math.round(xs[n]), ey = (int) Math.round(ys[n]), last = deltas[n - 1] >= 0 ? Glass.GREEN : Glass.RED;
                disk(g, ex, ey, (mini ? 3 : 4.5f) * k * (0.8f + 0.4f * Ui.pulse(System.currentTimeMillis() * 2)), Glass.alpha(last, 0.25f));
                disk(g, ex, ey, (mini ? 1.6f : 2.2f) * k, last);
            }
            if (!mini && n > 0 && reveal >= 1 && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
                int ik = Math.max(1, Math.round(k));
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

    // ---- the curve, area and zero line rasterised once into a texture (redrawn only when the data or the size change)

    private static final java.util.Map<String, Object[]> CACHE = new java.util.HashMap<>();   // slot -> {key, id}
    private static int seq;

    private static Identifier image(long[] deltas, long[] cum, double[] xs, double[] ys, double min, double max, double top, double bottom,
                                    double left, double right, int ox, int oy, int iw, int ih, float k, boolean mini, boolean gradient, int lineColor) {
        if (iw <= 0 || ih <= 0) return null;
        String slot = mini ? "mini" : "full";
        String key = java.util.Arrays.hashCode(deltas) + "/" + deltas.length + "/" + iw + "x" + ih + "/" + (ox % 1) + "/" + k + "/" + gradient + lineColor
                + "/" + Math.round(left - ox) + "/" + Math.round(top - oy);
        Object[] c = CACHE.get(slot);
        if (c != null && c[0].equals(key)) return (Identifier) c[1];
        int n = deltas.length;
        float[] r = new float[iw * ih], gg = new float[iw * ih], b = new float[iw * ih], a = new float[iw * ih];
        Monotone curve = new Monotone(xs, ys);
        int ik = Math.max(1, Math.round(k));
        int zero = (int) Math.round(bottom - (0 - min) / (max - min) * (bottom - top)) - oy;
        int L = (int) Math.round(left) - ox, Rr = (int) Math.round(right) - ox;
        blendRect(r, gg, b, a, iw, ih, L - (mini ? 0 : 4 * ik), zero, Rr + (mini ? 0 : 4 * ik), zero + ik, 0x40FFFFFF);
        int th = 2 * ik, halo = 4 * ik;
        int prev = (int) Math.round(curve.at(left)) - oy;
        for (int x = L; x < Rr; x++) {
            int y = (int) Math.round(curve.at(x + ox + 0.5)) - oy;
            if (y < zero) for (int j = y; j < zero; j++) blend(r, gg, b, a, iw, ih, x, j, Glass.lerp(0x664BE07A, 0x064BE07A, (j - y) / (float) Math.max(1, zero - y)));
            else if (y > zero) for (int j = zero; j < y; j++) blend(r, gg, b, a, iw, ih, x, j, Glass.lerp(0x06FF5C5C, 0x66FF5C5C, (j - zero) / (float) Math.max(1, y - zero)));
            float t = (float) ((x - L) / (double) Math.max(1, Rr - L));
            int col = gradient ? Glass.lerp(Glass.LAV, Glass.PINK, t) : lineColor;
            int y1 = Math.min(prev, y), y2 = Math.max(prev, y);
            blendRect(r, gg, b, a, iw, ih, x, y1 - halo / 2, x + 1, y2 + halo / 2, Glass.alpha(col, 0.18f));
            blendRect(r, gg, b, a, iw, ih, x, y1 - th / 2, x + 1, y2 + th / 2, col);
            prev = y;
        }
        NativeImage img = new NativeImage(iw, ih, true);
        for (int j = 0; j < ih; j++)
            for (int i = 0; i < iw; i++) {
                int q = j * iw + i;
                float al = a[q];
                int A = Math.round(al * 255), R = al > 0 ? Math.round(r[q] / al * 255) : 0, G = al > 0 ? Math.round(gg[q] / al * 255) : 0, B = al > 0 ? Math.round(b[q] / al * 255) : 0;
                img.setColorArgb(i, j, (A << 24) | (clamp(R) << 16) | (clamp(G) << 8) | clamp(B));
            }
        var tm = MinecraftClient.getInstance().getTextureManager();
        if (c != null) tm.destroyTexture((Identifier) c[1]);
        Identifier id = Identifier.of("ezgamble", "dyn/chart_" + slot + "_" + (seq++));
        tm.registerTexture(id, new NativeImageBackedTexture(() -> "ezgamble chart", img));
        CACHE.put(slot, new Object[]{key, id});
        return id;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static void blendRect(float[] r, float[] g, float[] b, float[] a, int w, int h, int x1, int y1, int x2, int y2, int c) {
        for (int j = Math.max(0, y1); j < Math.min(h, y2); j++) for (int i = Math.max(0, x1); i < Math.min(w, x2); i++) blend(r, g, b, a, w, h, i, j, c);
    }

    /** Source-over in premultiplied floats. */
    private static void blend(float[] r, float[] g, float[] b, float[] a, int w, int h, int i, int j, int c) {
        if (i < 0 || j < 0 || i >= w || j >= h) return;
        int q = j * w + i;
        float sa = ((c >>> 24) & 255) / 255f;
        if (sa <= 0) return;
        r[q] = ((c >> 16) & 255) / 255f * sa + r[q] * (1 - sa);
        g[q] = ((c >> 8) & 255) / 255f * sa + g[q] * (1 - sa);
        b[q] = (c & 255) / 255f * sa + b[q] * (1 - sa);
        a[q] = sa + a[q] * (1 - sa);
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
