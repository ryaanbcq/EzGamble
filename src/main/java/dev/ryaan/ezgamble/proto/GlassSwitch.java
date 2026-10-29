package dev.ryaan.ezgamble.proto;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** On/off switch: clear track off, Outmind gradient on, white glass knob that stretches when held. */
public class GlassSwitch extends Widget {
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private double anim;
    private long last = P.now();

    public GlassSwitch(double x, double y, double w, double h, BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, w, h); this.get = get; this.set = set; anim = get.getAsBoolean() ? 1 : 0;
    }

    @Override
    public void render(G g, double mx, double my) {
        boolean hover = over(mx, my), on = get.getAsBoolean();
        long t = P.now(); double dt = (t - last) / 160.0; last = t;
        anim = on ? Math.min(1, anim + dt) : Math.max(0, anim - dt);
        double e = P.easeOut(anim);
        P.capsule(g, x, y, w, h, "ghost", hover);
        if (e > 0) P.capsule(g, x, y, w, h, "primary", hover, e);
        double held = down ? 4 : 0, k = h - 4, kx = x + 2 + (w - 4 - k - held) * e;
        P.knob(g, kx, y + 2, k + held, k);
    }

    @Override
    public void click(double mx, double my) { P.play("click"); set.accept(!get.getAsBoolean()); }
}
