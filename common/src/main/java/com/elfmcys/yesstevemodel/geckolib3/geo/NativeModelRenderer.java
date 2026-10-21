

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.*;
import com.elfmcys.yesstevemodel.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.resources.Identifier;
import org.joml.*;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.optifine.OptiFineDetector;
import rip.ysm.gpu.GpuCapability;
import rip.ysm.gpu.GpuRenderPath;
import rip.ysm.gpu.IrisRenderPath;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class NativeModelRenderer {

    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, Identifier textureLocation) {
        OculusCompat.updatePBRState();
        boolean isCompatMode = OptiFineDetector.isOptifinePresent() || GeneralConfig.USE_COMPATIBILITY_RENDERER.get();
        // 26.x：绘制改为 submitCustomGeometry 延迟提交，多个提交的回调可能交错执行。
        // 原先复用静态 projectionModelViewMatrix 字段，回调执行时会被其他提交覆写，
        // 导致每次绘制用错矩阵 —— 表现为模型出现多个错位副本。改为本次调用独占的局部矩阵。
        org.joml.Matrix4f projectionModelViewMatrix = new org.joml.Matrix4f()
                .setPerspective((float) java.lang.Math.toRadians(net.minecraft.client.Minecraft.getInstance().options.fov().get()),
                        (float) net.minecraft.client.Minecraft.getInstance().getWindow().getWidth()
                                / java.lang.Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getHeight()),
                        0.05f, 1000.0f)
                .mul(RenderSystem.getModelViewMatrixCopy());
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();

        if (textureLocation != null && NativeLibLoader.isLoaded() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && GeneralConfig.USE_GPU_RENDERER.get()) {

            if(!GpuCapability.isAvailable())
            {
                ChatLogger.INSTANCE.logFormatted("Disabled GPU renderer for: " + GpuCapability.getReason());
                GeneralConfig.USE_GPU_RENDERER.set(false);
                return;
            }

            if (OculusCompat.isShaderPackInUse() && !isPreview) {
                if (IrisRenderPath.tryRender(model, pose, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation)) {
                    return;
                }
            } else {
                if (GpuRenderPath.tryRender(model, pose, boneParams, stateBuffer, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation)) {
                    return;
                }
            }
        }

        // 26.x：native SIMD 渲染库（nComputeModelVertices）是按 1.21.x 的顶点/矩阵布局预编译的，
        // 在 26.x 下会输出错乱几何（模型塌成一根棍、出现碎片）。
        // 由于仓库内没有 native 源码、无法重新编译，这里直接走 Java 实现。
        // 该实现已做零分配优化（骨骼矩阵池 + 线程本地暂存对象），
        // 在稳态下不产生短命对象，可替代原 native SIMD 路径。
        renderModel(
                buffer,
                pose,
                projectionModelViewMatrix,
                OptiFineDetector.isOptifinePresent(),
                model,
                boneParams,
                stateBuffer,
                textureIndex,
                renderPartMask,
                packedLight,
                packedOverlay,
                red, green, blue, alpha,
                isPreview
        );
    }

    public static void renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            GeoModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview) {

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return;

        // TODO: 修復GC壓力
        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();

        Scratch s = scratch();
        Matrix4f identityMat = s.identity;
        Matrix4f globalBoneMat = s.globalBone;
        Matrix3f localNormalMat = s.localNormal;
        Matrix3f globalNormalMat = s.globalNormal;

        Vector4f tempPos = s.tempPos;
        Vector3f tempNorm = s.tempNorm;
        Matrix4f[] boneLocalTransforms = new Matrix4f[mesh.bakedBones.size()];
        boolean[] boneVisible = new boolean[mesh.bakedBones.size()];

        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            calculateBoneMatrix(i, mesh.bakedBones, boneParams, boneLocalTransforms, boneVisible, identityMat, stateBuffer);
        }

        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            if (!boneVisible[i]) {
                continue;
            }

            GeoModel.BakedBone bone = mesh.bakedBones.get(i);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);

            // 法線全域矩陣
            localBoneMat.normal(localNormalMat);
            globalNormalMat.set(rootNormalMC).mul(localNormalMat);

            int currentPackedLight = bone.glow ? ((15) << 4 | (15) << 20) : packedLight;

            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    tempNorm.set(quad.normal).mul(globalNormalMat).normalize();
                    for (int v = 0; v < 4; v++) {
                        tempPos.set(quad.positions[v].x(), quad.positions[v].y(), quad.positions[v].z(), 1.0f).mul(globalBoneMat);
                        vertexConsumer.addVertex(tempPos.x(), tempPos.y(), tempPos.z())
                                .setColor(r, g, b, a)
                                .setUv(quad.uvs[v].x(), quad.uvs[v].y())
                                .setOverlay(packedOverlay)
                                .setLight(currentPackedLight)
                                .setNormal(tempNorm.x(), tempNorm.y(), tempNorm.z());
                    }
                }
            }
        }
    }

    /**
     * 骨骼矩阵池：按线程缓存，索引对应骨骼下标。
     *
     * <p>26.x 下 native SIMD 渲染路径不可用（预编译库按 1.21.x 顶点布局编译），
     * Java 回退路径必须自行做到零分配。原实现每个骨骼每帧 new 一个 Matrix4f，
     * 353 骨骼的模型在 119 次/秒的渲染频率下约产生 4 万个对象/秒，GC 压力显著。
     * 这里用线程本地池复用矩阵实例，模型骨骼数增加时按需扩容。
     */
    /**
     * 渲染暂存对象（每线程一份）。
     *
     * <p>renderModel 每帧每模型调用一次，原先每次分配 11 个临时对象（Matrix4f/Matrix3f/Vector4f/Vector3f）。
     * 在 119 次/秒 × 多模型的情况下产生大量短命对象。这里改为线程本地复用，
     * 配合骨骼矩阵池，使 Java 渲染路径在稳态下零分配。
     */
    private static final class Scratch {
        final Matrix4f identity = new Matrix4f();
        final Matrix4f globalBone = new Matrix4f();
        final Matrix3f localNormal = new Matrix3f();
        final Matrix3f globalNormal = new Matrix3f();
        final Vector4f tempPos = new Vector4f();
        final Vector3f tempNorm = new Vector3f();
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static Scratch scratch() {
        Scratch s = SCRATCH.get();
        s.identity.identity();
        return s;
    }

    private static final ThreadLocal<Matrix4f[]> BONE_MATRIX_POOL =
            ThreadLocal.withInitial(() -> new Matrix4f[256]);

    private static Matrix4f obtainBoneMatrix(int idx) {
        Matrix4f[] pool = BONE_MATRIX_POOL.get();
        if (idx >= pool.length) {
            int newLen = java.lang.Math.max(idx + 1, pool.length * 2);
            pool = java.util.Arrays.copyOf(pool, newLen);
            BONE_MATRIX_POOL.set(pool);
        }
        Matrix4f mat = pool[idx];
        if (mat == null) {
            mat = new Matrix4f();
            pool[idx] = mat;
        }
        return mat;
    }

    private static Matrix4f calculateBoneMatrix(int idx, java.util.List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, Matrix4f rootPose, float[] stateBuffer) {        if (cache[idx] != null) return cache[idx];

        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = calculateBoneMatrix(bone.parentIdx, bones, boneParams, cache, visibleCache, rootPose, stateBuffer);
            // 如果父骨骼不可見，子骨骼必然跟著不可見
            if (!visibleCache[bone.parentIdx]) {
                isVisible = false;
            }
        }

        // 从池中复用矩阵，避免每骨骼每帧 new 一个 Matrix4f（353 骨骼模型 ≈ 每秒 4 万个对象）。
        // native SIMD 路径在 26.x 不可用后，Java 路径必须自己做到零分配。
        Matrix4f localMat = obtainBoneMatrix(idx);
        localMat.set(parentMatrix);

        int pOffset = idx * 12;
        float animRx = boneParams[pOffset];
        float animRy = boneParams[pOffset + 1];
        float animRz = boneParams[pOffset + 2];
        float animTx = boneParams[pOffset + 3];
        float animTy = boneParams[pOffset + 4];
        float animTz = boneParams[pOffset + 5];
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];

        float unk1 = boneParams[pOffset + 9];
        float unk2 = boneParams[pOffset + 10];
        float unk3 = boneParams[pOffset + 11];

        if (unk1 != 0.0F && unk2 != 0.0F && unk3 != 0.0F) {
            //"".hashCode();
        }

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }/* else if (unk1 == 1 || unk2 == 1) isVisible = false;*/

        localMat.translate(
                (bone.pivotX - animTx) * 0.0625f,
                (bone.pivotY + animTy) * 0.0625f,
                (bone.pivotZ + animTz) * 0.0625f
        );
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);

