package com.elfmcys.yesstevemodel.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ExtraPlayerRenderConfig {

    public static ForgeConfigSpec.BooleanValue DISABLE_PLAYER_RENDER;

    public static ForgeConfigSpec.IntValue PLAYER_POS_X;

    public static ForgeConfigSpec.IntValue PLAYER_POS_Y;

    public static ForgeConfigSpec.DoubleValue PLAYER_SCALE;

    public static ForgeConfigSpec.DoubleValue PLAYER_YAW_OFFSET;

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("extra_player_render");
        builder.comment("Whether to display player");
        DISABLE_PLAYER_RENDER = builder.define("DisablePlayerRender", false);
        builder.comment("Player position x in screen");
        PLAYER_POS_X = builder.defineInRange("PlayerPosX", 10, 0, Integer.MAX_VALUE);
        builder.comment("Player position y in screen");
        PLAYER_POS_Y = builder.defineInRange("PlayerPosY", 10, 0, Integer.MAX_VALUE);
        builder.comment("Player scale in screen");
        PLAYER_SCALE = builder.defineInRange("PlayerScale", 40.0d, 8.0d, 360.0d);
        builder.comment("Player yaw offset in screen");
        PLAYER_YAW_OFFSET = builder.defineInRange("PlayerYawOffset", 5.0d, Double.MIN_VALUE, Double.MAX_VALUE);
        builder.pop();
    }

    /**
     * 把当前内存中的配置立即写回磁盘。
     *
     * <p>{@code ForgeConfigSpec.ConfigValue.set()} 只改内存，不会落盘；
     * 持久化依赖 NightConfig 的自动保存在游戏正常退出时触发。
     * 手机端（PojavLauncher 等）"大退"直接杀进程，走不到关闭钩子，
     * 用户调好的位置就丢了。
     *
     * <p>{@code ConfigValue.save()} 保存的是该值所属的**整个配置文件**，
     * 所以调任意一个字段即可。这些字段与 GeneralConfig 共用同一个 spec
     * （见 {@link GeneralConfig#buildSpec()}），会一并写入。
     *
     * <p>任何字段为 null（配置未初始化）时静默跳过，避免界面操作报错。
     */
    public static void save() {
        try {
            if (PLAYER_POS_X != null) {
                PLAYER_POS_X.save();
            } else if (PLAYER_SCALE != null) {
                PLAYER_SCALE.save();
            }
        } catch (Throwable ignored) {
            // 配置保存失败不应影响界面关闭
        }
    }
}