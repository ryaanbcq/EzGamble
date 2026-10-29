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
        float e = Ui.easeOut(anim);
        boolean hover = isHovered();
        Liquid.capsule(g, x, y, w, h, "ghost", hover, 1);
        if (e > 0) Liquid.capsule(g, x, y, w, h, "primary", hover, e);
        int k = h - 4;
        Liquid.knob(g, x + 2 + (w - 4 - k) * e, y + 2, k, k);
    }

    @Override
    public void onClick(Click click, boolean doubled) { set.accept(!get.getAsBoolean()); }

    @Override
    public void playDownSound(SoundManager sm) { sm.play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder b) { b.put(NarrationPart.TITLE, getMessage()); }
}
