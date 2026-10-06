package com.z80z99.z80zhealthbar.overlay.parts;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.overlay.*;
import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

public class MountHealthOverlay extends SimpleBarOverlay {

    // icons.png 心形坐标 (9x9)，坐骑心形按原版风格染橙色
    private static final int ICON_HEART_U = 52, ICON_HEART_V = 0;
    private static final int MOUNT_HEART_COLOR = 0xFFFF8C00;

    @Override
    public void renderOverlay(RenderGui renderGui, GuiGraphics graphics, float pt, int screenW, int screenH) {
        var cfg = ConfigManager.getConfig().overlay;
        var colors = ConfigManager.getConfig().colors;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !player.isPassenger()) return;

        var vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity mount)) return;
        if (!BarLayouts.visible("mount")) return; // 单条显示开关（编辑器组件级配置）

        float health = mount.getHealth();
        float maxHealth = mount.getMaxHealth();
        if (health >= maxHealth) return;

        int fillColor = ColorHelper.parseColor(colors.mountHealth);
        int boundColor = ColorHelper.parseColor(colors.mountHealthBound);
        int emptyColor = ColorHelper.parseColor(colors.mountHealthEmpty);

        Parameters params = new Parameters();
        params.value = health;
        params.maxValue = maxHealth;
        params.fillColor = fillColor;
        params.boundColor = boundColor;
        params.emptyColor = emptyColor;

        int barWidth = OverlayManager.length > 0 ? OverlayManager.length : FILL_FULL_WIDTH_LONG;
        int barH = cfg.overlayBarInnerHeight;
        int margin = cfg.overlayBarVerticalMargin;

        int left, top;
        OverlayPosition pos = cfg.mountHealthOnLeftSide ? OverlayPosition.LEFT : OverlayPosition.RIGHT;
        int[] free = BarLayouts.resolve("mount", screenW, screenH, barWidth, barH);
        if (free != null) {
            left = free[0];
            top = free[1];
        } else if (pos == OverlayPosition.LEFT) {
            left = OverlayManager.horizontalOffset;
            top = renderGui.getLeftHeight(margin);
            renderGui.setLeftHeight(top + barH + margin);
        } else {
            left = screenW - OverlayManager.horizontalOffset - barWidth;
            top = renderGui.getRightHeight(margin);
            renderGui.setRightHeight(top + barH + margin);
        }

        // Wave 11 美化：橙色染色心形图标（原版坐骑风格;编辑器可关/拆分独立偏移）
        if (BarLayouts.showIcon("mount")) {
            int[] io = BarLayouts.iconOffset("mount");
            int ix = left - 11 + io[0], iy = top + io[1];
            drawIconColor(graphics, ix, iy, barH, ICON_HEART_U, ICON_HEART_V, MOUNT_HEART_COLOR);
            BarLayouts.recordIcon("mount", ix, iy, 9, 9);
        }

        drawBarCard(graphics, left, top, barWidth, barH);
        HudBarPainter.drawRatioFill(graphics, left, top, barWidth, barH,
                health / maxHealth, fillColor);
        BarLayouts.record("mount", left, top, barWidth, barH);
    }
}
