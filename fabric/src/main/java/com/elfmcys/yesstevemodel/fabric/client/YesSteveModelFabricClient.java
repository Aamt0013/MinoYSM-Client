package com.elfmcys.yesstevemodel.fabric.client;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.renderer.AnimationDebugOverlay;
import com.elfmcys.yesstevemodel.client.renderer.ExtraPlayerOverlay;
import com.elfmcys.yesstevemodel.client.renderer.ModelSyncStateOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import rip.ysm.api.client.HudOverlay;

public final class YesSteveModelFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudOverlay debugOverlay = AnimationDebugOverlay.createOverlay();
        HudOverlay loadingOverlay = new ExtraPlayerOverlay();
        HudOverlay syncOverlay = new ModelSyncStateOverlay();
        // 26.x：HudRenderCallback 已移除，改用 HudElementRegistry 注册 HUD 元素
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("yes_steve_model", "ysm_overlays"), (guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            float partial = tickDelta.getGameTimeDeltaPartialTick(false);
            debugOverlay.extractRenderState(guiGraphics, mc.font, partial, w, h);
            loadingOverlay.extractRenderState(guiGraphics, mc.font, partial, w, h);
            syncOverlay.extractRenderState(guiGraphics, mc.font, partial, w, h);
        });

        ClientModelManager.loadDefaultModel();
        // 纯客户端模式的本地扫描不在这里启动：此时还不知道服务器装没装 YSM。
        // 单人游戏里服务端必然装了（同一个 jar），本地扫描纯属重复劳动；
        // 多人服务器要等握手超时才能确定。改由 ClientPlayerJoinNotification
        // 在进世界后按需触发，见 ClientModelManager.scheduleLocalModelScan()。
    }
}
