package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 21:46
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("equipmentAssets")
    EquipmentAssetManager getEquipmentAssetManager();

}
