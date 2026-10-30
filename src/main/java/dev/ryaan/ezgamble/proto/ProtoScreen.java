package dev.ryaan.ezgamble.proto;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The prototype's Screen over a vanilla Screen: its own list of Widgets (add, rebuild), mouse down / drag / up,
 * keys as Key events, wheel to the list under the pointer. Subclasses override init(), tick(), renderBg(), renderFg(),
 * key(), mouseDown(), mouseScrolled2(). The `G` of the frame is closed in finally, whatever happens in between.
 */
public abstract class ProtoScreen extends Screen {
    public final List<Widget> children = new ArrayList<>();
    public Widget focusedDrag;
    public double SW, SH;
    /** View transform, screen = virtual * viewZ + view(X|Y): the window can be moved and zoomed (GambleScreen). */
    public double viewX, viewY, viewZ = 1;
    public double vx(double sx) { return (sx - viewX) / viewZ; }
    public double vy(double sy) { return (sy - viewY) / viewZ; }
    /** Forced pointer for smoke tests. */
    public static int forceMx = -1, forceMy = -1;
    private int errors;

    protected ProtoScreen(String title) { super(Text.literal(title)); }

    public <T extends Widget> T add(T w) { children.add(w); return w; }
    public void rebuild() { children.clear(); init2(); }

    /** The prototype's init(): build widgets for the current state. */
    protected abstract void init2();

    @Override
    protected final void init() { relayout(); }

    /** The virtual screen follows the zoom: widgets are laid out again in it. */
    public void relayout() { SW = width / viewZ; SH = height / viewZ; children.clear(); init2(); }

    private void view(G g) { P.zoom = viewZ; g.push(); g.translate(viewX, viewY); g.scale(viewZ, viewZ); }

    /** Background layer (before render), and the content. */
    protected void renderBg(G g, double mx, double my) {}
    protected void renderFg(G g, double mx, double my) { renderChildren(g, mx, my); }

    public void renderChildren(G g, double mx, double my) {
        for (Widget c : new ArrayList<>(children)) {
            if (!c.visible) continue;
            if (c.clip != null) { g.enableScissor(c.clip.x(), c.clip.y(), c.clip.x() + c.clip.w(), c.clip.y() + c.clip.h()); try { c.render(g, mx, my); } finally { g.disableScissor(); } }
            else c.render(g, mx, my);
        }
    }

    private double mx(int m) { return vx(forceMx >= 0 ? forceMx : m); }
    private double my(int m) { return vy(forceMy >= 0 ? forceMy : m); }

    @Override
    public void renderBackground(DrawContext d, int mouseX, int mouseY, float delta) {
        G g = new G(d);
        try { view(g); renderBg(g, mx(mouseX), my(mouseY)); }
        catch (Exception e) { fail(e); }
        finally { g.closeAll(); P.zoom = 1; }
    }

    @Override
    public void render(DrawContext d, int mouseX, int mouseY, float delta) {
        G g = new G(d);
        long t0 = System.nanoTime();
        try { view(g); renderFg(g, mx(mouseX), my(mouseY)); }
        catch (Exception e) { fail(e); }
        finally {
            g.closeAll(); P.zoom = 1;
            // GambleScreen counts itself (its frame ends inside renderFg); the other screens are counted here
            if (dev.ryaan.ezgamble.DebugRun.ON && !(this instanceof dev.ryaan.ezgamble.ui.GambleScreen)) { dev.ryaan.ezgamble.DebugRun.renderNanos += System.nanoTime() - t0; dev.ryaan.ezgamble.DebugRun.renderFrames++; }
        }
    }

    private void fail(Exception e) {
        if (errors++ < 5) dev.ryaan.ezgamble.EzGambleMod.LOG.error("[ezgamble] screen render failed", e);
    }

    // ---- input

    public boolean mouseDown(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget c = children.get(i);
            if (c.active && c.over(mx, my)) { focusedDrag = c; c.down = true; c.click(mx, my); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) { return mouseDown(vx(click.x()), vy(click.y()), click.button()); }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) { if (focusedDrag != null) focusedDrag.drag(vx(click.x()), vy(click.y())); return true; }

    @Override
    public boolean mouseReleased(Click click) {
        if (focusedDrag != null) { focusedDrag.down = false; focusedDrag.release(vx(click.x()), vy(click.y())); }
        focusedDrag = null;
        return true;
    }

    /** Wheel: dy in GUI px down positive (MC gives notches up positive; 20 px a notch). */
    public boolean mouseScrolled2(double mx, double my, double dy, double dx) { return false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) { return mouseScrolled2(vx(mouseX), vy(mouseY), -v * 20 / viewZ, -h * 20 / viewZ); }

    /** Keys, prototype style. Default: Escape closes. */
    public void key(Key e) { if (e.is("Escape")) close(); }

    @Override
    public boolean keyPressed(KeyInput input) {
        Key k = Key.fromCode(input.key(), input.modifiers());
        // printable keys (letters, digits, space) arrive through charTyped as text, like the browser's e.key
        if (k.key().length() == 1 && !k.ctrl()) { lastLetter = k; return true; }
        if (k.key().isEmpty()) return false;
        key(k);
        return true;
    }

    private Key lastLetter;

    @Override
    public boolean charTyped(CharInput input) {
        String s = input.asString();
        key(new Key(s, true, lastLetter != null && lastLetter.shift(), false));
        lastLetter = null;
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    public final Map<String, Scroller> scrollers = new HashMap<>();
    public Scroller sc(String id) { return scrollers.computeIfAbsent(id, k -> new Scroller()); }
}
