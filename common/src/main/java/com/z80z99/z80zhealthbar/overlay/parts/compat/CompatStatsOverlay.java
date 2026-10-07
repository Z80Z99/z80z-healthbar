package com.z80z99.z80zhealthbar.overlay.parts.compat;

import com.z80z99.z80zhealthbar.compat.CompatAdapters;
import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.OverlayManager;
import com.z80z99.z80zhealthbar.overlay.OverlayPosition;
import com.z80z99.z80zhealthbar.overlay.RenderGui;
import com.z80z99.z80zhealthbar.overlay.SimpleBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * ASTEORBAR 布局下的兼容状态条组（数据源统一为 CompatAdapters）。
 * 任一适配器可用即渲染；各行 = 卡片条 + lang 文本（max 未知时仅文本）。
 * 继承 SimpleBarOverlay 以获得布局位置（CENTER/LEFT/RIGHT）能力。
 */
public class CompatStatsOverlay extends SimpleBarOverlay {

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        List<CompatAdapters.Stat> stats = CompatAdapters.collect(player);
        if (stats.isEmpty()) return;

        var cfg = ConfigManager.getConfig().overlay;
        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;
        int blockH = stats.size() * (barH + margin) - margin;

        int left, top;
        OverlayPosition pos = getDefinedPosition();
        if (pos == OverlayPosition.RIGHT) {
            left = screenW - OverlayManager.horizontalOffset - barWidth;
            top = renderGui.getRightHeight(margin);
            renderGui.setRightHeight(top + blockH + margin);
        } else if (pos == OverlayPosition.CENTER) {
            left = centerBarLeft(screenW, barWidth);
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + blockH + margin);
        } else {
            left = OverlayManager.horizontalOffset;
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + blockH + margin);
        }

        // compat 组件编辑项（参考其它组件形式）：显示数值文本开关 + 行文本模板（{name} {value} {max}）
        // 只读探查：长条管线读自定义布局里的 compat 行配置;组件不存在时用默认（显示文本）,
        // 不得经 get() 插回配置（删除的长条 compat 组会被复活）
        var compatC = com.z80z99.z80zhealthbar.config.ConfigManager.getConfig().hudLayout
                .peek(com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig.COMPAT);
        boolean showText = compatC == null || compatC.showText;
        String rowFmt = compatC == null || compatC.textFormat == null ? "" : compatC.textFormat;

        for (CompatAdapters.Stat stat : stats) {
            drawBarCard(graphics, left, top, barWidth, barH);
            if (stat.max() != null && stat.max() > 0) {
                com.z80z99.z80zhealthbar.overlay.HudBarPainter.drawRatioFill(graphics,
                        left, top, barWidth, barH,
                        (float) Math.max(0d, Math.min(1d, stat.value() / stat.max())), stat.color());
            }
            if (showText) {
                String text;
                if (!rowFmt.isBlank()) {
                    // {value}=当前值 {max}=上限（纯值,不带 "/上限" 后缀）——此前二者都走
                    // format(v,max),"饱和度: {value}/{max}" 会渲染成 "12.5/20/20.0/20"
                    text = rowFmt
                            .replace("{name}", net.minecraft.network.chat.Component.translatable(
                                    stat.langKey()).getString())
                            .replace("{value}", valueOnly(stat.value()))
                            .replace("{max}", stat.max() == null ? "—" : maxOnly(stat.max()));
                } else {
                    text = net.minecraft.network.chat.Component.translatable(
                            stat.langKey()).getString() + ": " + format(stat.value(), stat.max());
                }
                OverlayManager.addStringRender(text,
                        left + barWidth + 5, top + barH / 2 - 4, 0xFFFFFFFF, OverlayManager.ALIGN_LEFT);
            }
            top += barH + margin;
        }

        // AppleSkin 手持食物预览（手持食物时一行文本）
        float[] food = com.z80z99.z80zhealthbar.compat.CompatAdapters.AppleSkinAdapter.heldFoodValues(player);
        if (food != null) {
            OverlayManager.addStringRender(
                    net.minecraft.network.chat.Component.translatable(
                            "z80zhealthbar.stat.appleskin.preview",
                            (int) food[0], String.format(java.util.Locale.ROOT, "%.1f", food[1])).getString(),
                    left, top, 0xFFE8D0A0, OverlayManager.ALIGN_LEFT);
        }
    }

    private static String format(float v, Float max) {
        String vs = valueOnly(v);
        if (max == null) return vs;
        return vs + "/" + maxOnly(max);
    }

    /** 当前值文本（无 "/上限" 后缀;模板 {value} 与默认拼接共用） */
    private static String valueOnly(float v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** 上限文本：整数化——与自定义管线的 Math.round(max) 输出一致（两条管线同模板同结果） */
    private static String maxOnly(float m) {
        return m >= 100 ? String.valueOf(Math.round(m)) : String.format(java.util.Locale.ROOT, "%.0f", m);
    }
}
