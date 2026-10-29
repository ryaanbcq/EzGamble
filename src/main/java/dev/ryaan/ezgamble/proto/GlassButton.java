package dev.ryaan.ezgamble.proto;

/** kind: PRIMARY (tinted glass, the one action), GHOST (clear capsule), DANGER. */
public class GlassButton extends Widget {
    public String label, kind;
    public Runnable action;

    public GlassButton(double x, double y, double w, double h, String label, String kind, Runnable action) {
        super(x, y, w, h); this.label = label; this.kind = kind; this.action = action;
    }

    @Override
    public void render(G g, double mx, double my) {
        boolean hover = over(mx, my) && active;
        double op = active ? 1 : 0.4;   // disabled: faded
        g.push();
        if (down && hover) P.squish(g, x, y, w, h);   // pressed: the capsule gives a little
        P.capsule(g, x, y, w, h, kind.equals("PRIMARY") ? "primary" : kind.equals("DANGER") ? "danger" : "ghost", hover, op);
        int color = kind.equals("PRIMARY") ? P.INK : kind.equals("DANGER") ? P.RED : hover ? P.WHITE : P.TEXT;
        g.drawText(label, x + (int) ((w - F.width(label)) / 2), y + (int) ((h - 8) / 2), P.alpha(color, ((color >>> 24) / 255.0) * op), !kind.equals("PRIMARY"));
        g.pop();
    }

    @Override
    public void click(double mx, double my) { P.play("click"); action.run(); }
}
