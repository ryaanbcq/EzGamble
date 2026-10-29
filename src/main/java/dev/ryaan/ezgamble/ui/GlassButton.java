package dev.ryaan.ezgamble.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Glass-styled button: PRIMARY = accent gradient, GHOST = outlined, DANGER = red outline. */
public final class GlassButton extends ClickableWidget {
    public enum Kind { PRIMARY, GHOST, DANGER, SLIDERS }

    private final Kind kind;
    private final Runnable action;

    public GlassButton(int x, int y, int w, int h, String label, Kind kind, Runnable action) {
        super(x, y, w, h, Text.literal(label));
        this.kind = kind;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHovered() && active;
        switch (kind) {
            case PRIMARY -> {
                Glass.rounded(g, x + 1, y + 2, w, h, 0x40000000);
                Glass.accentRounded(g, x, y, w, h, active ? 1f : 0.4f);
                if (hover) Glass.rounded(g, x, y, w, h, 0x22FFFFFF);
            }
            case GHOST -> {
                Glass.rounded(g, x, y, w, h, hover ? 0x18FFFFFF : Glass.FILL_SOFT);
                Glass.outline(g, x, y, w, h, hover ? 0x40FFFFFF : 0x1FFFFFFF);
            }
            case DANGER -> {
                Glass.rounded(g, x, y, w, h, hover ? 0x33FF5C5C : 0x14FF5C5C);
                Glass.outline(g, x, y, w, h, hover ? 0xB3FF5C5C : 0x80FF5C5C);
            }
            case SLIDERS -> {
                Glass.rounded(g, x, y, w, h, hover ? 0x18FFFFFF : Glass.FILL_SOFT);
                Glass.outline(g, x, y, w, h, hover ? 0x40FFFFFF : 0x1FFFFFFF);
                // three sliders, Mod Menu style: track + knob
                int c = hover ? Glass.WHITE : Glass.TEXT, k = hover ? Glass.PINK : Glass.LAV;
                int ix = x + (w - 12) / 2, iy = y + (h - 12) / 2;
                int[] knob = {8, 2, 5};
                for (int i = 0; i < 3; i++) {
                    int ly = iy + i * 5;
                    g.fill(ix, ly + 1, ix + 12, ly + 2, c);
                    g.fill(ix + knob[i], ly, ix + knob[i] + 3, ly + 3, k);
                }
                return;
            }
        }
        var f = MinecraftClient.getInstance().textRenderer;
        String s = getMessage().getString();
        int color = switch (kind) { case PRIMARY -> Glass.INK; case GHOST, SLIDERS -> hover ? Glass.WHITE : Glass.TEXT; case DANGER -> Glass.RED; };
        g.drawText(f, s, x + (w - f.getWidth(s)) / 2, y + (h - 8) / 2, color, kind != Kind.PRIMARY);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        action.run();
    }

    @Override
    public void playDownSound(net.minecraft.client.sound.SoundManager sm) {
        sm.play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder b) {
        b.put(NarrationPart.TITLE, getMessage());
    }
}
