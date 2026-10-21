package com.elfmcys.yesstevemodel.client.event;

import net.minecraft.world.entity.LivingEntity;
import rip.ysm.api.entity.EntityDataBridge;

public class ShieldBlockCooldownEvent {

    public static final String TAG_KEY = "ysm$shield_block_cooldown";

    private ShieldBlockCooldownEvent() {
    }

    public static void onShieldBlock(LivingEntity entity) {
        EntityDataBridge.getPersistentData(entity).putInt(TAG_KEY, 5);
    }

    public static void onLivingTick(LivingEntity entity) {
        net.minecraft.nbt.CompoundTag tag = EntityDataBridge.getPersistentData(entity);
        java.util.Optional<Integer> cooldown = tag.getInt(TAG_KEY);
        if (cooldown.isPresent()) {
            int i = cooldown.get();
            if (i > 0) {
                tag.putInt(TAG_KEY, i - 1);
            } else {
                tag.remove(TAG_KEY);
            }
        }
    }

    public static boolean isOnCooldown(LivingEntity livingEntity) {
        return EntityDataBridge.getPersistentData(livingEntity).getInt(TAG_KEY).isPresent();
    }
}
