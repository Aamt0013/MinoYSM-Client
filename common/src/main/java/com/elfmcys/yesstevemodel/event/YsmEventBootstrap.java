package com.elfmcys.yesstevemodel.event;

import com.elfmcys.yesstevemodel.BuildFlags;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.event.AnimationLockEvent;
import com.elfmcys.yesstevemodel.client.event.ClientPlayerCloneEvent;
import com.elfmcys.yesstevemodel.client.event.ClientPlayerJoinNotification;
import com.elfmcys.yesstevemodel.client.event.ClientSetupEvent;
import com.elfmcys.yesstevemodel.client.event.ClientTickEvent;
import com.elfmcys.yesstevemodel.client.event.PlayerSkinTextureManager;
import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.DebugAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraPlayerRenderKey;
import com.elfmcys.yesstevemodel.client.input.InputStateKey;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import rip.ysm.api.PlatformAPI;

public final class YsmEventBootstrap {

    private YsmEventBootstrap() {
    }

    public static void register() {
        // 纯客户端构建：单人游戏里集成服务端与客户端同 JVM，
        // 这些服务端事件若不跳过会继续跑一整套模型扫描/加密/下发流程。
        if (!BuildFlags.CLIENT_ONLY) {
            ServerStartupEvent.register();
            EnterServerEvent.register();
            PlayerLogoutEvent.register();
            CapabilityEvent.register();
        } else {
            YesSteveModel.LOGGER.info("[YSM] Client-only build: server-side events skipped.");
        }
        CommonEvent.register();
        CommandRegistry.register();

        if (!PlatformAPI.isServer()) {
            EntityJoinCallbackEvent.register();

            ClientSetupEvent.register();
            ClientTickEvent.register();
            ClientPlayerJoinNotification.register();
            ClientPlayerCloneEvent.register();
            AnimationLockEvent.register();
            PlayerSkinTextureManager.register();
            RendererManager.register();
            PlayerModelToggleKey.register();
            AnimationRouletteKey.register();
            DebugAnimationKey.register();
            ExtraPlayerRenderKey.register();
            ExtraAnimationKey.register();
            InputStateKey.register();
        }

    }
}
