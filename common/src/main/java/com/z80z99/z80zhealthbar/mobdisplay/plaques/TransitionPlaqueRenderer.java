package com.z80z99.z80zhealthbar.mobdisplay.plaques;

import com.z80z99.z80zhealthbar.util.ColorHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** 带平滑颜色过渡的牌匾渲染器基类 - 移植自 MobPlaques */
public abstract class TransitionPlaqueRenderer {

    private final int defaultHighColor;
    private final int defaultLowColor;
    private boolean shiftColors = true;

    protected TransitionPlaqueRenderer(int highColor, int lowColor) {
        this.defaultHighColor = highColor;
        this.defaultLowColor = lowColor;
    }

    public abstract int getValue(LivingEntity entity);

    public abstract int getMaxValue(LivingEntity entity);

    public void setShiftColors(boolean shift) {
        this.shiftColors = shift;
    }

    /** 获取基于当前值百分比的过渡颜色 */
    protected int getColor(LivingEntity entity) {
        if (!shiftColors) return defaultHighColor;
        float ratio = getValuePercentage(entity);
        return getTransitionedColor(defaultHighColor, defaultLowColor, ratio);
    }

    protected float getValuePercentage(LivingEntity entity) {
        int value = getValue(entity);
        int max = getMaxValue(entity);
        if (max <= 0) return 0f;
        return Mth.clamp((float) value / max, 0f, 1f);
    }

    protected boolean belowMaxValue(LivingEntity entity) {
        return getValue(entity) < getMaxValue(entity);
    }

    /** RGB 颜色插值 - 移植自 MobPlaques */
    public static int getTransitionedColor(int highColor, int lowColor, float ratio) {
        int r1 = (highColor >> 16) & 0xFF;
        int g1 = (highColor >> 8) & 0xFF;
        int b1 = highColor & 0xFF;

        int r2 = (lowColor >> 16) & 0xFF;
        int g2 = (lowColor >> 8) & 0xFF;
        int b2 = lowColor & 0xFF;

        int r = (int)(r2 + (r1 - r2) * ratio);
        int g = (int)(g2 + (g1 - g2) * ratio);
        int b = (int)(b2 + (b1 - b2) * ratio);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
