package com.elfmcys.yesstevemodel.client.texture;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import rip.ysm.compat.oculus.ShadersTextureType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class OuterFileTexture extends AbstractTexture implements ITextureMap {
    private final byte[] data;
    private NativeImage loadedImage;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    public OuterFileTexture(byte[] data) {
        this.data = data;
    }

    public void load() {
        if (RenderSystem.isOnRenderThread()) {
            doLoad();
        } else {
            RenderSystem.queueFencedTask(this::doLoad);
        }
    }

    public void doLoad() {
        try {
            this.loadedImage = NativeImage.read(new ByteArrayInputStream(data));
            GpuDevice gpuDevice = RenderSystem.getDevice();
            this.texture = gpuDevice.createTexture("OutFileTexture", 5, GpuFormat.RGBA8_UNORM, loadedImage.getWidth(), loadedImage.getHeight(), 1, 1);
            this.textureView = gpuDevice.createTextureView(this.texture);
            gpuDevice.createCommandEncoder().writeToTexture(this.texture, loadedImage);
            this.sampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.NEAREST, FilterMode.NEAREST,
                false
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NativeImage getLoadedImage() {
        return loadedImage;
    }

    public void setSuffixTextures(Map<ShadersTextureType, OuterFileTexture> map) {
        this.suffixTextures = Reference2ReferenceMaps.unmodifiable(new Reference2ReferenceOpenHashMap<>(map));
    }

    public Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures() {
        return this.suffixTextures;
    }
}
