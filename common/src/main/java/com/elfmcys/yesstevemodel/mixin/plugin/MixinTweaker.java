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
     * 纯客户端构建下不应用的服务端 Mixin。
     *
     * <p>这些 Mixin 的目标都是服务端专用类（{@code ServerPlayer}、
     * {@code ServerGamePacketListenerImpl}），在纯客户端构建里没有意义，
     * 而且单人游戏时它们会真的注入到集成服务端的类上，维持一条本该废弃的
     * 服务端同步链路。跳过可以少一层注入开销，也避免与服务端逻辑产生耦合。
     */
    private static final Set<String> SERVER_ONLY_MIXINS = Set.of(
            "ServerPlayerMixin",
            "ServerCommonPacketListenerImplMixin"
    );

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
