package dev.ryaan.ezgamble.proto;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Liquid Glass lens body (prototype lensPill), rendered once per size into a texture of GUI pixels and drawn with
 * nearest filtering: staircase corners that stay crisp at any GUI scale, one quad a frame instead of ~100 fills.
 */
public final class LensSprite {
    private LensSprite() {}

    private static final Map<Long, Identifier> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final int MAX = 96;

    /** Draws the lens pill W x H (GUI px) at X, Y. */
    public static void draw(G g, int x, int y, int w, int h) {
        if (w <= 2 || h <= 2) return;
        g.d.drawTexture(RenderPipelines.GUI_TEXTURED, texture(w, h), x, y, 0, 0, w, h, w, h);
    }

    private static Identifier texture(int w, int h) {
        long key = ((long) w << 32) | h;
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(w, h, true);
        int[] ins = new int[h];
        double r = h / 2.0;
        for (int j = 0; j < h; j++) {   // pillRows: each row's ends follow a circle
            double dy = j + 0.5 - r;
            ins[j] = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
        }
        int sheenRows = (int) Math.round(h * 0.38);
        for (int j = 0; j < h; j++) {
            for (int x = ins[j]; x < w - ins[j]; x++) {
                int c = 0x24FFFFFF;   // body: lighter than the row
                if (j >= 1 && j < sheenRows && x > ins[j] && x < w - ins[j] - 1)
                    c = over(c, P.alpha(0xFFFFFFFF, 0.16 * (1 - j / (h * 0.38))));   // white reflection on the top third
                img.setColorArgb(x, j, c);
            }
        }
        // 1 px iridescent rim: top and bottom rows in full, then the run joining each row to its neighbours
        for (int j = 0; j < h; j++) {
            int a = ins[j];
            if (j == 0 || j == h - 1) { for (int x = a; x < w - a; x++) img.setColorArgb(x, j, irid(x - x % 2, w)); continue; }
            int e = Math.max(a + 1, Math.min(Math.max(ins[j - 1], ins[j + 1]), w / 2));
            for (int x = a; x < e; x++) img.setColorArgb(x, j, irid(x - x % 2, w));
            for (int x = w - e; x < w - a; x++) img.setColorArgb(x, j, irid(x - x % 2, w));
        }
        id = Identifier.of("ezgamble", "dyn/lens_" + w + "_" + h);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "ezgamble lens", img));
        CACHE.put(key, id);
        if (CACHE.size() > MAX) {   // least recently used goes
            var it = CACHE.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }


    /** Blue, pink, yellow along the lens (prototype irid; steps of 2 px like its runs). */
    private static int irid(int x, int w) {
        double t = x / (double) w;
        return t < 0.5 ? P.lerp(0xFF8EC5FF, 0xFFFF9AD5, t / 0.5) : P.lerp(0xFFFF9AD5, 0xFFFFE08A, (t - 0.5) / 0.5);
    }

    /** Source-over of two non-premultiplied ARGB colours. */
    private static int over(int dst, int src) {
        double sa = (src >>> 24) / 255.0, da = (dst >>> 24) / 255.0, oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int out = (int) Math.round(oa * 255) << 24;
        for (int sh = 0; sh <= 16; sh += 8) {
            double c = (((src >> sh) & 255) * sa + ((dst >> sh) & 255) * da * (1 - sa)) / oa;
            out |= ((int) Math.round(c) & 255) << sh;
        }
        return out;
    }
}
