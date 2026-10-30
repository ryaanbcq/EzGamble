package dev.ryaan.ezgamble.proto;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * The prototype's `g` (its emulated DrawContext), over the real one: same method names, float coordinates allowed,
 * so ported code reads like src.html. Fills round to the GUI pixel grid like the canvas did.
 */
public final class G {
    public final DrawContext d;
    private int depth, scissors;

    public G(DrawContext d) { this.d = d; }

    private static int r(double v) { return (int) Math.round(v); }

    public void fill(double x1, double y1, double x2, double y2, int c) {
        if ((c >>> 24) == 0) return;
        d.fill(r(Math.min(x1, x2)), r(Math.min(y1, y2)), r(Math.max(x1, x2)), r(Math.max(y1, y2)), c);
    }

    public void fillGradient(double x1, double y1, double x2, double y2, int top, int bot) {
        if (y2 <= y1 || x2 <= x1) return;
        d.fillGradient(r(x1), r(y1), r(x2), r(y2), top, bot);
    }

    /** Vanilla text, shadow = rgb x 0.25 offset (1, 1). Fractional positions are drawn through a translation. */
    public void drawText(Object s, double x, double y, int color, boolean shadow) {
        if ((color >>> 24) == 0) return;
        String t = String.valueOf(s);
        if (x == Math.rint(x) && y == Math.rint(y)) { d.drawText(F.tr(), t, (int) x, (int) y, color, shadow); return; }
        push(); translate(x, y); d.drawText(F.tr(), t, 0, 0, color, shadow); pop();
    }

    /** An item by vanilla id ("emerald", "tnt_side" = the tnt item...), or a stack. glint = forced enchantment shimmer. */
    public void drawItem(Object item, double x, double y) { drawItem(item, x, y, false); }

    public void drawItem(Object item, double x, double y, boolean glint) {
        ItemStack st = item instanceof ItemStack s ? s : Items2.stack(String.valueOf(item));
        if (st.isEmpty()) return;
        if (glint) st = P.shiny(st);
        if (x == Math.rint(x) && y == Math.rint(y)) { d.drawItem(st, (int) x, (int) y); return; }
        push(); translate(x, y); d.drawItem(st, 0, 0); pop();
    }

    /** A sprite of the mod (card/face, game/blackjack, crate/gold...) or a vanilla texture path ("block/oak_log"). */
    public void drawSprite(String name, double x, double y, double w, double h) {
        Identifier id = Items2.sprite(name);
        if (id == null) return;
        int iw = r(w), ih = r(h);
        push(); translate(x, y);
        d.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, iw, ih, iw, ih, iw, ih);
        pop();
    }

    /** The same, tinted / faded: argb multiplies the texture (0xFFFFFFFF = as is). */
    public void drawSprite(String name, double x, double y, double w, double h, int argb) {
        Identifier id = Items2.sprite(name);
        if (id == null) return;
        int iw = r(w), ih = r(h);
        push(); translate(x, y);
        d.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, iw, ih, iw, ih, iw, ih, argb);
        pop();
    }

    /** The player's face (hat layer included), by name: tab list skin, else Mojang, default meanwhile. */
    public void drawHead(String name, double x, double y, double size) {
        var tex = Faces.get(name);
        if (tex == null) return;
        push(); translate(x, y);
        PlayerSkinDrawer.draw(d, tex, 0, 0, r(size));
        pop();
    }

    /** A rectangle (u, v, uw x vh texels) of the player's 64x64 skin, stretched to w x h GUI px, nearest texel (the chibi). */
    public void drawSkinPart(String name, int u, int v, int uw, int vh, double x, double y, double w, double h) {
        var tex = Faces.get(name);
        if (tex == null) return;
        push(); translate(x, y); scale(w / uw, h / vh);
        d.drawTexture(RenderPipelines.GUI_TEXTURED, tex.body().texturePath(), 0, 0, u, v, uw, vh, uw, vh, 64, 64);
        pop();
    }

    public void enableScissor(double x1, double y1, double x2, double y2) { scissors++; d.enableScissor(r(x1), r(y1), r(x2), r(y2)); }
    public void disableScissor() { if (scissors > 0) { scissors--; d.disableScissor(); } }

    public void push() { depth++; d.getMatrices().pushMatrix(); }
    public void pop() { if (depth > 0) { depth--; d.getMatrices().popMatrix(); } }
    public void translate(double x, double y) { d.getMatrices().translate((float) x, (float) y); }
    public void scale(double sx, double sy) { d.getMatrices().scale((float) sx, (float) sy); }
    public void rotate(double a) { d.getMatrices().rotate((float) a); }

    /** Whatever a failed draw left open (prototype: saveDepth reset each frame). */
    public void closeAll() {
        while (scissors > 0) disableScissor();
        while (depth > 0) pop();
    }

    public static MinecraftClient mc() { return MinecraftClient.getInstance(); }
}
