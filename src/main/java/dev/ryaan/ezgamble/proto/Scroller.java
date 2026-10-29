package dev.ryaan.ezgamble.proto;

/** Smooth scroll of a list: wheel / trackpad, eased, thin indicator on the right. */
public final class Scroller {
    public double pos, target, max;
    private long at, t = P.now();

    public void wheel(double dy) {
        if (max <= 0) return;
        target = Math.max(0, Math.min(max, target + dy));
        if (Math.abs(dy) < 40) pos = target;   // trackpad: follow the fingers exactly
        at = P.now();
    }

    public void update(double max) {
        long now = P.now(), dt = Math.min(50, now - t); t = now; this.max = Math.max(0, max);
        target = Math.max(0, Math.min(this.max, target));
        pos += (target - pos) * Math.min(1, dt / 60.0);
        if (Math.abs(target - pos) < 0.3) pos = target;
    }

    public void indicator(G g, double x, double y, double h, double contentH) {
        if (max <= 0) return;
        double a = Math.max(0, 1 - Math.max(0, P.now() - at - 700) / 300.0);
        if (a <= 0) return;
        double th = Math.max(14, Math.round(h * h / contentH));
        double p = Math.max(0, Math.min(1, pos / max)), ty = Math.round(y + (h - th) * p);
        P.smooth(g, x, ty, 3, th, 1.5, P.alpha(0xFFFFFFFF, 0.5 * a));
    }
}
