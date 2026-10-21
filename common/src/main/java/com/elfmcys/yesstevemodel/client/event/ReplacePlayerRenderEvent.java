package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.playeranimator.PlayerAnimatorCompat;
import rip.ysm.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {


    private ReplacePlayerRenderEvent() {
    }

    public static boolean onRenderPlayerPre(Player entity, AvatarRenderState renderState, float partialTick, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (entity.equals(localPlayer) && GeneralConfig.DISABLE_SELF_MODEL.get().booleanValue()) {
            return false;
        }
        // 灵魂出窍（Tweakeroo freecam 等）期间，本地玩家的 isSpectator() 会返回 true
        // （Tweakeroo 的 CameraEntity 继承 LocalPlayer 并硬编码 isSpectator()=true）。
        // 若这里因 spectator 而跳过，留在原地的玩家模型就会回退到原版渲染。
        //
        // 因此：对"本地玩家本人"不做 spectator 过滤 —— 自身模型是否渲染已由
        // DISABLE_SELF_MODEL 与第一人称逻辑单独控制；spectator 过滤只用于其他玩家。
        boolean isSelf = entity.equals(localPlayer);
        if ((!isSelf && GeneralConfig.DISABLE_OTHER_MODEL.get().booleanValue())
                || (!isSelf && entity.isSpectator())) {
            return false;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(entity).ifPresent(cap -> {
            if (cap.isModelActive()) {
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || GeneralConfig.DISABLE_EXTERNAL_FP_ANIM.get().booleanValue()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    cancelled[0] = true;
                    // 26.x：优先使用事件传入的 collector。原实现在此取"当前渲染上下文"的 collector，
                    // 但那时 RenderContext 尚未 enter（下一行才进），取到的是外层值（通常为 null），
                    // 会导致模型完全不绘制。
                    SubmitNodeCollector bufferSource = collector != null ? collector : RenderContext.collector();
                    RenderContext.enter(collector, cameraState);
                    try {
                        int packedLight = ModelPreviewRenderer.isPreview()
                                ? 15728880
                                : renderState.lightCoords;
                        RendererManager.getPlayerRenderer().render(entity, renderState, entity.getYRot(), ModelPreviewRenderer.isPreview() ? 1.0f : partialTick, poseStack, bufferSource, packedLight);
                        if (OculusCompat.isRenderingShadowPass()) {
                        }
                    } finally {
                        RenderContext.exit();
                    }
                }
            }
        });
        return cancelled[0];
    }
}
