package dev.ryaan.ezgamble.proto;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Colour choice: round swatches, the picked one ringed in white. Colours are "gradient" or 0xAARRGGBB as Integer. */
public class ColorSwatches extends Widget {
    public static final Object[] COLORS = {"gradient", 0xFFFFFFFF, 0xFFA18CD1, 0xFFFBC2EB, 0xFF4BE07A, 0xFFFF5C5C, 0xFFFFD36B, 0xFF7FE3F0};
    private final Supplier<Object> get;
    private final Consumer<Object> set;

    public ColorSwatches(double x, double y, Supplier<Object> get, Consumer<Object> set) { super(x, y, COLORS.length * 12 - 2, 10); this.get = get; this.set = set; }

    @Override
    public void render(G g, double mx, double my) {
        for (int i = 0; i < COLORS.length; i++) {
            Object c = COLORS[i];
            double sx = x + i * 12;
            boolean sel = c.equals(get.get()), hov = mx >= sx && mx < sx + 10 && over(mx, my);
            if (c instanceof String) P.smooth(g, sx, y, 10, 10, 5, P.h(P.LAV, P.PINK), 0x59FFFFFF); else P.smooth(g, sx, y, 10, 10, 5, (Integer) c, 0x59FFFFFF);
            if (sel) P.ring(g, sx - 2, y - 2, 14, 14, 7, P.WHITE, 1); else if (hov) P.ring(g, sx - 2, y - 2, 14, 14, 7, 0x80FFFFFF, 1);
        }
    }

    @Override
    public void click(double mx, double my) {
        int i = (int) Math.floor((mx - x) / 12);
        if (i >= 0 && i < COLORS.length) { P.play("click"); set.accept(COLORS[i]); }
    }
}
