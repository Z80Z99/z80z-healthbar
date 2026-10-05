package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import com.z80z99.z80zhealthbar.util.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseOverlay {

    // Wave 11 美化：vanilla HUD 图标纹理（心形/鸡腿/气泡/护甲），9x9 切图
    public static final ResourceLocation VANILLA_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");

    public static final int FILL_FULL_WIDTH_LONG = 182;
    public static final int BOUND_FULL_WIDTH_LONG = 184;
    public static final int BOUND_FULL_WIDTH_SHORT = 95;
    public static final int Y_REGENERATION_FILL = 45;
    public static final int Y_FOOD_EXHAUSTION_FILL = 45;
    public static final int Y_EXPERIENCE_DECORATION = 36;
    public static final int Y_RIGHT_DECORATION = 45;
    public static final int Y_LEFT_DECORATION = 54;

    protected final List<BaseOverlay> overrideOverlay = new ArrayList<>();
    protected int tick;

    public void addOverrideOverlay(BaseOverlay overlay) {
        overrideOverlay.add(overlay);
    }

    public boolean shouldOverride() {
        return false;
    }

    /** 最终渲染入口 - 处理 override 链路和开关 */
    public void render(RenderGui renderGui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        var cfg = ConfigManager.getConfig().overlay;
        if (!cfg.enableOverlay) return;

        tick = renderGui.gui().getGuiTicks();

        // 检查是否有 override overlay
        for (BaseOverlay override : overrideOverlay) {
            if (override.shouldOverride()) {
                override.render(renderGui, graphics, partialTick, screenWidth, screenHeight);
                return;
            }
        }
        renderOverlay(renderGui, graphics, partialTick, screenWidth, screenHeight);
    }

    public abstract void renderOverlay(RenderGui renderGui, GuiGraphics graphics,
                                        float partialTick, int screenWidth, int screenHeight);

    // === 绘图辅助方法 ===

    protected void drawTextureFill(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        GuiHelper.drawTexturedRect(g, x, y, u, v, w, h);
    }

    protected void drawTextureFillColor(GuiGraphics g, int x, int y, int w, int h,
                                         int u, int v, int texW, int texH, int color) {
        GuiHelper.drawTexturedRectColor(g, x, y, x + w, y + h,
                u, v, u + w, v + h, texW, texH, color);
    }

    protected void drawEmptyFill(GuiGraphics g, int x, int y, int x2, int y2, int color) {
        GuiHelper.drawSolidColor(g, x, y, x2, y2, color);
    }

    // === Wave 11 美化辅助 ===

    /** 绘制 9x9 vanilla 图标（心形/鸡腿/气泡/护甲），垂直居中于条；x 为图标左上角 */
    protected void drawIcon(GuiGraphics g, int x, int y, int barH, int u, int v) {
        int iconY = y + (barH - 9) / 2;
        GuiHelper.drawTexturedRect(VANILLA_ICONS, g, x, iconY, u, v, 9, 9);
    }

    /** 绘制 9x9 染色 vanilla 图标（如坐骑橙色心形） */
    protected void drawIconColor(GuiGraphics g, int x, int y, int barH, int u, int v, int color) {
        int iconY = y + (barH - 9) / 2;
        GuiHelper.drawTexturedRectColor(VANILLA_ICONS, g, x, iconY, u, v, 9, 9, color);
    }

    /** 统一现代 HUD 卡片：旧 ASTEORBAR 各条与 CUSTOM 共用同一视觉原语。 */
    protected void drawBarCard(GuiGraphics g, int x, int y, int w, int h) {
        HudBarPainter.drawCard(g, x, y, w, h);
    }

    /** 居中计算：物品栏上方/中心布局时条真正水平居中（符合原版 HUD 逻辑） */
    protected int centerBarLeft(int screenW, int barWidth) {
        return (screenW - barWidth) / 2;
    }

    /** 1px 边框线（低血量/饥饿闪烁边框用，覆盖卡片默认边框） */
    protected void drawBound(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int alpha = ColorHelper.getAlpha(color);
        if (alpha > 0) {
            g.fill(x1, y1, x2, y1 + 1, color);
            g.fill(x1, y2 - 1, x2, y2, color);
            g.fill(x1, y1, x1 + 1, y2, color);
            g.fill(x2 - 1, y1, x2, y2, color);
        }
    }

    /** 二级填充（吸收 mode 1 半透明叠在血条之上），由 SimpleBarOverlay 渲染时调用 */
    protected void drawSecondFill(GuiGraphics g, int x, int y, int w, int h,
                                   SimpleBarOverlay.Parameters p) {
        if (p.secondValue <= 0) return;
        int fillW = (int) Math.max(0, Math.min(w, p.secondValue / p.maxValue * w));
        int alpha = (int) (p.secondFillAlpha * 255) & 0xFF;
        int color = (alpha << 24) | (p.secondFillColor & 0x00FFFFFF);
        GuiHelper.drawSolidColor(g, x, y, x + fillW, y + h, color);
    }

    /** 描边外扩填充（吸收 mode 2，bound 区域额外生长） */
    protected void drawBoundFill(GuiGraphics g, int x, int y, int w, int h,
                                  SimpleBarOverlay.Parameters p) {
        if (p.boundValue <= 0) return;
        int expand = (int) Math.max(0, Math.min(w, p.boundValue / p.maxValue * w));
        int alpha = (int) (p.boundFillAlpha * 255) & 0xFF;
        int color = (alpha << 24) | (p.boundFillColor & 0x00FFFFFF);
        // 上方扩
        GuiHelper.drawSolidColor(g, x, y - 1, x + w, y, color);
        // 下方扩
        GuiHelper.drawSolidColor(g, x, y + h, x + w, y + h + 1, color);
        // 右侧扩（按 expand 比例）
        GuiHelper.drawSolidColor(g, x + w, y, x + w + expand, y + h, color);
    }
}
