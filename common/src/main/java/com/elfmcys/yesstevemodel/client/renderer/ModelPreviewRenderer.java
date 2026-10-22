package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.VehicleCapability;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.animation.AnimationTracker;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.IBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.util.AnimatableCacheUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ExecutionException;
import com.mojang.math.Axis;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;

public final class ModelPreviewRenderer {

    private static boolean isPreviewMode = false;

    private static boolean isExtraPlayerMode = false;

    private static boolean isFirstPersonMode = false;

    public static void setPreviewMode(boolean previewMode) {
        isPreviewMode = previewMode;
    }

    public static boolean isPreview() {
        return isPreviewMode;
    }

    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        isExtraPlayerMode = extraPlayerMode;
    }

    public static boolean isExtraPlayer() {
        return isExtraPlayerMode;
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        isFirstPersonMode = firstPersonMode;
    }

    public static boolean isFirstPerson() {
        return isFirstPersonMode || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        return isFirstPersonMode && !FirstPersonCompat.isFirstPersonActive();
    }

    public static void renderVehicleModel(Entity entity, PoseStack poseStack, float partialTick) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            VehicleCapability.get(vehicle).ifPresent(cap -> {
                int index;
                AnimatedGeoModel model;
                List<IBone> list;
                if (!cap.isModelInitialized() || !cap.isModelReady() || (index = vehicle.getPassengers().indexOf(entity)) < 0 || (model = cap.getCurrentModel()) == null || model.passengerGroupChains().isEmpty() || index >= model.passengerGroupChains().size() || (list = model.passengerGroupChains().get(index)) == null) {
                    return;
                }
                float bodyRotation = CustomVehicleRenderer.getBodyRotation(vehicle, Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot()), partialTick);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRotation));
                RenderUtils.prepMatrixForLocator(poseStack, list);
                poseStack.mulPose(Axis.YN.rotationDegrees(180.0f - bodyRotation));
                double myRidingOffset = -(vehicle.getPassengerRidingPosition(entity).y - vehicle.getY());
                if (((entity instanceof Player) && PlayerCapability.get(entity).isPresent()) || TouhouLittleMaidCompat.isMaidRideable(entity)) {
                    myRidingOffset -= 0.5d;
                }
                poseStack.translate(0.0d, myRidingOffset, 0.0d);
            });
        }
    }

    // 动画测试界面的模型
    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, AvatarRenderState state, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1250.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees((-10.0f) + pitch);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        Pose oldPose = livingEntity.getPose();
        livingEntity.yBodyRot = -yaw;
        livingEntity.yBodyRotO = -yaw;
        livingEntity.setYRot(180.0f);
        livingEntity.yRotO = 180.0f;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = -yaw;
        livingEntity.yHeadRotO = -yaw;

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        poseStack.mulPose(rotationX);
        SubmitNodeCollector bufferSource = RenderContext.collector();

        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();
        if (animationTracker.isCurrentAnimation("sleep")) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
            poseStack.translate(0.5d, 0.5625d, 0.0d);
            livingEntity.setPose(Pose.SLEEPING);
        }
        if (animationTracker.isCurrentAnimation("swim") || animationTracker.isCurrentAnimation("swim_stand")) {
            livingEntity.setPose(Pose.SWIMMING);
        }
        if (animationTracker.isCurrentAnimation("sneak") || animationTracker.isCurrentAnimation("sneaking")) {
            livingEntity.setPose(Pose.CROUCHING);
        }
        if (animationTracker.isCurrentAnimation("sit")) {
            poseStack.translate(0.0d, -0.5d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("ride")) {
            poseStack.translate(0.0d, 0.85d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("ride_pig")) {
            poseStack.translate(0.0d, 0.3125d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("boat")) {
            poseStack.translate(0.0d, -0.45d, 0.0d);
        }
        try {
            renderVehicleForAnimation(yaw, animatableEntity, partialTick, poseStack, entityRenderDispatcher, bufferSource);
            if (animationTracker.isCurrentAnimation("sleep")) {
                renderBedPreview(scale, pitch, yaw, bufferSource);
            }
            if (renderGround) {
                renderGroundPreview(scale, pitch, yaw, bufferSource);
            }
            renderer.renderEntity((LivingAnimatable) animatableEntity, state, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        livingEntity.setPose(oldPose);

        modelViewStack.popMatrix();
        setPreviewMode(false);
    }

    private static void renderBedPreview(float scale, float pitch, float yaw, SubmitNodeCollector bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        // TODO(26.x): 方块预览改用 BlockModelResolver.update + submitBlockModel，装饰性预览暂缓
    }

    private static void renderGroundPreview(float scale, float pitch, float yaw, SubmitNodeCollector bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0f, 0.0f, 1.0f);
        // TODO(26.x): 方块预览改用 BlockModelResolver.update + submitBlockModel，装饰性预览暂缓
            }
            poseStack.translate(1.0f, 0.0f, -3.0f);
        }

        poseStack.translate(-1.0f, 1.0f, 1.0f);
        // TODO(26.x): 方块预览改用 BlockModelResolver.update + submitBlockModel，装饰性预览暂缓
        poseStack.translate(0.0f, 0.0f, 1.0f);
        // TODO(26.x): 方块预览改用 BlockModelResolver.update + submitBlockModel，装饰性预览暂缓
    }

    private static void renderVehicleForAnimation(float yaw, AnimatableEntity animatableEntity, float partialTick, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, SubmitNodeCollector bufferSource) throws ExecutionException {
        Entity entity = animatableEntity.getEntity();
        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();

        if (animationTracker.isCurrentAnimation("ride")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityTypes.HORSE), () -> EntityTypes.HORSE.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityTypes.PIG), () -> EntityTypes.PIG.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityTypes.OAK_BOAT), () -> EntityTypes.OAK_BOAT.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, SubmitNodeCollector bufferSource, Entity vehicleEntity, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.popPose();
    }

    // 模型预览页面
    public static <T extends Player, TAnimatable extends LivingAnimatable<T>, S extends AvatarRenderState> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, S state, GeoReplacedEntityRenderer<T, TAnimatable, S> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        ItemStack[] savedEquipment;
        setPreviewMode(true);
        LivingEntity livingEntity = animatable.getEntity();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1050.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, disablePreviewRotation ? 5.5d : 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(disablePreviewRotation ? 0.0f : -10.0f);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        if (hideEquipment && (livingEntity instanceof Player player)) {
            savedEquipment = new ItemStack[EquipmentSlot.values().length];
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                savedEquipment[slotIndex] = player.getItemBySlot(equipmentSlot).copy();
                player.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        float previewYaw = disablePreviewRotation ? 180.0f : 200.0f;
        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        Entity vehicle = livingEntity.getVehicle();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - previewYaw));
            livingEntity.yHeadRot = vehicleYaw;
            livingEntity.yHeadRotO = vehicleYaw;
        }

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        poseStack.mulPose(rotationX);
        SubmitNodeCollector bufferSource = RenderContext.collector();

        renderer.renderEntity(animatable, state, 0.0f, partialTick, poseStack, bufferSource, 15728880);

        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        if (savedEquipment != null) {
            Player player = (Player) livingEntity;
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                ItemStack itemStack = savedEquipment[slotIndex];
                player.setItemSlot(equipmentSlot, itemStack);
                slotIndex++;
            }
        }

        modelViewStack.popMatrix();
        setPreviewMode(false);
    }

    // 纸娃娃
    public static void renderPlayerOverlay(GuiGraphicsExtractor guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        setExtraPlayerMode(true);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate((float) (x + (scale * 0.5d)), (float) (y + (scale * 2.0f)), 0.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0f, 0.0f, -zDepth);
        poseStack.scale(scale, scale, scale);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.1f);
        Quaternionf rotationY = Axis.YP.rotationDegrees((Mth.lerp(partialTick, localPlayer.yBodyRotO, localPlayer.yBodyRot) + yawOffset) - 180.0f);
        rotationZ.mul(rotationY);
        poseStack.mulPose(rotationZ);

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationY.conjugate();
        poseStack.mulPose(rotationY);
        SubmitNodeCollector bufferSource = RenderContext.collector();

        modelViewStack.popMatrix();
        setExtraPlayerMode(false);
    }

    public static void submitLivingEntityPreview(
            GuiGraphicsExtractor guiGraphics,
            int x0, int y0, int x1, int y1,
            int displaySize,
            float partialTick,
            PlayerPreviewEntity animatable,
            boolean disablePreviewRotation,
            boolean hideEquipment
    ) {
        LivingEntity entity = animatable.getEntity();
        if (entity == null) {
            return;
        }

        ItemStack[] savedEquipment = null;
        if (hideEquipment && entity instanceof Player player) {
            EquipmentSlot[] slots = EquipmentSlot.values();
            savedEquipment = new ItemStack[slots.length];
            for (int i = 0; i < slots.length; i++) {
                savedEquipment[i] = player.getItemBySlot(slots[i]).copy();
                player.setItemSlot(slots[i], ItemStack.EMPTY);
            }
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState((Player) entity, state, partialTick);
        state.lightCoords = 15728880;

        float previewYaw = disablePreviewRotation ? 180.0F : 200.0F;
        state.bodyRot = previewYaw;
        state.yRot = 0.0F;
        state.xRot = 0.0F;

        PreviewEntityRegistry.register(state, animatable);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraTilt = null;
        if (!disablePreviewRotation) {
            cameraTilt = new Quaternionf().rotateX((float) (-10.0 * Math.PI / 180.0));
            rotation.mul(cameraTilt);
        }

        float entityScale = entity.getScale();
        float yOffsetPx = disablePreviewRotation ? 5.5F : 0.0F;
        float yOffsetModel = yOffsetPx * entityScale / (float) displaySize;
        Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F + yOffsetModel, 0.0F);
        float submitScale = (float) displaySize / entityScale;

        // 26.x：PIP 纹理尺寸必须为正，否则 createTexture 抛异常导致崩溃
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.entity(state, submitScale, translation, rotation, cameraTilt, x0, y0, x1, y1);
        guiGraphics.disableScissor();

        if (savedEquipment != null) {
            Player player = (Player) entity;
            EquipmentSlot[] slots = EquipmentSlot.values();
            for (int i = 0; i < slots.length; i++) {
                player.setItemSlot(slots[i], savedEquipment[i]);
            }
        }
    }

    /**
     * 个性化设置界面（{@code ModelSettingsScreen}）的模型预览。
     *
     * <p><b>为什么不用直接调 {@code renderEntity}</b>：那个界面是在屏幕的
     * {@code extractRenderState} 里渲染的，没有 PIP 上下文，
     * {@code RenderContext.collector()} 恒为 {@code null}。而 26.x 的模型绘制
     * 全靠 {@code SubmitNodeCollector} 提交自定义几何 —— collector 为 null 时
     * {@code IGeoRenderer} 会直接跳过绘制，结果就是预览区一片空白。
     *
     * <p>因此这里改走 PIP：和轮盘、模型选择界面用的是同一套
     * （{@code guiGraphics.entity} → {@code GuiEntityRenderer} →
     * {@code GuiEntityRendererMixin} 里建立 RenderContext），
     * 模型、护甲、手持物、鞘翅都能正常渲染。
     *
     * @param x0,y0,x1,y1 预览矩形（GUI 坐标）
     * @param displaySize 模型显示尺寸，越大越占满
     * @param pitch       上下视角（度），负值向下看
     * @param yaw         左右朝向（度）
     */
    public static void submitSettingsPreview(
            GuiGraphicsExtractor guiGraphics,
            int x0, int y0, int x1, int y1,
            float displaySize,
            float pitch,
            float yaw,
            float partialTick,
            LivingAnimatable<?> animatable
    ) {
        // PIP 纹理尺寸必须为正，否则 createTexture 抛异常
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (!(animatable instanceof CustomPlayerEntity customPlayer)) {
            return;
        }
        LivingEntity entity = customPlayer.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        float entityScale = entity.getScale();
        if (!(entityScale > 0.0F) || !Float.isFinite(entityScale)) {
            return;
        }
        float submitScale = displaySize / entityScale;
        if (!(submitScale > 0.0F) || !Float.isFinite(submitScale)) {
            return;
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState(player, state, partialTick);
        state.lightCoords = 15728880;
        // 朝向交给 renderState，不再去改 entity 的 yBodyRot（那是立即模式时代的做法）
        state.bodyRot = yaw;
        state.yRot = 0.0F;
        state.xRot = 0.0F;

        PreviewEntityRegistry.register(state, customPlayer);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraTilt = new Quaternionf().rotateX((float) Math.toRadians(-10.0 + pitch));
        rotation.mul(cameraTilt);

        Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F, 0.0F);

        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.entity(state, submitScale, translation, rotation, cameraTilt, x0, y0, x1, y1);
        guiGraphics.disableScissor();
    }

    public static void submitPlayerOverlay(
            GuiGraphicsExtractor guiGraphics,
            LocalPlayer localPlayer,
            double x, double y,
            float scale,
            float yawOffset,
            float partialTick
    ) {
        PlayerCapability cap = PlayerCapability.get(localPlayer).orElse(null);
        if (cap == null) {
            return;
        }
        cap.tickModel();

        // 26.x：PIP 渲染会用 (x1 - x0) / (y1 - y0) 作为纹理尺寸，
        // 若宽高 <= 0，GpuDevice.createTexture 会抛
        // IllegalArgumentException: mipLevels must be at most 0 for a texture of width ...
        //
        // 用户可在配置里自由调节 PLAYER_SCALE（含负值/极小值），
        // 缩放来回拖动时中间态可能让 halfW/halfH 变成负数或 0，从而反转 x0/x1。
        // 这里统一做防护：scale 取绝对值并设下限，且最终保证 x1>x0、y1>y0。
        float safeScale = Math.abs(scale);
        if (safeScale < 1.0E-3F) {
            return;
        }

        float cx = (float) (x + safeScale * 0.6F);
        float cy = (float) (y + safeScale * 1.0F);
        int halfW = Math.max(1, (int) (safeScale * 2.0F));
        int halfH = Math.max(1, (int) (safeScale * 2.5F));
        int x0 = (int) cx - halfW;
        int x1 = (int) cx + halfW;
        int y0 = (int) cy - halfH;
        int y1 = (int) cy + halfH;

        // 最终保险：PIP 纹理尺寸必须为正，否则 GpuDevice.createTexture 直接抛异常导致崩溃。
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState(localPlayer, state, partialTick);
        state.lightCoords = 15728880;
        PreviewEntityRegistry.register(state, cap);

        state.bodyRot = 180.0F;

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        float entityScale = localPlayer.getScale();
        Vector3f translation = new Vector3f(0.0F, localPlayer.getBbHeight() / 2.0F, 0.0F);
        // submitScale 同样不能为 0/负，否则 PIP 内部换算会得到非法尺寸
        float submitScale = safeScale / entityScale;
        if (!(submitScale > 0.0F) || !Float.isFinite(submitScale)) {            return;
        }        guiGraphics.entity(state, submitScale, translation, rotation, null, x0, y0, x1, y1);
    }

    public static void submitTexturePreview(
            GuiGraphicsExtractor guiGraphics,
            int x0, int y0, int x1, int y1,
            float anchorX, float anchorY,
            float zoom,
            float pitch,
            float yaw,
            PlayerPreviewEntity animatable,
            boolean renderGround,
            float partialTick
    ) {
        LivingEntity entity = animatable.getEntity();
        if (entity == null) {
            return;
        }

        AnimationTracker tracker = animatable.getAnimationStateMachine();
        Pose oldPose = entity.getPose();
        Pose newPose = oldPose;
        float poseYOffset = 0.0F;
        if (tracker.isCurrentAnimation("sleep")) {
            newPose = Pose.SLEEPING;
        } else if (tracker.isCurrentAnimation("swim") || tracker.isCurrentAnimation("swim_stand")) {
            newPose = Pose.SWIMMING;
        } else if (tracker.isCurrentAnimation("sneak") || tracker.isCurrentAnimation("sneaking")) {
            newPose = Pose.CROUCHING;
        } else if (tracker.isCurrentAnimation("sit")) {
            poseYOffset = -0.5F;
        } else if (tracker.isCurrentAnimation("ride")) {
            poseYOffset = 0.85F;
        } else if (tracker.isCurrentAnimation("ride_pig")) {
            poseYOffset = 0.3125F;
        } else if (tracker.isCurrentAnimation("boat")) {
            poseYOffset = -0.45F;
        }
        boolean poseChanged = newPose != oldPose;
        if (poseChanged) {
            entity.setPose(newPose);
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState((Player) entity, state, partialTick);
        state.lightCoords = 15728880;

        state.bodyRot = -yaw;
        state.yRot = Mth.wrapDegrees(180.0F + yaw);
        state.xRot = 0.0F;

        final float capturedYaw = yaw;
        final boolean wantGround = renderGround;
        final boolean wantBed = tracker.isCurrentAnimation("sleep");
        final boolean wantHorse = tracker.isCurrentAnimation("ride");
        final boolean wantPig = tracker.isCurrentAnimation("ride_pig");
        final boolean wantBoat = tracker.isCurrentAnimation("boat");
        PreviewEntityRegistry.SceneryRenderer scenery = null;
        if (wantGround || wantBed || wantHorse || wantPig || wantBoat) {
            scenery = (poseStack, bufferSource, packedLight) -> {
                if (wantHorse) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityTypes.HORSE);
                } else if (wantPig) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityTypes.PIG);
                } else if (wantBoat) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityTypes.OAK_BOAT);
                }
                if (wantBed) {
                    renderBedScenery(poseStack, bufferSource, packedLight, capturedYaw);
                }
                if (wantGround) {
                    renderGroundScenery(poseStack, bufferSource, packedLight, capturedYaw);
                }
            };
        }

        if (scenery != null) {
            PreviewEntityRegistry.register(state, animatable, scenery, null);
        } else {
            PreviewEntityRegistry.register(state, animatable);
        }

        Quaternionf cameraTilt = new Quaternionf().rotateX((float) Math.toRadians(-10.0 + pitch));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(cameraTilt);

        float entityScale = entity.getScale();
        float submitScale = zoom / entityScale;
        float rectCenterX = (x0 + x1) / 2.0F;
        float rectCenterY = (y0 + y1) / 2.0F;
        float translationX = (anchorX - rectCenterX) / submitScale;
        float translationY = (anchorY - rectCenterY) / submitScale + 0.8F + poseYOffset;
        if (wantBed) {
            state.bodyRot = yaw - 90;
        }

        Vector3f translation = new Vector3f(translationX, translationY, 0.0F);

        // 26.x：PIP 纹理尺寸必须为正，否则 createTexture 抛异常导致崩溃
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.entity(state, submitScale, translation, rotation, cameraTilt, x0, y0, x1, y1);
        guiGraphics.disableScissor();

        if (poseChanged) {
            entity.setPose(oldPose);
        }
    }

    private static void renderGroundScenery(PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight, float yaw) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0f, 0.0f, 1.0f);
                // TODO(26.x): 方块预览需改用 BlockModelResolver.update + SubmitNodeCollector.submitBlockModel，装饰性预览暂缓
            }
            poseStack.translate(1.0f, 0.0f, -3.0f);
        }
        poseStack.translate(-1.0f, 1.0f, 1.0f);
        // TODO(26.x): 方块预览需改用 BlockModelResolver.update + SubmitNodeCollector.submitBlockModel，装饰性预览暂缓
        poseStack.translate(0.0f, 0.0f, 1.0f);
        // TODO(26.x): 方块预览需改用 BlockModelResolver.update + SubmitNodeCollector.submitBlockModel，装饰性预览暂缓
        poseStack.popPose();
    }

    private static void renderBedScenery(PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight, float yaw) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        // TODO(26.x): 方块预览需改用 BlockModelResolver.update + SubmitNodeCollector.submitBlockModel，装饰性预览暂缓
        poseStack.popPose();
    }

    private static void renderVehicleScenery(
            PoseStack poseStack,
            SubmitNodeCollector bufferSource,
            int packedLight,
            float yaw,
            float partialTick,
            LivingEntity rider,
            EntityType<? extends Entity> vehicleType
    ) {
        if (rider.level() == null) {
            return;
        }
        Entity vehicle;
        try {
            vehicle = AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(vehicleType), () -> vehicleType.create(rider.level(), EntitySpawnReason.LOAD));
        } catch (java.util.concurrent.ExecutionException e) {
            return;
        }
        if (vehicle == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        double yOffset = -(vehicle.getPassengerRidingPosition(rider).y - vehicle.getY());
        poseStack.popPose();
    }
}
