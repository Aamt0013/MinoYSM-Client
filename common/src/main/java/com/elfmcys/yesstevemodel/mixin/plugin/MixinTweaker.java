package com.elfmcys.yesstevemodel.mixin.plugin;

import com.elfmcys.yesstevemodel.BuildFlags;
import com.elfmcys.yesstevemodel.util.obfuscate.Keep;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinTweaker implements IMixinConfigPlugin {

    /**
     * 纯客户端构建下**不应跳过**任何 Mixin。
     *
     * <p>曾经在这里跳过 {@code ServerPlayerMixin} 与
     * {@code ServerCommonPacketListenerImplMixin}，理由是"纯客户端用不到服务端逻辑"。
     * 这个判断是错的 —— 单人游戏里集成服务端跑在同一个 JVM，
     * 这些 Mixin 支撑的功能照常生效，而且有隐藏依赖：
     *
     * <p>{@code ServerCommonPacketListenerImplMixin} 给
     * {@code ServerGamePacketListenerImpl} 添加了
     * {@code ServerCommonPacketListenerImplAccessor} 接口，
     * 而 {@code NetworkHandler.isPlayerConnected()} 会强转成这个接口。
     * 跳过之后强转抛 {@code ClassCastException}，异常从
     * {@code Projectile.setOwner()} 一路抛出，导致投射物实体根本没生成 ——
     * 表现为雪球、箭矢、三叉戟、烟花、经验瓶扔出去就消失。
     *
     * <p>因此保留该字段但留空，仅作为将来需要时的显式开关。
     */
    private static final Set<String> SERVER_ONLY_MIXINS = Set.of();

    @Keep
    public void onLoad(String str) {
    }

    @Keep
    public String getRefMapperConfig() {
        return null;
    }

    @Keep
    public boolean shouldApplyMixin(String str, String str2) {
        if (BuildFlags.CLIENT_ONLY) {
            // str2 是完整类名，取简单名比较
            int idx = str2.lastIndexOf('.');
            String simpleName = idx >= 0 ? str2.substring(idx + 1) : str2;
            if (SERVER_ONLY_MIXINS.contains(simpleName)) {
                return false;
            }
        }
        return true;
    }

    @Keep
    public void acceptTargets(Set<String> set, Set<String> set2) {
    }

    @Keep
    public List<String> getMixins() {
        return null;
    }

    @Keep
    public void preApply(String str, ClassNode classNode, String str2, IMixinInfo iMixinInfo) {
    }

    @Keep
    public void postApply(String str, ClassNode classNode, String str2, IMixinInfo iMixinInfo) {
    }
}
