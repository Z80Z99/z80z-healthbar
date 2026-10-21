package com.z80z99.z80zhealthbar.datasource;

import com.z80z99.z80zhealthbar.config.ConfigManager;
import com.z80z99.z80zhealthbar.config.configs.CustomSourceConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标签解析：三级回退（已导入标签 → 模组语言文件 → 驼峰分词）。
 *
 * <p>标签**只用于显示**，永不参与方法解析。键格式固定为 {@code 完整类名#成员名}，
 * 成员名不含参数（同名重载共享标签）。
 */
public final class LabelResolver {

    private LabelResolver() {}

    /** 规范键：类名 + '#' + 成员名（去空白） */
    public static String key(String className, String memberName) {
        return (className == null ? "" : className.trim()) + "#"
                + (memberName == null ? "" : memberName.trim());
    }

    /**
     * 解析标签。
     *
     * @param className  声明类全名
     * @param memberName 方法/字段名（不含括号）
     * @param lang       目标模组的语言索引（可为 null）
     */
    public static String resolve(String className, String memberName, ModLangIndex lang) {
        String k = key(className, memberName);
        CustomSourceConfig cs = ConfigManager.getConfig().customSources;
        if (cs != null && cs.datasourceLabels != null) {
            String v = cs.datasourceLabels.get(k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        if (lang != null) {
            String v = lang.lookup(memberName);
            if (v != null && !v.isBlank()) return ModLangIndex.clean(v);
        }
        SemanticDictionary.Entry entry = SemanticDictionary.match(memberName);
        String semantic = entry == null ? null : SemanticDictionary.qualifiedLabel(entry, memberName);
        // 语义词典标签比纯分词更准确,优先；两者都没有时用分词
        return semantic != null && !semantic.isBlank()
                ? semantic : SemanticDictionary.humanize(memberName);
    }

    /** 合并写入标签表（不覆盖已有键）,返回实际新增条数 */
    public static int merge(Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        CustomSourceConfig cs = ConfigManager.getConfig().customSources;
        if (cs == null) return 0;
        if (cs.datasourceLabels == null) cs.datasourceLabels = new LinkedHashMap<>();
        int added = 0;
        for (var e : incoming.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null) continue;
            String cleanKey = k.trim();
            String cleanVal = v.trim();
            if (!isValidKey(cleanKey) || cleanVal.isEmpty()) continue;
            if (cleanVal.length() > 64) cleanVal = cleanVal.substring(0, 64);
            if (cs.datasourceLabels.containsKey(cleanKey)) continue;
            cs.datasourceLabels.put(cleanKey, cleanVal);
            added++;
        }
        return added;
    }

    /** 键必须形如 "包.类#成员"（含 '#'、两段都非空、长度受限） */
    public static boolean isValidKey(String key) {
        if (key == null || key.length() > 300) return false;
        int hash = key.indexOf('#');
        if (hash <= 0 || hash == key.length() - 1) return false;
        return key.indexOf('#', hash + 1) < 0;
    }

    /** 从 "包.类#方法(参数)" 中剥离参数部分（导入时容错） */
    public static String normalizeKey(String raw) {
        if (raw == null) return null;
        String k = raw.trim();
        int paren = k.indexOf('(');
        if (paren > 0) k = k.substring(0, paren);
        return k;
    }

    /** 清空标签表（管理页“重置标签”用） */
    public static void clearAll() {
        CustomSourceConfig cs = ConfigManager.getConfig().customSources;
        if (cs != null) cs.datasourceLabels = new LinkedHashMap<>();
    }
}
