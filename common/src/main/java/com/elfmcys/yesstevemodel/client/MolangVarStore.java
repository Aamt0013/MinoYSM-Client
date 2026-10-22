package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轮盘个性化设置（Molang roaming 变量）的本地持久化。
 *
 * <p><b>为什么需要这个类</b>：原版架构里，轮盘里的勾选/单选/滑条改的是
 * {@code v.roaming.*} 这类"漫游变量"，客户端改完通过 {@code C2SCompleteFeedbackPacket}
 * 交给服务端，服务端存在玩家数据里，下次进服再由
 * {@code S2CSetModelAndTexturePacket}（内含 {@code S2CSyncPlayerStatePacket}）下发回来。
 *
 * <p>纯客户端模式下这条链路是断的：
 * <ul>
 *   <li>服务器没装 YSM 时，{@code C2SCompleteFeedbackPacket} 没有接收方</li>
 *   <li>即便在单人游戏里集成服务端能收到，回传依赖 {@code EnterServerEvent}，
 *       而纯客户端构建跳过了该事件（否则会触发整套服务端模型扫描）</li>
 * </ul>
 * 结果是：本次游戏内改的设置能用，但退出重进就回到默认值。
 *
 * <p>因此这里在客户端落一份本地副本，按 <b>模型 id</b> 分组保存：
 * <pre>
 * config/yes_steve_model/molang_vars.json
 * {
 *   "Tsuki_Velpro v1.1.ysm": { "bagemotion": 2.0, "red_bow_headdress": 1.0 }
 * }
 * </pre>
 *
 * <p>用模型 id 而不是 {@code modelHashId} 作键：hashId 是模型内容哈希，
 * 模型文件一改就变，而用户视角的"同一个模型"是按 id 认的。
 *
 * <p>仅在没有 YSM 服务端时启用（{@code !NetworkHandler.isClientConnected()}），
 * 有服务端时以服务端数据为准，避免两边打架。
 */
public final class MolangVarStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path FILE =
            ServerModelManager.FOLDER.resolve("molang_vars.json");

    /** modelId -> (变量名 -> 值)。 */
    private static final Map<String, Map<String, Float>> DATA = new ConcurrentHashMap<>();

    private static volatile boolean loaded = false;

    /** 合并写入：短时间内多次改动只落盘一次。 */
    private static final AtomicBoolean SAVE_PENDING = new AtomicBoolean(false);

    private MolangVarStore() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> modelEntry : root.getAsJsonObject().entrySet()) {
                if (!modelEntry.getValue().isJsonObject()) {
                    continue;
                }
                Map<String, Float> vars = new HashMap<>();
                for (Map.Entry<String, JsonElement> varEntry :
                        modelEntry.getValue().getAsJsonObject().entrySet()) {
                    JsonElement value = varEntry.getValue();
                    if (value instanceof JsonPrimitive prim && prim.isNumber()) {
                        vars.put(varEntry.getKey(), prim.getAsFloat());
                    }
                }
                if (!vars.isEmpty()) {
                    DATA.put(modelEntry.getKey(), vars);
                }
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to read molang_vars.json, using defaults", t);
        }
    }

    /**
     * 读取某个模型保存的变量。
     *
     * @return 变量表；没有记录时返回空表（调用方无需判空）
     */
    public static Map<String, Float> get(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return Map.of();
        }
        // 快路径：加载完成后不再进同步块。
        // get() 会被模型加载路径调用，若每次都抢锁，碰上后台正在写文件
        // 就会让渲染线程短暂阻塞，累积起来表现为卡顿。
        if (!loaded) {
            ensureLoaded();
        }
        Map<String, Float> vars = DATA.get(modelId);
        return vars == null ? Map.of() : vars;
    }

    /**
     * 合并写入某个模型的若干变量，并安排一次异步落盘。
     *
     * @param modelId 模型 id
     * @param deltas  变量名 -> 新值
     */
    public static void putAll(String modelId, Map<String, Float> deltas) {
        if (modelId == null || modelId.isEmpty() || deltas == null || deltas.isEmpty()) {
            return;
        }
        ensureLoaded();
        DATA.computeIfAbsent(modelId, k -> new ConcurrentHashMap<>()).putAll(deltas);
        scheduleSave();
    }

    /** 合并写入单个变量。 */
    public static void put(String modelId, String name, float value) {
        if (name == null || name.isEmpty()) {
            return;
        }
        putAll(modelId, Map.of(name, value));
    }

    private static void scheduleSave() {
        if (!SAVE_PENDING.compareAndSet(false, true)) {
            return;
        }
        com.elfmcys.yesstevemodel.util.YSMThreadPool.submit(() -> {
            try {
                // 合并窗口：轮盘里连续拖动滑条会产生大量写入，等抖动停下来再落盘
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SAVE_PENDING.set(false);
                return;
            }
            SAVE_PENDING.set(false);
            saveNow();
        });
    }

    /** 立即落盘（进程退出前或需要确保写入时调用）。 */
    public static synchronized void saveNow() {
        if (!loaded || DATA.isEmpty()) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            // 按模型 id 排序，输出稳定，便于用户手改和 diff
            for (String modelId : new java.util.TreeSet<>(DATA.keySet())) {
                Map<String, Float> vars = DATA.get(modelId);
                if (vars == null || vars.isEmpty()) {
                    continue;
                }
                JsonObject varsObj = new JsonObject();
                for (Map.Entry<String, Float> e : new LinkedHashMap<>(vars).entrySet()) {
                    varsObj.addProperty(e.getKey(), e.getValue());
                }
                root.add(modelId, varsObj);
            }
            Files.createDirectories(FILE.getParent());
            Path tmp = Files.createTempFile(FILE.getParent(), "molangvars", ".tmp");
            try {
                Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicFailed) {
                    Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to save molang_vars.json", t);
        }
    }
}
