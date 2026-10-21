package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.client.animation.molang.struct.RoamingStruct;
import com.elfmcys.yesstevemodel.client.animation.molang.struct.RoamingSyncBatch;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import rip.ysm.compat.bettercombat.BetterCombatCompat;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import com.elfmcys.yesstevemodel.client.entity.PlayerEntityFrameState;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.IBone;
import com.elfmcys.yesstevemodel.molang.runtime.Int2FloatOpenHashMapStruct;
import com.elfmcys.yesstevemodel.molang.runtime.Struct;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SCompleteFeedbackPacket;
import com.elfmcys.yesstevemodel.network.message.FeedbackData;
import dev.architectury.injectables.annotations.ExpectPlatform;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMaps;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class PlayerCapability extends CustomPlayerEntity {

    @ExpectPlatform
    public static Optional<PlayerCapability> get(Player player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<PlayerCapability> get(Entity entity) {
        throw new AssertionError();
    }

    private final Int2ReferenceOpenHashMap<MolangVarHolder> molangVarsMap;

    private int currentModelHashId;

    private Struct serverVarContainer;

    public PlayerCapability(Player player) {
        super(player, player instanceof LocalPlayer, true);
        this.molangVarsMap = new Int2ReferenceOpenHashMap<>(8);
    }

    @Override
    public PlayerEntityFrameState createPositionTracker(Player player) {
        return new PlayerEntityFrameState(player, player instanceof LocalPlayer);
    }

    @Override
    public PlayerEntityFrameState getPositionTracker() {
        return (PlayerEntityFrameState) super.getPositionTracker();
    }

    @Nullable
    public Struct getServerVarContainer() {
        return this.serverVarContainer;
    }

    @Override
    public void onModelLoaded(ModelAssembly context) {
        super.onModelLoaded(context);
        this.currentModelHashId = getModelAssembly().getModelData().getHashId();
    }

    @Override
    public void clearModel() {
        this.currentModelHashId = 0;
        super.clearModel();
    }

    @Override
    public void setCurrentModel(AnimatedGeoModel model) {
        super.setCurrentModel(model);
        // 纯客户端：模型切换/重载时先把本地保存的漫游变量灌回来。
        ensureLocalVarsLoaded();
        MolangVarHolder varHolder = this.molangVarsMap.get(this.currentModelHashId);
        if (varHolder != null && varHolder.currentVars != null) {
            if (isLocalPlayerModel()) {
                this.serverVarContainer = new RoamingStruct(this.currentModelHashId, varHolder.currentVars);
                return;
            } else {
                this.serverVarContainer = new Int2FloatOpenHashMapStruct(varHolder.currentVars);
                return;
            }
        }
        // 纯客户端且本地没有存档时，仍然要给本地玩家一个"空"的 RoamingStruct。
        //
        // 关键点：漫游变量（v.roaming.*）的写入是在 RoamingStruct.putProperty() 里
        // 记录变更的。若这里返回 null，AnimationProcessor.setRoamingProperties(null)
        // 之后所有 v.roaming.* 的赋值都没有接收者 —— 轮盘的个性化设置既不会真正生效，
        // 也不会产生任何待同步变更，自然也就不会被持久化。
        //
        // 原版靠服务端在进服时下发一份（可能为空）变量表来建立这个 struct，
        // 纯客户端模式没有这一步，所以必须自己兜底。
        if (isLocalPlayerModel() && this.currentModelHashId != 0 && !NetworkHandler.isClientConnected()) {
            this.serverVarContainer = new RoamingStruct(this.currentModelHashId, new Int2FloatOpenHashMap());
            com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.debug(
                    "[YSM] Created empty roaming struct for local model: {} (hash={})",
                    getModelId(), this.currentModelHashId);
            return;
        }
        this.serverVarContainer = null;
        com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.debug(
                "[YSM] Roaming struct = null for model: {} (local={}, hash={}, connected={})",
                getModelId(), isLocalPlayerModel(), this.currentModelHashId,
                NetworkHandler.isClientConnected());
    }

    /**
     * 没有 YSM 服务端时，从本地 {@code molang_vars.json} 恢复该模型的漫游变量。
     *
     * <p>只处理本地玩家；有其他玩家的数据时以服务端同步为准。
     * 已经存在记录（说明服务端下发过）时不覆盖。
     */
    private void ensureLocalVarsLoaded() {
        if (!isLocalPlayerModel() || this.currentModelHashId == 0) {
            return;
        }
        if (NetworkHandler.isClientConnected()) {
            // 有 YSM 服务端：漫游变量由服务端权威保存与下发
            return;
        }
        if (this.molangVarsMap.containsKey(this.currentModelHashId)) {
            return;
        }
        java.util.Map<String, Float> saved = com.elfmcys.yesstevemodel.client.MolangVarStore.get(getModelId());
        if (saved.isEmpty()) {
            return;
        }
        Int2FloatOpenHashMap vars = new Int2FloatOpenHashMap(saved.size());
        for (java.util.Map.Entry<String, Float> e : saved.entrySet()) {
            vars.put(StringPool.computeIfAbsent(e.getKey()), e.getValue().floatValue());
        }
        MolangVarHolder holder = new MolangVarHolder();
        holder.currentVars = vars;
        this.molangVarsMap.put(this.currentModelHashId, holder);
        com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.info(
                "[YSM] Restored {} molang vars for model: {}", saved.size(), getModelId());
    }

    @Override
    public void reset() {
        this.serverVarContainer = null;
        super.reset();
    }

    @Override
    public void applyHeadTracking(AnimationEvent<? extends AnimatableEntity<Player>> event, boolean wasAnimEvaluated) {
        super.applyHeadTracking(event, wasAnimEvaluated);
        AnimatedGeoModel model2 = getCurrentModel();
        if (model2 != null && isLocalPlayerModel() && !event.isFirstPerson() && FirstPersonCompat.isLoaded()) {
            if (model2.allHeadBone() != null) {
                model2.allHeadBone().setHidden(FirstPersonCompat.shouldHideHead());
            }
            if (model2.viewLocatorBone() != null) {
                FirstPersonCompat.setCameraDistance(model2.viewLocatorBone().getPivotY() * getWidthScale());
            } else if (wasAnimEvaluated && !model2.headBones().isEmpty()) {
                IBone bone = model2.headBones().get(model2.headBones().size() - 1);
                FirstPersonCompat.setCameraDistance(bone == null ? 24.0f : bone.getPivotY() * getWidthScale());
            }
        }
    }

    @Override
    public void resetHeadTracking(boolean wasAnimEvaluated) {
        super.resetHeadTracking(wasAnimEvaluated);
        AnimatedGeoModel model2 = getCurrentModel();
        if (model2 != null && isLocalPlayerModel()) {
            if ((FirstPersonCompat.isLoaded() || BetterCombatCompat.isLoaded()) && model2.allHeadBone() != null) {
                model2.allHeadBone().setHidden(false);
            }
        }
    }

    public void updateMolangVars(int i, Int2FloatOpenHashMap int2FloatOpenHashMap) {
        MolangVarHolder varHolder = this.molangVarsMap.computeIfAbsent(i, i2 -> {
            return new MolangVarHolder();
        });
        if (isLocalPlayerModel()) {
            if (varHolder.currentVars == null) {
                varHolder.currentVars = int2FloatOpenHashMap;
                varHolder.applyPendingDeltas();
                if (i == this.currentModelHashId) {
                    this.serverVarContainer = new RoamingStruct(i, int2FloatOpenHashMap);
                    clearAnimationControllers();
                    return;
                }
                return;
            }
            return;
        }
        varHolder.currentVars = int2FloatOpenHashMap;
        varHolder.applyPendingDeltas();
        if (i == this.currentModelHashId) {
            this.serverVarContainer = new Int2FloatOpenHashMapStruct(int2FloatOpenHashMap);
        }
    }

    public boolean hasMolangVars(int i) {
        return this.molangVarsMap.containsKey(i);
    }

    private void applyMolangDelta(int i, Int2FloatMap int2FloatMap) {
        if (i == this.currentModelHashId && this.entity.getVehicle() != null && this.entity.getVehicle().getFirstPassenger() == this.entity) {
            VehicleCapability.get(this.entity.getVehicle()).ifPresent(cap -> {
                cap.updateFloatMap(int2FloatMap);
            });
        }
    }

    public void enqueueMolangDelta(int i, Int2FloatMap int2FloatMap) {
        if (!isLocalPlayerModel() && !int2FloatMap.isEmpty()) {
            MolangVarHolder varHolder = this.molangVarsMap.computeIfAbsent(i, i2 -> {
                return new MolangVarHolder();
            });
            if (varHolder.currentVars != null) {
                varHolder.currentVars.putAll(int2FloatMap);
            } else {
                varHolder.pendingDeltas.enqueue(int2FloatMap);
            }
            applyMolangDelta(i, int2FloatMap);
        }
    }

    public void tickAnimations() {
        if (isLocalPlayerModel() && this.currentModelHashId != 0) {
            Struct struct = this.serverVarContainer;
            if (struct instanceof RoamingStruct roamingStruct) {
                if (roamingStruct.hasPendingChanges()) {
                    RoamingSyncBatch syncBatch = roamingStruct.consumePendingBoneData();
                    applyMolangDelta(syncBatch.modelHashId(), syncBatch.changedVariables());
                    String[] strArr = new String[syncBatch.changedVariables().size()];
                    float[] fArr = new float[syncBatch.changedVariables().size()];
                    int i = 0;
                    ObjectIterator it = Int2FloatMaps.fastIterable(syncBatch.changedVariables()).iterator();
                    while (it.hasNext()) {
                        Int2FloatMap.Entry entry = (Int2FloatMap.Entry) it.next();
                        String str = StringPool.getString(entry.getIntKey());
                        if (str.length() <= RoamingStruct.MAX_VAR_NAME_LENGTH) {
                            strArr[i] = str;
                            fArr[i] = entry.getFloatValue();
                        } else {
                            strArr[i] = StringPool.EMPTY;
                            fArr[i] = 0.0f;
                        }
                        i++;
                    }
                    // 纯客户端：本地落盘一份。
                    //
                    // 原版只把变更发给服务端，服务端存进玩家数据、下次进服再下发。
                    // 服务器没装 YSM 时没有接收方，退出重进设置就丢了。
                    // 这里额外存到 config/yes_steve_model/molang_vars.json，
                    // 由 setCurrentModel() 里的 ensureLocalVarsLoaded() 恢复。
                    if (!NetworkHandler.isClientConnected()) {
                        java.util.Map<String, Float> deltas = new java.util.HashMap<>();
                        for (int k = 0; k < strArr.length; k++) {
                            if (strArr[k] != null && !strArr[k].isEmpty()) {
                                deltas.put(strArr[k], fArr[k]);
                            }
                        }
                        if (!deltas.isEmpty()) {
                            // 同步刷新内存表：模型若在本次游戏内重新加载，
                            // ensureLocalVarsLoaded() 会因"已有记录"直接返回，
                            // 不更新这里就会用回旧值。
                            MolangVarHolder holder = this.molangVarsMap.computeIfAbsent(
                                    this.currentModelHashId, k2 -> new MolangVarHolder());
                            if (holder.currentVars == null) {
                                holder.currentVars = new Int2FloatOpenHashMap();
                            }
                            for (java.util.Map.Entry<String, Float> e : deltas.entrySet()) {
                                holder.currentVars.put(
                                        StringPool.computeIfAbsent(e.getKey()), e.getValue().floatValue());
                            }
                            com.elfmcys.yesstevemodel.client.MolangVarStore.putAll(getModelId(), deltas);
                            com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.info(
                                    "[YSM] Saved {} molang vars locally for model: {}",
                                    deltas.size(), getModelId());
                        }
                    }
                    NetworkHandler.sendToServer(new C2SCompleteFeedbackPacket(new FeedbackData(this.currentModelHashId, new Object2FloatArrayMap(strArr, fArr), null, this.entity.getId())));
                }
            }
        }
    }

    public void copyFrom(PlayerCapability playerCapability) {
        this.molangVarsMap.putAll(playerCapability.molangVarsMap);
        initModelWithTexture(playerCapability.getModelId(), playerCapability.currentTextureName);
        setForceDisabled(playerCapability.isForceDisabled());
        playerCapability.molangVarsMap.clear();
        playerCapability.serverVarContainer = null;
    }

    @Override
    @NotNull
    public LivingAnimatable<Player>.TexturedModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isActive) {
        return new TexturedModelWrapper(modelAssembly, isActive, true, true, 600);
    }

    private static class MolangVarHolder {

        public volatile Int2FloatOpenHashMap currentVars;

        public final ObjectArrayFIFOQueue<Int2FloatMap> pendingDeltas = new ObjectArrayFIFOQueue<>(4);

        private MolangVarHolder() {
        }

        public void applyPendingDeltas() {
            while (!this.pendingDeltas.isEmpty()) {
                this.currentVars.putAll(this.pendingDeltas.dequeue());
            }
        }
    }
}