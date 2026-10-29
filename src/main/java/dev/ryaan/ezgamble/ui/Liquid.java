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

    /** Colour of a point: u, v = 0..1 across the rect. horizontal = the colour changes along x. */
    public interface Paint { int at(float u, float v); default boolean horizontal() { return false; } }

    public static Paint solid(int c) { return (u, v) -> c; }

    public static Paint hgrad(int a, int b) {
        return new Paint() {
            public int at(float u, float v) { return lerp(a, b, u); }
            public boolean horizontal() { return true; }
        };
    }

    public static Paint vgrad(int a, int b) { return (u, v) -> lerp(a, b, v); }

    private static float k() { return (float) MinecraftClient.getInstance().getWindow().getScaleFactor(); }

    // ------------------------------------------------------------------ primitives (real pixels)

    /** Rounded rect filled with paint, opacity op. All in real pixels. */
    private static void shape(DrawContext g, float X, float Y, float W, float H, float R, Paint p, float op) {
        if (W <= 0 || H <= 0) return;
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        int y0 = (int) Math.floor(Y), y1 = (int) Math.ceil(Y + H);
        for (int j = y0; j < y1; j++) {
            float vc = Math.min(j + 1, Y + H) - Math.max(j, Y);   // vertical coverage of this row
            if (vc <= 0) continue;
            float v = (j + 0.5f - Y) / H;
            float yc = j + 0.5f;
            boolean corner = yc < Y + R || yc > Y + H - R;
            if (!corner) {
                // straight sides: one partial pixel each side, the rest in one run
                edgeRow(g, X, W, j, vc, p, op, Y, H);
                continue;
            }
            float cy = yc < Y + R ? Y + R : Y + H - R;
            // corners: per pixel with the circle distance, merged runs where fully covered
            cornerRow(g, X, X + R, X + R, cy, R, j, 1, p, op, X, W, v);
            run(g, X + R, X + W - R, j, vc, p, op, X, W, v);
            cornerRow(g, X + W - R, X + W, X + W - R, cy, R, j, 1, p, op, X, W, v);
        }
    }

    private static void edgeRow(DrawContext g, float X, float W, int j, float vc, Paint p, float op, float Y, float H) {
        float v = (j + 0.5f - Y) / H;
        int xa = (int) Math.floor(X), xb = (int) Math.ceil(X + W);
        if (xb - xa <= 2) { run(g, X, X + W, j, vc, p, op, X, W, v); return; }
        float la = xa + 1 - X, ra = X + W - (xb - 1);
        px(g, xa, j, vc * la, p, op, X, W, v);
        run(g, xa + 1, xb - 1, j, vc, p, op, X, W, v);
        px(g, xb - 1, j, vc * ra, p, op, X, W, v);
    }

    private static void cornerRow(DrawContext g, float from, float to, float cx, float cy, float R, int j, float vc,
                                  Paint p, float op, float X, float W, float v) {
        int a = (int) Math.floor(from), b = (int) Math.ceil(to);
        int runStart = -1;
        for (int i = a; i < b; i++) {
            float hx = Math.min(i + 1, to) - Math.max(i, from);
            if (hx <= 0) continue;
            // 2x2 supersampling of the circle: smooth edges whatever the slope
            float cov = 0;
            for (int s = 0; s < 4; s++) {
                float sx = i + 0.25f + 0.5f * (s & 1), sy = j + 0.25f + 0.5f * (s >> 1);
                float dx = sx - cx, dy = sy - cy;
                if (dx * dx + dy * dy <= R * R) cov += 0.25f;
            }
            if (cov == 0) continue;
            // refine the edge: distance based coverage for partial samples
            if (cov < 1) {
                float dx = i + 0.5f - cx, dy = j + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) - R;
                cov = Math.max(0, Math.min(1, 0.5f - d));
            }
            float c = cov * hx * vc;
            if (c >= 0.999f) { if (runStart < 0) runStart = i; continue; }
            if (runStart >= 0) { run(g, runStart, i, j, 1, p, op, X, W, v); runStart = -1; }
            if (c > 0.01f) px(g, i, j, c, p, op, X, W, v);
        }
        if (runStart >= 0) run(g, runStart, b, j, 1, p, op, X, W, v);
    }

    /** Horizontal run [x1, x2) on row j, cut in 2 px pieces when the paint changes along x. */
    private static void run(DrawContext g, float x1, float x2, int j, float cov, Paint p, float op, float X, float W, float v) {
        int a = Math.round(x1), b = Math.round(x2);
        if (b <= a || cov <= 0) return;
        if (!p.horizontal()) { put(g, a, j, b, mul(p.at(0.5f, v), cov * op)); return; }
        for (int i = a; i < b; i += 2) put(g, i, j, Math.min(b, i + 2), mul(p.at((i + 1 - X) / W, v), cov * op));
    }

    private static void px(DrawContext g, int i, int j, float cov, Paint p, float op, float X, float W, float v) {
        if (cov <= 0.01f) return;
        put(g, i, j, i + 1, mul(p.at((i + 0.5f - X) / W, v), cov * op));
    }

    private static void put(DrawContext g, int x1, int j, int x2, int c) { if ((c >>> 24) != 0) g.fill(x1, j, x2, j + 1, c); }

    /** Ring of width lw inside the rect edge: outer shape minus inner shape, coverage per pixel near corners. */
    private static void stroke(DrawContext g, float X, float Y, float W, float H, float R, float lw, Paint p, float op) {
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        float Ri = Math.max(0, R - lw);
        int y0 = (int) Math.floor(Y), y1 = (int) Math.ceil(Y + H);
        for (int j = y0; j < y1; j++) {
            float yc = j + 0.5f, v = (yc - Y) / H;
            float vc = Math.min(j + 1, Y + H) - Math.max(j, Y);
            if (vc <= 0) continue;
            float topBand = Math.max(0, Math.min(j + 1, Y + lw) - Math.max(j, Y)), botBand = Math.max(0, Math.min(j + 1, Y + H) - Math.max(j, Y + H - lw));
            boolean corner = yc < Y + R || yc > Y + H - R;
            if (!corner) {
                // the two side bands
                band(g, X, X + lw, j, vc, p, op, X, W, v);
                band(g, X + W - lw, X + W, j, vc, p, op, X, W, v);
                continue;
            }
            float cy = yc < Y + R ? Y + R : Y + H - R;
            float flat = Math.max(topBand, botBand);
            if (flat > 0) run(g, X + R, X + W - R, j, flat, p, op, X, W, v);
            arc(g, X, X + R, X + R, cy, R, Ri, j, p, op, X, W, v);
            arc(g, X + W - R, X + W, X + W - R, cy, R, Ri, j, p, op, X, W, v);
        }
    }

    private static void band(DrawContext g, float from, float to, int j, float vc, Paint p, float op, float X, float W, float v) {
        int a = (int) Math.floor(from), b = (int) Math.ceil(to);
        for (int i = a; i < b; i++) {
            float c = Math.min(i + 1, to) - Math.max(i, from);
            px(g, i, j, c * vc, p, op, X, W, v);
        }
    }

    private static void arc(DrawContext g, float from, float to, float cx, float cy, float R, float Ri, int j,
                            Paint p, float op, float X, float W, float v) {
        int a = (int) Math.floor(from), b = (int) Math.ceil(to);
        for (int i = a; i < b; i++) {
            float dx = i + 0.5f - cx, dy = j + 0.5f - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
            float c = Math.max(0, Math.min(1, R + 0.5f - d)) - Math.max(0, Math.min(1, Ri + 0.5f - d));
            float hx = Math.min(i + 1, to) - Math.max(i, from);
            px(g, i, j, c * hx, p, op, X, W, v);
        }
    }

    // ------------------------------------------------------------------ the prototype's surfaces (GUI coordinates)

    private interface Real { void draw(float k); }

    private static void real(DrawContext g, Real r) {
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
            shape(g, X, Y, W, H, R, (u, v) -> {
                float t = v * H / sh;
                return t >= 1 ? 0 : Glass.alpha(0xFFFFFFFF, 0.10f * (1 - t));
            }, 1);
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
                    float sh = H * 0.55f;
                    shape(g, X, Y, W, H, R, (u, v) -> { float t = v * H / sh; return t >= 1 ? 0 : Glass.alpha(0xFFFFFFFF, 0.38f * (1 - t)); }, opacity);
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
