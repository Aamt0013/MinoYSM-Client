package com.elfmcys.yesstevemodel.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import dev.architectury.event.events.common.LifecycleEvent;

public final class ServerStartupEvent {

    private ServerStartupEvent() {
    }

    public static void register() {
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            // 调试用：-Dysm.disableServerModels=true 可模拟"服务端没装 YSM"的场景，
            // 用来验证纯客户端模式的本地模型加载是否独立可用。
            if (Boolean.getBoolean("ysm.disableServerModels")) {
                YesSteveModel.LOGGER.warn("[YSM] Server-side model loading disabled by -Dysm.disableServerModels");
                return;
            }
            ServerModelManager.loadModels(result -> {
                if (!result.isSuccess()) {
                    server.execute(() -> {
                        throw new RuntimeException("YSM Loading Failed: " + result.getErrorMessage().getString(256));
                    });
                }
            }, null);
        });
    }
}