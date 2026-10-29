package dev.ryaan.ezgamble.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryaan.ezgamble.ui.LensGlass;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Supplier;

/**
 * The GUI is drawn in ranges of prepared draws (vanilla already cuts them in two around the blur). Each range is cut
 * again just before a Liquid Glass lens draw: what is on screen at that point is copied, and the lens samples the copy.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow @Final private List<Object> draws;

    @Shadow
    private void render(Supplier<String> name, Framebuffer framebuffer, GpuBufferSlice fog, GpuBufferSlice dynamic, GpuBuffer indices, VertexFormat.IndexType indexType, int from, int to) { throw new AssertionError(); }

    @Redirect(method = "renderPreparedDraws", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V"))
    private void ezgamble$splitAtLens(GuiRenderer self, Supplier<String> name, Framebuffer fb, GpuBufferSlice fog, GpuBufferSlice dynamic, GpuBuffer indices, VertexFormat.IndexType type, int from, int to) {
        int start = from;
        for (int i = from; i < to && i < draws.size(); i++) {
            if (((GuiDrawAccessor) draws.get(i)).ezgamble$pipeline() != LensGlass.PIPELINE) continue;
            if (i > start) render(name, fb, fog, dynamic, indices, type, start, i);
            LensGlass.capture(fb);   // what is on screen right now: the lens draw (next range) samples it
            start = i;
        }
        if (to > start) render(name, fb, fog, dynamic, indices, type, start, to);
    }
}
