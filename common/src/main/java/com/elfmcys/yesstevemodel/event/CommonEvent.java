package com.elfmcys.yesstevemodel.event;

import com.elfmcys.yesstevemodel.BuildFlags;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import rip.ysm.api.PlatformAPI;
import rip.ysm.compat.touhoulittlemaid.TouhouMaidCompat;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import dev.architectury.event.events.common.LifecycleEvent;

import java.io.IOException;

public final class CommonEvent {

    private CommonEvent() {
    }

    public static Object nativeInit() {
        try {
            ServerModelManager.reloadPacks();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void register() {
        LifecycleEvent.SETUP.register(() -> {
            if (!YesSteveModel.isAvailable()) {
                YesSteveModel.LOGGER.error(YesSteveModel.getErrorMessage());
                return;
            }
            NetworkHandler.init();
            TouhouMaidCompat.init();
            // 纯客户端构建：跳过服务端模型扫描。
            // reloadPacks() 会解压 61 MB 内置模型、扫描全部模型、加密写 cache/server，
            // 单人游戏里这些产物客户端根本用不到（客户端走自己的本地扫描）。
            if (BuildFlags.CLIENT_ONLY) {
                YesSteveModel.LOGGER.info("[YSM] Client-only build: skip reloadPacks() on server side.");
                return;
            }
            nativeInit();
        });
    }
}