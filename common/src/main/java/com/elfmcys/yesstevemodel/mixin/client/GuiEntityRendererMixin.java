package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.PreviewEntityRegistry;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public class GuiEntityRendererMixin {
    @Inject(method = "renderToTexture", at = @At("HEAD"))
    private void ysm$enterPreviewMode(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
    }

    @Inject(method = "renderToTexture", at = @At("RETURN"))
    private void ysm$exitPreviewMode(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }

    @Redirect(
            method = "renderToTexture*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            )
    )
    private void ysm$redirectPreviewDispatch(
            EntityRenderDispatcher dispatcher,
            EntityRenderState state,
            CameraRenderState cameraState,
            double x, double y, double z,
            PoseStack poseStack,
            SubmitNodeCollector collector
    ) {
        PreviewEntityRegistry.Entry entry = PreviewEntityRegistry.getEntry(state);        if (entry != null && entry.animatable() != null && state instanceof AvatarRenderState playerState) {
            try {
                // 26.x：GUI 渲染上下文直接用传入的 collector，不再有 renderBuffers().bufferSource()
                RenderContext.enter(collector, cameraState);
                try {
                    if (entry.beforeEntity() != null) {
                        entry.beforeEntity().render(poseStack, collector, state.lightCoords);
                    }
                    CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
                    float framePartialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    renderer.renderEntity(entry.animatable(), playerState, 0.0f, framePartialTick, poseStack, collector, state.lightCoords);
                    if (entry.afterEntity() != null) {
                        entry.afterEntity().render(poseStack, collector, state.lightCoords);
                    }
                } finally {
                    RenderContext.exit();
                }
            } finally {
                // 只消费条目；state 仍留在 PREVIEW_STATES 中，
                // 用于拦住"同帧第二次 submit 回落原版"造成的重复绘制。
                PreviewEntityRegistry.remove(state);
            }
            return;
        }
        // 该 state 属于预览但条目已被消费（PIP 同帧多次 submit）：
        // 必须直接跳过。若回落原版，会用另一套变换把原版玩家模型再画一遍，
        // 与自定义模型叠加 → 屏幕上出现多个错位副本。
        if (PreviewEntityRegistry.isPreviewState(state)) {
            return;
        }
        dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
    }
}
