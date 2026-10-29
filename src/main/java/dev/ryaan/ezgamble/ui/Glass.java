package dev.ryaan.ezgamble.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Outmind "glass" theme: plum translucent panels with pixel-rounded corners, lavender to pink gradient accents. */
public final class Glass {
    private Glass() {}

    public static final int LAV = 0xFFA18CD1, PINK = 0xFFFBC2EB;
    public static final int TEXT = 0xFFCFC8DE, WHITE = 0xFFFFFFFF, MUTE = 0xFF7D7590, DIM = 0xFF5A536B;
    public static final int GREEN = 0xFF4BE07A, RED = 0xFFFF5C5C, INK = 0xFF1A1226;
    public static final int PANEL_TOP = 0xF6161022, PANEL_BOT = 0xF80C0A14, HUD_TOP = 0xDC161022, HUD_BOT = 0xE60C0A14;
    public static final int BORDER = 0x4DFBC2EB, BORDER_SOFT = 0x1AFFFFFF, FILL_SOFT = 0x0AFFFFFF;

    // row insets for a 3 px pixel-art corner: rows 0 and 1 are shortened, the rest is square
    private static final int[] INSET = {2, 1};

    /** Rounded rectangle, flat colour. */
    public static void rounded(DrawContext g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < INSET.length; i++) {
            g.fill(x + INSET[i], y + i, x + w - INSET[i], y + i + 1, color);
            g.fill(x + INSET[i], y + h - 1 - i, x + w - INSET[i], y + h - i, color);
        }
        g.fill(x, y + INSET.length, x + w, y + h - INSET.length, color);
    }

    /** Rounded rectangle with a vertical gradient. */
    public static void roundedGradient(DrawContext g, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < INSET.length; i++) {
            g.fill(x + INSET[i], y + i, x + w - INSET[i], y + i + 1, top);
            g.fill(x + INSET[i], y + h - 1 - i, x + w - INSET[i], y + h - i, bottom);
        }
        g.fillGradient(x, y + INSET.length, x + w, y + h - INSET.length, top, bottom);
    }

    /** 1 px rounded outline. */
    public static void outline(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** Main panel: drop shadow, plum gradient, pink outline. */
    public static void panel(DrawContext g, int x, int y, int w, int h, boolean hud) {
        rounded(g, x + 3, y + 4, w, h, 0x50000000);
        rounded(g, x + 1, y + 2, w, h, 0x40000000);
        roundedGradient(g, x, y, w, h, hud ? HUD_TOP : PANEL_TOP, hud ? HUD_BOT : PANEL_BOT);
        outline(g, x, y, w, h, BORDER);
    }

    /** Horizontal lavender to pink gradient, drawn in strips. */
    public static void accentBar(DrawContext g, int x, int y, int w, int h, float alpha) {
        int a = Math.round(255 * alpha) << 24;
        for (int i = 0; i < w; i += 2) {
            int c = lerp(LAV, PINK, (float) i / Math.max(1, w - 1)) & 0x00FFFFFF | a;
            g.fill(x + i, y, Math.min(x + w, x + i + 2), y + h, c);
        }
    }

    /** Rounded strip with the horizontal accent gradient (buttons, first row). */
    public static void accentRounded(DrawContext g, int x, int y, int w, int h, float alpha) {
        int a = Math.round(255 * alpha) << 24;
        for (int i = 0; i < w; i += 2) {
            int c = lerp(LAV, PINK, (float) i / Math.max(1, w - 1)) & 0x00FFFFFF | a;
            int x1 = x + i, x2 = Math.min(x + w, x + i + 2);
            int top = 0, bot = 0;
            if (x1 < x + 2 || x2 > x + w - 2) { top = 2; bot = 2; }
            else if (x1 < x + 3 || x2 > x + w - 3) { top = 1; bot = 1; }
            g.fill(x1, y + top, x2, y + h - bot, c);
        }
    }

    /** Text coloured from lavender to pink across its width. */
    public static void gradientText(DrawContext g, TextRenderer f, String s, int x, int y, boolean shadow) {
        int total = Math.max(1, f.getWidth(s));
        int cx = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int c = lerp(LAV, PINK, (float) cx / total);
            g.drawText(f, ch, x + cx, y, c, shadow);
            cx += f.getWidth(ch);
        }
    }

    /** Small filled dot, corners cut. */
    public static void dot(DrawContext g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 5, y + 4, color);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bb = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (r << 16) | (gg << 8) | bb;
    }

    /** Copy of the stack with the enchantment glint forced on (every reward looks precious). */
    public static net.minecraft.item.ItemStack shiny(net.minecraft.item.ItemStack stack) {
        net.minecraft.item.ItemStack c = stack.copy();
        c.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return c;
    }

    public static int alpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255 * alpha) << 24);
    }
}
