package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public class ExperienceBarOverlay extends SimpleBarOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int expLevel = HudPreviewState.active ? HudPreviewState.xpLevel : player.experienceLevel;
        float expProgress = HudPreviewState.active ? HudPreviewState.xpProgress : player.experienceProgress;
        if (!BarLayouts.visible("experience")) return; // 单条显示开关（编辑器组件级配置）
        if (hideUnchanged(expLevel + expProgress)) return; // 数值长期不变时隐藏

        int fillColor = ColorHelper.parseColor(colors.experience);
        int boundColor = ColorHelper.parseColor(colors.experienceBound);
        int emptyColor = ColorHelper.parseColor(colors.experienceEmpty);

        // 经验条动态（dynamicFx.smooth/healGlow 复用）：BarFx 平滑填充 + 获得/失去经验脉冲
        // （progress 变化→flash;level 提升/大量获得→heal 泛绿——实体条同款语义）
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        long nowMs = System.currentTimeMillis();
        var fxSt = com.z80z99.z80zhealthbar.mobdisplay.BarFx.tick(
                com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.fxKey(player.getId() ^ 0x5EED),
                Math.max(0f, Math.min(1f, expProgress)), false, nowMs);
        float dispProgress = dxCfg.enabled && dxCfg.smooth
                ? Math.max(0f, Math.min(1f, fxSt.display())) : expProgress;

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        int left, top;
        OverlayPosition pos = getDefinedPosition();
        int[] free = BarLayouts.resolve("experience", screenW, screenH, barWidth, barH);
        if (free != null) {
            left = free[0];
            top = free[1];
        } else if (pos == OverlayPosition.CENTER) {
            // Wave 11 修复：物品栏上方布局真正水平居中
            left = centerBarLeft(screenW, barWidth);
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + barH + margin);
        } else if (pos == OverlayPosition.LEFT) {
            left = OverlayManager.horizontalOffset;
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + barH + margin);
        } else {
            left = screenW - OverlayManager.horizontalOffset - barWidth;
            top = renderGui.getRightHeight(margin);
            renderGui.setRightHeight(top + barH + margin);
        }

        // Wave 11 美化：卡片 + 统一填充（高光/压暗由共享原语提供）
        int dynFill = fillColor;
        if (dxCfg.enabled && dxCfg.healGlow) {
            dynFill = ColorHelper.lerp(dynFill, 0xFF50E080, fxSt.heal() * 0.5f);
        }
        drawBarCard(graphics, left, top, barWidth, barH);
        HudBarPainter.drawRatioFill(graphics, left, top, barWidth, barH, dispProgress, dynFill);
        drawSheen(graphics, left, top, barWidth, barH,
                (int) (dispProgress * HudBarPainter.innerWidth(barWidth)));

        // 显示等级/进度文本（Wave 11：移至条内居中,原版经验条风格;组件化:可关/拆分独立偏移）
        // 模板优先：textFormat 支持 {level} {xp_percent}（14:百分比文本数据开放）
        if ((cfg.displayExperienceLevel || cfg.displayExperienceProgress) && BarLayouts.showText("experience")) {
            String text;
            var layoutC = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().hudLayout
                    .get(com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.EXPERIENCE);
            if (layoutC.textFormat != null && !layoutC.textFormat.isBlank()) {
                text = com.z80z99.z80zhealthbar.overlay.CustomHudRenderer.formatText(layoutC.textFormat, player);
            } else {
                StringBuilder sb = new StringBuilder();
                if (cfg.displayExperienceLevel) sb.append(expLevel);
                if (cfg.displayExperienceProgress) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(String.format("%.0f%%", expProgress * 100));
                }
                text = sb.toString();
            }
            int[] to = BarLayouts.textOffset("experience");
            int textX = left + barWidth / 2 + to[0];
            int textY = top + barH / 2 - 4 + ConfigManager.getConfig().overlay.overlayBarTextOffsetY + to[1];
            OverlayManager.addStringRender(text, textX, textY, 0xFFFFFFFF);
            BarLayouts.recordText("experience", textX - mc.font.width(text) / 2, textY,
                    mc.font.width(text), 9);
        }
        BarLayouts.record("experience", left, top, barWidth, barH);
    }

    /** 扫光（与生命条同款） */
    private void drawSheen(GuiGraphics graphics, int left, int top, int barWidth, int barH, int fillW) {
        var dxCfg = ConfigManager.getConfig().dynamicFx;
        if (!dxCfg.enabled || !dxCfg.sheen || fillW <= 0) return;
        int innerW = HudBarPainter.innerWidth(barWidth);
        int[] band = com.z80z99.z80zhealthbar.overlay.HudFx.sheenBand(innerW, fillW,
                System.currentTimeMillis(), 0);
        if (band == null) return;
        int x0 = left + HudBarPainter.INSET + band[0];
        int x1 = left + HudBarPainter.INSET + band[1];
        int edge = com.z80z99.z80zhealthbar.overlay.HudFx.sheenEdgeW(innerW);
        int y0 = HudBarPainter.fillTop(top, barH);
        int h = HudBarPainter.innerHeight(barH);
        if (h <= 0) return;
        graphics.fill(x0 + edge, y0, x1, y0 + h, 0x10FFFFFF);
        graphics.fill(x0, y0, Math.min(x0 + edge, x1), y0 + h, 0x20FFFFFF);
    }
}
