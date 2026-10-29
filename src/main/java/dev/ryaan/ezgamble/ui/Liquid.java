package dev.ryaan.ezgamble.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Liquid Glass of the prototype (glass, capsule, knob, smooth, ring, rim), drawn with fills in REAL pixels:
 * anti-aliased rounded corners (coverage from the distance to the corner circle), gradients, specular rim,
 * soft shadow. The blurred backdrop is vanilla's menu blur (DrawContext.applyBlur, once per frame).
 * Every call takes GUI coordinates and works under the current matrices (pop animation).
 */
public final class Liquid {
    private Liquid() {}

    /** Fill counter for the smoke test. */
    public static final class F {
        public static long n, nanos;
        public static final java.util.Map<String, Long> by = new java.util.TreeMap<>();
        static String cur = "?";
        static void fill(DrawContext g, int a, int b, int c, int d, int col) { n++; g.fill(a, b, c, d, col); }
        static void grad(DrawContext g, int a, int b, int c, int d, int c1, int c2) { n++; g.fillGradient(a, b, c, d, c1, c2); }
    }

    /** Colour of a point: u, v = 0..1 across the rect. horizontal = the colour changes along x. */
    public interface Paint { int at(float u, float v); default boolean horizontal() { return false; } default float[] stops() { return NONE; } }
    private static final float[] NONE = {};

    public static Paint solid(int c) { return (u, v) -> c; }

    public static Paint hgrad(int a, int b) {
        return new Paint() {
            public int at(float u, float v) { return lerp(a, b, u); }
            public boolean horizontal() { return true; }
        };
    }

    public static Paint vgrad(int a, int b) { return (u, v) -> lerp(a, b, v); }

    /** White sheen from the top, fading to nothing at v = end. */
    static Paint sheen(float end, float alpha) {
        return new Paint() {
            public int at(float u, float v) { return v >= end ? 0 : Glass.alpha(0xFFFFFFFF, alpha * (1 - v / end)); }
            public float[] stops() { return new float[]{end}; }
        };
    }

    private static float k() { return (float) MinecraftClient.getInstance().getWindow().getScaleFactor(); }

    // ------------------------------------------------------------------ primitives (real pixels)

    // ---- anti-aliased corner masks, rendered once per size into textures (a corner = one textured quad)

    private static final java.util.Map<String, net.minecraft.util.Identifier> MASKS = new java.util.HashMap<>();

