package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.entity.EntityRenderCache;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每帧驱动模型动画与第一人称状态。
 *
 * <p>原实现在 1.21.x 挂钩 LevelRenderer 内部的两个 intermediary 方法，26.2 下这两个锚点
 * （{@code Minecraft.getMainRenderTarget()}、{@code LevelRenderer.submitEntities(...)}）都已移除，
 * 且本项目使用恒等映射、不生成 refMap，intermediary 名无法再解析。
 *
 * <p>现改为挂钩 26.x 的 {@code LevelRenderer#render(...)}：
 * <ul>
 *   <li>HEAD：置第一人称模式 + {@link EntityRenderCache#tick(float)} ——
 *       这是异步动画（{@code GeoEntity#submitAsyncUpdate} → {@code AnimationProcessor}）
 *       每帧推进的唯一入口，缺失会导致模型静止不动。</li>
 *   <li>RETURN：等待异步结果并清理缓存 + 复位第一人称模式。</li>
 * </ul>
 *
 * <p>注入器只声明 {@link CallbackInfo}、不声明目标方法参数，避免与 26.x 的参数类型不匹配。
 */
@Mixin({LevelRenderer.class})
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void ysm$beforeLevelRender(CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setFirstPersonMode(true);
            EntityRenderCache.tick(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ysm$afterLevelRender(CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            EntityRenderCache.clear();
            ModelPreviewRenderer.setFirstPersonMode(false);
        }
    }
}
