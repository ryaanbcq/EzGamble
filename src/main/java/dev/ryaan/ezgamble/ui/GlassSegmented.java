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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Segmented control (tabs, ranges): one capsule, and a highlighted pill that slides to the selected segment.
 * The screen keeps the instance between init() calls so the pill keeps sliding instead of jumping.
 */
public final class GlassSegmented extends ClickableWidget {
    private final String[] ids, labels;
    private final int[] widths;
    private final Supplier<String> get;
    private final Consumer<String> set;
    private float ix = Float.NaN, iw;
    private long last = System.currentTimeMillis();

    public GlassSegmented(int h, String[][] items, Supplier<String> get, Consumer<String> set) {
        super(0, 0, 0, h, Text.literal("segmented"));
        ids = new String[items.length]; labels = new String[items.length]; widths = new int[items.length];
        int w = 4;
        for (int i = 0; i < items.length; i++) {
            ids[i] = items[i][0]; labels[i] = items[i][1];
            widths[i] = Ui.font().getWidth(labels[i]) + 12;
            w += widths[i];
        }
        setWidth(w);
        this.get = get; this.set = set;
    }

    /** Moves the control; the pill follows without a jump. */
    public void place(int x, int y) {
        if (!Float.isNaN(ix)) ix += x - getX();
        setX(x); setY(y);
    }

    private int segX(int i) { int sx = getX() + 2; for (int j = 0; j < i; j++) sx += widths[j]; return sx; }

    @Override
    protected void renderWidget(DrawContext g, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        String cur = get.get();
        int sel = -1;
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(cur)) sel = i;
        long t = System.currentTimeMillis();
        float dt = Math.min(64, t - last); last = t;
        Liquid.capsule(g, x, y, w, h, "ghost", false, 1);
        if (sel >= 0) {
            int sx = segX(sel), sw = widths[sel];
            if (Float.isNaN(ix)) { ix = sx; iw = sw; }
            float f = 1 - (float) Math.exp(-dt / 70f);
            float moving = Math.abs(sx - ix);
            ix += (sx - ix) * f; iw += (sw - iw) * f;
            float stretch = Math.min(10, moving * 0.35f);   // liquid: wider while it travels
            Liquid.capsule(g, ix - stretch / 2, y + 2, iw + stretch, h - 4, "selected", isHovered(), 1);
        } else ix = Float.NaN;
        boolean over = isHovered();
        for (int i = 0; i < ids.length; i++) {
            int sx = segX(i);
            boolean hov = over && mx >= sx && mx < sx + widths[i];
            g.drawText(Ui.font(), labels[i], sx + (widths[i] - Ui.font().getWidth(labels[i])) / 2, y + (h - 8) / 2,
                    i == sel || hov ? Glass.WHITE : Glass.TEXT, true);
        }
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        double mx = click.x();
        for (int i = 0; i < ids.length; i++) {
            int sx = segX(i);
            if (mx >= sx && mx < sx + widths[i] && !ids[i].equals(get.get())) set.accept(ids[i]);
        }
    }

    @Override
    public void playDownSound(SoundManager sm) { sm.play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f)); }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder b) { b.put(NarrationPart.TITLE, getMessage()); }
}
