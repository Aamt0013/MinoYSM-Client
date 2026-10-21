package com.elfmcys.yesstevemodel.access;

import com.elfmcys.yesstevemodel.mixin.client.EntityRenderDispatcherMixin;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface IEntityRenderDispatcher {

    @Nullable
    Entity ysm$getEntityForState(EntityRenderState state);

}