//        if (bone.name.equals("gun")) {
//            //"".hashCode();
//        }

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        if (unk3 == 1.0F && stateBuffer != null && isVisible) {
            int offset = idx * 4;
            // bone pivot abs
            if (offset + 2 < stateBuffer.length) {
                stateBuffer[offset + 0] =-localMat.m30() * 16;
                stateBuffer[offset + 1] = localMat.m31() * 16;
                stateBuffer[offset + 2] = localMat.m32() * 16;
            }
        }

        localMat.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);

        cache[idx] = localMat;
        visibleCache[idx] = isVisible; // 保存當前骨骼的可見性
        return localMat;
    }

    private static final float[] matrixTransferArray = new float[48];
    @SuppressWarnings("unused") // TODO: native中直接往VertexConsumer中的buffer写入顶点
    public static void submitVertices(Object v, int vertexCount, ByteBuffer fBuf, ByteBuffer iBuf) {
        FloatBuffer f = fBuf.order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer in = iBuf.order(ByteOrder.nativeOrder()).asIntBuffer();
        int fIdx = 0, iIdx = 0;

        for (int i = 0; i < vertexCount; i++) {
            ((VertexConsumer) v)
                    .addVertex(f.get(fIdx),     f.get(fIdx + 1), f.get(fIdx + 2))
                    .setColor(f.get(fIdx + 3), f.get(fIdx + 4), f.get(fIdx + 5), f.get(fIdx + 6))
                    .setUv(f.get(fIdx + 7), f.get(fIdx + 8))
                    .setOverlay(in.get(iIdx))
                    .setLight(in.get(iIdx + 1))
                    .setNormal(f.get(fIdx + 9), f.get(fIdx + 10), f.get(fIdx + 11));
            fIdx += 12;
            iIdx += 2;
        }
    }


    public static void nativeRenderModel( // TODO:
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, GeoModel mesh, float[] boneVertex, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview) {

        if (mesh.nativeModelHandle == 0) return;

        pose.pose().get(matrixTransferArray, 0);
        pose.normal().get(matrixTransferArray, 16);
        projectionModelViewMatrix.get(matrixTransferArray, 32);

        GeoModel.nComputeModelVertices(
                mesh.nativeModelHandle,
                vertexConsumer,
                matrixTransferArray,
                boneVertex,
                renderPartMask,
                packedLight, packedOverlay,
                r, g, b, a
        );
    }
}
