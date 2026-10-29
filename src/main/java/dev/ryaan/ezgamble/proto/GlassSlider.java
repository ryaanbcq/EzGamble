package dev.ryaan.ezgamble.proto;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** 0..1 slider: thin capsule track, gradient fill, glass knob that grows while dragged. */
public class GlassSlider extends Widget {
    private final DoubleSupplier get;
    private final Consumer<Double> set;
    public boolean held;

    public GlassSlider(double x, double y, double w, double h, DoubleSupplier get, Consumer<Double> set) { super(x, y, w, h); this.get = get; this.set = set; }

    @Override
    public void render(G g, double mx, double my) {
        double v = get.getAsDouble(), ty = y + h / 2 - 2;
        P.capsule(g, x, ty, w, 4, "ghost", false);
        if (v > 0) P.capsule(g, x, ty, Math.max(4, (w - 4) * v + 4), 4, "primary", false);
        double kw = held ? 12 : 9, kh = held ? h + 2 : h;
        P.knob(g, x + (w - 9) * v + (9 - kw) / 2, y + (h - kh) / 2, kw, kh);
    }

    @Override public void click(double mx, double my) { held = true; drag(mx, my); }
    @Override public void drag(double mx, double my) { if (held) set.accept(Math.max(0, Math.min(1, (mx - x - 4) / (w - 9)))); }
    @Override public void release(double mx, double my) { held = false; }
}
