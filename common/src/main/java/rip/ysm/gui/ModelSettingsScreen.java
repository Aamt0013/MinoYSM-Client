package rip.ysm.gui;

import com.elfmcys.yesstevemodel.client.renderer.RenderContext;

import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import rip.ysm.gui.components.BooleanOptionRow;
import rip.ysm.gui.components.RadioOptionRow;
import rip.ysm.gui.components.SliderOptionRow;
import rip.ysm.gui.components.groups.IdentifiedGroup;
import rip.ysm.gui.molang.MolangOption;

import java.util.ArrayList;
import java.util.List;

public class ModelSettingsScreen extends OptionScreen {

    private final ModelAssembly modelAssembly;

    private final AnimatableEntity<?> animatable;

    @Nullable
    private final String initialGroupId;

    private int previewLeft, previewTop, previewRight, previewBottom;

    private float yaw = 200.0f;

    private float pitch = 0.0f;

    private float zoom = 90.0f;

    private float offsetX = 0.0f;

    private float offsetY = 0.0f;

    private boolean draggingPreview;

    private int draggingButton = -1;

    public ModelSettingsScreen(ModelAssembly modelAssembly, AnimatableEntity<?> animatable, @Nullable Screen parent, @Nullable String initialGroupId) {
        super(Component.translatable("gui.yes_steve_model.model_settings.title"), parent);
        this.modelAssembly = modelAssembly;
        this.animatable = animatable;
        this.initialGroupId = initialGroupId;
    }

    @Override
    protected int computePanelWidth() {
        return Math.min(this.width - 40, 640);
    }

    @Override
    protected int computePanelHeight() {
        return Math.min(this.height - 40, 360);
    }

    @Override
    protected boolean shouldUseCompactTabs() {
        return this.width < 620;
    }

    @Override
    protected int computeRowAreaRight() {
        return panelRight - previewWidth() - 4;
    }

