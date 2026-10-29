package dev.ryaan.ezgamble.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GuiRenderer.Draw is package-private: its pipeline tells the lens draws apart. */
@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$Draw")
public interface GuiDrawAccessor {
    @Accessor("pipeline")
    RenderPipeline ezgamble$pipeline();
}
