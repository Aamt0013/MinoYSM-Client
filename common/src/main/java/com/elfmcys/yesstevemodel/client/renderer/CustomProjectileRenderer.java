package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ProjectileCapability;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.projectile.Projectile;

public class CustomProjectileRenderer {

    /** 诊断计数：只打印前若干次，避免刷屏。 */
    private static final java.util.concurrent.atomic.AtomicLong DBG =
            new java.util.concurrent.atomic.AtomicLong();

    public static boolean renderProjectile(Projectile projectile, EntityRenderState state, float partialTick, PoseStack poseStack, SubmitNodeCollector multiBufferSource, int packedLight) {
        return ProjectileCapability.get(projectile).map(cap -> {
            boolean ready = cap.isModelInitialized() && cap.isModelReady();
            SubmitNodeCollector collector = multiBufferSource != null
                    ? multiBufferSource : RenderContext.collector();
            long n = DBG.incrementAndGet();
            if (n <= 12) {
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.debug(
                        "[YSM] projectile {}: capReady={} arg={} ctx={} model={}",
                        projectile.getType(), ready,
                        multiBufferSource != null, RenderContext.collector() != null,
                        cap.getModelId());
            }
            if (ready) {
                // 26.x 的模型绘制必须经由 SubmitNodeCollector 提交自定义几何。
                // 拿不到 collector 时**绝不能**抑制原版渲染 —— 否则自定义和原版都不画，
                // 表现为雪球/箭矢/三叉戟投出去就"消失"。
                // 返回 true 让原版渲染器接手，至少能看到原版投射物。
                if (collector == null) {
                    return true;
                }
                RendererManager.getProjectileRenderer().render(cap.getEntity(), state, partialTick, poseStack, collector, packedLight);
                return false;
            }
            return true;
        }).orElse(true);
    }
}
