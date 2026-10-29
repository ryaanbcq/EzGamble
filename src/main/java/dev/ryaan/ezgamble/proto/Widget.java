package dev.ryaan.ezgamble.proto;

/** The prototype's Widget: a rectangle that draws itself and takes clicks. GUI coordinates, doubles like the JS. */
public class Widget {
    public double x, y, w, h;
    public boolean visible = true, active = true, down;
    /** A widget inside a scrolling list only answers where the list shows it. */
    public Clip clip;
    /** Set by a list: the row's y before scrolling and the list id. */
    public ListPos list;

    public record Clip(double x, double y, double w, double h) {}
    public record ListPos(String id, double baseY) {}

    public Widget(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    public boolean over(double mx, double my) {
        Clip c = clip;
        if (c != null && !(mx >= c.x && my >= c.y && mx < c.x + c.w && my < c.y + c.h)) return false;
        return visible && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void render(G g, double mx, double my) {}
    public void click(double mx, double my) {}
    public void drag(double mx, double my) {}
    public void release(double mx, double my) {}
    /** Keys while focused; true if used. */
    public boolean key(Key e) { return false; }
}
