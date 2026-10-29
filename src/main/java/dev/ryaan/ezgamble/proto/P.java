package dev.ryaan.ezgamble.proto;

import dev.ryaan.ezgamble.ui.Glass;
import dev.ryaan.ezgamble.ui.Liquid;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** The prototype's global helpers and colours (src.html top level), same names. */
public final class P {
    private P() {}

    // ---- colours (glass mode: the vibrant greys and red of the prototype)
    public static final int LAV = 0xFFA18CD1, PINK = 0xFFFBC2EB, TEXT = 0xFFCFC8DE, WHITE = 0xFFFFFFFF;
    public static final int MUTE = 0xFFB8AFCC, DIM = 0xFF9C93B5, GREEN = 0xFF4BE07A, INK = 0xFF1A1226, RED = 0xFFFF8A8A;
    public static final int PANEL_TOP = 0xF6161022, PANEL_BOT = 0xF80C0A14, HUD_TOP = 0xDC161022, HUD_BOT = 0xE60C0A14;
    public static final int BORDER = 0x4DFBC2EB, BORDER_SOFT = 0x1AFFFFFF, FILL_SOFT = 0x0AFFFFFF, SOFT_GREY = 0xFFA79FB8;

    public static long now() { return System.currentTimeMillis(); }
    public static double easeOut(double x) { x = Math.max(0, Math.min(1, x)); return 1 - Math.pow(1 - x, 3); }
    public static double easeIn(double x) { return x * x; }
    /** AuctionHud.pulse: 0..1 over 1.2 s. */
    public static double pulse(double t) { return 0.5 + 0.5 * Math.sin(t / 1200 * 2 * Math.PI); }

    public static int lerp(int a, int b, double t) { return Glass.lerp(a, b, (float) t); }
    /** Colour with this alpha (replaces it, like the prototype). */
    public static int alpha(int c, double a) { return (c & 0xFFFFFF) | ((int) Math.round(255 * Math.max(0, Math.min(1, a))) << 24); }
    public static int shade(int c, double k) {
        return (c & 0xFF000000) | ((int) Math.round(((c >>> 16) & 255) * k) << 16) | ((int) Math.round(((c >>> 8) & 255) * k) << 8) | (int) Math.round((c & 255) * k);
    }

    public static String skinOf(String name) { return name; }
    public static void play(String name) { Snd.play(name); }
    public static void play(String name, double pitch) { Snd.play(name, pitch); }
    public static void play(String name, double pitch, double vol) { Snd.play(name, pitch, vol); }

