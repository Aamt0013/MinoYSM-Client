package rip.ysm.api.config;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraftforge.common.ForgeConfigSpec;
import net.neoforged.fml.config.ModConfig;

public final class ConfigRegistration {

    private ConfigRegistration() {
    }

    @ExpectPlatform
    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec) {
        throw new AssertionError();
    }

    /**
     * 指定配置文件名注册。{@code fileName} 不含类型后缀与扩展名，
     * 最终文件为 {@code config/<fileName>-<client|server>.toml}。
     *
     * <p>用途：模组 id 与配置目录名不一致时（本项目的 mod id 是 {@code minoysm}，
     * 但配置目录要保持 {@code yes_steve_model}），用这个重载把文件名固定下来。
     *
     * <p>{@code modId} 必须是真实存在的 Fabric 模组 id —— Forge Config API Port 会在
     * {@code ModConfig} 构造时校验，找不到会抛
     * {@code IllegalArgumentException: No mod with id ...}。
     */
    @ExpectPlatform
    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec, String fileName) {
        throw new AssertionError();
    }
}
