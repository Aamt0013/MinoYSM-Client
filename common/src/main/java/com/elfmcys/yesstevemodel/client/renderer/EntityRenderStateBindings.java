package com.elfmcys.yesstevemodel.client.renderer;

import com.google.common.collect.MapMaker;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class EntityRenderStateBindings {

    private static final Map<EntityRenderState, Entity> BINDINGS =
            new MapMaker().weakKeys().<EntityRenderState, Entity>makeMap();

    private EntityRenderStateBindings() {
    }

    public static void bind(EntityRenderState state, Entity entity) {
        if (state != null && entity != null) {
            BINDINGS.put(state, entity);
        }
    }

    @Nullable
    public static Entity get(@Nullable EntityRenderState state) {
        return state == null ? null : BINDINGS.get(state);
    }
}
