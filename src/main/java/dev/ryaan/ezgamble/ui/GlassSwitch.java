package dev.ryaan.ezgamble.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** On/off switch: clear track when off, Outmind gradient when on, knob sliding in 160 ms. */
public final class GlassSwitch extends ClickableWidget {
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private float anim;
    private long last = System.currentTimeMillis();

    public GlassSwitch(int x, int y, int w, int h, BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, w, h, Text.literal("switch"));
        this.get = get; this.set = set;
        anim = get.getAsBoolean() ? 1 : 0;
    }

    @Override
    protected void renderWidget(DrawContext g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean on = get.getAsBoolean();
        long t = System.currentTimeMillis();
        float dt = (t - last) / 160f; last = t;
        anim = on ? Math.min(1, anim + dt) : Math.max(0, anim - dt);
        if (on) { Glass.rounded(g, x + 1, y + 2, w, h, 0x40000000); Glass.accentRounded(g, x, y, w, h, 1f); }
        else { Glass.rounded(g, x, y, w, h, isHovered() ? 0x18FFFFFF : Glass.FILL_SOFT); Glass.outline(g, x, y, w, h, 0x1FFFFFFF); }
        int k = h - 6;
        Glass.rounded(g, x + 3 + Math.round((w - 6 - k) * Ui.easeOut(anim)), y + 3, k, k, on ? Glass.INK : Glass.TEXT);
    }

    @Override
    public void onClick(Click click, boolean doubled) { set.accept(!get.getAsBoolean()); }

    @Override
    public void playDownSound(SoundManager sm) { sm.play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder b) { b.put(NarrationPart.TITLE, getMessage()); }
}
