package rip.ysm.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.mixin.client.GlBufferAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class GpuMesh {
    public final long pointer;
    public final int vao;
    public final int vbo;
    public final GpuBuffer ibo;
    public final int boneSsbo;
    public final int vertexCount;
    public final int indexCount;
    public final int boneCount;
    public final int partMask1Start, partMask1Count;
    public final int partMask2Start, partMask2Count;
    public final int partMask3Start, partMask3Count;
    public final ByteBuffer perFrameBoneBuffer;

    private GpuBuffer xformVbo;
    private boolean disposed = false;

    GpuMesh(long pointer, int vao, int vbo, GpuBuffer ibo, int boneSsbo, int vertexCount, int indexCount, int boneCount, int pm1s, int pm1c, int pm2s, int pm2c, int pm3s, int pm3c) {
        this.pointer = pointer;
        this.vao = vao;
        this.vbo = vbo;
        this.ibo = ibo;
        this.boneSsbo = boneSsbo;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.boneCount = boneCount;
        this.partMask1Start = pm1s;
        this.partMask1Count = pm1c;
        this.partMask2Start = pm2s;
        this.partMask2Count = pm2c;
        this.partMask3Start = pm3s;
        this.partMask3Count = pm3c;
        this.perFrameBoneBuffer = MemoryUtil.memAlloc(boneCount * 144);
    }

    public int indexOffsetBytes(int renderPartMask) {
        if (renderPartMask == 0 || renderPartMask == 3) return 0;
        if (renderPartMask == 1) return partMask1Start * Integer.BYTES;
        if (renderPartMask == 2) return partMask2Start * Integer.BYTES;
        return 0;
    }

    public int indexFirstIndex(int renderPartMask) {
        if (renderPartMask == 0 || renderPartMask == 3) return 0;
        if (renderPartMask == 1) return partMask1Start;
        if (renderPartMask == 2) return partMask2Start;
        return 0;
    }

    public int indexDrawCount(int renderPartMask) {
        if (renderPartMask == 0) return indexCount;
        if (renderPartMask == 3) return indexCount;
        int self = (renderPartMask == 1) ? partMask1Count : (renderPartMask == 2) ? partMask2Count : 0;
        return self + partMask3Count;
    }

    public int iboHandle() {
        return ((GlBufferAccessor) (Object) ibo).ysm$getHandle();
    }

    public GpuBuffer xformVbo() {
        return xformVbo;
    }

    public int xformVboHandle() {
        return xformVbo == null ? 0 : ((GlBufferAccessor) (Object) xformVbo).ysm$getHandle();
    }

    public void ensureXformBuffers() {
        if (xformVbo != null) return;
        xformVbo = RenderSystem.getDevice().createBuffer(() -> "ysm-xform-vbo", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) vertexCount * 36);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        GlStateManager._glDeleteBuffers(vbo);
        ibo.close();
        GlStateManager._glDeleteBuffers(boneSsbo);
        GL45.glDeleteVertexArrays(vao);
        if (xformVbo != null) xformVbo.close();
        if (pointer != 0) {
            GeoModel.nFreeGpuMesh(pointer);
        }
        MemoryUtil.memFree(perFrameBoneBuffer);
    }
}
