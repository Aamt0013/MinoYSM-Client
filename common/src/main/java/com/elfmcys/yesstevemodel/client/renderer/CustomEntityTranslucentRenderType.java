package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class CustomEntityTranslucentRenderType {

    private CustomEntityTranslucentRenderType() {
    }

    public static RenderType get(Identifier identifier) {
        return RenderTypes.entityTranslucent(identifier);
    }
}
