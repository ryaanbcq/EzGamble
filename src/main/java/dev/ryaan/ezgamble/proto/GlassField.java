package dev.ryaan.ezgamble.proto;

/** Text field: clear capsule, blinking caret, placeholder; typing handled here (type(e)). search = magnifier + clear. */
public class GlassField extends Widget {
    public String value, placeholder;
    public int max;
    public boolean focused = true, search, selectAll;
    public long typedAt = P.now();
    public Runnable onChange;
    /** Characters accepted (default: letters, digits, space, _ . ' -). */
    public String allowed = "[\\p{L}\\p{N} _.'-]";

    public GlassField(double x, double y, double w, double h, String value, String placeholder) { this(x, y, w, h, value, placeholder, 16); }
    public GlassField(double x, double y, double w, double h, String value, String placeholder, int max) {
        super(x, y, w, h); this.value = value; this.placeholder = placeholder; this.max = max;
    }

    @Override
    public void render(G g, double mx, double my) {
        P.capsule(g, x, y, w, h, focused ? "selected" : "ghost", over(mx, my));
        double ty = y + (int) ((h - 8) / 2), tx = x + (search ? 20 : 9);
        if (search) {   // magnifier: a 5 px ring and its handle, then a clear button once something is typed
            double mx0 = x + 8, my0 = ty;
            int c = focused ? P.WHITE : P.MUTE;
            g.fill(mx0 + 1, my0, mx0 + 4, my0 + 1, c); g.fill(mx0 + 1, my0 + 4, mx0 + 4, my0 + 5, c);
            g.fill(mx0, my0 + 1, mx0 + 1, my0 + 4, c); g.fill(mx0 + 4, my0 + 1, mx0 + 5, my0 + 4, c);
            g.fill(mx0 + 5, my0 + 5, mx0 + 6, my0 + 6, c); g.fill(mx0 + 6, my0 + 6, mx0 + 7, my0 + 7, c);
            if (!value.isEmpty()) g.drawText("×", x + w - 12, ty, overClear(mx, my) ? P.WHITE : P.MUTE, false);
        }
        if (selectAll && !value.isEmpty()) g.fill(tx - 1, ty - 1, tx + F.width(value) + 1, ty + 9, 0x66A18CD1);
        if (!value.isEmpty()) g.drawText(value, tx, ty, P.WHITE, true); else g.drawText(placeholder, tx, ty, P.DIM, false);
        if (focused && ((P.now() - typedAt) % 1060) < 530) {
            double cx = tx + F.width(value) + 1;
            g.fill(cx, ty - 1, cx + 1, ty + 9, P.WHITE);
        }
    }

    public boolean overClear(double mx, double my) { return search && !value.isEmpty() && over(mx, my) && mx >= x + w - 16; }

    @Override
    public void click(double mx, double my) {
        if (overClear(mx, my)) { value = ""; typedAt = P.now(); if (onChange != null) onChange.run(); return; }
        focused = true;
    }

    /** true if the key was used. */
    public boolean type(Key e) {
        if (selectAll && (e.is("Backspace") || e.isChar())) value = "";
        selectAll = false;
        if (e.is("Backspace")) { if (!value.isEmpty()) value = value.substring(0, value.length() - 1); typedAt = P.now(); if (onChange != null) onChange.run(); return true; }
        if (e.isChar() && e.key().matches(allowed) && value.length() < max && F.width(value + e.key()) < w - (search ? 34 : 20)) {
            value += e.key(); typedAt = P.now(); if (onChange != null) onChange.run(); return true;
        }
        return false;
    }

    @Override
    public boolean key(Key e) { return focused && type(e); }
}
