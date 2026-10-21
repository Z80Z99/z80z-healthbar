package com.z80z99.z80zhealthbar.datasource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 翻译清单的导出/导入（剪贴板 JSON）。
 *
 * <p>AI 只产出标签，不产出类名/方法名——导入时键必须落在当前候选集内，
 * 且不参与任何反射解析。这样 AI 出错最多是一个难看的名字。
 */
public final class LabelExchange {

    /** 导出条目上限（保证提示词在 4KB 内） */
    public static final int MAX_EXPORT_ITEMS = 40;

    private LabelExchange() {}

    /** 单个候选的导出信息 */
    public record Item(String className, String memberName, String returnType,
                       Float value, Float max, String langHint) {}

    /** 导入结果：有效标签 + 被忽略的条数 */
    public record ImportResult(Map<String, String> labels, int ignored) {}

    /** 固定提示词（中文），拼在 JSON 之前 */
    static final String INSTRUCTION =
            "只翻译 labels 里的方法名为简洁中文名（不超过 20 字），"
                    + "原样保留所有键、类名与方法名，不得增删条目。"
                    + "只输出一个 JSON 对象：{\"labels\":{\"类名#方法名\":\"中文名\"}}";

    /** 构造剪贴板文本：提示词 + 若干候选 */
    public static String exportJson(String modId, String modName, String modVersion,
                                    String className, List<Item> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mod", Map.of("id", nz(modId), "name", nz(modName), "version", nz(modVersion)));
        root.put("class", nz(className));
        root.put("instruction", INSTRUCTION);
        Map<String, Object> candidates = new LinkedHashMap<>();
        int n = 0;
        for (Item it : items) {
            if (it == null || n >= MAX_EXPORT_ITEMS) break;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("method", nz(it.memberName()));
            c.put("returns", nz(it.returnType()));
            if (it.value() != null) c.put("value", it.value());
            if (it.max() != null) c.put("max", it.max());
            if (it.langHint() != null && !it.langHint().isBlank()) c.put("lang", it.langHint());
            candidates.put(LabelResolver.key(it.className(), it.memberName()), c);
            n++;
        }
        root.put("candidates", candidates);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * 解析 AI 返回的 JSON。只接受 {@code {"labels":{...}}};
     * 键不在 allowedKeys 内的条目忽略并计数（防止 AI 编造新成员）。
     */
    public static ImportResult importJson(String json, java.util.Set<String> allowedKeys) {
        if (json == null || json.isBlank()) return new ImportResult(Map.of(), 0);
        try {
            Map<?, ?> root = new com.google.gson.Gson().fromJson(json, Map.class);
            if (root == null) return new ImportResult(Map.of(), 0);
            Object labelsObj = root.get("labels");
            Map<?, ?> labels;
            if (labelsObj instanceof Map<?, ?> m) {
                labels = m;
            } else if (root.keySet().stream().anyMatch(k -> k != null && String.valueOf(k).contains("#"))) {
                // 容错：AI 可能直接返回 { "类#方法": "名" }
                labels = root;
            } else {
                return new ImportResult(Map.of(), 0);
            }
            Map<String, String> out = new LinkedHashMap<>();
            int ignored = 0;
            for (var e : labels.entrySet()) {
                String rawKey = LabelResolver.normalizeKey(String.valueOf(e.getKey()));
                String val = e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
                if (rawKey == null || val.isEmpty()) { ignored++; continue; }
                if (!LabelResolver.isValidKey(rawKey)) { ignored++; continue; }
                if (allowedKeys != null && !allowedKeys.contains(rawKey)) { ignored++; continue; }
                out.put(rawKey, val.length() > 64 ? val.substring(0, 64) : val);
            }
            return new ImportResult(out, ignored);
        } catch (Throwable t) {
            return null; // 解析失败 = 调用方 toast,配置不动
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
