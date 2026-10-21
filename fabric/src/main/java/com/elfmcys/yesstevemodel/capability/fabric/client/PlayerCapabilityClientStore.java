package com.elfmcys.yesstevemodel.capability.fabric.client;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerCapabilityClientStore {

    private static final ConcurrentMap<UUID, PlayerCapability> STORE = new ConcurrentHashMap<>();

    private PlayerCapabilityClientStore() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (!(player instanceof AbstractClientPlayer)) {
            return Optional.empty();
        }
        UUID uuid = player.getUUID();
        PlayerCapability existing = STORE.get(uuid);
        if (existing != null && existing.entity == player) {
            return Optional.of(existing);
        }

        PlayerCapability fresh = new PlayerCapability(player);
        if (existing != null) {
            // 26.x 修复：穿过下界/末地传送门时，客户端会重建 LocalPlayer 对象，
            // 导致 existing.entity != player。原实现直接丢弃旧 capability 并新建一个空的，
            // 模型选择状态随之丢失 —— 表现为"穿传送门后变回默认模型"；
            // 在未安装 YSM 的服务器上无法重新同步，会永久停在默认模型。
            //
            // AnimatableEntity.entity 是 final 无法重新绑定，因此改为把"与实体无关"的
            // 模型选择状态迁移到新实例上，由后续渲染/同步流程重新加载模型资源。
            migrateModelState(existing, fresh);
        }
        STORE.put(uuid, fresh);
        return Optional.of(fresh);
    }

    /**
     * 迁移与具体实体对象无关的模型状态。
     *
     * <p>{@code setModelId} 内部会调用 {@code refreshModel()}，
     * 从 {@code ClientModelManager} 重新查装配好的模型资源并重建渲染形状，
     * 因此这里只需把"用户选中的模型 id"传过去，渲染缓存会自动重建。
     *
     * <p>不迁移渲染缓存本身（骨骼、动画状态、Molang 变量）——那些绑定旧实体对象，
     * 迁移过来会引用已失效的 level/位置。
     */
    private static void migrateModelState(PlayerCapability from, PlayerCapability to) {
        try {
            String modelId = from.getModelId();
            if (modelId != null && !modelId.isEmpty()) {
                // 会触发 refreshModel()，重新绑定模型资源
                to.setModelId(modelId);
            }
            if (from.isDisabledState()) {
                // 保留"暂时禁用"状态（例如正在播放切换动画）
                to.isDisabled = true;
            }
        } catch (Throwable ignored) {
            // 迁移失败不影响主流程：退化为等待服务器重新同步
        }
    }

    public static void clear() {
        STORE.clear();
    }
}
