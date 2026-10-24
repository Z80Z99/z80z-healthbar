package com.z80z99.z80zhealthbar.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.z80z99.z80zhealthbar.config.configs.HudLayoutConfig;

import java.util.Set;

/**
 * 用户样式的剪贴板编解码（1.0.148 样式分享）。
 *
 * <p>纯 JVM、无 MC 依赖,可单测。parse 只做结构与类型解析（UserStyle 字段由 Gson 宽松映射）;
 * {@link #sanitize} 负责安全化：唯一命名 + 组件逐项钳制（复用 ConfigValidator.validateComponent）
 * + 空 components 拒收。防御手改/外部粘贴的任意 JSON。
 */
public final class StyleCodec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StyleCodec() {}

    /** 序列化单份样式（分享用） */
    public static String export(HudLayoutConfig.UserStyle style) {
        return GSON.toJson(style);
    }

    /** 解析剪贴板 JSON;非法/结构不符返回 null（调用方 toast 失败即可） */
    public static HudLayoutConfig.UserStyle parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            var style = GSON.fromJson(json, HudLayoutConfig.UserStyle.class);
            return style == null ? null : style;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * 导入前的安全化：
     * 1) components 为空/空串键 → 拒收（返回 false）;
     * 2) 每个组件过 {@code ConfigValidator.validateComponent}（钳制越界值,与主布局同规则）;
     * 3) 名字空白 → base 兜底;与 taken 撞名 → 追加序号唯一化。
     */
    public static boolean sanitize(HudLayoutConfig.UserStyle style, Set<String> takenNames,
                                   java.util.function.IntFunction<String> fallbackName) {
        if (style == null || style.components == null || style.components.isEmpty()) return false;
        style.components.keySet().removeIf(k -> k == null || k.isBlank()
                || k.endsWith(".text") || k.endsWith(".icon"));
        if (style.components.isEmpty()) return false;
        for (var e : style.components.entrySet()) {
            if (e.getValue() != null) ConfigValidator.validateComponent(e.getValue());
        }
        style.scaleMode = Math.max(0, Math.min(1, style.scaleMode));
        style.referenceWidth = style.referenceWidth <= 0 ? 960
                : Math.max(320, Math.min(3840, style.referenceWidth));
        style.referenceHeight = style.referenceHeight <= 0 ? 540
                : Math.max(240, Math.min(2160, style.referenceHeight));
        style.name = style.name == null ? "" : style.name.trim();
        if (style.name.isEmpty()) style.name = fallbackName.apply(1);
        if (takenNames != null) {
            while (takenNames.contains(style.name)) {
                style.name = style.name + " 2";
            }
        }
        return true;
    }
}
