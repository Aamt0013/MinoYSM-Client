package com.elfmcys.yesstevemodel;

/**
 * 构建形态标志。
 *
 * <p>本仓库会产出两种 jar，模组 id 相同、不能共存：
 * <ul>
 *   <li><b>双端版</b>（{@code environment: *}）—— 客户端与服务端功能完整，
 *       由服务端扫描模型、算 hash、通过握手下发给客户端。</li>
 *   <li><b>纯客户端版</b>（{@code environment: client}）—— 只作为客户端模组使用，
 *       自己扫描 {@code custom/}、{@code built/} 目录，不依赖服务端。</li>
 * </ul>
 *
 * <p>纯客户端版虽然 {@code environment} 是 {@code client}，但单人游戏里
 * <b>集成服务端跑在同一个 JVM 内</b>，{@code LifecycleEvent.SERVER_BEFORE_START}
 * 等事件照常触发。若不显式关掉，{@code ServerModelManager} 会继续做一整套
 * 无意义的服务端工作（扫描 47 个模型、加密写 cache/server、维护同步状态），
 * 白白拖慢启动并可能与客户端本地扫描互相干扰。
 *
 * <p>{@link #CLIENT_ONLY} 为 true 时，这些服务端路径会被跳过。
 */
public final class BuildFlags {

    /**
     * 是否为纯客户端构建。
     *
     * <p>由构建脚本通过 {@code -Dysm.clientOnly=true} 或直接改此常量切换。
     * 默认 true —— 本仓库当前产出的就是纯客户端版。
     */
    public static final boolean CLIENT_ONLY = readClientOnly();

    private BuildFlags() {
    }

    private static boolean readClientOnly() {
        // 允许运行时覆盖，便于同一份代码跑两种验证
        String prop = System.getProperty("ysm.clientOnly");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        return true;
    }
}
