package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.data.Payments;
import dev.ryaan.ezgamble.hud.HudConfig.Rect;
import dev.ryaan.ezgamble.hud.HudConfig.Style;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * HUD layout editor. No fixed side panel (it hid widgets and jumped sides):
 *  - a compact glass TOOLBAR at the bottom centre (presets, Reset, Export, Done), sliding away when a dragged widget
 *    passes under it;
 *  - an INSPECTOR popover anchored next to the selected widget, on the side with room, never over it; hidden
 *    while dragging, popping back after the drop;
 *  - selection animation: spring "pop" of the widget, a lavender to pink ripple leaving its edges, a breathing
 *    ring while selected, and a lift (x1.04) while dragged.
 */
public final class HudEditorScreen extends ProtoScreen {
    static final int IW = 150, PAD = 12;
    /** Inspector rows per widget; the toast only has its switch, it wears the card's style. */
    static final Map<String, String[]> ROWS = Map.of(
            "session", new String[]{"visible", "size", "color", "opacity", "numbers", "sign", "curve", "winrate", "total"},
            "last", new String[]{"visible"},
            "pay", new String[]{"visible", "size", "color", "opacity", "numbers"});
    static final Map<String, String> LABEL = Map.of("visible", "Visible", "size", "Size", "color", "Color", "opacity", "Opacity", "numbers", "Numbers",
            "sign", "+/- colors", "curve", "Curve", "winrate", "Win rate", "total", "All-time");

    private record Pt(double x, double y) {}
    private record Guide(char axis, double v) {}
    private record Snap(double v, Guide guide) {}
    private static final class Drag { String id; double dx, dy, sx, sy; }
    /** A widget of the toolbar or inspector with its offset from the panel's corner. */
    private static final class Ctl { final Widget w; final double dx, dy; Ctl(Widget w, double dx, double dy) { this.w = w; this.dx = dx; this.dy = dy; } }

    private String sel;
    private long selAt, insAt, last = now();
    private Drag dragging;
    private boolean moved;
    private final List<Guide> guides = new ArrayList<>();
    private String side;
    private Pt ins;
    private double lift, tbOff;
    private long resetArmed;
    private final List<Ctl> toolbar = new ArrayList<>(), ctrl = new ArrayList<>();
    private GlassSegmented presets;
    private GlassButton resetBtn, numbersBtn;

    public HudEditorScreen() { super("HUD layout"); }

    private int ih() { return sel != null ? 30 + ROWS.get(sel).length * 22 + (sel.equals("last") ? 14 : 0) : 0; }   // + the toast's hint line

    // ---- toolbar

    private record TB(int x, int y, int w, int h) {}

    private TB toolbarRect() {
        if (presets == null) presets = new GlassSegmented(0, 0, 18, new String[][]{{"minimal", "Minimal"}, {"standard", "Standard"}, {"streamer", "Streamer"}},
                HudConfig::presetOf, n -> { applyPreset(n); selAt = now(); });
        int w = (int) (3 + presets.w + 6 + 46 + 4 + 50 + 4 + 52 + 4);
        return new TB((int) (SW - w) >> 1, (int) SH - 22 - 8 - 24, w, 24);
    }

    @Override
    protected void init2() {
        toolbar.clear();
        TB t = toolbarRect();
        double x = t.x() + 3;
        presets.place(x, 0); toolbar.add(new Ctl(presets, 0, 3)); add(presets); x += presets.w + 6;
        resetBtn = new GlassButton(x, 0, 46, 16, resetArmed > now() ? "Sure?" : "Reset", "DANGER", this::reset);
        toolbar.add(new Ctl(resetBtn, 0, 4)); add(resetBtn); x += 50;
        GlassButton ex = new GlassButton(x, 0, 50, 16, "Export", "GHOST", HudEditorScreen::export);
        toolbar.add(new Ctl(ex, 0, 4)); add(ex); x += 54;
        GlassButton done = new GlassButton(x, 0, 52, 16, "DONE", "PRIMARY", this::close);
        toolbar.add(new Ctl(done, 0, 4)); add(done);
        buildInspector();
    }

