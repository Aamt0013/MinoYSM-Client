package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.access.IEntityRenderDispatcher;
import com.elfmcys.yesstevemodel.client.renderer.CustomFishingHookRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomVehicleRenderer;
import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.client.renderer.VehicleRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements IEntityRenderDispatcher {


    @Override
    @Unique
    public Entity ysm$getEntityForState(EntityRenderState state) {
        return EntityRenderStateBindings.get(state);
    }

    @WrapWithCondition(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
            )
    )
    private boolean ysm$wrapEntityRendererSubmit(
            EntityRenderer<?, ?> renderer,
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        Entity entity = EntityRenderStateBindings.get(state);
        if (entity == null) {
            return true;
        }

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        SubmitNodeCollector bufferSource = RenderContext.collector();
        int packedLight = state.lightCoords;

        RenderContext.enter(collector, cameraState);
        try {
            if (entity instanceof Projectile projectile) {
                if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                    boolean callOriginal;
                    if (projectile instanceof FishingHook fishingHook) {
                        callOriginal = CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, state, partialTick, poseStack, bufferSource, packedLight);
                    } else {
                        callOriginal = CustomProjectileRenderer.renderProjectile(projectile, state, partialTick, poseStack, bufferSource, packedLight);
                    }
                    return callOriginal;
                }
                return true;
            }
            // 只有真正配置了载具模型的实体才走载具替换。
            // 26.x 下本方法对所有实体渲染都会触发，若不判能力，
            // 原版界面里的玩家预览（InventoryScreen 等）会被当成载具处理，
            // 导致原版玩家模型被取消、只剩手持物品（视觉上像一根木棍）。
            if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()
                    && !(entity instanceof net.minecraft.world.entity.player.Player)
                    && com.elfmcys.yesstevemodel.capability.VehicleCapability.get(entity).isPresent()) {
                VehicleRenderer vehicleRenderer = RendererManager.getVehicleRenderer();
                vehicleRenderer.extractRenderState(entity, state, partialTick);
                ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
                return CustomVehicleRenderer.renderVehicle(entity, state, entity.getRotationVector().x, partialTick, poseStack, bufferSource, packedLight);
            }
            return true;
        } finally {
            RenderContext.exit();
        }
    }
}
