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
            public int at(float u, float v) { return v >= end ? 0x00FFFFFF : Glass.alpha(0xFFFFFFFF, alpha * (1 - v / end)); }   // transparent WHITE at the end: a gradient to 0 (transparent black) went through grey
            public float[] stops() { return new float[]{end}; }
        };
    }

    private static float k() { return (float) (MinecraftClient.getInstance().getWindow().getScaleFactor() * dev.ryaan.ezgamble.proto.P.zoom); }

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
     * A corner of a shape: one quad when the paint is flat over it, otherwise horizontal bands that follow the paint's
     * vertical gradient (a single colour per corner left lighter squares at the ends of a capsule's sheen).
     */
    private static void corner(DrawContext g, net.minecraft.util.Identifier id, int r, int q, int x, int y, Paint p, float op, float X, float Y, float W, float H) {
        if (p.horizontal()) {   // a left-right gradient: vertical slices, each with the colour of its own x
            int n = Math.min(8, Math.max(2, r / 3));
            float v = (y + r / 2f - Y) / H;
            for (int i = 0; i < n; i++) {
                int a = i * r / n, b = (i + 1) * r / n;
                if (b <= a) continue;
                int c = mul(p.at((x + (a + b) / 2f - X) / W, v), op);
                if ((c >>> 24) == 0) continue;
                F.n++;
                g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x + a, y, (q % 2) * r + a, (q / 2) * r, b - a, r, 2 * r, 2 * r, c);
            }
            return;
        }
        float u = (x + r / 2f - X) / W;
        int top = mul(p.at(u, (y - Y) / H), op), bot = mul(p.at(u, (y + r - Y) / H), op);
        if (top == bot) { quarter(g, id, r, q, x, y, top); return; }
        int n = Math.min(8, Math.max(2, r / 3));
        for (int i = 0; i < n; i++) {
            int a = i * r / n, b = (i + 1) * r / n;
            if (b <= a) continue;
            int c = mul(p.at(u, (y + (a + b) / 2f - Y) / H), op);
            if ((c >>> 24) == 0) continue;
            F.n++;
            g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y + a, (q % 2) * r, (q / 2) * r + a, r, b - a, 2 * r, 2 * r, c);
        }
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
            corner(g, m, r, 0, x0, y0, p, op, X, Y, W, H);
            corner(g, m, r, 1, x1 - r, y0, p, op, X, Y, W, H);
            corner(g, m, r, 2, x0, y1 - r, p, op, X, Y, W, H);
            corner(g, m, r, 3, x1 - r, y1 - r, p, op, X, Y, W, H);
            column(g, x0, x0 + r, y0 + r, y1 - r, p, op, X, Y, W, H, x0 + r / 2f);
            column(g, x1 - r, x1, y0 + r, y1 - r, p, op, X, Y, W, H, x1 - r / 2f);
        }
        hspan(g, x0 + r, x1 - r, y0, y1, p, op, X, Y, W, H);
    }

    /** Columns covering [xa, xb) x [ya, yb): one when the paint is the same along x, ~48 across the rect otherwise. */
    private static void hspan(DrawContext g, int xa, int xb, int ya, int yb, Paint p, float op, float X, float Y, float W, float H) {
        if (xb <= xa) return;
        if (!p.horizontal()) { column(g, xa, xb, ya, yb, p, op, X, Y, W, H, (xa + xb) / 2f); return; }
        // as many columns as the colour really changes across the span (a step of ~4 levels is invisible): a faint
        // wash needs a handful, a strong gradient up to ~48. Was always ~48, the HUD alone cost 1.2 ms a frame.
        float v = ((ya + yb) / 2f - Y) / H, tv = 0;
        int prev = mul(p.at((xa - X) / W, v), op);
        for (int sIdx = 1; sIdx <= 8; sIdx++) {
            int c = mul(p.at((xa + (xb - xa) * sIdx / 8f - X) / W, v), op);
            tv += premulDiff(prev, c); prev = c;
        }
        int n = Math.max(1, Math.min(48, (int) Math.ceil(tv / 4)));
        int step = Math.max(2, (int) Math.ceil((xb - xa) / (float) n));
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
        // getStackTrace materialises the whole render stack (15 to 40 us): with 30 to 70 surfaces a menu frame that was
        // up to 1.4 ms of the 3 ms budget. The "fills by surface" breakdown is only read by the smoke test's perf step.
        if (!dev.ryaan.ezgamble.DebugRun.ON) { real0(g, r); return; }
        long t0 = System.nanoTime(), n0 = F.n;
        String who = Thread.currentThread().getStackTrace()[3].getMethodName();
        try { real0(g, r); } finally { F.nanos += System.nanoTime() - t0; F.by.merge(who, F.n - n0, Long::sum); }
    }

    /** True while the menu's zoom is in motion (the resize grip): GambleScreen writes it every frame it draws. */
    public static boolean live;

    private static void real0(DrawContext g, Real r) {
        float k = k();
        if (live) k = Math.max(1, Math.round(k));   // a transient zoom: a whole density keeps every baked key (the quads resample a little, the geometry stays exact in GUI space); the release snaps to a whole k anyway
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

    // ------------------------------------------------------------------ baked surfaces
    // A glass sheet was about 180 slivers a frame (shadow, body, wash, sheen, rim), a capsule rim about 90, for pictures
    // identical frame to frame: a felt's bottom row alone was 650 rim draws. The static layers are now rendered once per
    // size, per pixel, into a texture (same paints, same 4x4 supersampled edge coverage as the corner masks) and drawn as
    // one tinted quad. Alpha and rim strength are NOT in the keys: the body's and the rim's alphas are linear in them, so a
    // fade (EventHud, RecapScreen, a switch) is a quad tint and never re-bakes. The shadow's six stacked rings are NOT
    // linear in their strength (a plain tint of a strength-1 bake was 19 % light as the strength tends to 0, 14 % at a HUD
    // card at background 0.3), so the shadow is baked per tenth of strength and tinted the rest of the way: within 2 %
    // everywhere, at most ten bakes per size for a fade. Every key carries Themes.ver and a theme switch drops the lot
    // (clearBaked). While the resize grip is dragged (live) the density is rounded, so nothing bakes per frame.

    private record Baked(net.minecraft.util.Identifier id, int w, int h) {}
    private static final java.util.Map<String, Baked> BAKED = new java.util.LinkedHashMap<>(64, 0.75f, true);
    private static long bakedPx;
    private static int bakedSeq;
    private static final long PX_BUDGET = 12_000_000L;   // 8 B per pixel (the NativeImage stays with the texture next to the GPU copy): about 96 MB, 3x the biggest glass triple plus the HUD
    private static final long MAX_BAKE = 1_200_000L;     // bigger than this (the menu panel from 4 real px per GUI px up) stays on the live path: a bake is a dozen per-pixel passes on the render thread
    private static final float SHADOW_PAD = 16;          // GUI px around a glass that its shadow can reach (14 k spread, 4 k drop)
    private static final float RIM_UNIT = 1.1f;          // the strongest rim asked for (a primary capsule): baked at that, tinted down

    private static Baked baked(String key, int iw, int ih, java.util.function.Consumer<Canvas> painter) {
        Baked b = BAKED.get(key);
        if (b != null) return b;
        Canvas c = new Canvas(iw, ih);
        painter.accept(c);
        var tm = MinecraftClient.getInstance().getTextureManager();
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("ezgamble", "dyn/baked_" + (bakedSeq++));
        tm.registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(() -> "ezgamble baked", c.image()));
        b = new Baked(id, iw, ih);
        BAKED.put(key, b);
        bakedPx += (long) iw * ih;
        while (BAKED.size() > 1 && (BAKED.size() > 128 || bakedPx > PX_BUDGET)) {   // the least recently drawn goes first
            var it = BAKED.entrySet().iterator();
            Baked old = it.next().getValue();
            it.remove();
            bakedPx -= (long) old.w() * old.h();
            tm.destroyTexture(old.id());
        }
        return b;
    }

    /** Drops every baked surface (a theme switch: the body's tints and the wash are theme colours). Safe before the texture manager exists. */
    public static void clearBaked() {
        if (BAKED.isEmpty()) return;
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Baked b : BAKED.values()) tm.destroyTexture(b.id());
        BAKED.clear();
        bakedPx = 0;
    }

    /** One quad: the baked picture at (x, y) in real pixels, multiplied by tint (its alpha = the layer's opacity). */
    private static void blit(DrawContext g, Baked b, int x, int y, int tint) {
        if ((tint >>> 24) == 0) return;
        F.n++;
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, b.id(), x, y, 0, 0, b.w(), b.h(), b.w(), b.h(), tint);
    }

    private static int tint(float a) { return (Math.round(255 * Math.max(0, Math.min(1, a))) << 24) | 0xFFFFFF; }

    /** Signed distance from (px, py) to the edge of the rounded rect, negative inside. */
    private static float sd(float px, float py, float x, float y, float w, float h, float r) {
        float hw = w / 2, hh = h / 2;
        r = Math.max(0, Math.min(r, Math.min(hw, hh)));
        float qx = Math.abs(px - x - hw) - (hw - r), qy = Math.abs(py - y - hh) - (hh - r);
        float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
    }

    /** Coverage of pixel (px, py) by the rounded rect: 0 or 1 away from the edge, 4x4 supersampled along it (the masks' rule). */
    private static float cov(int px, int py, float x, float y, float w, float h, float r) {
        if (w <= 0 || h <= 0) return 0;
        float d = sd(px + 0.5f, py + 0.5f, x, y, w, h, r);
        if (d <= -0.75f) return 1;
        if (d >= 0.75f) return 0;
        int hits = 0;
        for (int sy = 0; sy < 4; sy++)
            for (int sx = 0; sx < 4; sx++)
                if (sd(px + (sx + 0.5f) / 4, py + (sy + 0.5f) / 4, x, y, w, h, r) <= 0) hits++;
        return hits / 16f;
    }

    /** A premultiplied float canvas the layers are composited into (source-over), read out once as a NativeImage. */
    private static final class Canvas {
        final int w, h;
        final float[] r, g, b, a;

        Canvas(int w, int h) { this.w = w; this.h = h; r = new float[w * h]; g = new float[w * h]; b = new float[w * h]; a = new float[w * h]; }

        void shape(float X, float Y, float W, float H, float R, Paint p, float op) { layer(X, Y, W, H, R, 0, p, op); }

        void stroke(float X, float Y, float W, float H, float R, float lw, Paint p, float op) { layer(X, Y, W, H, R, lw, p, op); }

        /** The rounded rect (a ring of width lw when lw > 0) painted with p at opacity op, over its bounding box only. */
        private void layer(float X, float Y, float W, float H, float R, float lw, Paint p, float op) {
            if (W <= 0 || H <= 0 || op <= 0) return;
            R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
            int x1 = Math.max(0, (int) Math.floor(X) - 1), y1 = Math.max(0, (int) Math.floor(Y) - 1);
            int x2 = Math.min(w, (int) Math.ceil(X + W) + 1), y2 = Math.min(h, (int) Math.ceil(Y + H) + 1);
            for (int py = y1; py < y2; py++)
                for (int px = x1; px < x2; px++) {
                    if (lw > 0 && sd(px + 0.5f, py + 0.5f, X, Y, W, H, R) < -(lw + 1.5f)) continue;   // inside the ring both coverages are 1: a stroke was two full passes over an empty interior
                    float cv = cov(px, py, X, Y, W, H, R);
                    if (lw > 0) cv -= cov(px, py, X + lw, Y + lw, W - 2 * lw, H - 2 * lw, R - lw);
                    if (cv <= 0.001f) continue;
                    int c = p.at((px + 0.5f - X) / W, (py + 0.5f - Y) / H);
                    float sa = ((c >>> 24) & 255) / 255f * op * cv;
                    if (sa <= 0) continue;
                    int q = py * w + px;
                    r[q] = ((c >> 16) & 255) / 255f * sa + r[q] * (1 - sa);
                    g[q] = ((c >> 8) & 255) / 255f * sa + g[q] * (1 - sa);
                    b[q] = (c & 255) / 255f * sa + b[q] * (1 - sa);
                    a[q] = sa + a[q] * (1 - sa);
                }
        }

        net.minecraft.client.texture.NativeImage image() {
            net.minecraft.client.texture.NativeImage img = new net.minecraft.client.texture.NativeImage(w, h, true);
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    int q = j * w + i;
                    float al = a[q];
                    int A = Math.round(al * 255);
                    img.setColorArgb(i, j, A == 0 ? 0 : (A << 24) | (c8(r[q] / al) << 16) | (c8(g[q] / al) << 8) | c8(b[q] / al));
                }
            return img;
        }

        private static int c8(float v) { return Math.max(0, Math.min(255, Math.round(v * 255))); }
    }

    /** The shadow's six stacked expanded shapes (prototype: shadowBlur 14, offset 4), into the canvas. */
    private static void shadowInto(Canvas c, float X, float Y, float W, float H, float R, float k, float strength) {
        int n = 6;
        float spread = 14 * k, a = 0.5f * strength / n;
        for (int i = n; i >= 1; i--) {
            float e = spread * i / n;
            c.shape(X - e * 0.6f, Y + 4 * k - e * 0.4f, W + e * 1.2f, H + e * 1.2f, R + e, solid(0xFF04020A), a);
        }
    }

    /** The specular rim's paint: a diagonal gradient from (X, Y) to (X + 0.6 W, Y + H), bright top left, a pink glint bottom right. */
    private static Paint rimPaint(float W, float H, float strength) {
        float gx = 0.6f * W, gy = H, len2 = gx * gx + gy * gy;
        int p0 = Glass.alpha(0xFFFFFFFF, 0.62f * strength), p1 = Glass.alpha(0xFFFFFFFF, 0.10f * strength),
                p2 = Glass.alpha(0xFFFFFFFF, 0.05f * strength), p3 = Glass.alpha(Glass.PINK, 0.42f * strength);
        return new Paint() {
            public int at(float u, float v) {
                float t = Math.max(0, Math.min(1, (u * W * gx + v * H * gy) / len2));
                return t < 0.35f ? lerp(p0, p1, t / 0.35f) : t < 0.7f ? lerp(p1, p2, (t - 0.35f) / 0.35f) : lerp(p2, p3, (t - 0.7f) / 0.3f);
            }
            public boolean horizontal() { return true; }
        };
    }

    /** The rim's two strokes (the gradient edge, a faint inner line) into the canvas. */
    private static void rimInto(Canvas c, float X, float Y, float W, float H, float R, float k, float strength) {
        float lw = Math.max(1, k * 0.6f);
        c.stroke(X, Y, W, H, R, lw, rimPaint(W, H, strength), 1);
        float l2 = Math.max(1, k * 0.5f);
        c.stroke(X + lw * 2, Y + lw * 2, W - lw * 4, H - lw * 4, Math.max(0, R - lw * 2), l2, solid(Glass.alpha(0xFFFFFFFF, 0.05f * strength)), 1);
    }

    /** Specular edge of a capsule: one quad from the baked rim, tinted by strength (every alpha of it is linear in strength). */
    private static void rim(DrawContext g, float X, float Y, float W, float H, float R, float k, float strength) {
        if (strength <= 0) return;
        int x0 = Math.round(X), y0 = Math.round(Y), iw = Math.round(X + W) - x0, ih = Math.round(Y + H) - y0;
        if (iw <= 0 || ih <= 0) return;
        if ((long) iw * ih > MAX_BAKE) { rimLive(g, X, Y, W, H, R, k, strength); return; }
        int r = Math.max(0, Math.min(Math.round(R), Math.min(iw, ih) / 2));
        Baked b = baked("rim/" + iw + "x" + ih + "/" + r + "/" + k + "#" + dev.ryaan.ezgamble.themes.Themes.ver, iw, ih,
                c -> rimInto(c, 0, 0, iw, ih, r, k, RIM_UNIT));
        blit(g, b, x0, y0, tint(strength / RIM_UNIT));
    }

    /** The live rim (two slivered strokes), for a surface too big to bake. */
    private static void rimLive(DrawContext g, float X, float Y, float W, float H, float R, float k, float strength) {
        float lw = Math.max(1, k * 0.6f);
        stroke(g, X, Y, W, H, R, lw, rimPaint(W, H, strength), 1);
        float l2 = Math.max(1, k * 0.5f);
        stroke(g, X + lw * 2, Y + lw * 2, W - lw * 4, H - lw * 4, Math.max(0, R - lw * 2), l2, solid(Glass.alpha(0xFFFFFFFF, 0.05f * strength)), 1);
    }

    /** The sheet over the body: the Outmind wash (lavender to pink, diagonal), the top sheen, the rim. */
    private static void sheetInto(Canvas c, float W, float H, float R, float k) {
        float gx = W, gy = H, len2 = gx * gx + gy * gy;
        int w0 = Glass.alpha(Glass.LAV, 0.13f), w1 = Glass.alpha(Glass.PINK, 0.07f);
        c.shape(0, 0, W, H, R, (u, v) -> lerp(w0, w1, (u * W * gx + v * H * gy) / len2), 1);
        float sh = Math.min(H * 0.45f, 40 * k);
        c.shape(0, 0, W, H, R, sheen(sh / H, 0.10f), 1);
        rimInto(c, 0, 0, W, H, R, k, 1);
    }

    /**
     * The glass sheet: shadow, plum tint lighter at the top, Outmind wash, top sheen, rim. Backdrop blur = vanilla.
     * Three baked quads: the shadow (tinted by shadowStrength), the body (tinted by a: exact, its alpha is linear in a)
     * and the sheet (wash, sheen, rim). Keyed by real-pixel size: a HUD card and the menu panel bake once each.
     */
    public static void glass(DrawContext g, float x, float y, float w, float h, float r, float a, float shadowStrength) {
        real(g, k -> {
            int x0 = Math.round(x * k), y0 = Math.round(y * k), iw = Math.round((x + w) * k) - x0, ih = Math.round((y + h) * k) - y0;
            if (iw <= 0 || ih <= 0) return;
            if ((long) iw * ih > MAX_BAKE) { glassLive(g, k, x * k, y * k, w * k, h * k, r, a, shadowStrength); return; }
            int R = Math.max(0, Math.min(Math.round(Math.min(r * k, Math.min(iw, ih) / 2f)), Math.min(iw, ih) / 2));
            String size = iw + "x" + ih + "/" + R + "/" + k + "#" + dev.ryaan.ezgamble.themes.Themes.ver;
            if (shadowStrength > 0) {
                int pad = (int) Math.ceil(SHADOW_PAD * k);
                // the strength rounded up to a tenth goes in the key (never over 1 for the tint); baked at half resolution, it is a
                // 14 k blur so the loss is invisible and the bake four times cheaper, then drawn 2x by the quad
                float sq = Math.max(0.1f, (float) Math.ceil(shadowStrength * 10 - 1e-4) / 10f);
                int bw = (iw + 2 * pad + 1) / 2, bh = (ih + 2 * pad + 1) / 2;
                Baked sh = baked("shadow/" + size + "/" + sq, bw, bh, c -> shadowInto(c, pad / 2f, pad / 2f, iw / 2f, ih / 2f, R / 2f, k / 2, sq));
                int tn = tint(shadowStrength / sq);
                if ((tn >>> 24) != 0) {
                    F.n++;
                    g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, sh.id(), x0 - pad, y0 - pad, 0, 0, 2 * bw, 2 * bh, bw, bh, bw, bh, tn);
                }
            }
            float aa = Math.min(0.94f, a);
            Baked body = baked("body/" + size, iw, ih, c -> c.shape(0, 0, iw, ih, R, vgrad(Glass.alpha(Glass.PANEL_TOP, 0.88f), Glass.alpha(Glass.PANEL_BOT, 1)), 1));   // the theme's tints (Lavender: 221836 over 0C0A14)
            blit(g, body, x0, y0, tint(aa));
            Baked sheet = baked("sheet/" + size, iw, ih, c -> sheetInto(c, iw, ih, R, k));
            blit(g, sheet, x0, y0, 0xFFFFFFFF);
        });
    }

    /** The live glass (about 180 slivers), for a sheet too big to bake. Real-pixel coordinates. */
    private static void glassLive(DrawContext g, float k, float X, float Y, float W, float H, float r, float a, float shadowStrength) {
        float R = Math.min(r * k, Math.min(W, H) / 2);
        if (shadowStrength > 0) {
            int n = 6;
            float spread = 14 * k, sa = 0.5f * shadowStrength / n;
            for (int i = n; i >= 1; i--) {
                float e = spread * i / n;
                shape(g, X - e * 0.6f, Y + 4 * k - e * 0.4f, W + e * 1.2f, H + e * 1.2f, R + e, solid(0xFF04020A), sa);
            }
        }
        float aa = Math.min(0.94f, a);
        shape(g, X, Y, W, H, R, vgrad(Glass.alpha(Glass.PANEL_TOP, aa * 0.88f), Glass.alpha(Glass.PANEL_BOT, aa)), 1);
        float gx = W, gy = H, len2 = gx * gx + gy * gy;
        int w0 = Glass.alpha(Glass.LAV, 0.13f), w1 = Glass.alpha(Glass.PINK, 0.07f);
        shape(g, X, Y, W, H, R, new Paint() {
            public int at(float u, float v) { return lerp(w0, w1, (u * W * gx + v * H * gy) / len2); }
            public boolean horizontal() { return true; }
        }, 1);
        float sh = Math.min(H * 0.45f, 40 * k);
        shape(g, X, Y, W, H, R, sheen(sh / H, 0.10f), 1);
        rimLive(g, X, Y, W, H, R, k, 1);
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

    /** Largest channel difference between two colours once multiplied by their alpha (what the eye sees). */
    private static float premulDiff(int a, int b) {
        float aa = (a >>> 24) / 255f, ab = (b >>> 24) / 255f, d = Math.abs(aa - ab) * 255;
        for (int sh = 0; sh <= 16; sh += 8) d = Math.max(d, Math.abs(((a >> sh) & 255) * aa - ((b >> sh) & 255) * ab));
        return d;
    }

    private static int mul(int c, float a) {
        int al = Math.round(((c >>> 24) & 255) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) { return Glass.lerp(a, b, t); }
}