    /** Export: the JSON goes to the clipboard. */
    static void export() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.keyboard.setClipboard(exportJson());
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal("HUD layout copied to the clipboard").withColor(0x4BE07A), false);
    }

    // ---- inspector

    private void buildInspector() {
        // keep the toolbar widgets, drop the inspector's
        children.clear();
        for (Ctl c : toolbar) children.add(c.w);
        ctrl.clear();
        numbersBtn = null;
        if (sel == null) return;
        Style own = HUD.get(sel), c = style(sel);
        int rx = IW - PAD;
        java.util.function.Consumer<Ctl> put = ct -> { ct.w.visible = false; ctrl.add(ct); add(ct.w); };
        put.accept(new Ctl(new GlassButton(0, 0, 16, 16, "×", "GHOST", () -> select(null)), rx - 16, 7));
        String[] rows = ROWS.get(sel);
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            int y = 28 + i * 22;
            switch (row) {
                case "visible" -> put.accept(new Ctl(new GlassSwitch(0, 0, 28, 14, () -> own.visible, v -> { own.visible = v; save(); selAt = now(); }), rx - 28, y));
                case "size" -> {
                    put.accept(new Ctl(new GlassButton(0, 0, 16, 16, "‹", "GHOST", () -> stepScale(-1)), rx - 62, y - 1));
                    put.accept(new Ctl(new GlassButton(0, 0, 16, 16, "›", "GHOST", () -> stepScale(1)), rx - 16, y - 1));
                }
                case "color" -> put.accept(new Ctl(new ColorSwatches(0, 0, () -> c.color, v -> { c.color = v; save(); }), rx - (COLORS.length * 12 - 2), y + 2));
                case "opacity" -> put.accept(new Ctl(new GlassSlider(0, 0, 52, 9, () -> c.background, v -> { c.background = Math.round(v * 20) / 20.0; save(); }), rx - 82, y + 2));
                case "numbers" -> {
                    numbersBtn = new GlassButton(0, 0, 62, 16, "", "GHOST", () -> { c.numbers = c.numbers.equals("short") ? "full" : "short"; save(); });
                    put.accept(new Ctl(numbersBtn, rx - 62, y - 1));
                }
                case "sign" -> put.accept(new Ctl(new GlassSwitch(0, 0, 28, 14, () -> c.autoSignColor, v -> { c.autoSignColor = v; save(); selAt = now(); }), rx - 28, y));
                case "curve" -> put.accept(new Ctl(new GlassSwitch(0, 0, 28, 14, () -> c.rows.curve, v -> { c.rows.curve = v; save(); selAt = now(); }), rx - 28, y));
                case "winrate" -> put.accept(new Ctl(new GlassSwitch(0, 0, 28, 14, () -> c.rows.winrate, v -> { c.rows.winrate = v; save(); selAt = now(); }), rx - 28, y));
                case "total" -> put.accept(new Ctl(new GlassSwitch(0, 0, 28, 14, () -> c.rows.total, v -> { c.rows.total = v; save(); selAt = now(); }), rx - 28, y));
                default -> {}
            }
        }
    }

    private void select(String id) {
        if (java.util.Objects.equals(id, sel)) return;
        sel = id; selAt = now(); side = null; ins = null; insAt = now();
        if (id != null) play("click");
        buildInspector();
    }

    private static boolean inter(double ax, double ay, double aw, double ah, double bx, double by, double bw, double bh) {
        return Math.max(0, Math.min(ax + aw, bx + bw) - Math.max(ax, bx)) * Math.max(0, Math.min(ay + ah, by + bh) - Math.max(ay, by)) > 0;
    }

    /** Where the inspector goes: beside the widget on the side with room (right, left, below, above), clear of the toolbar. */
    private Pt inspectorTarget() {
        int IH = ih(), M = 8, E = 4;
        Rect r = wrect(sel);
        TB t = toolbarRect();
        double maxY = SH - IH - E;
        double cy = clampY(r.y() + r.h() / 2.0 - IH / 2.0, maxY, E), cx = clampX(r.x() + r.w() / 2.0 - IW / 2.0, E);
        Map<String, Pt> place = Map.of("right", new Pt(r.x() + r.w() + M, cy), "left", new Pt(r.x() - M - IW, cy),
                "below", new Pt(cx, r.y() + r.h() + M), "above", new Pt(cx, r.y() - M - IH));
        java.util.function.Predicate<Pt> fits = pz -> pz.x() >= E && pz.y() >= E && pz.x() + IW <= SW - E && pz.y() + IH <= SH - E
                && !inter(pz.x(), pz.y(), IW, IH, t.x(), t.y(), t.w(), t.h());
        // other widgets (the toast counts with its card) should stay visible too
        List<Rect> others = new ArrayList<>();
        for (String o : ORDER) {
            if (o.equals(sel) || !HUD.get(o).visible) continue;
            if (sel.equals("session") && o.equals("last")) continue;
            if (sel.equals("last") && o.equals("session")) continue;
            others.add(wrect(o));
        }
        java.util.function.Predicate<Pt> clear = pz -> {
            if (!fits.test(pz)) return false;
            double px = clampX(pz.x(), E), py = clampY(pz.y(), maxY, E);
            for (Rect o : others) if (inter(px, py, IW, IH, o.x(), o.y(), o.w(), o.h())) return false;
            return true;
        };
        if (side == null || !fits.test(place.get(side))) {   // sticky: only changes side when the current one no longer fits
            String[] sides = {"right", "left", "below", "above"};
            String pick = null;
            for (String k : sides) if (clear.test(place.get(k))) { pick = k; break; }
            if (pick == null) for (String k : sides) if (fits.test(place.get(k))) { pick = k; break; }
            side = pick != null ? pick : "right";
        }
        Pt pz = place.get(side);
        return new Pt(clampX(pz.x(), E), clampY(pz.y(), maxY, E));
    }

    private double clampY(double v, double maxY, double E) { return Math.max(E, Math.min(maxY, v)); }
    private double clampX(double v, double E) { return Math.max(E, Math.min(SW - IW - E, v)); }

    private void stepScale(int d) {
        Style c = style(sel);
        int i = -1;
        for (int j = 0; j < SCALES.length; j++) if (SCALES[j] == c.scale) i = j;
        c.scale = SCALES[Math.max(0, Math.min(SCALES.length - 1, (i == -1 ? 2 : i) + d))]; save();
        selAt = now();   // a little pop to show the new size
    }

    private void reset() {
        if (now() < resetArmed) { HudConfig.reset(); resetArmed = 0; resetBtn.label = "Reset"; select(null); }
        else { resetArmed = now() + 3000; resetBtn.label = "Sure?"; }
    }

    @Override
    public void tick() { if (resetArmed != 0 && now() > resetArmed) { resetArmed = 0; if (resetBtn != null) resetBtn.label = "Reset"; } }

    @Override
    protected void renderBg(G g, double mx, double my) { g.fill(0, 0, SW, SH, 0x300A0710); }

    /** Spring pop after selection: +12 % then settles (critically damped sine). */
    private double popScale() {
        double t = (now() - selAt) / 1000.0;
        return t < 0.6 ? 1 + 0.12 * Math.exp(-9 * t) * Math.sin(18 * t) : 1;
    }

    @Override
    protected void renderFg(G g, double mx, double my) {
        long t = now(), dt = Math.min(64, t - last); last = t;
        lift += ((dragging != null && moved ? 1 : 0) - lift) * (1 - Math.exp(-dt / 70.0));
        List<String> order = new ArrayList<>();
        for (String id : ORDER) if (!id.equals(sel)) order.add(id);
        if (sel != null) order.add(sel);
        if ("session".equals(sel) || "last".equals(sel)) { order.remove("last"); order.add(order.indexOf("session"), "last"); }   // toast stays under the card
        for (String id : order) {
            Rect r = wrect(id);
            boolean selected = id.equals(sel);
            if (id.equals("last") && !HUD.session.visible && !HUD.last.visible) continue;
            double s = selected ? popScale() * (1 + 0.04 * lift) : 1;
            double cx = r.x() + r.w() / 2.0, cy = r.y() + r.h() / 2.0, rr = 8 * effScale(id) + 2;
            g.push(); g.translate(cx, cy); g.scale(s, s); g.translate(-cx, -cy);
            GambleHud.drawWidget(g, id, r.x(), r.y());
            if (!HUD.get(id).visible) {
                smooth(g, r.x(), r.y(), r.w(), r.h(), 8 * effScale(id), 0xB30C0A14);
                String hs = "hidden";
                g.drawText(hs, r.x() + ((r.w() - F.width(hs)) >> 1), r.y() + ((r.h() - 8) >> 1), DIM, false);
            }
            boolean hov = dragging == null && mx >= r.x() && my >= r.y() && mx < r.x() + r.w() && my < r.y() + r.h();
            if (selected) ring(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, rr, lerp(LAV, PINK, pulse(t * 1.5)), 1.5);
            else ring(g, r.x() - 2, r.y() - 2, r.w() + 4, r.h() + 4, rr, hov ? 0x80FFFFFF : 0x33FFFFFF, 1);
            g.pop();
            if (selected) {   // ripple leaving the edges, 520 ms
                double e = (t - selAt) / 520.0;
                if (e < 1) {
                    double grow = 2 + 12 * easeOut(e);
                    int col = lerp(LAV, PINK, e);
                    ring(g, r.x() - grow, r.y() - grow, r.w() + 2 * grow, r.h() + 2 * grow, rr + grow, alpha(col, 0.9 * (1 - e)), 1.3);
                }
            }
        }
        for (Guide gd : guides) {
            if (gd.axis() == 'x') g.fill(gd.v(), 0, gd.v() + 1, SH, 0x99A18CD1); else g.fill(0, gd.v(), SW, gd.v() + 1, 0x99A18CD1);
        }
        renderInspector(g, mx, my, dt);
        renderToolbar(g, mx, my, dt);
    }

    private void renderInspector(G g, double mx, double my, long dt) {
        if (sel == null || (dragging != null && moved)) { for (Ctl c : ctrl) c.w.visible = false; return; }
        int IH = ih();
        Pt tg = inspectorTarget();
        if (ins == null) ins = tg;
        double f = 1 - Math.exp(-dt / 90.0);
        ins = new Pt(ins.x() + (tg.x() - ins.x()) * f, ins.y() + (tg.y() - ins.y()) * f);
        int px = (int) Math.round(ins.x()), py = (int) Math.round(ins.y());
        for (Ctl c : ctrl) { c.w.x = px + c.dx; c.w.y = py + c.dy; c.w.visible = true; }
        // pops out of the widget's side: scale 0.85 -> 1 from the edge facing the widget
        double e = easeOut(Math.min(1, (now() - insAt) / 220.0)), sc = 0.85 + 0.15 * e;
        Rect r = wrect(sel);
        double ox = "right".equals(side) ? px : "left".equals(side) ? px + IW : Math.max(px, Math.min(px + IW, r.x() + r.w() / 2.0));
        double oy = "below".equals(side) ? py : "above".equals(side) ? py + IH : Math.max(py, Math.min(py + IH, r.y() + r.h() / 2.0));
        g.push(); g.translate(ox, oy); g.scale(sc, sc); g.translate(-ox, -oy);
        glass(g, px, py, IW, IH, 12, 0.84, 1);
        Style c = style(sel);
        if (numbersBtn != null) numbersBtn.label = c.numbers.equals("short") ? "1.5M" : "1 500 000";
        for (Ctl ct : ctrl) ct.w.render(g, mx, my);
        int lx = px + PAD, rx = px + IW - PAD;
        String[] rows = ROWS.get(sel);
        gradientText(g, DEF.get(sel).name().toUpperCase(Locale.ROOT), lx, py + 11, true);
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            int y = py + 28 + i * 22;
            if (i > 0 && row.equals("curve")) g.fill(lx, y - 4, rx, y - 3, 0x14FFFFFF);   // card rows below a hairline
            g.drawText(LABEL.get(row), lx, y + 3, TEXT, false);
            if (row.equals("size")) {
                String s = String.format(Locale.ROOT, "%.2f", c.scale);
                if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
                s += "x";
                boolean capped = effScale(sel) < c.scale - 0.01;
                g.drawText(s, rx - 31 - (F.width(s) >> 1), y + 3, capped ? MUTE : WHITE, false);   // grey = shrunk to fit this screen
            }
            if (row.equals("opacity")) { String op = Math.round(c.background * 100) + "%"; g.drawText(op, rx - F.width(op), y + 3, WHITE, false); }
        }
        if (sel.equals("last")) g.drawText("Same style as the card", lx, py + 50, DIM, false);
        g.pop();
    }

    private void renderToolbar(G g, double mx, double my, long dt) {
        TB t = toolbarRect();
        boolean under = false;
        if (dragging != null && moved) {
            Rect r = wrect(dragging.id);
            under = r.x() < t.x() + t.w() && r.x() + r.w() > t.x() && r.y() < t.y() + t.h() + 8 && r.y() + r.h() > t.y() - 8;
        }
        tbOff += ((under ? 60 : 0) - tbOff) * (1 - Math.exp(-dt / 80.0));   // slides down out of the way
        int off = (int) Math.round(tbOff);
        for (Ctl c : toolbar) c.w.y = t.y() + c.dy + off;
        glass(g, t.x(), t.y() + off, t.w(), t.h(), 12, 0.84, 1);
        for (Ctl c : toolbar) c.w.render(g, mx, my);
    }

    private String hitWidget(double mx, double my) {
        List<String> order = new ArrayList<>();
        for (String id : ORDER) if (!id.equals(sel)) order.add(id);
        if (sel != null) order.add(sel);
        for (int i = order.size() - 1; i >= 0; i--) {
            Rect r = wrect(order.get(i));
            if (mx >= r.x() && my >= r.y() && mx < r.x() + r.w() && my < r.y() + r.h()) return order.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.visible && c.active && c.over(mx, my)) { focusedDrag = c; c.down = true; c.click(mx, my); return true; }
        }
        if (sel != null && ins != null && dragging == null) {   // click on the inspector's empty glass: swallow
            if (mx >= ins.x() && my >= ins.y() && mx < ins.x() + IW && my < ins.y() + ih()) return true;
        }
        String id = hitWidget(mx, my);
        select(id);
        if ("pay".equals(id) && Payments.cur == null && Payments.queue.isEmpty()) Payments.queue.add(new Payments.Paid("Steve", 2_500_000, 0));   // preview, not counted
        String dragId = "last".equals(id) ? "session" : id;   // grabbing the toast moves its card
        if (id != null) {
            Rect r = wrect(dragId);
            dragging = new Drag(); dragging.id = dragId; dragging.dx = mx - r.x(); dragging.dy = my - r.y(); dragging.sx = mx; dragging.sy = my;
            moved = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double ddx, double ddy) {
        super.mouseDragged(click, ddx, ddy);
        if (dragging == null) return true;
        double mx = click.x(), my = click.y();
        if (!moved && Math.abs(mx - dragging.sx) + Math.abs(my - dragging.sy) < 3) return true;   // a click is not a drag
        moved = true;
        String id = dragging.id;
        Rect r = wrect(id);
        Style c = HUD.get(id);
        List<double[]> ox = new ArrayList<>(), oy = new ArrayList<>();
        for (String o : ORDER) {
            if (o.equals(id) || o.equals("last") || !HUD.get(o).visible) continue;
            Rect q = wrect(o);
            ox.add(new double[]{q.x(), q.x() + q.w()}); oy.add(new double[]{q.y(), q.y() + q.h()});
        }
        Snap snx = snap(mx - dragging.dx, r.w(), SW, ox, 'x'), sny = snap(my - dragging.dy, r.h(), SH, oy, 'y');
        guides.clear();
        if (snx.guide() != null) guides.add(snx.guide());
        if (sny.guide() != null) guides.add(sny.guide());
        c.x = SW - r.w() > 0 ? snx.v() / (SW - r.w()) : 0;
        c.y = SH - r.h() > 0 ? sny.v() / (SH - r.h()) : 0;
        return true;
    }

    /** Snap within 4 px to: 4 px screen margin, screen centre, edges of the other widgets. */
    private Snap snap(double v, double size, double total, List<double[]> spans, char axis) {
        v = Math.max(0, Math.min(total - size, v));
        double M = 4;
        List<double[]> cand = new ArrayList<>();
        cand.add(new double[]{M, M});
        cand.add(new double[]{total - size - M, total - M});
        cand.add(new double[]{Math.round((total - size) / 2), (int) total >> 1});
        for (double[] s : spans) {
            double a = s[0], b = s[1];
            cand.add(new double[]{a, a}); cand.add(new double[]{b - size, b}); cand.add(new double[]{b + 2, b + 2}); cand.add(new double[]{a - size - 2, a - 2});
        }
        double bestD = 0, bestPos = 0, bestG = 0;
        boolean has = false;
        for (double[] p : cand) {
            double d = Math.abs(p[0] - v);
            if (d <= 4 && (!has || d < bestD)) { has = true; bestD = d; bestPos = p[0]; bestG = p[1]; }
        }
        return has ? new Snap(bestPos, new Guide(axis, bestG)) : new Snap(v, null);
    }

    @Override
    public boolean mouseReleased(Click click) {
        super.mouseReleased(click);
        if (dragging != null && moved) { save(); side = null; ins = null; insAt = now(); }   // inspector pops back beside the new spot
        dragging = null; moved = false; guides.clear();
        return true;
    }

    @Override
    public void key(Key e) {
        if (e.is("Escape")) { if (sel != null) select(null); else close(); return; }
        if (sel == null) return;
        Style c = HUD.get(sel);
        Rect r = wrect(sel);
        int st = e.shift() ? 10 : 1;
        int[] mv = switch (e.key()) {
            case "ArrowLeft" -> new int[]{-st, 0};
            case "ArrowRight" -> new int[]{st, 0};
            case "ArrowUp" -> new int[]{0, -st};
            case "ArrowDown" -> new int[]{0, st};
            default -> null;
        };
        if (mv == null) return;
        double nx = Math.max(0, Math.min(SW - r.w(), r.x() + mv[0])), ny = Math.max(0, Math.min(SH - r.h(), r.y() + mv[1]));
        c.x = SW - r.w() > 0 ? nx / (SW - r.w()) : 0; c.y = SH - r.h() > 0 ? ny / (SH - r.h()) : 0; save();
    }

    /** Back to the menu's HUD tab. */
    @Override
    public void close() {
        save();
        GambleScreen.tab = "hud";
        MinecraftClient.getInstance().setScreen(new GambleScreen());
    }
}
