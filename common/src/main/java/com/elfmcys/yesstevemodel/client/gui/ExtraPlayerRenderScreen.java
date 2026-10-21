package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;


public class ExtraPlayerRenderScreen extends Screen {

    private static final char RESET_KEY = 'r';

    private int mouseStartX;

    private int mouseStartY;

    private float rotationX;

    private float rotationY;

    private boolean isDragging;

    private boolean isRightDragging;

    private int offsetX;

    private int offsetY;

    public ExtraPlayerRenderScreen() {
        super(Component.literal("YSM Extra Player Render Config GUI"));
        this.isDragging = false;
        this.isRightDragging = false;
        this.offsetX = 5;
        this.offsetY = 1;
        this.mouseStartX = ExtraPlayerRenderConfig.PLAYER_POS_X.get().intValue();
        this.mouseStartY = ExtraPlayerRenderConfig.PLAYER_POS_Y.get().intValue();
        this.rotationX = ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue();
        this.rotationY = ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue();
        if (PauseScreenButtonBuilder.isServerConnected()) {
            this.offsetX = 16;
            this.offsetY = 0;
        }
    }

    public void init() {
        clearWidgets();
        int i = -30;
        if (PauseScreenButtonBuilder.isServerConnected()) {
            addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> {
                resetTransform();
            }).bounds((this.width / 2) - 50, this.height - 35, 100, 30).build());
            i = -60;
        }
        MutableComponent mutableComponentTranslatable = Component.translatable("gui.yes_steve_model.hide_or_show");
        int iWidth = this.font.width(mutableComponentTranslatable) + 24;
        addRenderableWidget(Checkbox.builder(mutableComponentTranslatable, this.font)
                .pos((this.width - iWidth) / 2, this.height + i)
                .selected(ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get().booleanValue())
                .onValueChange((cb, value) -> ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.set(Boolean.valueOf(value)))
                .build());
    }

    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int boxLeft = this.mouseStartX;
        int boxTop = this.mouseStartY;
        int boxRight = (int) (boxLeft + (this.rotationX));
        int boxBottom = (int) (boxTop + (this.rotationX * 2.0f));
        guiGraphics.verticalLine((this.width / 2) - 1, -2, this.height + 2, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, (this.height / 2) - 1, -1610612737);
        guiGraphics.verticalLine(10, -2, this.height + 2, -1610612737);
        guiGraphics.verticalLine(this.width - 10, -2, this.height + 2, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, 10, -1610612737);
        guiGraphics.horizontalLine(-2, this.width + 2, this.height - 10, -1610612737);
        guiGraphics.verticalLine(boxLeft, boxTop, boxBottom, -65536);
        guiGraphics.verticalLine(boxRight, boxTop, boxBottom, -65536);
        guiGraphics.horizontalLine(boxLeft, boxRight, boxTop, -65536);
        guiGraphics.horizontalLine(boxLeft, boxRight, boxBottom, -65536);
        guiGraphics.fillGradient(boxLeft, boxTop, boxRight, boxBottom, 1342177279, 1342177279);
        guiGraphics.fillGradient(boxLeft - this.offsetX, boxTop - this.offsetX, boxLeft + this.offsetX, boxTop + this.offsetX, -16711777, -16711777);
        guiGraphics.fillGradient(boxRight - this.offsetX, boxBottom - this.offsetX, boxRight + this.offsetX, boxBottom + this.offsetX, -16777057, -16777057);
        int tipY = 15;
        for (FormattedCharSequence formattedCharSequence : this.font.split(Component.translatable("gui.yes_steve_model.extra_player_render.tips"), 500)) {
            guiGraphics.text(this.font, formattedCharSequence, (this.width - 15) - this.font.width(formattedCharSequence), tipY, -1);
            tipY += 10;
        }
        if (Minecraft.getInstance().player != null && !ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get().booleanValue()) {
            ModelPreviewRenderer.submitPlayerOverlay(guiGraphics, Minecraft.getInstance().player, this.mouseStartX, this.mouseStartY, this.rotationX, this.rotationY, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractBlurredBackground(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics) {

    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean inLeftHandleX = ((double) (this.mouseStartX - this.offsetX)) < mouseX && mouseX < ((double) (this.mouseStartX + this.offsetX));
        boolean inLeftHandleY = ((double) (this.mouseStartY - this.offsetX)) < mouseY && mouseY < ((double) (this.mouseStartY + this.offsetX));
        if (button == 0 && inLeftHandleX && inLeftHandleY) {
            this.isDragging = true;
        }
        int rightHandleX = (int) (this.mouseStartX + (this.rotationX));
        int rightHandleY = (int) (this.mouseStartY + (this.rotationX * 2.0f));
        boolean inRightHandleX = ((double) (rightHandleX - this.offsetX)) < mouseX && mouseX < ((double) (rightHandleX + this.offsetX));
        boolean inRightHandleY = ((double) (rightHandleY - this.offsetX)) < mouseY && mouseY < ((double) (rightHandleY + this.offsetX));
        if (button == 0 && inRightHandleX && inRightHandleY) {
            this.isRightDragging = true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = this.isDragging || this.isRightDragging;
        this.isDragging = false;
        this.isRightDragging = false;
        // 拖拽结束立即落盘：防止用户调好位置后直接杀进程（手机端常见），
        // 那时既不会走 onClose() 也来不及触发正常退出的自动保存。
        if (wasDragging) {
            persistTransform();
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.isRightDragging) {
            // 原实现 Math.min(...) 直接取鼠标位移，向左/上拖动会得到负值。
            // 该值会写回 PLAYER_SCALE 并在渲染时换算成 PIP 纹理尺寸，
            // 负数会导致 width/height 为负 → GpuDevice.createTexture 抛异常崩溃。
            // 这里按配置声明的合法区间 [8, 360] 钳制。
            double raw = Math.min(mouseX - this.mouseStartX, (mouseY - this.mouseStartY) / 2.0d);
            if (!Double.isFinite(raw)) {
                return true;
            }
            this.rotationX = (float) Math.max(8.0d, Math.min(360.0d, raw));
            return true;
        }
        if (this.isDragging) {
            this.mouseStartX = (int) mouseX;
            this.mouseStartY = (int) mouseY;
            return true;
        }
        if (event.button() == this.offsetY) {
            this.rotationY += (float) (dragX * 2.0d);
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        int codePoint = event.codepoint();
        if (Character.toLowerCase(codePoint) == RESET_KEY && Minecraft.getInstance().hasAltDown()) {
            resetTransform();
        }
        return super.charTyped(event);
    }

    private void resetTransform() {
        this.mouseStartX = 10;
        this.mouseStartY = 10;
        this.rotationX = 40.0f;
        this.rotationY = 5.0f;
    }

    /**
     * 把界面上的拖拽结果写回配置并立即落盘。
     *
     * <p>拆成独立方法是为了能从多个退出路径调用：
     * <ul>
     *   <li>{@link #onClose()} —— 按 ESC / 关闭按钮</li>
     *   <li>{@link #removed()} —— 屏幕被移除（手机端返回键、手势返回走这里）</li>
     *   <li>{@link #mouseReleased} —— 拖拽结束，防止"调好位置后直接杀进程"</li>
     * </ul>
     */
    private void persistTransform() {
        ExtraPlayerRenderConfig.PLAYER_POS_X.set(Integer.valueOf(this.mouseStartX));
        ExtraPlayerRenderConfig.PLAYER_POS_Y.set(Integer.valueOf(this.mouseStartY));
        // 兜底：确保写回的缩放在合法区间内。ForgeConfigSpec 的 set 不校验 defineInRange，
        // 非法值（如负缩放）会直接进入内存并导致渲染崩溃。
        float safeScale = this.rotationX;
        if (!Float.isFinite(safeScale)) {
            safeScale = 40.0f;
        }
        safeScale = Math.max(8.0f, Math.min(360.0f, safeScale));
        ExtraPlayerRenderConfig.PLAYER_SCALE.set(Double.valueOf(safeScale));
        ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.set(Double.valueOf(this.rotationY));

        // 必须显式落盘。
        //
        // ForgeConfigSpec 的 set() 只改内存里的 ConfigValue，不会写回 config/*.toml。
        // 真正的持久化依赖 NightConfig 的自动保存，而它只在游戏正常退出时可靠触发。
        // 手机端（PojavLauncher 等）"大退"是直接杀进程，走不到关闭钩子，
        // 配置就丢了，下次进来位置又回到默认值。
        //
        // ConfigValue.save() 保存的是该值所属的整个配置文件，
        // 因此调任意一个字段的 save() 即可（ExtraPlayerRenderConfig 与
        // GeneralConfig 共用同一个 spec，见 GeneralConfig.buildSpec）。
        ExtraPlayerRenderConfig.save();
    }

    @Override
    public void removed() {
        // 手机端用返回键/手势退出时不会走 onClose()，只有 removed() 一定被调用
        persistTransform();
        super.removed();
    }

    @Override
    public void onClose() {
        persistTransform();
        super.onClose();
    }

}
