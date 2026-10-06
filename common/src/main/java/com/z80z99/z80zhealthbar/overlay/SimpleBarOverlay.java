package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class SimpleBarOverlay extends BaseOverlay {

    /** 渲染参数 - 包含条形的颜色、数值信息 */
    public static class Parameters {
        public int fillColor;
        public int boundColor;
        public int emptyColor;
        public double value;
        public double maxValue;
        public int verticalShift;
        public boolean blink;

        // === V1 Wave 3 扩容：支持吸收值 mode 1 (STACK) 与 mode 2 (BOUND)，mobhealthbar 仿真 ===
        /** 二级填充色（吸收 mode 1 半透明叠在血条上） */
        public int secondFillColor = 0;
        /** 二级填充透明度 0..1（mode 1 用） */
        public float secondFillAlpha = 0.66f;
        /** 二级填充值（吸收值），与 value 同量纲 */
        public double secondValue = 0;
        /** 描边填充色（吸收 mode 2 描边外扩） */
        public int boundFillColor = 0;
        /** 描边填充值（吸收 mode 2 外扩比例） */
        public double boundValue = 0;
        /** 描边填充透明度 0..1（mode 2 用） */
        public float boundFillAlpha = 0.9f;

        /** 三种文本插槽（中心/左/右），由 PlayerHealthOverlay 等填充，渲染时交由 OverlayManager.addStringRender */
        public String centerText;
        public String leftText;
        public String rightText;
        public int centerTextColor = 0xFFFFFFFF;
        public int leftTextColor = 0xFFFFFFFF;
        public int rightTextColor = 0xFFFFFFFF;

        public Parameters() {
            this.fillColor = 0xFFFFFFFF;
            this.boundColor = 0xFF000000;
            this.emptyColor = 0xFF000000;
            this.value = 0;
            this.maxValue = 1;
            this.verticalShift = 0;
            this.blink = false;
        }
    }

    /** 图层描述 - 支持多层彩色血条 */
    public static class Layer {
        public final double value;
        public final int color;
        public Layer(double value, int color) {
            this.value = value;
            this.color = color;
        }
    }

    public static final int[] SHIFT = {0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1};

    protected long lastChangeMillis;
    protected Parameters lastParameters = new Parameters();
    protected OverlayPosition definedPosition = OverlayPosition.UNSPECIFIED;
    protected final Map<String, BiConsumer<Player, Parameters>> postProcessors = new LinkedHashMap<>();
    protected final Map<String, Layer> layers = new LinkedHashMap<>();
    protected double lastValue;
    protected double lastFadeValue;
    protected double valueFadeFrom;
    protected double valueFadeTo;
    protected long valueFadeStartMillis;
    protected long valueFadeDuration = 500;

    private static final double VALUE_FADE_START_DELAY = 500;
    private static final double VALUE_FADE_DURATION_MIN = 250;
    private static final double VALUE_FADE_DURATION_MAX = 1000;

    public void setDefinedPosition(OverlayPosition position) {
        this.definedPosition = position;
    }

    // ---- 数值不变自动隐藏（hideUnchangingBarAfterSeconds,此前为死配置现已接线） ----
    private double trackedValue = Double.NaN;
    private long valueSteadySince;

    /** 数值持续不变超过配置秒数 → 跳过本条渲染（0 = 关闭;编辑器预览常显便于拖拽编辑） */
    protected boolean hideUnchanged(double value) {
        int secs = ConfigManager.getConfig().overlay.hideUnchangingBarAfterSeconds;
        if (secs <= 0) return false;
        if (com.z80z99.z80zhealthbar.overlay.parts.HudPreviewState.active) return false;
        long now = System.currentTimeMillis();
        if (value != trackedValue) {
            trackedValue = value;
            valueSteadySince = now;
            return false;
        }
        return now - valueSteadySince >= secs * 1000L;
    }

    public OverlayPosition getDefinedPosition() {
        return definedPosition;
    }

    /** 低血量/低饱食度抖动效果 */
    public void applyShakeEffect(Parameters params, int divide) {
        int idx = (tick / Math.max(divide, 1)) % SHIFT.length;
        params.verticalShift = SHIFT[idx];
    }

    /** 计算平滑过渡值 */
    protected double getFadeValue(double currentValue) {
        long now = System.currentTimeMillis();
        if (currentValue != lastValue) {
            if (lastChangeMillis == 0) {
                lastChangeMillis = now;
            } else if (now - lastChangeMillis >= VALUE_FADE_START_DELAY) {
                valueFadeFrom = lastFadeValue;
                valueFadeTo = currentValue;
                valueFadeStartMillis = now;
                valueFadeDuration = (long) Math.max(VALUE_FADE_DURATION_MIN,
                        Math.min(VALUE_FADE_DURATION_MAX,
                                Math.abs(currentValue - lastFadeValue) * 50));
                lastChangeMillis = 0;
            }
            lastValue = currentValue;
        } else {
            lastChangeMillis = 0;
        }

        if (valueFadeStartMillis > 0) {
            float progress = (float)(now - valueFadeStartMillis) / valueFadeDuration;
            if (progress >= 1.0f) {
                valueFadeStartMillis = 0;
                lastFadeValue = valueFadeTo;
                return valueFadeTo;
            }
            // 余弦缓动
            progress = (float)(1.0 - Math.cos(progress * Math.PI) / 2.0);
            return valueFadeFrom + (valueFadeTo - valueFadeFrom) * progress;
        }
        lastFadeValue = currentValue;
        return currentValue;
    }
}
