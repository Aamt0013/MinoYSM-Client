package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.RenderContext;

import com.elfmcys.yesstevemodel.client.renderer.CustomEntityTranslucentRenderType;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    SubmitNodeCollector getCurrentRTB();

    default void setCurrentRTB(SubmitNodeCollector bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        animatable.resetAnimationState();
        Identifier tex = animatable.getTextureLocation();
        if (vertexConsumer != null) {
            NativeModelRenderer.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, tex);
        } else {
            // 26.x：SubmitNodeCollector 不再提供"立即取缓冲"，改为提交自定义几何体，由收集器稍后统一绘制。
            // CustomGeometryRenderer 回调给出当时的 Pose 与 VertexConsumer，正好对应旧 renderMesh 的前两个参数。
            // collector 可能为 null（旧 render 路径未传），此处从渲染上下文兜底；都为空则跳过本次绘制，
            // 避免 NPE 中断整帧渲染（会导致界面卡死）。
            SubmitNodeCollector collector = bufferSource != null ? bufferSource : RenderContext.collector();
            if (collector != null) {
                // 关键：matrixData / absPivotData 是 AnimatedGeoModel 内部复用的共享数组，动画求值时就地覆写。
                // 而 CustomGeometryRenderer 是**延迟执行**的：若直接闭包引用它们，等真正绘制时读到的
                // 已是后续帧的数据，表现为骨骼错位、只有头部在动、姿态絮乱。因此必须在此刻做快照。
                float[] matrixSnapshot = model.getMatrixData().clone();
                float[] pivotSnapshot = model.getAbsPivotData().clone();
                collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) ->
                        NativeModelRenderer.renderMesh(consumer, pose, model.getGeoModel(), matrixSnapshot, pivotSnapshot, i, 0, i2, i3, f2, f3, f4, f5, tex));
            }
        }
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
            float width = animatable.getHeightScale();
            float height = animatable.getWidthScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, PoseStack poseStack, float partialTick, SubmitNodeCollector bufferSource,
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue,
                            float alpha) {
    }

    @Nullable
    default RenderType getRenderType(Identifier identifier, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return CustomEntityTranslucentRenderType.get(identifier);
            }
            return RenderTypes.entityCutout(identifier);
        }
        if (z2) {
            return RenderTypes.outline(identifier);
        }
        return null;
    }

    default Color getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}