    private int previewWidth() {
        if (compactTabs) {
            int panelW = panelRight - panelLeft;
            return Mth.clamp(panelW / 3, 110, 180);
        }
        return 200;
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(applyBtn);
        removeWidget(undoBtn);
        removeWidget(cancelBtn);
        applyBtn.visible = false;
        undoBtn.visible = false;
        cancelBtn.visible = false;
        applyBtn.active = false;
        undoBtn.active = false;
        saveBtn.setMessage(Component.translatable("gui.yes_steve_model.config.done"));
        saveBtn.setX(panelRight - saveBtn.getWidth());
        previewLeft = panelRight - previewWidth();
        previewTop = rowAreaTop;
        previewRight = panelRight;
        previewBottom = panelBottom - 60;
        if (initialGroupId != null) {
            for (OptionGroup g : groups) {
                if (g instanceof IdentifiedGroup ig && initialGroupId.equals(ig.id)) {
                    selectGroup(g);
                    break;
                }
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parentScreen);
    }

    @Override
    protected void collectBlurRegions(List<int[]> out) {
        super.collectBlurRegions(out);
        out.add(new int[]{previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop});
    }

    @Override
    protected void registerGroups() {
        List<ExtraAnimationButtons> ordered = new ArrayList<>(modelAssembly.getModelData().getModelProperties().getExtraAnimationButtons().values());
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (ExtraAnimationButtons cfgGroup : ordered) {
            IdentifiedGroup g = new IdentifiedGroup(cfgGroup.getId(), groupLabel(cfgGroup));
            int formIndex = 0;
            for (AbstractConfig form : cfgGroup.getConfigForms()) {
                OptionRow<?> row = buildRow(cfgGroup.getId(), formIndex, form);
                if (row != null) g.add(row);
                formIndex++;
            }
            groups.add(g);
        }
    }

    private String groupLabel(ExtraAnimationButtons group) {
        String fallback = group.getName() == null || group.getName().isEmpty() ? group.getId() : group.getName();
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.name".formatted(group.getId()), fallback);
    }

    @Nullable
    private OptionRow<?> buildRow(String groupId, int formIndex, AbstractConfig form) {
        String title = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.title".formatted(groupId, formIndex), form.getTitle());
        String desc = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.description".formatted(groupId, formIndex), form.getDescription());
        if (form instanceof CheckboxConfig cfg) {
            return new BooleanOptionRow(0, 0, 0, 22, MolangOption.ofBoolean(title, desc, animatable, cfg.getValue()));
        }
        if (form instanceof RangeConfig cfg) {
            return new SliderOptionRow(0, 0, 0, 22, MolangOption.ofDouble(title, desc, animatable, cfg.getValue()), cfg.getMin(), cfg.getMax(), cfg.getStep(), "");
        }
        if (form instanceof RadioConfig cfg) {
            OrderedStringMap<String, String> labels = cfg.getLabels();
            List<String> texts = new ArrayList<>(labels.size());
            String[] writeExprs = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) {
                texts.add(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.labels.%d".formatted(groupId, formIndex, i), labels.getKeyAt(i)));
                writeExprs[i] = labels.getValueAt(i);
            }
            return new RadioOptionRow(0, 0, 0, 22, MolangOption.ofIndex(title, desc, animatable, cfg.getValue(), writeExprs), texts);
        }
        return null;
    }

    @Override
    protected void renderExtras(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(previewLeft, previewTop, previewRight, previewBottom, 0x66000000);
        renderPreview(g, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor g, float partialTick) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!(animatable instanceof LivingAnimatable<?> la)) return;

        // 走 PIP 提交（guiGraphics.entity），而不是直接调 renderEntity。
        //
        // 屏幕渲染路径没有 PIP 上下文，RenderContext.collector() 为 null，
        // 而 26.x 的模型绘制依赖 SubmitNodeCollector 提交自定义几何 ——
        // collector 为 null 时 IGeoRenderer 会直接跳过绘制，预览区就是空的。
        //
        // 平移量通过偏移预览矩形实现，并夹在面板内，避免画出裁剪区。
        int x0 = previewLeft;
        int y0 = previewTop;
        int x1 = previewRight;
        int y1 = previewBottom;
        int dx = (int) offsetX;
        int dy = (int) offsetY;
        // 限制平移范围，保证矩形不越出面板
        int maxDx = Math.max(0, (x1 - x0) / 2);
        int maxDy = Math.max(0, (y1 - y0) / 2);
        dx = Mth.clamp(dx, -maxDx, maxDx);
        dy = Mth.clamp(dy, -maxDy, maxDy);
        x0 += dx;
        x1 += dx;
        y0 += dy;
        y1 += dy;

        // 26.x：用 DeltaTracker 的帧间插值，Screen#extractRenderState 的 partialTick
        // 每帧在 ~0 与 ~1 之间跳变，会让模型姿态抽搐
        float framePartialTick = Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(false);

        ModelPreviewRenderer.submitSettingsPreview(
                g, x0, y0, x1, y1, zoom, pitch, yaw, framePartialTick, la);
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isInPreview(event.x(), event.y())) {
            draggingPreview = true;
            draggingButton = event.button();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPreview && event.button() == draggingButton) {
            draggingPreview = false;
            draggingButton = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        if (draggingPreview && button == draggingButton) {
            if (button == 0) {
                yaw = (float) (yaw + dragX * 1.2);
                pitch = Mth.clamp((float) (pitch - dragY * 0.8), -85.0f, 85.0f);
            } else if (button == 1) {
                offsetX = (float) (offsetX + dragX);
                offsetY = (float) (offsetY + dragY);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (isInPreview(mouseX, mouseY)) {
            zoom = Mth.clamp((float) (zoom * (1.0 + delta * 0.1)), 30.0f, 400.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInPreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX < previewRight && mouseY >= previewTop && mouseY < previewBottom;
    }

}
