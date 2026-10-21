package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 纯客户端模式下的模型选择持久化。
 *
 * <p>原版 YSM 的模型选择由服务端 capability 保存（写进玩家 NBT），
 * 服务器没装模组时无处可存，每次重启都会退回默认模型。
 * 这里用 {@code config/yes_steve_model/local_selection.json} 记录本地玩家
 * 上次选中的模型 id 与贴图名，进世界时自动套用。
 *
 * <p>只在"未连接 YSM 服务端"时生效：一旦服务端下发了模型同步，
 * 以服务端状态为准，避免两边打架。
 */
public final class LocalModelSelection {

    private static final Path FILE = ServerModelManager.FOLDER.resolve("local_selection.json");

    private static String modelId = "";
    private static String textureId = "";
    private static boolean loaded = false;

    private LocalModelSelection() {
    }

    /** 读取已保存的选择（首次调用时从磁盘加载）。 */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.exists(FILE)) {
                return;
            }
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
                modelId = obj.get("model").getAsString();
            }
            if (obj.has("texture") && obj.get("texture").isJsonPrimitive()) {
                textureId = obj.get("texture").getAsString();
            }
            YesSteveModel.LOGGER.info("[YSM] Local selection loaded: " + modelId);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to read local selection", t);
        }
    }

    /** 保存当前选择。 */
    public static void save(@Nullable String newModelId, @Nullable String newTextureId) {
        if (newModelId == null || newModelId.isEmpty()) {
            return;
        }
        if (newModelId.equals(modelId) && (newTextureId == null || newTextureId.equals(textureId))) {
            return;
        }
        modelId = newModelId;
        if (newTextureId != null) {
            textureId = newTextureId;
        }
        loaded = true;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("model", modelId);
            obj.addProperty("texture", textureId);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, obj.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to save local selection", t);
        }
    }

    public static String getModelId() {
        return modelId;
    }

    public static String getTextureId() {
        return textureId;
    }

    /**
     * 进世界后把保存的本地选择套用到本地玩家身上。
     *
     * <p>只在模型表里确实存在该模型时才套用，避免模型被删掉后玩家卡在空模型。
     * 必须切到渲染线程执行，因为 {@code setModelId} 会重建渲染资源。
     *
     * <p>本地扫描可能比玩家进世界更早完成（缓存命中时只需几秒），此时
     * {@code Minecraft.player} 还是 null。这里不能直接放弃，而是挂一个
     * 客户端 tick 重试，等玩家真正进世界后再套用。
     */
    public static void applyToLocalPlayer() {
        load();
        if (modelId.isEmpty()) {
            return;
        }
        Minecraft.getInstance().execute(() -> tryApply(0));
    }

    /** 单次尝试套用；玩家还没就绪则挂到下一 tick 重试。 */
    private static void tryApply(int attempt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            // 玩家还没进世界：最多重试 600 次（约 30 秒），避免无限占用 tick 回调
            if (attempt < 600) {
                scheduleRetry(attempt + 1);
            } else {
                YesSteveModel.LOGGER.warn("[YSM] Local selection not applied: player never became ready");
            }
            return;
        }
        if (!ClientModelManager.getModelContext(modelId).isPresent()) {
            YesSteveModel.LOGGER.warn("[YSM] Saved local model not found, keep default: " + modelId);
            return;
        }
        com.elfmcys.yesstevemodel.capability.PlayerCapability.get(player).ifPresentOrElse(cap -> {            cap.initModelWithTexture(modelId, textureId.isEmpty() ? null : textureId);            YesSteveModel.LOGGER.info("[YSM] Applied saved local model: " + modelId);
        }, () -> scheduleRetry(attempt + 1));
    }

    private static void scheduleRetry(int attempt) {
        com.elfmcys.yesstevemodel.util.YSMThreadPool.submit(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Minecraft.getInstance().execute(() -> tryApply(attempt));
        });
    }
}
