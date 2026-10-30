package dev.ryaan.ezgamble.proto;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The prototype's canvas drawing (ctx.ellipse, gradients, strokes: chips, coins, the wheel...), done with Java2D into
 * a texture at REAL pixel resolution and cached by key. The painter draws in GUI units: (0, 0) is the sprite's top
 * left, the Graphics2D is already scaled by the GUI scale and anti-aliased. Put everything that changes the picture
 * in the key (quantise animations: an angle in 64 steps, not every frame). At most 256 sprites are kept.
 */
public final class Vec {
    private Vec() {}

    private static final Map<String, Identifier> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int seq;

    /** Draws sprite `key` (w x h GUI px, painted once by painter) at (x, y). pad = extra margin around for shadows. */
    public static void draw(G g, String key, double x, double y, double w, double h, double pad, Consumer<Graphics2D> painter) {
        float k = (float) (MinecraftClient.getInstance().getWindow().getScaleFactor() * P.zoom);
        int iw = (int) Math.ceil((w + 2 * pad) * k), ih = (int) Math.ceil((h + 2 * pad) * k);
        if (iw <= 0 || ih <= 0) return;
        String full = key + "@" + k + "/" + iw + "x" + ih;
        Identifier id = CACHE.get(full);
        if (id == null) {
            BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.scale(k, k);
            g2.translate(pad, pad);
            try { painter.accept(g2); } finally { g2.dispose(); }
            NativeImage img = new NativeImage(iw, ih, true);
            for (int j = 0; j < ih; j++) for (int i = 0; i < iw; i++) img.setColorArgb(i, j, bi.getRGB(i, j));
            id = Identifier.of("ezgamble", "dyn/vec_" + (seq++));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "ezgamble vec", img));
            CACHE.put(full, id);
            if (CACHE.size() > 256) {
                var it = CACHE.entrySet().iterator();
                Identifier old = it.next().getValue();
                it.remove();
                MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
            }
        }
        g.push();
        g.translate(x - pad, y - pad);
        g.scale(1 / k, 1 / k);
        g.d.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, iw, ih, iw, ih, iw, ih);
        g.pop();
    }

    /** java.awt.Color from 0xAARRGGBB. */
    public static java.awt.Color c(int argb) { return new java.awt.Color(argb, true); }
}