    public static ItemStack shiny(ItemStack s) { ItemStack c = s.copy(); c.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true); return c; }

    // ---- flat shapes (EzAuction Glass)
    public static void rounded(G g, double x, double y, double w, double h, int c) {
        int X = (int) Math.round(x), Y = (int) Math.round(y), W = (int) Math.round(w), H = (int) Math.round(h);
        Glass.rounded(g.d, X, Y, W, H, c);
    }
    public static void outline(G g, double x, double y, double w, double h, int c) {
        Glass.outline(g.d, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), c);
    }
    public static void accentBar(G g, double x, double y, double w, double h, double a) {
        Glass.accentBar(g.d, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), (float) a);
    }
    public static void accentRounded(G g, double x, double y, double w, double h, double a) {
        Glass.accentRounded(g.d, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), (float) a);
    }
    public static void dot(G g, double x, double y, int c) { g.fill(x + 1, y, x + 4, y + 5, c); g.fill(x, y + 1, x + 5, y + 4, c); }

    public static void gradientText(G g, String s, double x, double y, boolean shadow) { gradientText(g, s, x, y, shadow, 1); }
    public static void gradientText(G g, String s, double x, double y, boolean shadow, double a) {
        int total = Math.max(1, F.width(s)), cx = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int c = lerp(LAV, PINK, (double) cx / total);
            g.drawText(ch, x + cx, y, a >= 1 ? c : alpha(c, a), shadow);
            cx += F.width(ch);
            i += Character.charCount(cp);
        }
    }

    // ---- liquid glass (see ui.Liquid)
    public static Liquid.Paint h(int a, int b) { return Liquid.hgrad(a, b); }
    public static Liquid.Paint v(int a, int b) { return Liquid.vgrad(a, b); }

    public static void smooth(G g, double x, double y, double w, double h, double r, int fill) { smooth(g, x, y, w, h, r, fill, 0); }
    public static void smooth(G g, double x, double y, double w, double h, double r, int fill, int stroke) { Liquid.smooth(g.d, (float) x, (float) y, (float) w, (float) h, (float) r, fill, stroke); }
    public static void smooth(G g, double x, double y, double w, double h, double r, Liquid.Paint fill) { smooth(g, x, y, w, h, r, fill, 0); }
    public static void smooth(G g, double x, double y, double w, double h, double r, Liquid.Paint fill, int stroke) { Liquid.smooth(g.d, (float) x, (float) y, (float) w, (float) h, (float) r, fill, stroke); }
    public static void ring(G g, double x, double y, double w, double h, double r, int c, double lw) { Liquid.ring(g.d, (float) x, (float) y, (float) w, (float) h, (float) r, c, (float) lw); }
    public static void ring(G g, double x, double y, double w, double h, double r, int c) { ring(g, x, y, w, h, r, c, 1); }
    public static void knob(G g, double x, double y, double w, double h) { Liquid.knob(g.d, (float) x, (float) y, (float) w, (float) h); }
    /** kind: primary, selected, danger, ghost. */
    public static void capsule(G g, double x, double y, double w, double h, String kind, boolean hover) { capsule(g, x, y, w, h, kind, hover, 1); }
    public static void capsule(G g, double x, double y, double w, double h, String kind, boolean hover, double opacity) {
        Liquid.capsule(g.d, (float) x, (float) y, (float) w, (float) h, kind, hover, (float) opacity);
    }
    /** The glass sheet: a = tint opacity, shadow strength. */
    public static void glass(G g, double x, double y, double w, double h, double r, double a, double shadow) {
        Liquid.glass(g.d, (float) x, (float) y, (float) w, (float) h, (float) r, (float) a, (float) shadow);
    }
    public static void glass(G g, double x, double y, double w, double h, double r, double a) { glass(g, x, y, w, h, r, a, 1); }

    /** Hover highlight of a clickable card: brighter glass and a pink rim (k = 0..1). */
    public static void hoverSheen(G g, double x, double y, double w, double h, double r, double k) {
        if (k <= 0.01) return;
        smooth(g, x, y, w, h, r, alpha(0xFFFFFFFF, 0.06 * k), alpha(PINK, 0.65 * k));
    }

    /** Pressed: scale 0.96 around the centre (call between push and pop). */
    public static void squish(G g, double x, double y, double w, double h) {
        g.translate(x + w / 2, y + h / 2); g.scale(0.96, 0.96); g.translate(-(x + w / 2), -(y + h / 2));
    }

    /** Content card: accent, red, row, chart, soft. */
    public static void card(G g, double x, double y, double w, double h, String kind) {
        switch (kind) {
            case "accent" -> smooth(g, x, y, w, h, 7, h(0x24A18CD1, 0x14FBC2EB), 0x4DFBC2EB);
            case "red" -> smooth(g, x, y, w, h, h / 2, 0x26FF5C5C, 0x80FF5C5C);
            case "row" -> smooth(g, x, y, w, h, 6, 0x0DFFFFFF, 0x0FFFFFFF);
            case "chart" -> smooth(g, x, y, w, h, 7, 0x0AFFFFFF, 0x0DFFFFFF);
            default -> smooth(g, x, y, w, h, 7, 0x0DFFFFFF, 0x14FFFFFF);
        }
    }

    public static void tile(G g, double x, double y, String label, String value, int color) {
        g.drawText(label, x, y, MUTE, false);
        g.drawText(value, x, y + 12, color, false);
    }

    public static int pillW(String label, String value) { return (label == null || label.isEmpty() ? 0 : F.width(label) + 4) + F.width(value) + 14; }
    public static void pill(G g, double x, double y, String label, String value) { pill(g, x, y, label, value, WHITE, "ghost"); }
    public static void pill(G g, double x, double y, String label, String value, int vc) { pill(g, x, y, label, value, vc, "ghost"); }
    public static void pill(G g, double x, double y, String label, String value, int vc, String kind) {
        int w = pillW(label, value);
        smooth(g, x, y, w, 15, 7.5, kind.equals("selected") ? 0x2EA18CD1 : 0x10FFFFFF, 0);
        double tx = x + 7;
        if (label != null && !label.isEmpty()) { g.drawText(label, tx, y + 4, MUTE, false); tx += F.width(label) + 4; }
        g.drawText(value, tx, y + 4, vc, false);
    }
    /** items: {label, value[, color]}. */
    public static void pillsRight(G g, double rx, double y, Object[][] items) {
        double x = rx;
        for (int i = items.length - 1; i >= 0; i--) {
            String l = (String) items[i][0], v = (String) items[i][1];
            x -= pillW(l, v);
            pill(g, x, y, l, v, items[i].length > 2 ? (int) items[i][2] : WHITE);
            x -= 4;
        }
    }

    public static int seatTagW(String name, String value) { return 5 + 8 + 4 + F.width(name) + 6 + F.width(value) + 7; }
    public static void seatTag(G g, double cx, double y, String skin, String name, String value, int vc, String kind) {
        int w = seatTagW(name, value);
        double x = Math.round(cx - w / 2.0);
        boolean sel = kind.equals("selected");
        if (sel) smooth(g, x, y, w, 15, 7.5, h(0x40A18CD1, 0x33FBC2EB), 0x66FBC2EB); else smooth(g, x, y, w, 15, 7.5, 0x10FFFFFF, 0);
        g.drawHead(skin, x + 5, y + 4, 8);
        g.drawText(name, x + 17, y + 4, sel ? WHITE : TEXT, false);
        g.drawText(value, x + 17 + F.width(name) + 6, y + 4, vc, false);
    }
    public static void seatTag(G g, double cx, double y, String skin, String name, String value, int vc) { seatTag(g, cx, y, skin, name, value, vc, "ghost"); }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000, h = s / 3600, m = (s / 60) % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s % 60) : String.format("%d:%02d", m, s % 60);
    }
    public static String pad2(long n) { return String.format("%02d", n); }
    private static LocalDateTime dt(long t) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()); }
    public static String DATE(long t) { var d = dt(t); return pad2(d.getDayOfMonth()) + "/" + pad2(d.getMonthValue()) + " " + pad2(d.getHour()) + ":" + pad2(d.getMinute()); }
    public static String DATE_S(long t) { return DATE(t) + ":" + pad2(dt(t).getSecond()); }
}
