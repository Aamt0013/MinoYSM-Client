package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import rip.ysm.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;

public class CustomPlayerElytraLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private static final Identifier WINGS_LOCATION = Identifier.parse("textures/entity/equipment/wings/elytra.png");

    private final ElytraModel elytraModel;

    public CustomPlayerElytraLayer(EntityRendererProvider.Context context) {
        this.elytraModel = new ElytraModel(context.getModelSet().bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 26.x 的 submitModel 必须要有有效的 collector。
        // 个性化设置界面（ModelSettingsScreen）走的是屏幕渲染路径，直接调 renderEntity，
        // 那里没有 PIP 上下文，RenderContext.collector() 为 null，传下来就是 null ——
        // 不拦会 NPE 崩掉整个界面。护甲层和手持物层本来就有同样的守卫。
        if (bufferSource == null) {
            return;
        }
        Identifier cloakTextureLocation;
        LivingEntity entity = entityLivingBaseIn.getEntity();
        ItemStack stack = CosmeticArmorHelper.getElytraItem(entity);
        AnimatedGeoModel animatedGeoModel = entityLivingBaseIn.getCurrentModel();
        if (!stack.isEmpty() && animatedGeoModel != null && !animatedGeoModel.elytraBones().isEmpty() && (entity instanceof AbstractClientPlayer abstractClientPlayer)) {
            net.minecraft.world.entity.player.PlayerSkin skin = abstractClientPlayer.getSkin();
            if (skin.elytra() != null) {
                cloakTextureLocation = skin.elytra().texturePath();
            } else if (skin.cape() != null && abstractClientPlayer.isModelPartShown(PlayerModelPart.CAPE)) {
                cloakTextureLocation = skin.cape().texturePath();
            } else {
                cloakTextureLocation = WINGS_LOCATION;
            }
            poseStack.pushPose();
            renderElytra(poseStack, animatedGeoModel);
//            poseStack.translate(0.0d, 1.5d, 0.0d);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            this.elytraModel.setupAnim(state);
            // 26.x：ItemRenderer.getFoilBuffer 已移除，改为 submitModel 提交（附魔光效交给渲染管线处理）
            bufferSource.submitModel(this.elytraModel, state, poseStack,
                    RenderTypes.armorCutoutNoCull(cloakTextureLocation),
                    packedLightIn, OverlayTexture.NO_OVERLAY, -1, null);
            poseStack.popPose();
        }
    }

    public void renderElytra(PoseStack poseStack, AnimatedGeoModel model) {
        RenderUtils.prepMatrixForLocator(poseStack, model.elytraBones());
    }
}