package rip.ysm.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudOverlay {
    void extractRenderState(GuiGraphicsExtractor guiGraphics, Font font, float partialTick, int screenWidth, int screenHeight);
}