    /** White disk of radius r (ring of width lw when lw > 0), 2r x 2r, coverage from 4x4 supersampling. */
    private static net.minecraft.util.Identifier mask(int r, float lw) {
        String key = r + "_" + Math.round(lw * 100);
        net.minecraft.util.Identifier id = MASKS.get(key);
        if (id != null) return id;
        int n = 2 * r;
        net.minecraft.client.texture.NativeImage img = new net.minecraft.client.texture.NativeImage(n, n, true);
        float ri = lw > 0 ? Math.max(0, r - lw) : -1;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++)
                    for (int sx = 0; sx < 4; sx++) {
                        float dx = x + (sx + 0.5f) / 4 - r, dy = y + (sy + 0.5f) / 4 - r, d2 = dx * dx + dy * dy;
                        if (d2 <= r * r && (ri < 0 || d2 >= ri * ri)) hits++;
                    }
                img.setColorArgb(x, y, (Math.round(hits * 255f / 16) << 24) | 0xFFFFFF);
            }
        id = net.minecraft.util.Identifier.of("ezgamble", "dyn/mask_" + key);
        net.minecraft.client.texture.NativeImageBackedTexture tex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "ezgamble mask", img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        MASKS.put(key, id);
        return id;
    }

    /** Quarter q (0 TL, 1 TR, 2 BL, 3 BR) of a mask at (x, y), tinted. */
    private static void quarter(DrawContext g, net.minecraft.util.Identifier id, int r, int q, int x, int y, int color) {
        if ((color >>> 24) == 0 || r <= 0) return;
        F.n++;
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, (q % 2) * r, (q / 2) * r, r, r, 2 * r, 2 * r, color);
    }

    /**
     * Rounded rect filled with paint, opacity op, in real pixels: four textured corners, the straight parts as
     * vertical-gradient columns (exact for any paint linear in v); a paint that changes along x is cut in ~48 columns.
     */
    private static void shape(DrawContext g, float X, float Y, float W, float H, float R, Paint p, float op) {
        if (W <= 0 || H <= 0 || op <= 0) return;
        int x0 = Math.round(X), y0 = Math.round(Y), x1 = Math.round(X + W), y1 = Math.round(Y + H);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(Math.round(R), Math.min(w, h) / 2));
        X = x0; Y = y0; W = w; H = h;
        if (r > 0) {
            net.minecraft.util.Identifier m = mask(r, 0);
            quarter(g, m, r, 0, x0, y0, mul(p.at(r / 2f / W, r / 2f / H), op));
            quarter(g, m, r, 1, x1 - r, y0, mul(p.at(1 - r / 2f / W, r / 2f / H), op));
            quarter(g, m, r, 2, x0, y1 - r, mul(p.at(r / 2f / W, 1 - r / 2f / H), op));
            quarter(g, m, r, 3, x1 - r, y1 - r, mul(p.at(1 - r / 2f / W, 1 - r / 2f / H), op));
            column(g, x0, x0 + r, y0 + r, y1 - r, p, op, X, Y, W, H, x0 + r / 2f);
            column(g, x1 - r, x1, y0 + r, y1 - r, p, op, X, Y, W, H, x1 - r / 2f);
        }
        hspan(g, x0 + r, x1 - r, y0, y1, p, op, X, Y, W, H);
    }

    /** Columns covering [xa, xb) x [ya, yb): one when the paint is the same along x, ~48 across the rect otherwise. */
    private static void hspan(DrawContext g, int xa, int xb, int ya, int yb, Paint p, float op, float X, float Y, float W, float H) {
        if (xb <= xa) return;
        if (!p.horizontal()) { column(g, xa, xb, ya, yb, p, op, X, Y, W, H, (xa + xb) / 2f); return; }
        int step = Math.max(2, Math.round(W / 48));
        for (int i = xa; i < xb; i += step) { int e = Math.min(xb, i + step); column(g, i, e, ya, yb, p, op, X, Y, W, H, (i + e) / 2f); }
    }

    /** Columns [x1, x2) from row y1 to y2, the paint's vertical gradient (split at its stops). */
    private static void column(DrawContext g, int x1, int x2, int y1, int y2, Paint p, float op, float X, float Y, float W, float H, float cx) {
        if (y2 <= y1 || x2 <= x1) return;
        float u = (cx - X) / W;
        float[] stops = p.stops();
        int from = y1;
        for (int k = 0; k <= stops.length; k++) {
            int to = k < stops.length ? Math.round(Y + stops[k] * H) : y2;
            to = Math.max(from, Math.min(y2, to));
            if (to > from) {
                int c1 = mul(p.at(u, (from - Y) / H), op), c2 = mul(p.at(u, (to - Y) / H), op);
                if ((c1 >>> 24) != 0 || (c2 >>> 24) != 0) {
                    if (c1 == c2) F.fill(g, x1, from, x2, to, c1); else F.grad(g, x1, from, x2, to, c1, c2);
                }
            }
            from = to;
        }
    }

    /** Ring of width lw along the edge: textured corners, four straight sides. */
    private static void stroke(DrawContext g, float X, float Y, float W, float H, float R, float lw, Paint p, float op) {
        if (W <= 0 || H <= 0 || op <= 0) return;
        int x0 = Math.round(X), y0 = Math.round(Y), x1 = Math.round(X + W), y1 = Math.round(Y + H);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(Math.round(R), Math.min(w, h) / 2));
        int l = Math.max(1, Math.round(lw));
        X = x0; Y = y0; W = w; H = h;
        if (r > 0) {
            net.minecraft.util.Identifier m = mask(r, lw);
            quarter(g, m, r, 0, x0, y0, mul(p.at(r / 2f / W, r / 2f / H), op));
            quarter(g, m, r, 1, x1 - r, y0, mul(p.at(1 - r / 2f / W, r / 2f / H), op));
            quarter(g, m, r, 2, x0, y1 - r, mul(p.at(r / 2f / W, 1 - r / 2f / H), op));
            quarter(g, m, r, 3, x1 - r, y1 - r, mul(p.at(1 - r / 2f / W, 1 - r / 2f / H), op));
        }
        column(g, x0, x0 + l, y0 + r, y1 - r, p, op, X, Y, W, H, x0 + l / 2f);
        column(g, x1 - l, x1, y0 + r, y1 - r, p, op, X, Y, W, H, x1 - l / 2f);
        hspan(g, x0 + r, x1 - r, y0, y0 + l, p, op, X, Y, W, H);
        hspan(g, x0 + r, x1 - r, y1 - l, y1, p, op, X, Y, W, H);
    }

    // ------------------------------------------------------------------ the prototype's surfaces (GUI coordinates)

    private interface Real { void draw(float k); }

    private static void real(DrawContext g, Real r) {
        long t0 = System.nanoTime(), n0 = F.n;
        String who = Thread.currentThread().getStackTrace()[3].getMethodName();
        try { real0(g, r); } finally { F.nanos += System.nanoTime() - t0; F.by.merge(who, F.n - n0, Long::sum); }
    }

    private static void real0(DrawContext g, Real r) {
        float k = k();
        g.getMatrices().pushMatrix();
        try {
            g.getMatrices().scale(1 / k, 1 / k);
            r.draw(k);
        } finally {
            g.getMatrices().popMatrix();
        }
    }

    /** Content surface: smooth corners, translucent fill, optional 1 px stroke. */
    public static void smooth(DrawContext g, float x, float y, float w, float h, float r, Paint fill, int stroke) {
        real(g, k -> {
            shape(g, x * k, y * k, w * k, h * k, r * k, fill, 1);
            if (stroke != 0) stroke(g, x * k, y * k, w * k, h * k, r * k, Math.max(1, k * 0.6f), solid(stroke), 1);
        });
    }

    public static void smooth(DrawContext g, float x, float y, float w, float h, float r, int fill, int stroke) {
        smooth(g, x, y, w, h, r, solid(fill), stroke);
    }

    /** Smooth ring (selection, flash). */
    public static void ring(DrawContext g, float x, float y, float w, float h, float r, int color, float lw) {
        real(g, k -> stroke(g, x * k, y * k, w * k, h * k, r * k, Math.max(1, k * lw * 0.75f), solid(color), 1));
    }

    /** Soft floating shadow: stacked expanded shapes, fading out (prototype: shadowBlur 14, offset 4). */
    private static void shadow(DrawContext g, float X, float Y, float W, float H, float R, float k, float strength) {
        int n = 6;
        float spread = 14 * k, a = 0.5f * strength / n;
        for (int i = n; i >= 1; i--) {
            float e = spread * i / n;
            shape(g, X - e * 0.6f, Y + 4 * k - e * 0.4f, W + e * 1.2f, H + e * 1.2f, R + e, solid(0xFF04020A), a);
        }
    }

    /** Specular edge: bright at the top left, fading, a pink glint at the bottom right; plus a faint inner line. */
    private static void rim(DrawContext g, float X, float Y, float W, float H, float R, float k, float strength) {
        float lw = Math.max(1, k * 0.6f);
        // diagonal gradient from (X, Y) to (X + 0.6 W, Y + H)
        float gx = 0.6f * W, gy = H, len2 = gx * gx + gy * gy;
        int p0 = Glass.alpha(0xFFFFFFFF, 0.62f * strength), p1 = Glass.alpha(0xFFFFFFFF, 0.10f * strength),
                p2 = Glass.alpha(0xFFFFFFFF, 0.05f * strength), p3 = Glass.alpha(Glass.PINK, 0.42f * strength);
        Paint diag = new Paint() {
            public int at(float u, float v) {
                float t = Math.max(0, Math.min(1, (u * W * gx + v * H * gy) / len2));
                return t < 0.35f ? lerp(p0, p1, t / 0.35f) : t < 0.7f ? lerp(p1, p2, (t - 0.35f) / 0.35f) : lerp(p2, p3, (t - 0.7f) / 0.3f);
            }
            public boolean horizontal() { return true; }
        };
        stroke(g, X, Y, W, H, R, lw, diag, 1);
        float l2 = Math.max(1, k * 0.5f);
        stroke(g, X + lw * 2, Y + lw * 2, W - lw * 4, H - lw * 4, Math.max(0, R - lw * 2), l2, solid(Glass.alpha(0xFFFFFFFF, 0.05f * strength)), 1);
    }

    /** The glass sheet: shadow, plum tint lighter at the top, Outmind wash, top sheen, rim. Backdrop blur = vanilla. */
    public static void glass(DrawContext g, float x, float y, float w, float h, float r, float a, float shadowStrength) {
        real(g, k -> {
            float X = x * k, Y = y * k, W = w * k, H = h * k, R = Math.min(r * k, Math.min(W, H) / 2);
            if (shadowStrength > 0) shadow(g, X, Y, W, H, R, k, shadowStrength);
            float aa = Math.min(0.94f, a);
            shape(g, X, Y, W, H, R, vgrad(argb(aa * 0.88f, 34, 24, 54), argb(aa, 12, 10, 20)), 1);
            float gx = W, gy = H, len2 = gx * gx + gy * gy;
            int w0 = Glass.alpha(Glass.LAV, 0.13f), w1 = Glass.alpha(Glass.PINK, 0.07f);
            shape(g, X, Y, W, H, R, new Paint() {
                public int at(float u, float v) { return lerp(w0, w1, (u * W * gx + v * H * gy) / len2); }
                public boolean horizontal() { return true; }
            }, 1);
            float sh = Math.min(H * 0.45f, 40 * k);
            shape(g, X, Y, W, H, R, sheen(sh / H, 0.10f), 1);
            rim(g, X, Y, W, H, R, k, 1);
        });
    }

    /** Capsule control: primary (Outmind gradient + sheen), selected, danger, ghost (clear). */
    public static void capsule(DrawContext g, float x, float y, float w, float h, String kind, boolean hover, float opacity) {
        real(g, k -> {
            float X = x * k, Y = y * k, W = w * k, H = h * k, R = H / 2;
            switch (kind) {
                case "primary" -> {
                    // coloured glow under it
                    for (int i = 3; i >= 1; i--) {
                        float e = 8 * k * i / 3;
                        shape(g, X - e * 0.5f, Y + 2 * k - e * 0.3f, W + e, H + e * 0.6f, R + e * 0.5f, solid(Glass.LAV), 0.45f / 3 * opacity * 0.6f);
                    }
                    float o = hover ? 1 : 0.92f;
                    shape(g, X, Y, W, H, R, hgrad(Glass.alpha(Glass.LAV, o), Glass.alpha(Glass.PINK, o)), opacity);
                    shape(g, X, Y, W, H, R, sheen(0.55f, 0.38f), opacity);
                    rim(g, X, Y, W, H, R, k, 1.1f * opacity);
                }
                case "selected" -> {
                    shape(g, X, Y, W, H, R, hgrad(Glass.alpha(Glass.LAV, 0.34f), Glass.alpha(Glass.PINK, 0.24f)), opacity);
                    shape(g, X, Y, W, H, R, solid(Glass.alpha(0xFFFFFFFF, hover ? 0.12f : 0.08f)), opacity);
                    rim(g, X, Y, W, H, R, k, opacity);
                }
                case "danger" -> {
                    shape(g, X, Y, W, H, R, solid(Glass.alpha(Glass.RED, hover ? 0.24f : 0.12f)), opacity);
                    stroke(g, X, Y, W, H, R, Math.max(1, k * 0.6f), solid(Glass.alpha(Glass.RED, hover ? 0.75f : 0.5f)), opacity);
                }
                default -> {
                    shape(g, X, Y, W, H, R, solid(Glass.alpha(0xFFFFFFFF, hover ? 0.10f : 0.045f)), opacity);
                    rim(g, X, Y, W, H, R, k, (hover ? 0.75f : 0.45f) * opacity);
                }
            }
        });
    }

    /** White glass knob (switch). */
    public static void knob(DrawContext g, float x, float y, float w, float h) {
        real(g, k -> {
            float X = x * k, Y = y * k, W = w * k, H = h * k, R = H / 2;
            for (int i = 2; i >= 1; i--) {
                float e = 4 * k * i / 2;
                shape(g, X - e * 0.5f, Y + k - e * 0.3f, W + e, H + e * 0.6f, R + e * 0.5f, solid(0xFF04020A), 0.45f / 2 * 0.6f);
            }
            shape(g, X, Y, W, H, R, vgrad(0xFAFFFFFF, 0xF0ECE4F8), 1);
        });
    }

    // ------------------------------------------------------------------ colours

    private static int argb(float a, int r, int gg, int b) { return (Math.round(255 * Math.max(0, Math.min(1, a))) << 24) | (r << 16) | (gg << 8) | b; }

    private static int mul(int c, float a) {
        int al = Math.round(((c >>> 24) & 255) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) { return Glass.lerp(a, b, t); }
}
