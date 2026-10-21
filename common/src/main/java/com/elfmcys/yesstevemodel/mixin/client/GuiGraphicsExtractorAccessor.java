package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.x：{@code GuiGraphicsExtractor.guiRenderState} 变为 private，
 * 而自定义 GUI 元素（如扇形切片）仍需直接向其提交渲染状态，故用 accessor 暴露。
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("guiRenderState")
    GuiRenderState ysm$guiRenderState();
}
