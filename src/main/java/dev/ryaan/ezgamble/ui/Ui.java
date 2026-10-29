package dev.ryaan.ezgamble.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;

/** Small drawing helpers shared by the menu pages (prototype: card, tile, pill, clock, DATE_S...). */
public final class Ui {
    private Ui() {}

    public static TextRenderer font() { return MinecraftClient.getInstance().textRenderer; }

    public static float easeOut(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }

    /** 0..1 over 1.2 s (AuctionHud.pulse). */
    public static float pulse(long t) { return 0.5f + 0.5f * (float) Math.sin(t / 1200.0 * 2 * Math.PI); }

    /** Content card, flat look (the liquid glass shader comes later). kind: accent, red, row, soft, chart. */
    public static void card(DrawContext g, int x, int y, int w, int h, String kind) {
        switch (kind) {
            case "accent" -> { Glass.accentRounded(g, x, y, w, h, 0.16f); Glass.outline(g, x, y, w, h, 0x59A18CD1); }
            case "red" -> { Glass.rounded(g, x, y, w, h, Glass.alpha(Glass.RED, 0.14f)); Glass.outline(g, x, y, w, h, Glass.alpha(Glass.RED, 0.45f)); }
            case "row" -> Glass.rounded(g, x, y, w, h, 0x08FFFFFF);
            default -> { Glass.rounded(g, x, y, w, h, 0x0AFFFFFF); if (kind.equals("soft")) Glass.outline(g, x, y, w, h, Glass.BORDER_SOFT); }
        }
    }

    /** ProfitScreen.tile: grey label, value below. */
    public static void tile(DrawContext g, int x, int y, String label, String value, int color) {
        g.drawText(font(), label, x, y, Glass.MUTE, false);
        g.drawText(font(), value, x, y + 12, color, false);
    }

    public static int pillW(String label, String value) {
        return (label.isEmpty() ? 0 : font().getWidth(label) + 4) + font().getWidth(value) + 14;
    }

    /** Data capsule: quiet fill, grey label, value. */
    public static void pill(DrawContext g, int x, int y, String label, String value, int vc) {
        Glass.rounded(g, x, y, pillW(label, value), 15, 0x14FFFFFF);
        int tx = x + 7;
        if (!label.isEmpty()) { g.drawText(font(), label, tx, y + 4, Glass.MUTE, false); tx += font().getWidth(label) + 4; }
        g.drawText(font(), value, tx, y + 4, vc, false);
    }

    /** Pills laid out from the right edge leftwards, 4 px apart. items = {label, value, color}. */
    public static void pillsRight(DrawContext g, int rx, int y, Object[][] items) {
        int x = rx;
        for (int i = items.length - 1; i >= 0; i--) {
            String l = (String) items[i][0], v = (String) items[i][1];
            x -= pillW(l, v);
            pill(g, x, y, l, v, items[i].length > 2 ? (int) items[i][2] : Glass.WHITE);
            x -= 4;
        }
    }

    /** Gradient text with an overall opacity (title fade). */
    public static void gradientText(DrawContext g, String s, int x, int y, boolean shadow, float a) {
        TextRenderer f = font();
        int total = Math.max(1, f.getWidth(s)), cx = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int c = Glass.lerp(Glass.LAV, Glass.PINK, (float) cx / total);
            g.drawText(f, ch, x + cx, y, a >= 1 ? c : Glass.alpha(c, a), shadow);
            cx += f.getWidth(ch);
        }
    }

    public static String clock(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s / 60) % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s % 60) : String.format("%d:%02d", m, s % 60);
    }

    /** "29/10 07:12:05". */
    public static String dateS(long t) {
        LocalDateTime d = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault());
        return String.format("%02d/%02d %02d:%02d:%02d", d.getDayOfMonth(), d.getMonthValue(), d.getHour(), d.getMinute(), d.getSecond());
    }
}
