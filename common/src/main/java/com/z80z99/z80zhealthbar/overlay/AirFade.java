package com.z80z99.z80zhealthbar.overlay;

import com.z80z99.z80zhealthbar.config.ConfigManager;

/**
 * 氧气条出入水淡入淡出跟踪器（玩家 HUD 两管线共用）。
 * 每帧 {@link #alpha(boolean, long)} 汇报当前可见性与时间戳，返回渲染透明度：
 * 出现 200ms 淡入；消失后保留 400ms 再 200ms 淡出（此前硬出硬入很突兀）。
 * 渲染端把透明度乘进各绘制色即可（条本体/图标/文本统一）。
 */
public final class AirFade {

    private static long lastChangeMs;
    private static boolean visible;

    private AirFade() {}

    /** 当前渲染透明度 0..1（dynamicFx 关闭时恒为 1,行为与旧版硬切一致） */
    public static float alpha(boolean nowVisible, long now) {
        var fx = ConfigManager.getConfig().dynamicFx;
        if (!fx.enabled) {
            visible = nowVisible;
            return 1f;
        }
        if (nowVisible != visible) {
            visible = nowVisible;
            lastChangeMs = now;
        }
        return HudFx.fadeAlpha(now, lastChangeMs, nowVisible, 200, 400, 200);
    }

    /** 淡出完全结束（透明度归零且不可见）→ 调用方可整条跳过渲染 */
    public static boolean fullyHidden(float alpha) {
        return alpha <= 0.01f;
    }
}
