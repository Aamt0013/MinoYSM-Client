package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import rip.ysm.api.PlatformAPI;

public final class ClientPlayerJoinNotification {

    private static boolean notified = false;

    /**
     * 正在经历维度切换（穿传送门、末地传送等）。
     *
     * <p>Architectury 的 {@code CLIENT_PLAYER_QUIT} 由 {@code MixinMinecraft#handleLogin} 触发，
     * 该注入点不仅在断开连接时命中，在"切换 level"时也会命中。穿下界传送门时客户端会收到
     * Respawn 包并重建 {@code ClientLevel}，于是 QUIT 被误触发。
     *
     * <p>若此时执行 {@code resetSync()}，模型状态会被清空并回退到默认模型。
     * 在未安装 YSM 的服务器上更严重：客户端无法重新同步，会永久停在默认模型。
     *
     * <p>因此用 {@code CLIENT_PLAYER_RESPAWN} 标记"这是维度切换/重生，不是退出"，
     * QUIT 回调见到标记就跳过重置。
     */
    private static volatile boolean dimensionChanging = false;

    private ClientPlayerJoinNotification() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(ClientPlayerJoinNotification::onPlayerJoin);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientPlayerJoinNotification::onPlayerQuit);
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(ClientPlayerJoinNotification::onPlayerRespawn);
    }

    private static void onPlayerRespawn(LocalPlayer oldPlayer, LocalPlayer newPlayer) {
        // 维度切换或死亡重生：标记，让紧随其后的 QUIT 回调不要重置同步状态
        dimensionChanging = true;
        notified = newPlayer != null;
    }

    private static void onPlayerJoin(LocalPlayer player) {
        if (notified) {
            return;
        }
        ClientModelManager.runPendingModelCallback();
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        // 纯客户端模式：调度本地扫描（宽限期内若服务端完成同步就取消）。
        ClientModelManager.scheduleLocalModelScan();
        // 本地模型就绪后套用上次保存的选择。
        // 服务端装了 YSM 时以服务端下发为准，这里的 applyToLocalPlayer 会因
        // 模型表里没有对应 id 或服务端随后覆盖而自然失效。
        ClientModelManager.whenLocalModelsReady(com.elfmcys.yesstevemodel.client.LocalModelSelection::applyToLocalPlayer);

        // 纯客户端构建：这条"服务端版本不同"的提示对我们是误导。
        //
        // 原版语义是"服务器没装/装了别的版本 YSM，所以拿不到服务端模型"。
        // 但纯客户端版本来就靠本地模型工作，服务器有没有 YSM 都无所谓，
        // 只要 custom/ 里有模型就一切正常。真正需要提示用户的只有一种情况：
        // 本地一个模型都没有 —— 那时该告诉他去放模型，而不是说服务端版本不对。
        //
        // 时机也提前：本地扫描一完成就判断，不必等 60 秒。
        if (com.elfmcys.yesstevemodel.BuildFlags.CLIENT_ONLY) {
            ClientModelManager.whenLocalModelsReady(() -> {
                Minecraft.getInstance().execute(() -> {
                    LocalPlayer localPlayer = Minecraft.getInstance().player;
                    if (localPlayer == null) {
                        return;
                    }
                    // 服务端下发了模型（装了 YSM 且版本匹配）时不算异常
                    if (NetworkHandler.isConnectionValid(localPlayer.connection.getConnection())) {
                        return;
                    }
                    if (!ClientModelManager.getLocalModelIds().isEmpty()) {
                        return;
                    }
                    localPlayer.sendSystemMessage(
                            Component.translatable("message.yes_steve_model.client.local_models_empty"));
                });
            });
            if (Minecraft.getInstance().isLocalServer()) {
                return;
            }
            return;
        }

        if (Minecraft.getInstance().isLocalServer()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(60000L);
                Minecraft.getInstance().execute(() -> {
                    LocalPlayer localPlayer = Minecraft.getInstance().player;
                    if (localPlayer != null && localPlayer.connection.isAcceptingMessages() && !NetworkHandler.isConnectionValid(localPlayer.connection.getConnection())) {
                        // 纯客户端模式下这是正常状态（服务器没装 YSM 也能用本地模型），
                        // 只在本地也没有任何模型时才提示。
                        if (ClientModelManager.getLocalModelIds().isEmpty()) {
                            localPlayer.sendSystemMessage(Component.translatable("message.yes_steve_model.client.server_not_found"));
                        }
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void onPlayerQuit(LocalPlayer player) {
        // 退出世界时把轮盘个性化设置强制落盘。
        // 平时靠 500ms 防抖异步写入，若玩家改完设置立刻退出/杀进程会丢掉最后一次改动。
        // 放在所有 early-return 之前，保证任何退出路径都会刷一次。
        try {
            com.elfmcys.yesstevemodel.client.MolangVarStore.saveNow();
        } catch (Throwable ignored) {
            // 保存失败不应影响退出流程
        }
        // 维度切换/重生引发的 QUIT：保留模型状态，不重置
        if (dimensionChanging) {
            dimensionChanging = false;
            return;
        }
        if (!notified) {
            return;
        }
        notified = false;
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        // 双保险：确认连接确实已断开，避免其他路径的误触发
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();
        boolean reallyDisconnected = minecraft.player == null
                || listener == null
                || !listener.getConnection().isConnected();
        if (!reallyDisconnected) {
            return;
        }
        ClientModelManager.resetSync();
    }
}
