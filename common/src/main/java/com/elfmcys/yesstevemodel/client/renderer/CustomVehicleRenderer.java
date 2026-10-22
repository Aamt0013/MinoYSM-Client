package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.VehicleCapability;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import rip.ysm.api.entity.EntityDataBridge;

public class CustomVehicleRenderer {
    public static boolean renderVehicle(Entity entity, EntityRenderState state, float yaw, float partialTick, PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight) {
        return VehicleCapability.get(entity).map(cap -> {
            if (cap.isModelInitialized() && cap.isModelReady()) {
                // 与投射物同理：拿不到 collector 时不要抑制原版渲染，
                // 否则自定义和原版都不画，载具会"消失"。
                SubmitNodeCollector collector = bufferSource != null
                        ? bufferSource : RenderContext.collector();
                if (collector == null) {
                    return true;
                }
                RendererManager.getVehicleRenderer().renderEntity(cap, state, getBodyRotation(entity, yaw, partialTick), partialTick, poseStack, collector, packedLight);
                return false;
            }
            return true;
        }).orElse(true);
    }

    public static float getBodyRotation(Entity entity, float entityYaw, float partialTick) {
        float bodyRotation = entityYaw;
        if (entity instanceof LivingEntity) {
            bodyRotation = getLivingBodyRotation((LivingEntity) entity, partialTick);
        } else if (entity instanceof AbstractMinecart) {
            bodyRotation = getMinecartBodyRotation((AbstractMinecart) entity, partialTick, bodyRotation);
        }
        return bodyRotation;
    }

    private static float getLivingBodyRotation(LivingEntity entity, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);

        if (entity.isPassenger() && entity.getVehicle() != null && EntityDataBridge.shouldRiderSit(entity.getVehicle())) {
            Entity vehicle = entity.getVehicle();
            if (vehicle instanceof LivingEntity livingVehicle) {
                float vehicleBodyYaw = Mth.rotLerp(partialTick, livingVehicle.yBodyRotO, livingVehicle.yBodyRot);
                float yawDiff = Mth.clamp(Mth.wrapDegrees(headYaw - vehicleBodyYaw), -85.0f, 85.0f);
                bodyYaw = headYaw - yawDiff;

                if (yawDiff * yawDiff > 2500.0f) {
                    bodyYaw += yawDiff * 0.2f;
                }
            }
        }
        return bodyYaw;
    }

    private static float getMinecartBodyRotation(AbstractMinecart minecart, float partialTick, float defaultYaw) {
        double interpX = Mth.lerp(partialTick, minecart.xOld, minecart.getX());
        double interpY = Mth.lerp(partialTick, minecart.yOld, minecart.getY());
        double interpZ = Mth.lerp(partialTick, minecart.zOld, minecart.getZ());
        MinecartBehavior behavior = minecart.getBehavior();
        Vec3 interpPos = (behavior instanceof OldMinecartBehavior old) ? (old.getPos(interpX, interpY, interpZ)) : new net.minecraft.world.phys.Vec3(interpX, interpY, interpZ);

        float calculatedYaw = defaultYaw;

        if (interpPos != null) {
            // TODO 1.21.4 port: fix new minecart behavior
            Vec3 frontOffsetPos = (behavior instanceof OldMinecartBehavior old) ? (old.getPosOffs(interpX, interpY, interpZ, 0.30000001192092896d)) : new net.minecraft.world.phys.Vec3(interpX, interpY, interpZ);
            Vec3 backOffsetPos = (behavior instanceof OldMinecartBehavior old) ? (old.getPosOffs(interpX, interpY, interpZ, -0.30000001192092896d)) : new net.minecraft.world.phys.Vec3(interpX, interpY, interpZ);

            if (frontOffsetPos == null) {
                frontOffsetPos = interpPos;
            }
            if (backOffsetPos == null) {
                backOffsetPos = interpPos;
            }

            Vec3 directionVec = backOffsetPos.add(-frontOffsetPos.x, -frontOffsetPos.y, -frontOffsetPos.z);
            if (directionVec.length() != 0.0d) {
                calculatedYaw = (float) ((Math.atan2(directionVec.z, directionVec.x) * 180.0d) / Math.PI);
            }
        }
        return calculatedYaw;
    }
}
