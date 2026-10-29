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
        if (kind == Kind.SLIDERS) {
            Liquid.capsule(g, x, y, w, h, "ghost", hover, 1);
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
        float op = active ? 1f : 0.4f;
        g.getMatrices().pushMatrix();
        if (hover && isFocused() && net.minecraft.client.MinecraftClient.getInstance().mouse.wasLeftButtonClicked()) {   // pressed: the capsule gives a little
            g.getMatrices().translate(x + w / 2f, y + h / 2f); g.getMatrices().scale(0.96f, 0.96f); g.getMatrices().translate(-(x + w / 2f), -(y + h / 2f));
        }
        Liquid.capsule(g, x, y, w, h, kind == Kind.PRIMARY ? "primary" : kind == Kind.DANGER ? "danger" : "ghost", hover, op);
        var f = MinecraftClient.getInstance().textRenderer;
        String s = getMessage().getString();
        int color = switch (kind) { case PRIMARY -> Glass.INK; case GHOST, SLIDERS -> hover ? Glass.WHITE : Glass.TEXT; case DANGER -> Glass.RED; };
        g.drawText(f, s, x + (w - f.getWidth(s)) / 2, y + (h - 8) / 2, Glass.alpha(color, op), kind != Kind.PRIMARY);
        g.getMatrices().popMatrix();
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
