package dev.ryaan.ezgamble.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** A vanilla text field without its box, drawn inside a glass box (the box is drawn by the screen). */
public final class GlassField {
    public final TextFieldWidget field;
    public final int x, y, w, h;

    public GlassField(TextRenderer f, int x, int y, int w, int h, String value, int max) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        field = new TextFieldWidget(f, x + 9, y + (h - 8) / 2, w - 18, 10, Text.literal(""));
        field.setDrawsBackground(false);
        field.setMaxLength(max);
        field.setEditableColor(Glass.WHITE);
        field.setText(value);
    }

    public void drawBox(DrawContext g) {
        Liquid.capsule(g, x, y, w, h, field.isFocused() ? "selected" : "ghost", false, 1);
    }

    public boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public String text() { return field.getText(); }
}
