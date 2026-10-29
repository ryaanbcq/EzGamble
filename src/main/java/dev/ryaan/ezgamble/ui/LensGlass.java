package dev.ryaan.ezgamble.ui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.mixin.DrawContextAccessor;
import dev.ryaan.ezgamble.proto.G;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.OptionalDouble;

/**
 * Liquid Glass lens drawn by a real shader (shaders/core/lens.fsh, the prototype's LensGL): one quad per lens, which
 * samples a copy of what the screen shows just before it (GuiRendererMixin takes the copy). If the shader does not
 * compile, {@link #ok()} turns false and the segmented controls fall back to their sliding pill.
 */
public final class LensGlass {
    private LensGlass() {}

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("ezgamble", "pipeline/lens"))
            .withVertexShader(Identifier.of("ezgamble", "core/lens"))
            .withFragmentShader(Identifier.of("ezgamble", "core/lens"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build();

    private static Boolean valid;   // null = not compiled yet
    private static GpuTexture copy;
    private static GpuTextureView copyView;
    private static GpuSampler sampler;

    /** False once the shader failed: callers draw the old pill. */
    public static boolean ok() {
        if (valid == null) {
            try { valid = RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid(); }
            catch (Throwable e) { valid = false; EzGambleMod.LOG.error("[ezgamble] lens shader failed", e); }
            if (!valid) EzGambleMod.LOG.warn("[ezgamble] lens shader unavailable, the tabs use the sliding pill");
        }
        return valid;
    }

    /** The copy texture follows the framebuffer size (made here, before the draws are prepared, never mid-render). */
    private static boolean ensureCopy() {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int w = fb.textureWidth, h = fb.textureHeight;
        if (copy != null && copy.getWidth(0) == w && copy.getHeight(0) == h) return true;
        GpuDevice dev = RenderSystem.getDevice();
        if (copyView != null) copyView.close();
        if (copy != null) copy.close();
        copy = dev.createTexture(() -> "ezgamble lens backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        copyView = dev.createTextureView(copy);
        if (sampler == null) sampler = dev.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        return true;
    }

    /** Called by GuiRendererMixin right before a lens draw: copy what is on screen. */
    public static void capture(Framebuffer fb) {
        if (copy == null || fb.getColorAttachment() == null) return;
        int w = Math.min(copy.getWidth(0), fb.textureWidth), h = Math.min(copy.getHeight(0), fb.textureHeight);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(fb.getColorAttachment(), copy, 0, 0, 0, 0, 0, w, h);
    }

    /**
     * Queues the lens centred on (cx, cy), w x h in GUI px, at the current transform; pressed 0..1, centre magnification,
     * lean -1..1 (the jelly front swelling toward the motion).
     * @return false when the shader is not available (draw the fallback instead)
     */
    public static boolean draw(G g, double cx, double cy, double w, double h, double pressed, double mag, double lean) {
        if (!ok() || !ensureCopy()) return false;
        Matrix3x2f pose = new Matrix3x2f(g.d.getMatrices());
        double k = MinecraftClient.getInstance().getWindow().getScaleFactor() * Math.hypot(pose.m00(), pose.m01());
        double pad = h * 0.8;   // room for the shadow
        float x1 = (float) (cx - w / 2 - pad), y1 = (float) (cy - h / 2 - pad), x2 = (float) (cx + w / 2 + pad), y2 = (float) (cy + h / 2 + pad);
        ScreenRect bounds = new ScreenRect((int) Math.floor(x1), (int) Math.floor(y1), (int) Math.ceil(x2 - x1) + 1, (int) Math.ceil(y2 - y1) + 1).transformEachVertex(pose);
        ((DrawContextAccessor) g.d).ezgamble$state().addSimpleElement(new Element(pose, x1, y1, x2, y2, (float) cx, (float) cy, (float) k,
                (int) Math.round(w * k), (int) Math.round(h * k), (float) pressed, (float) mag, (float) lean, TextureSetup.of(copyView, sampler), bounds));
        return true;
    }

    private record Element(Matrix3x2f pose, float x1, float y1, float x2, float y2, float cx, float cy, float k, int wPx, int hPx,
                           float pressed, float mag, float lean, TextureSetup textureSetup, ScreenRect bounds) implements SimpleGuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PIPELINE; }
        @Override public ScreenRect scissorArea() { return null; }

        @Override
        public void setupVertices(VertexConsumer vc) {
            corner(vc, x1, y1); corner(vc, x1, y2); corner(vc, x2, y2); corner(vc, x2, y1);
        }

        private void corner(VertexConsumer vc, float x, float y) {
            vc.vertex(pose, x, y)
                    .color(Math.round(pressed * 255), Math.round(Math.max(-1, Math.min(1, lean)) * 127 + 128), 0, 255)
                    .texture((x - cx) * k, (y - cy) * k)
                    .overlay(wPx, hPx)
                    .light(Math.round(k * 256), Math.round(mag * 1000))
                    .normal(0, 0, 1);
        }
    }
}
