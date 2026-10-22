package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.resource.TextureCache;

import com.elfmcys.yesstevemodel.resource.ModelParseCache;

import com.elfmcys.yesstevemodel.model.ServerModelManager;

import com.elfmcys.yesstevemodel.BuildFlags;
import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.IGuiWidget;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.model.ModelAssemblyFactory;
import com.elfmcys.yesstevemodel.client.model.ProjectileModelBundle;
import com.elfmcys.yesstevemodel.client.model.VehicleModelBundle;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.client.upload.IResourceLocatable;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SModelSyncPayload;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMClientMapper;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.elfmcys.yesstevemodel.util.YSMThreadPool;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.jetbrains.annotations.Nullable;
import rip.ysm.legacy.YesModelUtils;
import rip.ysm.security.YSMByteBuf;
import rip.ysm.security.YSMClientCache;
import rip.ysm.security.YsmCrypt;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ClientModelManager {

    private static int syncStep = 1;
    private static byte[] key1;
    private static byte[] lastKey;
    private static byte[] serverKey;
    private static byte[] clientKey;
    private static String currentCacheFolderName;
    private static final AtomicInteger pendingModelsCount = new AtomicInteger(0);

    private static final ThreadPoolExecutor modelPhraseExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "YSM-Model-Parse-Thread");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Map<UUID, ServerModelContext> serverModels = new ConcurrentHashMap<>();

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static volatile ModelAssembly localModelContext;
    private static volatile Runnable pendingModelCallback;
    private static IResourceLocatable defaultTexture;
    private static volatile Connection serverConnection;

    private static volatile Map<String, ModelAssembly> modelAssemblyMap = Object2ReferenceMaps.emptyMap();
    private static volatile Map<String, ModelPackData> modelPackMap = new Object2ReferenceOpenHashMap<>();

    private static final ConcurrentLinkedQueue<Pair<ModelAssembly, String>> pendingModelQueue = new ConcurrentLinkedQueue<>();
    private static final WeakHashMap<IGuiWidget, Object> guiWidgets = new WeakHashMap<>();
    private static final SyncStatus syncState = new SyncStatus();
    private static boolean isOysmServer = false;
    private static boolean allowUpload = false;

    /**
     * 本地模型（纯客户端模式）。
     *
     * <p>原版 YSM 的模型加载完全由服务端驱动：服务端扫 built/custom/auth，算 hash，
     * 通过 packet 02 下发 (hash1, hash2, modelId)，客户端再按 hash 查 cache/client。
     * 服务器没装 YSM 时这条链一步都不走，客户端永远只有内置 default 模型。
     *
     * <p>纯客户端模式下，客户端自己扫 custom/ 目录，本地解密、本地解析，
     * 用一个稳定的伪 hash 注册进 {@link #modelAssemblyMap}，不依赖任何服务端数据。
     * 伪 hash 由 modelId 的哈希派生，只要模型路径不变就一直稳定，
     * 因此能和服务端下发的模型共存于同一张表而不会互相覆盖。
     */
    private static final Set<String> localModelIds = ConcurrentHashMap.newKeySet();

    /**
     * 本地扫描是否已启动（防重入）。
     *
     * <p>注意与 {@link #localModelsReady} 的区别：这个标志只表示"已经开始扫"，
     * 扫描还没结束时它就是 true。判断"模型可用"必须用 {@link #localModelsReady}。
     */
    private static final java.util.concurrent.atomic.AtomicBoolean localScanStarted =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 本地扫描是否已完成（模型已写入 {@link #modelAssemblyMap}）。 */
    private static volatile boolean localModelsReady = false;

    /** 本地模型解析完成回调；支持多个订阅者。 */
    private static final List<Runnable> localModelsReadyCallbacks =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /** 本地扫描的失败原因，供界面/日志展示；null 表示无错误。 */
    private static volatile String localScanError = null;

    public enum SyncState {
        WAITING, LOADING, IDLE, PREPARING, SYNCING
    }

    public static class ServerModelContext {
        public final UUID uuid;
        public final long hash1;
        public final long hash2;
        public final String modelId;
        public final boolean isAuth;
        public final int isCustomSkinModel;
        public final int version;

        public byte[] fileBuffer;
        public int totalSize;
        public int bytesReceived;

        public ServerModelContext(long hash1, long hash2, String modelId, boolean isAuth, int isCustomSkinModel, int version) {
            this.uuid = new UUID(hash1, hash2);
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.modelId = modelId;
            this.isAuth = isAuth;
            this.isCustomSkinModel = isCustomSkinModel;
            this.version = version;
        }
    }

    /**
     * 客户端本地扫描的兜底调度。
     *
     * <p>服务端装了 YSM 时，握手会下发全部模型，本地扫描纯属重复劳动
     * （单人游戏尤其明显：刚解析完 46 个模型就被服务端版本全部接管，
     * 白花十几秒还多一轮装配/释放）。
     *
     * <p>因此不在一开始就扫，而是等一个宽限期：
     * <ul>
     *   <li>服务端在宽限期内完成同步 → 取消本地扫描</li>
     *   <li>宽限期到点仍没有服务端同步 → 认为服务器没装 YSM，启动本地扫描</li>
     * </ul>
     *
     * <p>宽限期取值参考原版逻辑：客户端本来就会等 60 秒再提示"服务器未安装"。
     * 这里取 5 秒——握手在正常网络下 1 秒内就能完成，5 秒足够区分。
     */
    public static void scheduleLocalModelScan() {
        if (localScanScheduled.compareAndSet(false, true)) {
            YSMThreadPool.submit(() -> {
                try {
                    Thread.sleep(LOCAL_SCAN_GRACE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (serverSyncReceived) {
                    YesSteveModel.LOGGER.info("[YSM] Server provides models, skip local scan.");
                    return;
                }
                YesSteveModel.LOGGER.info("[YSM] No server model sync after grace period, scanning locally.");
                loadLocalModels();
            });
        }
    }

    /** 服务端握手是否已经下发过模型数据。 */
    private static volatile boolean serverSyncReceived = false;
    private static final java.util.concurrent.atomic.AtomicBoolean localScanScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** 等待服务端同步的宽限期（毫秒）。 */
    private static final long LOCAL_SCAN_GRACE_MS = 5000L;

    /**
     * 纯客户端模式：本地扫描 {@code custom/}、{@code built/}、{@code auth/}，
     * 自行解密解析并注册进 {@link #modelAssemblyMap}。
     *
     * <p>不依赖服务端任何数据，因此服务器未安装 YSM 时同样可用。
     * 重复调用是幂等的（{@link #localScanStarted} 守卫）。
     *
     * <p>解析在后台线程串行执行（与服务器同步共用 {@link #modelPhraseExecutor}），
     * 完成后切回渲染线程写入 {@link #modelAssemblyMap}。
     */
    public static void loadLocalModels() {
        // 配置关闭时完全不扫描，行为退回原版（只认服务端下发的模型）
        try {
            if (GeneralConfig.CLIENT_ONLY_MODELS != null && !GeneralConfig.CLIENT_ONLY_MODELS.get()) {
                YesSteveModel.LOGGER.info("[YSM] ClientOnlyModels disabled, skip local scan.");
                return;
            }
        } catch (Throwable t) {
            // 配置系统异常时按默认（开启）处理，但留下痕迹便于排查
            YesSteveModel.LOGGER.warn("[YSM] Failed to read ClientOnlyModels, assuming enabled", t);
        }
        if (!localScanStarted.compareAndSet(false, true)) {
            return;
        }

        YesSteveModel.LOGGER.info("[YSM] Local model scan started (client-only mode).");
        // 用独立线程池：resetClientState() 会清空 modelPhraseExecutor 的队列，
        // 而本地扫描与服务器同步无关，不该被断线/换维度打断。
        YSMThreadPool.submit(() -> {
            long startMs = System.currentTimeMillis();
            // 必须先让 default 模型装配完成：非 primary 模型会从 primaryAssembly
            // 继承动画表，primary 未就绪时 buildAssembly 会 NPE。
            try {
                runPendingModelCallback();
            } catch (Throwable t) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to flush default model before local scan", t);
            }
            // 客户端初始化是异步的，default 可能还没进队列，这里短暂等待
            for (int i = 0; i < 100 && !ModelAssemblyFactory.isPrimaryReady(); i++) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                try {
                    runPendingModelCallback();
                } catch (Throwable ignored) {
                }
            }
            if (!ModelAssemblyFactory.isPrimaryReady()) {
                YesSteveModel.LOGGER.warn("[YSM] Primary model still not ready, local scan skipped.");
                localScanError = "primary model not ready";
                // 允许后续重试（例如 native 库延迟就绪）
                localScanStarted.set(false);
                return;
            }
            Object2ReferenceOpenHashMap<String, ModelAssembly> collected = new Object2ReferenceOpenHashMap<>();
            java.util.List<ModelPackData> collectedPacks = new java.util.ArrayList<>();
            java.util.List<ModelSource> sources = new java.util.ArrayList<>();
            try {
                ensureLocalModelDirs();

                // 纯客户端构建下服务端不会执行 reloadPacks()，内置模型就没人解压了。
                // 因此这里必须自己解压一次——但仅当 built/ 为空时才做：
                //   * 全新安装：built/ 不存在或为空 → 解压
                //   * 常规启动：built/ 已有上次的完整副本 → 跳过（省下 61 MB 的删除+重写）
                // 服务端构建（双端版）仍由 reloadPacks() 负责，不会走到这里。
                extractBuiltinIfMissing();

                for (Path dir : new Path[]{
                        ServerModelManager.BUILT,
                        ServerModelManager.CUSTOM,
                        ServerModelManager.AUTH}) {
                    scanLocalModelDir(dir, sources);
                }
                // 包（ysm-pack.json/png）元数据原本只由服务端扫描、经 S2C 包下发。
                // 纯客户端构建下服务端不工作，必须自己扫一遍，
                // 否则模型选择界面里的包图标会全部退化成紫黑格。
                scanLocalModelPacks(collectedPacks);
            } catch (Throwable t) {
                YesSteveModel.LOGGER.error("[YSM] Local model scan failed", t);
                localScanError = t.toString();
            }

            // jar 内置的 default 由 loadDefaultModel() 以 primary 身份装配，
            // 不能再走普通流程，否则会用非 primary 副本覆盖掉 primary 的动画继承关系。
            // 注意只跳过 id 恰好为 "default" 的：用户完全可能在 custom/default/ 放自己的模型。
            sources.removeIf(s -> "default".equals(s.modelId()));

            // 优先解析本地玩家上次选中的模型：大量模型时先让"自己的模型"可用，
            // 其余的随后补齐，避免用户进世界后长时间看不到自己的模型。
            prioritizeSelectedModel(sources);

            // 串行解析 + 串行装配。
            //
            // 曾经尝试过并行解析（8 线程跑 buildParsedBundle）来提速，实测 61 个模型
            // 9794 ms → 3903 ms，但引入了两处严重问题：
            //   1. GeckoLibCache 的 EXTRA_BINDING/bindings 是普通 HashMap，惰性初始化
            //      在并发下会丢条目，导致 Molang 绑定缺失；
            //   2. GeoModel.buildNativeCache() 调用的 native 函数内部维护全局句柄表，
            //      并发进入会损坏缓存。
            // 两处都加了锁之后仍然观察到预览渲染（轮盘 / 额外玩家）异常，
            // 说明这条路径上还有未发现的共享状态。模型渲染是这个模组的核心功能，
            // 不能为了启动速度冒渲染错乱的风险，因此回退为串行。
            //
            // 保留的性能优化：透光扫描结果缓存、贴图缓存、解析结果缓存、
            // FloatValue 驻留 —— 这些都不改变线程模型。
            for (ModelSource src : sources) {
                long t0 = System.currentTimeMillis();
                RawYsmModel raw = loadRawModel(src);
                if (raw == null) {
                    continue;
                }
                ClientModelInfo parsed = YSMClientMapper.buildParsedBundle(raw, src.modelId());
                LOCAL_PARSE_MS.addAndGet(System.currentTimeMillis() - t0);

                if (!ModelAssemblyFactory.isPrimaryReady()) {
                    YesSteveModel.LOGGER.warn("[YSM] Skip local model (primary not ready): " + src.modelId());
                    continue;
                }
                long t1 = System.currentTimeMillis();
                try {
                    ModelAssembly assembly = ModelAssemblyFactory.buildAssembly(parsed, false, false);
                    collected.put(src.modelId(), assembly);
                } catch (Throwable t) {
                    YesSteveModel.LOGGER.warn("[YSM] Failed to build local model: " + src.modelId(), t);
                }
                LOCAL_ASSEMBLE_MS.addAndGet(System.currentTimeMillis() - t1);
            }

            final Object2ReferenceOpenHashMap<String, ModelAssembly> result = collected;
            final java.util.List<ModelPackData> packsResult = collectedPacks;
            final long elapsed = System.currentTimeMillis() - startMs;
            Minecraft.getInstance().execute(() -> {
                if (!packsResult.isEmpty()) {
                    // 复用服务端同步路径的同一套注册逻辑（含图标纹理上传/释放）
                    onModelPacksReceived(packsResult.toArray(new ModelPackData[0]));
                    YesSteveModel.LOGGER.info("[YSM] Local model packs registered: " + packsResult.size()
                            + " (" + packsResult.stream().map(ModelPackData::getPath).toList() + ")");
                }
                if (!result.isEmpty()) {
                    Object2ReferenceOpenHashMap<String, ModelAssembly> merged =
                            new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
                    // 同名时本地覆盖服务端：本地文件是用户刚放进去的，
                    // 且服务端那份可能是旧版本。被覆盖的旧装配体需要释放资源。
                    for (Map.Entry<String, ModelAssembly> entry : result.entrySet()) {
                        String id = entry.getKey();
                        ModelAssembly previous = merged.put(id, entry.getValue());
                        if (previous != null && previous != entry.getValue()) {
                            releaseAssembly(previous);
                        }
                        localModelIds.add(id);
                    }
                    modelAssemblyMap = merged;
                }
                localModelsReady = true;
                // 启动阶段结束：此后渲染线程再长时间不推进就是真卡顿，不再按宽限阈值放过
                com.elfmcys.yesstevemodel.client.util.StallWatchdog.arm();
                YesSteveModel.LOGGER.info("[YSM] Local models ready: " + localModelIds.size()
                        + " registered in " + elapsed + " ms (client-only mode)."
                        + " [cache hit=" + LOCAL_CACHE_HITS.get() + " miss=" + LOCAL_CACHE_MISSES.get()
                        + " parse=" + LOCAL_PARSE_MS.get() + "ms assemble=" + LOCAL_ASSEMBLE_MS.get() + "ms"
                        + " molangFails=" + YSMClientMapper.drainParseFailCount() + "]");
                LOCAL_CACHE_HITS.set(0);
                LOCAL_CACHE_MISSES.set(0);
                LOCAL_PARSE_MS.set(0);
                LOCAL_ASSEMBLE_MS.set(0);
                fireLocalModelsReadyCallbacks();
                forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(modelAssemblyMap));
            });
        });
    }

    /**
     * 扫描本地模型包的元数据（{@code ysm-pack.json} / {@code ysm-pack.png}）。
     *
     * <p>原版这套数据只由服务端 {@code ServerModelManager.scanDirectoryPacks()}
     * 填充，再经 {@code S2CModelSyncPayload} 的包信息段下发到客户端。
     * 纯客户端构建下服务端不工作，这条链路断了，模型选择界面里所有包图标
     * 都会退化成紫黑格（MissingTexture）。
     *
     * <p>目录结构与服务端一致：在 {@code built/}、{@code custom/}、{@code auth/}
     * 各自的一级子目录里找 {@code ysm-pack.json}，{@code folderPath} 用
     * "子目录名/" 的形式（与服务端 {@code baseDir.relativize} 的结果一致）。
     */
    private static void scanLocalModelPacks(java.util.List<ModelPackData> out) {
        for (Path baseDir : new Path[]{
                ServerModelManager.BUILT,
                ServerModelManager.CUSTOM,
                ServerModelManager.AUTH}) {
            if (baseDir == null || !Files.isDirectory(baseDir)) {
                continue;
            }
            try (var stream = Files.walk(baseDir, 1)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> !p.equals(baseDir))
                        .forEach(path -> {
                            Path packJson = path.resolve("ysm-pack.json");
                            if (!Files.exists(packJson)) {
                                return;
                            }
                            try {
                                String folderPath = baseDir.relativize(path).toString().replace('\\', '/') + "/";

                                String name = "";
                                String description = "";
                                Map<String, Map<String, String>> lang = null;

                                String jsonStr = Files.readString(packJson, java.nio.charset.StandardCharsets.UTF_8);
                                com.google.gson.JsonObject json =
                                        com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                                if (json.has("name")) name = json.get("name").getAsString();
                                if (json.has("description")) description = json.get("description").getAsString();
                                if (json.has("lang") && json.get("lang").isJsonObject()) {
                                    lang = new HashMap<>();
                                    for (Map.Entry<String, com.google.gson.JsonElement> e :
                                            json.getAsJsonObject("lang").entrySet()) {
                                        if (!e.getValue().isJsonObject()) continue;
                                        Map<String, String> translations = new HashMap<>();
                                        for (Map.Entry<String, com.google.gson.JsonElement> t :
                                                e.getValue().getAsJsonObject().entrySet()) {
                                            translations.put(t.getKey(), t.getValue().getAsString());
                                        }
                                        lang.put(e.getKey(), translations);
                                    }
                                }

                                OuterFileTexture icon = null;
                                Path packPng = path.resolve("ysm-pack.png");
                                if (Files.exists(packPng)) {
                                    icon = new OuterFileTexture(Files.readAllBytes(packPng));
                                }

                                out.add(new ModelPackData(folderPath, name, description, icon, lang));
                            } catch (Throwable t) {
                                YesSteveModel.LOGGER.warn("[YSM] Failed to read pack metadata: " + packJson, t);
                            }
                        });
            } catch (Throwable t) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to scan pack dir: " + baseDir, t);
            }
        }
    }

    /**
     * 确保本地模型目录存在。
     *
     * <p>刻意不调用 {@link ServerModelManager#extractBuiltinModels()}：那个方法会先
     * 递归删除 {@code built/} 再重写，单人游戏下服务端 {@code reloadPacks()} 与
     * 客户端本地扫描会并发执行，两边同时删/写同一目录会把 621 个文件搞成残缺状态。
     * 解压职责留给服务端；纯客户端场景（服务器没装模组）下 {@code built/} 是上一次
     * 运行留下的完整副本，可直接复用。
     */
    private static void ensureLocalModelDirs() {
        for (Path dir : new Path[]{
                ServerModelManager.BUILT,
                ServerModelManager.CUSTOM,
                ServerModelManager.AUTH}) {
            try {
                Files.createDirectories(dir);
            } catch (Throwable t) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to create local model dir: " + dir, t);
            }
        }
    }

    /**
     * 内置模型缺失或版本变化时重新解压。
     *
     * <p>{@code built/} 是解压产物，内容随模组版本变化。判断依据必须是
     * <b>版本戳</b>，不能只看"目录里有没有模型文件夹"：
     * 用户从旧版本升级时，旧版解压出的模型（例如已从 jar 移除的 wine_fox）
     * 会一直留在磁盘上被加载，表现为"新版本居然还带着旧版的内置模型"。
     *
     * <p>所以比对 {@link ServerModelManager#BUILT_VERSION_STAMP}：
     * <ul>
     *   <li>版本戳匹配 → 跳过解压（省下全量删除+重写的开销）</li>
     *   <li>版本戳缺失/不一致 → 强制重新解压（会先清空整个 built/）</li>
     * </ul>
     *
     * <p>不用文件数判断是否"已解压"——{@code notice.txt} 会被写进空目录，
     * 用文件数会误判。
     */
    private static void extractBuiltinIfMissing() {
        // 先确保黑名单文件存在：全新安装时 reloadPacks() 不会跑，
        // 没有这个文件用户就没法禁用内置模型。
        ServerModelManager.ensureBlacklistTemplate();

        String currentVersion = currentModVersion();
        String stampedVersion = ServerModelManager.readBuiltVersionStamp();

        if (currentVersion != null && currentVersion.equals(stampedVersion)) {
            YesSteveModel.LOGGER.debug("[YSM] built/ version stamp matches ({}), skip extraction.", currentVersion);
            applyBlacklist();
            return;
        }

        YesSteveModel.LOGGER.info("[YSM] built/ extraction needed (stamp=" + stampedVersion
                + ", current=" + currentVersion + "), re-extracting builtin models...");
        long t0 = System.currentTimeMillis();
        try {
            // 该方法会先递归清空 built/，旧版本残留的模型会一并消失
            ServerModelManager.extractBuiltinModels();
            YesSteveModel.LOGGER.info("[YSM] Builtin models extracted in "
                    + (System.currentTimeMillis() - t0) + " ms.");
            if (currentVersion != null) {
                ServerModelManager.writeBuiltVersionStamp(currentVersion);
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("[YSM] Failed to extract builtin models", t);
        }
        // 解压后立刻应用黑名单，否则被禁用的模型会先加载一遍再被删掉
        applyBlacklist();
    }

    /** 当前模组版本号；取不到时返回 null（此时每次都重新解压，行为安全但略慢）。 */
    private static String currentModVersion() {
        try {
            return dev.architectury.platform.Platform.getMod(YesSteveModel.MOD_CONTAINER_ID).getVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 应用 {@code blacklist.txt} 里的规则。
     *
     * <p>纯客户端构建跳过了 {@code reloadPacks()}，而黑名单原本只在那里被处理，
     * 导致用户写的禁用规则被静默忽略、内置模型照样加载。这里补上调用。
     */
    private static void applyBlacklist() {
        try {
            Path blacklistFile = ServerModelManager.FOLDER.resolve("blacklist.txt");
            if (!Files.exists(blacklistFile)) {
                return;
            }
            ServerModelManager.processBlacklist(blacklistFile);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to apply model blacklist", t);
        }
    }

    /** 释放一个装配体占用的贴图与 native 缓存。 */
    private static void releaseAssembly(ModelAssembly assembly) {
        try {
            for (AbstractTexture tex : assembly.getTextures()) {
                UploadManager.removeTexture(tex);
            }
            if (NativeLibLoader.isLoaded()) {
                for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                    entry.getValue().getModel().freeNativeCache();
                }
                for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                    entry.getValue().getModel().freeNativeCache();
                }
                assembly.getAnimationBundle().getMainModel().freeNativeCache();
                assembly.getAnimationBundle().getArmModel().freeNativeCache();
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to release model assembly", t);
        }
    }

    private static void fireLocalModelsReadyCallbacks() {
        List<Runnable> callbacks;
        synchronized (localModelsReadyCallbacks) {
            if (localModelsReadyCallbacks.isEmpty()) {
                return;
            }
            callbacks = new ArrayList<>(localModelsReadyCallbacks);
            localModelsReadyCallbacks.clear();
        }
        for (Runnable cb : callbacks) {
            try {
                cb.run();
            } catch (Throwable t) {
                YesSteveModel.LOGGER.error("[YSM] localModelsReady callback failed", t);
            }
        }
    }

    /**
     * 扫描单个模型根目录。支持两种形态：
     * <ul>
     *   <li>文件夹模型：目录里直接有 {@code ysm.json}（内置模型、解包后的模型）</li>
     *   <li>文件模型：目录里有 {@code *.ysm}（加密模型包）</li>
     * </ul>
     */
    private static void scanLocalModelDir(Path baseDir, java.util.List<ModelSource> out) {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return;
        }
        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (dir.equals(baseDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        if (YSMFolderDeserializer.isModelFolder(dir)) {
                            String modelId = baseDir.relativize(dir).toString().replace('\\', '/');
                            out.add(new ModelSource(modelId, dir, null));
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    } catch (Throwable t) {
                        YesSteveModel.LOGGER.warn("[YSM] Error probing local dir: " + dir, t);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (!file.getFileName().toString().endsWith(".ysm")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String modelId = baseDir.relativize(file).toString().replace('\\', '/');
                    out.add(new ModelSource(modelId, null, file));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Failed walking local model dir: " + baseDir, t);
        }
    }

    /**
     * 一个待解析的模型来源。
     *
     * <p>扫描阶段只收集路径（纯 IO，很快），解密/反序列化/解析在
     * {@link #loadRawModel} 与调用处串行执行。
     *
     * <p>注意：这里刻意保持「扫描」与「解析」两阶段分离的结构，但解析是串行的。
     * 曾试过并行解析提速，因 GeckoLibCache 的 HashMap 惰性初始化与
     * {@code GeoModel.buildNativeCache()} 的 native 全局状态都不是线程安全的，
     * 会导致 Molang 绑定丢失与渲染异常，已回退。
     *
     * @param modelId 模型 id（相对路径）
     * @param folder  文件夹模型目录；文件模型时为 null
     * @param file    {@code .ysm} 文件；文件夹模型时为 null
     */
    private record ModelSource(String modelId, Path folder, Path file) {
    }

    /**
     * 解密 + 反序列化一个模型来源，返回 {@link RawYsmModel}（含磁盘缓存）。
     *
     * <p>可能返回 null（格式不支持 / 文件损坏 / 缓存与解析都失败）。
     */
    @Nullable
    private static RawYsmModel loadRawModel(ModelSource src) {
        if (src.folder() != null) {
            // 文件夹模型的缓存键：由目录内所有文件的相对路径+大小+修改时间
            // 折叠成一个指纹。任一文件变动都会让指纹变化，从而自动失效。
            byte[] fingerprint = fingerprintFolder(src.folder());
            RawYsmModel cached = fingerprint != null
                    ? ModelParseCache.load(fingerprint, FOLDER_MODEL_CACHE_TAG) : null;
            if (cached != null) {
                LOCAL_CACHE_HITS.incrementAndGet();
                return cached;
            }
            LOCAL_CACHE_MISSES.incrementAndGet();
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(src.folder())) {
                RawYsmModel raw = deserializer.deserialize();
                if (raw != null && fingerprint != null) {
                    ModelParseCache.store(fingerprint, FOLDER_MODEL_CACHE_TAG, raw);
                }
                return raw;
            } catch (Throwable t) {
                YesSteveModel.LOGGER.warn("[YSM] Local folder model failed: " + src.folder(), t);
                return null;
            }
        }

        try {
            byte[] raw = Files.readAllBytes(src.file());
            int crypto = YesModelUtils.getYsmCryptoVersion(raw);

            // 解析结果磁盘缓存。
            //
            // 反序列化是整个启动流程里最贵的一步（46 个模型实测约 8-10 秒），
            // 而结果只存在于内存。这里按"原始文件字节 + crypto 版本"做键，
            // 命中时直接读回 RawYsmModel，跳过解密+反序列化。
            //
            // 键用 crypto 而不是固定 32：v2 走 YSMFolderDeserializer、
            // v3 走 YSMBinaryDeserializer，两条路径的产物结构不同，必须分开。
            RawYsmModel cached = ModelParseCache.load(raw, crypto);
            if (cached != null) {
                LOCAL_CACHE_HITS.incrementAndGet();
                return cached;
            }
            LOCAL_CACHE_MISSES.incrementAndGet();

            RawYsmModel rawModel;
            if (crypto == 1 || crypto == 2) {
                // 旧版加密（YSGP v1/v2）：容器里是一堆文件名 -> 字节
                Map<String, byte[]> input = YesModelUtils.input(raw);
                if (input.isEmpty()) {
                    YesSteveModel.LOGGER.warn("[YSM] Local legacy model unreadable: " + src.modelId());
                    return null;
                }
                try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(input)) {
                    rawModel = deserializer.deserialize();
                }
            } else if (crypto == 3) {
                // 新版二进制加密（YSGP v3）
                // 解密结果自带 format 头，必须用单参数构造让它自己读，
                // 不能像客户端缓存那样硬编码 32（缓存里 format 已被剥离）。
                byte[] decrypted = YsmCrypt.decryptYsmFile(raw);
                try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                    rawModel = deserializer.deserializeKeepOpen();
                    // footer 的字段布局随 format 变化（format<9 没有、>26 多一个版本号、
                    // >=24 多一个 int），必须交给官方方法解析，不能手工读。
                    deserializer.parseYSMFooter(rawModel);
                }
            } else {
                YesSteveModel.LOGGER.warn("[YSM] Unknown .ysm crypto version for " + src.modelId());
                return null;
            }
            ModelParseCache.store(raw, crypto, rawModel);
            return rawModel;
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[YSM] Local file model failed: " + src.modelId(), t);
            return null;
        }
    }



    /**
     * 文件夹模型的缓存标签。
     *
     * <p>与 .ysm 文件用的 crypto 版本（1/2/3）区分开，避免两条路径的缓存互相污染。
     */
    private static final int FOLDER_MODEL_CACHE_TAG = 0x59464F4C; // "YFOL"

    /** 本地扫描各阶段累计耗时（毫秒），用于定位瓶颈。 */
    private static final java.util.concurrent.atomic.AtomicLong LOCAL_PARSE_MS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong LOCAL_ASSEMBLE_MS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong LOCAL_CACHE_HITS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong LOCAL_CACHE_MISSES = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 计算文件夹模型的内容指纹。
     *
     * <p>由目录内所有文件的"相对路径 + 大小 + 修改时间(毫秒)"拼接后取 SHA-256。
     * 任一文件被增删改，指纹都会变化，从而让旧缓存自动失效。
     *
     * <p>不用文件内容做哈希：内置模型 61 MB，全量读一遍比反序列化还慢。
     * 大小+时间戳足以捕捉常规编辑（用户手动改文件时 mtime 一定变）。
     *
     * @return 指纹字节；目录不可读时返回 null（调用方跳过缓存）
     */
    @Nullable
    private static byte[] fingerprintFolder(Path dir) {
        try {
            StringBuilder sb = new StringBuilder(256);
            try (var s = Files.walk(dir)) {
                List<Path> files = s.filter(Files::isRegularFile)
                        .sorted()
                        .toList();
                for (Path p : files) {
                    sb.append(dir.relativize(p).toString().replace('\\', '/'))
                            .append('|')
                            .append(Files.size(p))
                            .append('|')
                            .append(Files.getLastModifiedTime(p).toMillis())
                            .append('\n');
                }
            }
            if (sb.length() == 0) {
                return null;
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable t) {
            YesSteveModel.LOGGER.debug("[YSM] Failed to fingerprint folder: {}", dir);
            return null;
        }
    }

    /**
     * 把本地玩家上次选中的模型提到最前面。
     *
     * <p>大量模型（几十上百个）时解析要几十秒，用户进世界后最先想看的是
     * "自己的模型"。解析队列按顺序消费，提前就能先拿到它。
     *
     * <p>匹配时忽略大小写和 {@code .ysm} 后缀：选择记录里存的是文件 id
     * （如 {@code Trissy_特莉丝.ysm}），而扫描得到的 modelId 也是相对路径，
     * 但历史版本可能存在后缀差异。
     */
    private static void prioritizeSelectedModel(java.util.List<ModelSource> sources) {
        if (sources.size() < 2) {
            return;
        }
        try {
            String selected = LocalModelSelection.getModelId();
            if (selected == null || selected.isEmpty()) {
                return;
            }
            String target = selected.toLowerCase();
            String targetNoExt = target.endsWith(".ysm")
                    ? target.substring(0, target.length() - 4) : target;
            for (int i = 0; i < sources.size(); i++) {
                String id = sources.get(i).modelId().toLowerCase();
                String idNoExt = id.endsWith(".ysm") ? id.substring(0, id.length() - 4) : id;
                if (id.equals(target) || idNoExt.equals(targetNoExt)) {
                    if (i > 0) {
                        sources.add(0, sources.remove(i));
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 优先级只是优化，失败不影响正确性
        }
    }

    /** 本地模型（纯客户端模式加载的那些）的 id 集合。 */
    public static Set<String> getLocalModelIds() {
        return java.util.Collections.unmodifiableSet(localModelIds);
    }

    /** 本地扫描是否已完成（模型已可用）。 */
    public static boolean isLocalModelsLoaded() {
        return localModelsReady;
    }

    /** 本地扫描的失败原因；null 表示无错误。 */
    public static String getLocalScanError() {
        return localScanError;
    }

    /**
     * 注册"本地模型就绪"回调。
     *
     * <p>若扫描已完成则立即执行；否则排队，等扫描结束后统一触发。
     * 支持多个订阅者（内部是列表，不会互相覆盖）。
     *
     * <p>回调在渲染线程执行。
     */
    public static void whenLocalModelsReady(Runnable callback) {
        if (callback == null) {
            return;
        }
        if (localModelsReady) {
            try {
                callback.run();
            } catch (Throwable t) {
                YesSteveModel.LOGGER.error("[YSM] localModelsReady callback failed", t);
            }
            return;
        }
        localModelsReadyCallbacks.add(callback);
    }

    /**
     * 是否已经装配过 primary（default）模型。
     *
     * <p>用原子标志而不是 {@code ModelAssemblyFactory.isPrimaryReady()}：后者在
     * 装配完成后才为 true，而本方法要防止的是"装配开始前的并发重入"。
     */
    private static final java.util.concurrent.atomic.AtomicBoolean defaultModelRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void loadDefaultModel() {
        // 幂等守卫。
        //
        // 原实现没有守卫，而它在三处被调用：客户端初始化、resetClientState()
        // （断线/换维度都会触发）、getLocalModelContext() 兜底。重复调用会重新
        // 构造 pendingModelCallback，把本地扫描线程正在等待的那个回调顶掉，
        // 表现为"偶发加载不出模型"。
        if (!defaultModelRequested.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.debug("[YSM] Default model already requested, skip.");
            return;
        }
        // 初始化缓存目录：
        //   cache/textures —— 贴图处理（解码 + PNG 重编码）结果，启动加载的主要瓶颈
        //   cache/parsed   —— 模型反序列化结果，跳过重复解析
        try {
            TextureCache.setCacheDir(ServerModelManager.FOLDER.resolve("cache").resolve("textures"));
            ModelParseCache.setCacheDir(
                    ServerModelManager.FOLDER.resolve("cache").resolve("parsed"));
            TextureCache.resetStats();
            // 贴图缓存随模型更新会持续增长，启动时按体积上限清理一次
            TextureCache.trimIfOversized();
        } catch (Throwable ignored) {
            // 缓存不可用不影响正常加载
        }
        YesSteveModel.LOGGER.info("[YSM] Loading builtin default model...");
        try {
            String resourcePath = "/assets/yes_steve_model/builtin/default";
            URL resourceUrl = YesSteveModel.class.getResource(resourcePath);
            if (resourceUrl == null) {
                YesSteveModel.LOGGER.error("[YSM] Builtin default model not found in classpath: " + resourcePath);
                return;
            }
            URI uri = resourceUrl.toURI();
            Path defaultPath;
            FileSystem jarFs = null;
            if ("jar".equals(uri.getScheme())) {
                try {
                    jarFs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                defaultPath = jarFs.getPath(resourcePath);
            } else {
                defaultPath = Paths.get(uri);
            }

            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(defaultPath)) {
                RawYsmModel rawModel = deserializer.deserialize();

                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, "default");


                onModelDataReceived(parsedBundle, "default", true, false);
                YesSteveModel.LOGGER.info("[YSM] Successfully pushed Default Model to render queue.");
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[YSM] Failed to dispatch Default Model", e);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[YSM] Failed to load builtin default model", e);
        }
    }

    private static void processServerData(ByteBuffer data) {
        if (data == null) {
            resetClientState();
            return;
        }
        try {
            if (!data.hasRemaining() && data.position() > 0) {
                data.flip();
            }
            if (!data.hasRemaining()) return;

            byte[] packetBytes = new byte[data.remaining()];
            data.get(packetBytes);

            byte[] decrypted;
            if (syncStep == 1) {
                decrypted = YsmCrypt.decrypt(packetBytes, YsmCrypt.publicKey);
                System.out.println(Arrays.toString(decrypted));
                if (decrypted != null) handlePacket01(decrypted);
            } else if (syncStep == 2) {
                decrypted = YsmCrypt.decrypt(packetBytes, lastKey);
                if (decrypted != null) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        handlePacket03(buf);
                    }
                }
            } else if (syncStep == 3) {
                decrypted = YsmCrypt.decrypt(packetBytes, key1);
                if (decrypted != null) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        handlePacket05(buf);
                    }
                }
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[YSM] Sync Error at step " + syncStep, e);
        }
    }

    private static void handlePacket01(byte[] decryptedBuffer) throws Exception {
        key1 = new byte[56];
        System.arraycopy(decryptedBuffer, decryptedBuffer.length - 56, key1, 0, 56);
        syncStep = 2;

        YesSteveModel.LOGGER.info("[YSM] Exchanged Key1. Preparing to send Packet 02.");
        onSyncProgress(-1); // Preparing GUI stage

        int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x02);
            outBuf.getRawBuf().writeByte(0x00);

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, true);
            lastKey = result.nextKey();

            sendModelFile(ByteBuffer.wrap(result.data()));
        }
    }

    private record ModelHash(long hash1, long hash2) {
    }

    private static final List<ModelHash> cachedModelHashes = new ArrayList<>();

    private static void handlePacket03(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt(); // expect 3
        long folderHash = buf.readVarLong();
        currentCacheFolderName = Long.toHexString(folderHash);

        serverKey = new byte[56];
        buf.getRawBuf().readBytes(serverKey);

        clientKey = new byte[56];
        buf.getRawBuf().readBytes(clientKey);

        File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(currentCacheFolderName).toFile();
        if (!cacheDir.exists()) cacheDir.mkdirs();

        Map<UUID, File> localCacheMap = YSMClientCache.buildCacheIndex(cacheDir, clientKey);
        List<ModelHash> modelsToRequest = new ArrayList<>();

        int unkSize = buf.readVarInt();
        onSyncProgress(unkSize);

        // 只有真的收到模型条目才算"服务器提供了模型"。
        // 服务端没装 YSM 时 unkSize 为 0，此时仍要允许本地扫描兜底。
        if (unkSize > 0) {
            markServerModelsReceived();
        }

        Set<String> validServerModelIds = new HashSet<>();
        List<String> previousModelIds = new ArrayList<>();
        List<String> updatedModelIds = new ArrayList<>();
        List<Boolean> isModelReadyList = new ArrayList<>();

        for (int i = 0; i < unkSize; i++) {
            long hash1 = buf.readVarLong();
            long hash2 = buf.readVarLong();
            ModelHash mHash = new ModelHash(hash1, hash2);
            cachedModelHashes.add(mHash);

            String modelId = buf.readString();
            boolean isAuth = buf.readVarInt() == 1;// isAuth
            int isCustomSkinModel = buf.readVarInt();// is misc/2_steve misc/1_alex
//            System.out.println("Received model hash: " + mHash + ", id: " + modelId + ", unk1: " + isAuth + ", unk2: " + isCustomSkinModel);
            int version = buf.readVarInt(); // 对于文件夹未加密的模型，为65535

            ServerModelContext ctx = new ServerModelContext(hash1, hash2, modelId, isAuth, isCustomSkinModel, version);
            serverModels.put(ctx.uuid, ctx);
            validServerModelIds.add(modelId);

            File cachedFile = localCacheMap.get(ctx.uuid);
            boolean isFileValid = YSMClientCache.verifyFileContent(cachedFile, hash1, hash2);

            boolean alreadyInMemory = modelAssemblyMap != null && modelAssemblyMap.containsKey(modelId);

            if (isFileValid) {
                YesSteveModel.LOGGER.info("[YSM] Cache HIT & Validated: " + ctx.uuid);
                if (alreadyInMemory) {
                    previousModelIds.add(modelId);
                    updatedModelIds.add(modelId);
                    isModelReadyList.add(isAuth);
                } else {
                    // 命中缓存
                    modelPhraseExecutor.submit(() -> {
                        if (clientKey == null) return;
                        try {
                            byte[] fileBytes = Files.readAllBytes(cachedFile.toPath());
                            byte[] decompressed = YsmCrypt.read(fileBytes, clientKey);
                            parseAndLoadModel(decompressed, modelId, isAuth);
                        } catch (Exception e) {
                            YesSteveModel.LOGGER.error("[YSM] Failed to parse and load cached model: " + modelId, e);
                        }
                    });
                }
            } else {
                YesSteveModel.LOGGER.info("[YSM] Cache MISS or Invalid: " + ctx.uuid + " -> Requesting...");
                modelsToRequest.add(mHash);
            }
        }

        int unkSize2 = buf.readVarInt();
        List<ModelPackData> parsedPacks = new ArrayList<>();

        for (int i = 0; i < unkSize2; i++) {
            String folderPath = buf.readString();

            OuterFileTexture iconTexture = null;
            if (buf.readVarInt() != 0) {
                byte[] textureData = buf.readByteArray();
                int textureWidth = buf.readVarInt();
                int textureHeight = buf.readVarInt();
                int imageFormat = buf.readVarInt();
                int unkImageData = buf.readVarInt();

                byte[] png = YSMClientMapper.toPng(textureData, imageFormat, textureWidth, textureHeight);

                iconTexture = new OuterFileTexture(png);
            }

            String folderName = "";
            String folderDesc = "";
            int hasYSMPackInfo = buf.readVarInt();
            if (hasYSMPackInfo != 0) {
                folderName = buf.readString();
                folderDesc = buf.readString();
            }

            Map<String, Map<String, String>> languageData = new HashMap<>();
            int languageSize = buf.readVarInt();
            for (int j = 0; j < languageSize; j++) {
                String languageType = buf.readString();
                int translateKeySize = buf.readVarInt();
                Map<String, String> translationMap = new HashMap<>();
                for (int k = 0; k < translateKeySize; k++) {
                    translationMap.put(buf.readString(), buf.readString());
                }
                languageData.put(languageType, translationMap);
            }
            parsedPacks.add(new ModelPackData(folderPath, folderName, folderDesc, iconTexture, languageData));
        }

        if (!parsedPacks.isEmpty()) {
            onModelPacksReceived(parsedPacks.toArray(new ModelPackData[0]));
        }

        List<String> modelsToRemove = new ArrayList<>();
        if (modelAssemblyMap != null) {
            for (String loadedId : modelAssemblyMap.keySet()) {
                if ("default".equals(loadedId)) continue;

                boolean isLocal = localModelIds.contains(loadedId);
                if (isLocal) {
                    // 本地扫描出来的模型不在服务端列表里，正常不该被删。
                    // 但如果服务端这次确实下发了同名模型（用户把它也放到了服务器上，
                    // 或服务端版本更新），就要让服务端版本接管：把它加入待移除列表，
                    // 由后面的重新加载流程用新装配体替换。否则本地这份会永久占位，
                    // 用户更新 .ysm 文件后必须重启才生效。
                    if (!validServerModelIds.contains(loadedId)) {
                        continue;
                    }
                    YesSteveModel.LOGGER.info("[YSM] Server now provides local model, replacing: " + loadedId);
                    modelsToRemove.add(loadedId);
                    continue;
                }

                if (!validServerModelIds.contains(loadedId)) {
                    modelsToRemove.add(loadedId);
                } else if (modelsToRequest.stream().anyMatch(h -> serverModels.containsKey(new UUID(h.hash1, h.hash2)) && serverModels.get(new UUID(h.hash1, h.hash2)).modelId.equals(loadedId))) {
                    modelsToRemove.add(loadedId);
                }
            }
        }

        if (!modelsToRemove.isEmpty() || !previousModelIds.isEmpty()) {
            boolean[] readyArr = new boolean[isModelReadyList.size()];
            for (int j = 0; j < isModelReadyList.size(); j++) {
                readyArr[j] = isModelReadyList.get(j);
            }

            onModelContextsUpdated(
                    modelsToRemove.isEmpty() ? null : modelsToRemove.toArray(new String[0]),
                    previousModelIds.isEmpty() ? null : previousModelIds.toArray(new String[0]),
                    updatedModelIds.isEmpty() ? null : updatedModelIds.toArray(new String[0]),
                    readyArr
            );
            YesSteveModel.LOGGER.info("[YSM] Cleaned up {} outdated models and updated {} existing models during sync.", modelsToRemove.size(), previousModelIds.size());
        }

        syncStep = 3;
        pendingModelsCount.set(modelsToRequest.size());

        int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x04);

            outBuf.writeVarInt(modelsToRequest.size());
            for (ModelHash h : modelsToRequest) {
                outBuf.writeVarLong(h.hash1);
                outBuf.writeVarLong(h.hash2);
            }

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, false);
            sendModelFile(ByteBuffer.wrap(result.data()));
        }

        if (pendingModelsCount.get() == 0) {
            modelPhraseExecutor.submit(() -> {
                YesSteveModel.LOGGER.info("[YSM] All models loaded from local cache. Handshake complete!");
                onSyncComplete();
            });
        }
    }

    private static void handlePacket05(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt();
        if (type != 5) return;

        long hash1 = buf.readVarLong();
        long hash2 = buf.readVarLong();
        UUID uuid = new UUID(hash1, hash2);

        ServerModelContext ctx = serverModels.get(uuid);
        if (ctx == null) {
            YesSteveModel.LOGGER.warn("[YSM] Received unexpected file chunk for model: " + uuid);
            return;
        }

        int totalSize = buf.readVarInt();
        int chunkOffset = buf.readVarInt();
        int chunkLength = buf.readVarInt();

        // 首次接收时初始化缓冲区
        if (ctx.fileBuffer == null) {
            ctx.fileBuffer = new byte[totalSize];
            ctx.totalSize = totalSize;
            ctx.bytesReceived = 0;
        }

        buf.getRawBuf().readBytes(ctx.fileBuffer, chunkOffset, chunkLength);
        ctx.bytesReceived += chunkLength;

        if (ctx.bytesReceived >= totalSize) {
            byte[] fileBuffer = ctx.fileBuffer;

            modelPhraseExecutor.submit(() -> {
                if (clientKey == null) return;
                try {
                    String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                    File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    byte[] cachedFileData = YsmCrypt.transcodeServerDataToClientCache(fileBuffer, serverKey, clientKey, hash1, hash2);

                    String legitFileName = YSMClientCache.generateCacheFileName(hash1, hash2, clientKey);
                    File outFile = new File(cacheDir, legitFileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(cachedFileData);
                    }

                    YesSteveModel.LOGGER.info("[YSM] Downloaded & Cached: " + outFile.getAbsolutePath());
                    byte[] decompressed = YsmCrypt.read(cachedFileData, clientKey);

                    parseAndLoadModel(decompressed, ctx.modelId, ctx.isAuth);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[YSM] Failed to save/parse downloaded model: " + ctx.modelId, e);
                } finally {
                    if (pendingModelsCount.decrementAndGet() <= 0) {
                        YesSteveModel.LOGGER.info("[YSM] All missing models downloaded and loaded successfully!");
                        onSyncComplete();
                    }
                }
            });
        }
    }


    private static void parseAndLoadModel(byte[] decompressed, String modelId, boolean isAuth) {
        try {
//            if (true) return;
            // IR

            // 26.x：改为走带磁盘缓存的解析入口。原始实现每次启动都完整反序列化全部模型
            // （实测约 19 秒），而解析结果只存在于内存。缓存命中时可直接跳过反序列化。
            //
            // footer 的补充字段与 reader 偏移耦合，属于解析过程的一部分，
            // 因此一并纳入缓存，不再单独从 reader 读取。
            RawYsmModel rawModel = ModelParseCache.load(decompressed, 32);
            if (rawModel == null) {
                try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decompressed, 32)) {
                    rawModel = deserializer.deserializeKeepOpen();
                    YSMByteBuf reader = deserializer.getReader();

                    // footer 字段与 reader 偏移耦合，属于解析过程的一部分，一并写入缓存
                    rawModel.footer.version = reader.readVarInt();
                    rawModel.footer.unkInt1 = reader.readVarInt();
                    if (rawModel.footer.unkInt1 != 0) {
                        rawModel.footer.rand = reader.readString();
                    }
                    rawModel.footer.time = reader.readVarLong();
                    if (rawModel.footer.unkInt1 != 0) {
                        rawModel.footer.extra = reader.readString();
                        rawModel.footer.unkInt2 = reader.readVarInt();
                    }
                }
                ModelParseCache.store(decompressed, 32, rawModel);
            }
            if (rawModel == null) {
                YesSteveModel.LOGGER.warn("[YSM] Empty model payload for " + modelId);
                return;
            }

            // 组装到客户端模型
            ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
            onModelDataReceived(parsedBundle, modelId, false, isAuth);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[YSM] Failed to parse and load model: " + modelId, e);
        }
    }

    private static OrderedStringMap<String, OuterFileTexture> toOrderedTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures == null || textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        return new OrderedStringMap<>(
                textures.keySet().toArray(new String[0]),
                textures.values().toArray(new OuterFileTexture[0])
        );
    }

    private static void resetClientState() {
        syncStep = 1;
        key1 = null;
        lastKey = null;
        serverKey = null;
        clientKey = null;

        // 断开连接：下一次进世界要重新判断"服务器装没装 YSM"，
        // 因此把同步标记和调度标记都复位。本地模型本身保留（重连后仍可用）。
        serverSyncReceived = false;
        localScanScheduled.set(false);

        modelPhraseExecutor.getQueue().clear();

        currentCacheFolderName = null;
        pendingModelsCount.set(0);
        cachedModelHashes.clear();

        serverModels.clear();

        Map<String, ModelPackData> oldPreviews = modelPackMap;
        if (oldPreviews != null && !oldPreviews.isEmpty()) {
            for (ModelPackData preview : oldPreviews.values()) {
                if (preview.getTexture() != null) {
                    Identifier loc = FileTypeUtil.getPackIconLocation(preview.getPath());
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().getTextureManager().release(loc);
                    });
                }
            }
        }

        modelPackMap = new Object2ReferenceOpenHashMap<>();
        localModelContext = null;
        defaultTexture = null;
        pendingModelCallback = null;
        pendingModelQueue.clear();
        loadDefaultModel();

        forEachGuiWidget(l -> {
            try {
                l.onSyncBegin();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static SyncStatus getSyncStatus() {
        RenderSystem.assertOnRenderThread();
        return syncState;
    }

    public static Map<String, ModelAssembly> getModelAssemblyMap() {
        return modelAssemblyMap;
    }

    public static Map<String, ModelPackData> getModelPackMap() {
        return modelPackMap;
    }

    public static Optional<ModelAssembly> getModelContext(String str) {
        return Optional.ofNullable(modelAssemblyMap.get(str));
    }

    public static ModelAssembly getLocalModelContext() {
        runPendingModelCallback();
        flushPendingModels();

        ModelAssembly model = localModelContext;
        if (model != null) return model;

        // 触发预加载
        loadDefaultModel();
        model = localModelContext;
        if (model != null) return model;

        Map<String, ModelAssembly> reg = modelAssemblyMap;
        if (reg != null && !reg.isEmpty()) {
            model = reg.get("default");
            if (model == null) {
                for (ModelAssembly v : reg.values()) {
                    if (v != null) {
                        model = v;
                        break;
                    }
                }
            }
            if (model != null) {
                localModelContext = model;
                return model;
            }
        }
        return null;
    }

    public static Identifier getDefaultTexture() {
        return defaultTexture.getResourceLocation().get();
    }

    public static <T extends IGuiWidget> T registerGuiWidget(T t) {
        guiWidgets.put(t, null);
        return t;
    }

    public static void unregisterGuiWidget(IGuiWidget guiWidget) {
        guiWidgets.remove(guiWidget, null);
    }

    private static void forEachGuiWidget(Consumer<IGuiWidget> consumer) {
        Iterator<IGuiWidget> it = guiWidgets.keySet().iterator();
        while (it.hasNext()) {
            try {
                consumer.accept(it.next());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    public static void resetSync() {
        isOysmServer = false;
        allowUpload = false;
        processServerData(null);
        NetworkHandler.resetClientHandshake();
        Minecraft.getInstance().execute(() -> {
            // 纯客户端构建下没有服务端同步，重置后也应回到 IDLE 而不是 WAITING，
            // 否则断线重连后顶部又会挂上"等待同步"。
            syncState.setState(BuildFlags.CLIENT_ONLY ? SyncState.IDLE : SyncState.WAITING);
        });
    }

    public static boolean isAllowUpload() {
        return allowUpload;
    }

    public static boolean isOysmServer() {
        return isOysmServer;
    }

    private static void sendModelFile(ByteBuffer byteBuffer) {
        if (Minecraft.getInstance().player != null) {
            try {
                NetworkHandler.sendToServer(new C2SModelSyncPayload(byteBuffer));
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        Connection connection = serverConnection;
        if (!connection.isConnected()) {
            return;
        }
        try {
            connection.send(NetworkHandler.toServerboundPacket(new C2SModelSyncPayload(byteBuffer)));
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static void startSync(Connection connection, ByteBuffer byteBuffer) {
        serverConnection = connection;
        processServerData(byteBuffer);
    }

    /**
     * 标记"服务端确实下发了模型清单"。
     *
     * <p>不能只用 {@link #startSync} 判定：那是客户端收到第一个同步包就触发，
     * 而握手是双向的——即使服务端没有模型可发，客户端也会先发出 Packet 02。
     * 只有真正解析到模型条目（{@code unkSize > 0}）才算"服务器装了 YSM 且有模型"。
     */
    private static void markServerModelsReceived() {
        if (!serverSyncReceived) {
            serverSyncReceived = true;
            YesSteveModel.LOGGER.info("[YSM] Server model list received, local scan not needed.");
        }
    }

    public static void onSyncConnected() {
        if (Minecraft.getInstance().isLocalServer()) {
            syncState.setState(SyncState.LOADING);
        } else {
            syncState.setState(SyncState.IDLE);
        }
        forEachGuiWidget(IGuiWidget::onSyncBegin);
    }

    private static void onSyncProgress(int totalModels) {
        if (totalModels == -1) {
            Minecraft.getInstance().execute(() -> {
                syncState.setState(SyncState.PREPARING);
                forEachGuiWidget(IGuiWidget::onSyncError);
            });
        } else {
            Minecraft.getInstance().execute(() -> {
                if (totalModels > 0) {
                    syncState.startSyncing(totalModels);
                } else {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> guiWidget.onSyncProgress(totalModels, 0));
            });
        }
    }

    private static void onModelPacksReceived(ModelPackData[] packDataArr) {
        Object2ReferenceOpenHashMap<String, ModelPackData> newPackMap = new Object2ReferenceOpenHashMap<>();

        for (ModelPackData packData : packDataArr) {
            if (StringUtils.isBlank(packData.getName())) {
                packData = new ModelPackData(packData.getPath(), FileTypeUtil.getFinalPathSegment(packData.getPath()), packData.getDescription(), packData.getTexture(), packData.getTranslations());
            }
            newPackMap.put(packData.getPath(), packData);
            OuterFileTexture iconTexture = packData.getTexture();
            if (iconTexture != null) {
                Identifier location2 = FileTypeUtil.getPackIconLocation(packData.getPath());
                Minecraft.getInstance().submit(() -> {
                    Minecraft.getInstance().getTextureManager().register(location2, iconTexture);
                    iconTexture.load();
                });
            }
        }

        for (ModelPackData packData : modelPackMap.values()) {
            if (!newPackMap.containsKey(packData.getPath()) && packData.getTexture() != null) {
                Identifier location = FileTypeUtil.getPackIconLocation(packData.getPath());
                Minecraft.getInstance().submit(() -> Minecraft.getInstance().getTextureManager().release(location));
            }
        }
        modelPackMap = newPackMap;
    }

    private static void onModelContextsUpdated(String[] removedModelIds, String[] previousModelIds, String[] updatedModelIds, boolean[] isModelReady) {
        Minecraft.getInstance().execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            if (removedModelIds != null) {
                ArrayList<ModelAssembly> removed = new ArrayList<>(removedModelIds.length);
                for (String str : removedModelIds) {
                    ModelAssembly assembly = map.remove(str);
                    // 被移除的模型不再由本地维护，从本地集合里剔除，
                    // 否则后续 localModelIds 会一直留着已失效的 id。
                    localModelIds.remove(str);
                    if (assembly != null) {
                        removed.add(assembly);
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    for (ModelAssembly assembly : removed) {
                        for (AbstractTexture tex : assembly.getTextures())
                            UploadManager.removeTexture(tex);
                        if (NativeLibLoader.isLoaded()) {
                            for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                                entry.getValue().getModel().freeNativeCache();
                            }
                            for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                                entry.getValue().getModel().freeNativeCache();
                            }
                            assembly.getAnimationBundle().getMainModel().freeNativeCache();
                            assembly.getAnimationBundle().getArmModel().freeNativeCache();
                        }
                    }
                });
            }
            if (previousModelIds != null) {
                ModelAssembly[] modelAssemblies = new ModelAssembly[previousModelIds.length];
                for (int i = 0; i < previousModelIds.length; i++) {
                    modelAssemblies[i] = map.remove(previousModelIds[i]);
                }
                for (int i = 0; i < modelAssemblies.length; i++) {
                    ModelAssembly modelAssembly = modelAssemblies[i];
                    if (modelAssembly != null) {
                        modelAssembly.getTextureRegistry().setAuthModel(isModelReady[i]);
                        map.put(updatedModelIds[i], modelAssembly);
                    }
                }
            }
            modelAssemblyMap = map;
            if ((removedModelIds != null && removedModelIds.length > 0) || (previousModelIds != null && previousModelIds.length > 0)) {
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onModelsLoaded(map);
                });
            }
        });
    }

    private static void onModelDataReceived(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) throws Exception {
        if (isPrimary) {
            pendingModelCallback = () -> {
                processModelData(parsedBundle, modelId, true, false);
            };
        } else {
            runPendingModelCallback();
            processModelData(parsedBundle, modelId, false, isAuth);
        }
    }

    public static void runPendingModelCallback() {
        Runnable runnable = pendingModelCallback;
        if (runnable != null) {
            synchronized (runnable) {
                Runnable runnable2 = pendingModelCallback;
                if (runnable2 != null) {
                    runnable2.run();
                    pendingModelCallback = null;
                }
            }
        }
    }

    public static void processModelData(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        if (parsedBundle != null) {
            try {
                ModelAssembly runtimeModel = ModelAssemblyFactory.buildAssembly(parsedBundle, isPrimary, isAuth);
                pendingModelQueue.add(Pair.of(runtimeModel, modelId));
                if (isPrimary) {
                    localModelContext = runtimeModel;

                    Minecraft.getInstance().execute(() -> {
                        defaultTexture = UploadManager.getOrCreateLocatable(runtimeModel.getAnimationBundle().getTextures().getValueAt(0), true);
                    });
                    return;
                }
            } catch (Exception e) {
                if (isPrimary) throw e;
                YesSteveModel.LOGGER.error(
                        new StringFormattedMessage("Failed to process {}", modelId), e);
                return;
            }
        }
        Minecraft.getInstance().execute(() -> {
            if (syncState.currentState == SyncState.SYNCING) {
                syncState.syncedModels++;
                int loaded = syncState.syncedModels;
                if (loaded == syncState.totalModels) {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onSyncProgress(syncState.getTotalModels(), loaded);
                });
            }
        });
    }

    private static void onSyncComplete() {
        syncStep = 1;
        serverModels.clear();
        cachedModelHashes.clear();

        Minecraft.getInstance().execute(() -> {
            syncState.setState(SyncState.IDLE);
            forEachGuiWidget(IGuiWidget::onSyncComplete);
        });
    }

    public static void setAllowUpload(boolean allowUpload) {
        ClientModelManager.allowUpload = allowUpload;
    }

    public static void setOysmServer(boolean isOysmServer) {
        ClientModelManager.isOysmServer = isOysmServer;
    }

    private static void onSyncError(@Nullable Object obj) {
        Minecraft.getInstance().execute(() -> {
            syncState.setState(SyncState.IDLE);
            forEachGuiWidget(guiWidget -> {
                guiWidget.onSyncMessage(obj == null ? null : (Component) obj);
            });
            if (obj instanceof Component component) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(component);
                }
                YesSteveModel.LOGGER.error(component.getString(256));
            }
        });
    }

    public static void flushPendingModels() {
        if (pendingModelQueue.isEmpty())
            return;

        Object2ReferenceOpenHashMap<String, ModelAssembly> object2ReferenceOpenHashMap = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        while (true) {
            Pair<ModelAssembly, String> pairPoll = pendingModelQueue.poll();
            if (pairPoll != null) {
                object2ReferenceOpenHashMap.put(pairPoll.getRight(), pairPoll.getLeft());
            } else {
                modelAssemblyMap = object2ReferenceOpenHashMap;
                forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(object2ReferenceOpenHashMap));
                return;
            }
        }
    }

    public static int getPendingModelCount() {
        return pendingModelQueue.size();
    }

    public static class SyncStatus {
        /**
         * 初始状态。
         *
         * <p>原版是 {@code WAITING}（等服务器握手），只有 {@code onSyncConnected()}
         * 才会把它推进到 {@code LOADING}/{@code IDLE}。纯客户端构建下服务端事件
         * 已被跳过，那个回调永远不会来，状态就永久停在 {@code WAITING}，
         * 于是 HUD 顶部一直挂着"等待同步"的提示。
         *
         * <p>纯客户端模式下没有服务端同步这回事，直接从 {@code IDLE} 开始。
         */
        private SyncState currentState = BuildFlags.CLIENT_ONLY ? SyncState.IDLE : SyncState.WAITING;

        private int totalModels = -1;

        private int syncedModels = -1;

        public SyncState getCurrentState() {
            return this.currentState;
        }

        public int getSyncedModels() {
            return this.syncedModels;
        }

        public int getTotalModels() {
            return this.totalModels;
        }

        public void setState(SyncState syncState) {
            System.out.println("Sync state: " + syncState);
            this.currentState = syncState;
            this.totalModels = -1;
            this.syncedModels = -1;
        }

        public void startSyncing(int totalModels) {
            this.currentState = SyncState.SYNCING;
            this.totalModels = totalModels;
            this.syncedModels = 0;
        }
    }

    public static void exportAllCachedModels(@Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                if (clientKey == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("未连接到服务器或尚未完成握手同步，无法获取客户端解密密钥。"), "", "", 0));
                    }
                    return;
                }

                String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();

                if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("尚未生成任何缓存或缓存文件夹不存在: " + folder), "", "", 0));
                    }
                    return;
                }

                File[] files = cacheDir.listFiles();
                if (files == null || files.length == 0) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("缓存文件夹中没有任何模型可供导出。"), "", "", 0));
                    }
                    return;
                }

                int successCount = 0;
                for (File file : files) {
                    if (!file.isFile()) continue;

                    try {
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        byte[] clearText = YsmCrypt.read(fileBytes, clientKey);

                        int coreDataLength;
                        String exportName = file.getName(); // Fallback name

                        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(clearText, 32)) {
                            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                            coreDataLength = deserializer.getReader().getRawBuf().readerIndex();

                            if (rawModel.metadata != null && rawModel.metadata.name != null && !rawModel.metadata.name.trim().isEmpty()) {
                                exportName = rawModel.metadata.name.trim();
                            } else if (rawModel.properties != null && rawModel.properties.sha256 != null && !rawModel.properties.sha256.isEmpty()) {
                                exportName = rawModel.properties.sha256;
                            }
                        }

                        exportName = exportName.replaceAll("[\\\\/:*?\"<>|]", "_");

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeDword(32);

                            outBuf.getRawBuf().writeBytes(clearText, 0, coreDataLength);

                            outBuf.writeVarInt(32); // Version
                            outBuf.writeVarInt(1);

                            byte[] randBytes = new byte[8];
                            SECURE_RANDOM.nextBytes(randBytes);
                            StringBuilder sb = new StringBuilder(16);
                            for (byte b : randBytes) {
                                sb.append(String.format("%02x", b));
                            }
                            outBuf.writeString(sb.toString()); // rand hash

                            outBuf.writeVarLong(java.time.Instant.now().getEpochSecond()); // time
                            outBuf.writeString(extra != null ? extra : ""); // extra info
                            outBuf.writeVarInt(0); // padding

                            byte[] rawBytes = new byte[outBuf.getRawBuf().readableBytes()];
                            outBuf.getRawBuf().readBytes(rawBytes);

                            byte[] finalEncrypted = YsmCrypt.encryptYsmFile(rawBytes);

                            Path exportPath = ServerModelManager.EXPORT.resolve(exportName + ".ysm");
                            Files.createDirectories(exportPath.getParent());
                            Files.write(exportPath, finalEncrypted);

                            successCount++;
                            YesSteveModel.LOGGER.info("[YSM] Successfully exported cached model to: " + exportPath);
                        }
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("[YSM] Failed to export cached model: " + file.getName(), e);
                    }
                }

                if (callback != null) {
                    String displayPath = Paths.get("export").toString();
                    if (successCount > 0) {
                        callback.accept(new ExportResult(true, null, displayPath, "", 0));
                    } else {
                        callback.accept(new ExportResult(false, Component.literal("导出完成，但没有成功导出任何模型。可能是缓存已损坏。"), "", "", 0));
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[YSM] Error during batch export", e);
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("批量导出过程发生严重错误: " + e.getMessage()), "", "", 0));
                }
            }
        });
    }
}
