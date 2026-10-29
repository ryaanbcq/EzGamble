package dev.ryaan.ezgamble.proto;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

/** The prototype's TextRenderer `F`. */
public final class F {
    private F() {}
    public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    public static int width(Object s) { return tr().getWidth(String.valueOf(s)); }
    public static String trimToWidth(Object s, double max) { return tr().trimToWidth(String.valueOf(s), Math.max(0, (int) max)); }
}
