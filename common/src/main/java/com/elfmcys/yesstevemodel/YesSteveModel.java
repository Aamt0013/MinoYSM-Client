package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ModSoundEvents;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.event.YsmEventBootstrap;
import com.elfmcys.yesstevemodel.util.obfuscate.Keep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rip.ysm.api.PlatformAPI;
import rip.ysm.api.config.ConfigRegistration;

import java.io.File;
import java.io.IOException;

/**
 * TODO:
 * 默认模型应该就在模组架加载的时候就预加载了
 * 其它模型统统都是进入世界后加载
 */
public class YesSteveModel {

    /**
     * 资源与配置的命名空间。**刻意保持不变** —— 贴图路径（assets/yes_steve_model/...）、
     * 配置目录（config/yes_steve_model/）、Cardinal Components 标识、数据包标签都依赖它，
     * 改了会让已有用户的模型文件夹和配置文件全部失效。
     */
    public static final String MOD_ID = "yes_steve_model";

    /**
     * Fabric 模组 id。与 {@link #MOD_ID} 分开是必要的：
     * 官方 Yes Steve Model 的模组 id 正是 yes_steve_model，沿用同一个 id 时，
     * 用户只要同时装了官方版就会在启动阶段因 "Duplicate mod id" 直接崩溃，
     * 连错误提示都指向不了真正原因。
     */
    public static final String MOD_CONTAINER_ID = "minoysm";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private YesSteveModel() {
    }

    public static void init() {
        LOGGER.info("Initializing YesSteveModel, platform: " + PlatformAPI.getPlatformName());
        checkConflictingMods();
        try {
            NativeLibLoader.init();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize native lib", e);
        }
        if (!NativeLibLoader.isAvailable()) {
            LOGGER.error(getErrorMessage());
        } else {
            initConfig();
        }
        YsmEventBootstrap.register();
    }

    /**
     * 检测是否有另一个 YSM 系模组同时安装。
     *
     * <p>模组 id 已经和官方版分开了，所以 Fabric 不会再拦下这种组合；但两个模组都会接管
     * 玩家模型渲染，同时启用会重复绘制、互相抢贴图，出问题时很难判断是谁造成的。
     * 这里主动报一条明确的错误，比让用户面对花屏去猜要好。
     */
    private static void checkConflictingMods() {
        if (!Platform.isModLoaded(MOD_ID)) {
            return;
        }
        LOGGER.error("================================================================");
        LOGGER.error("检测到另一个 YSM 系模组同时安装（mod id = {}）。", MOD_ID);
        LOGGER.error("两个模组都会接管玩家模型渲染，同时启用会重复绘制甚至崩溃。");
        LOGGER.error("请从 mods/ 目录移除其中一个后重启游戏。");
        LOGGER.error("================================================================");
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void initConfig() {
        File oldConfig = Platform.getConfigFolder().resolve("yes_steve_model-common.toml").toFile();
        if (oldConfig.isFile()) {
            File file2 = Platform.getConfigFolder().resolve("yes_steve_model-client.toml").toFile();
            if (!file2.isFile()) {
                oldConfig.renameTo(file2);
            } else {
                oldConfig.delete();
            }
        }
        // modId 必须是真实存在的 Fabric 模组 id（minoysm），否则 Forge Config API Port
        // 会在 ModConfig 构造时抛 "No mod with id ..."。
        // 文件名单独指定为 yes_steve_model-<type>.toml，让老用户的配置继续生效。
        // 注意：这里的 fileName 是**完整文件名**（含 .toml），框架不会再拼接 mod id 或类型。
        ConfigRegistration.register(MOD_CONTAINER_ID, ModConfig.Type.CLIENT, GeneralConfig.buildSpec(), legacyConfigName(ModConfig.Type.CLIENT));
        ConfigRegistration.register(MOD_CONTAINER_ID, ModConfig.Type.SERVER, ServerConfig.buildSpec(), legacyConfigName(ModConfig.Type.SERVER));
        if (!PlatformAPI.isServer()) {
            ModSoundEvents.REGISTER.register();
        }
    }

    /**
     * 沿用旧版配置文件名：{@code yes_steve_model-<client|server>.toml}。
     *
     * <p>模组 id 从 {@code yes_steve_model} 改成 {@code minoysm} 后，框架默认会用
     * {@code minoysm-client.toml}，那样老用户的设置就全丢了。这里显式给出旧文件名，
     * 配置文件位置不变，无需迁移。
     */
    private static String legacyConfigName(ModConfig.Type type) {
        return MOD_ID + "-" + type.extension() + ".toml";
    }

    @Keep
    public static boolean isAvailable() {
        return NativeLibLoader.isAvailable();
    }

    public static boolean isOnAndroid() {
        return NativeLibLoader.isOnAndroid();
    }

    @Environment(EnvType.CLIENT)
    public static void sendUnavailableMessage() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.sendSystemMessage(getUnavailableComponent());
        }
    }

    public static Component getUnavailableComponent() {
        return NativeLibLoader.getErrorComponent();
    }

    public static String getErrorMessage() {
        return NativeLibLoader.getErrorMessage();
    }
}
