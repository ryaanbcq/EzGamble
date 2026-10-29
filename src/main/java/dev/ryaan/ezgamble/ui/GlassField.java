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
        field = new TextFieldWidget(f, x + 6, y + (h - 8) / 2, w - 12, 10, Text.literal(""));
        field.setDrawsBackground(false);
        field.setMaxLength(max);
        field.setEditableColor(Glass.WHITE);
        field.setText(value);
    }

    public void drawBox(DrawContext g) {
        boolean focus = field.isFocused();
        Glass.rounded(g, x, y, w, h, focus ? 0x14A18CD1 : Glass.FILL_SOFT);
        Glass.outline(g, x, y, w, h, focus ? 0x99A18CD1 : Glass.BORDER_SOFT);
    }

    public boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public String text() { return field.getText(); }
}
