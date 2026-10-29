package dev.ryaan.ezgamble.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The GUI render state behind a DrawContext: the lens adds its own element to it. */
@Mixin(DrawContext.class)
public interface DrawContextAccessor {
    @Accessor("state")
    GuiRenderState ezgamble$state();
}
