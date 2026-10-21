package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class PreviewEntityRegistry {

    @FunctionalInterface
    public interface SceneryRenderer {
        void render(PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight);
    }

    public record Entry(
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity
    ) {
    }

    private static final Map<EntityRenderState, Entry> ENTRIES = new IdentityHashMap<>();

    /**
     * 曾经注册过的预览 state（不在消费时删除）。
     *
     * <p>26.x 的 PIP 渲染会对同一个 state 触发多次 {@code EntityRenderDispatcher.submit}。
     * 若在第一次重定向后就 {@code ENTRIES.remove(state)}，后续调用因查不到条目而回落到
     * 原版渲染，导致同一个预览实体被画两遍（自定义模型 + 原版玩家模型），
     * 视觉上就是多个错位副本。这里单独记录"该 state 属于预览"，
     * 保证它永远不会回落到原版路径。
     */
    private static final Set<EntityRenderState> PREVIEW_STATES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** 防御性上限：正常每帧只有十几个预览 state，超过说明清理失效，直接重置避免无限增长。 */
    private static final int MAX_STATES = 512;

    private PreviewEntityRegistry() {
    }

    public static void register(EntityRenderState state, CustomPlayerEntity animatable) {
        register(state, animatable, null, null);
    }

    public static void register(
            EntityRenderState state,
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity
    ) {
        if (ENTRIES.size() > MAX_STATES) {
            ENTRIES.clear();
            PREVIEW_STATES.clear();
        }
        ENTRIES.put(state, new Entry(animatable, beforeEntity, afterEntity));
        PREVIEW_STATES.add(state);
    }

    /** 该 state 是否为本模组注册的预览实体（条目可能已被消费，但仍须阻止原版回退）。 */
    public static boolean isPreviewState(EntityRenderState state) {
        return PREVIEW_STATES.contains(state);
    }

    @Deprecated
    @Nullable
    public static CustomPlayerEntity get(EntityRenderState state) {
        Entry entry = ENTRIES.get(state);
        return entry == null ? null : entry.animatable();
    }

    @Nullable
    public static Entry getEntry(EntityRenderState state) {
        return ENTRIES.get(state);
    }

    public static void remove(EntityRenderState state) {
        ENTRIES.remove(state);
    }
}
