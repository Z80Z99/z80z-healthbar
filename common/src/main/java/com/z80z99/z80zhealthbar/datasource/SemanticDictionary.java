package com.z80z99.z80zhealthbar.datasource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 语义词典（中英）：把方法名映射成人话名，并给出“正向/排除”关键词。
 *
 * <p>只影响标签与排序分数，**不参与任何反射解析**——词表判断错误不会导致配置失效。
 * 覆盖口渴/生命/法力/体力/能量/温度/理智/氧气等常见玩家状态；未命中时退化为驼峰分词。
 */
public final class SemanticDictionary {

    private SemanticDictionary() {}

    /** 语义条目：主关键词、排除关键词、中文名、限定标签（命中排除词时用） */
    public record Entry(String id, List<String> positive, List<String> negative,
                        String zhLabel, List<Qualifier> qualifiers) {}

    /** 限定标签：命中某个词时给更精确的名字（如 quench → 润泽度） */
    public record Qualifier(String keyword, String zhLabel) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("thirst",
                    List.of("thirst", "hydration", "water", "drink", "moisture"),
                    List.of("max", "saturation", "quench", "exhaustion", "rate", "decay"),
                    "口渴值",
                    List.of(new Qualifier("quench", "润泽度"),
                            new Qualifier("saturation", "口渴饱和度"),
                            new Qualifier("exhaustion", "口渴消耗度"))),
            new Entry("health",
                    List.of("health", "hp", "life", "vitality"),
                    List.of("max", "regen", "heal", "rate"),
                    "生命值", List.of()),
            new Entry("mana",
                    List.of("mana", "magic", "spell", "aether"),
                    List.of("max", "regen", "rate"),
                    "法力值", List.of()),
            new Entry("stamina",
                    List.of("stamina", "endurance", "fatigue"),
                    List.of("max", "regen", "rate"),
                    "体力值", List.of()),
            new Entry("energy",
                    List.of("energy", "power", "charge", "fe"),
                    List.of("max", "capacity", "rate", "transfer"),
                    "能量", List.of()),
            new Entry("temperature",
                    List.of("temperature", "temp", "heat", "cold", "warmth", "bodytemp"),
                    List.of("max", "min", "rate"),
                    "体温", List.of()),
            new Entry("sanity",
                    List.of("sanity", "sane", "insanity", "mind"),
                    List.of("max", "rate"),
                    "理智值", List.of()),
            new Entry("oxygen",
                    List.of("oxygen", "air", "breath"),
                    List.of("max", "rate"),
                    "氧气", List.of())
    );

    /** 匹配方法名对应的语义条目（null = 未命中）。
     *  先按正向词匹配（最具体）;否则按限定词/排除词回退归属——
     *  例如 getQuenched 不含 thirst 正向词,但它是口渴体系的润泽度。 */
    public static Entry match(String methodName) {
        String n = normalize(methodName);
        if (n.isEmpty()) return null;
        for (Entry e : ENTRIES) {
            for (String p : e.positive()) {
                if (n.contains(p)) return e;
            }
        }
        for (Entry e : ENTRIES) {
            for (Qualifier q : e.qualifiers()) {
                if (n.contains(q.keyword())) return e;
            }
            for (String x : e.negative()) {
                if (n.contains(x)) return e;
            }
        }
        return null;
    }

    /** 是否命中该条目的排除词 */
    public static boolean isNegative(Entry entry, String methodName) {
        if (entry == null) return false;
        String n = normalize(methodName);
        for (String x : entry.negative()) {
            if (n.contains(x)) return true;
        }
        return false;
    }

    /** 命中排除词时返回更精确的限定名（如 quench → 润泽度）；无则回退条目主名 */
    public static String qualifiedLabel(Entry entry, String methodName) {
        if (entry == null) return null;
        String n = normalize(methodName);
        for (Qualifier q : entry.qualifiers()) {
            if (n.contains(q.keyword())) return q.zhLabel();
        }
        return entry.zhLabel();
    }

    /** 是否是需要降权的“非主值”词（max/ratio/percent/scale 等） */
    public static boolean isDerivedName(String methodName) {
        String n = normalize(methodName);
        return n.contains("max") || n.contains("min") || n.contains("ratio")
                || n.contains("percent") || n.contains("scale") || n.contains("capacity")
                || n.contains("total");
    }

    /** 方法名 → 人话名（驼峰分词；去 get/is/has 前缀、去括号） */
    public static String humanize(String methodName) {
        if (methodName == null || methodName.isBlank()) return "";
        String n = methodName;
        int paren = n.indexOf('(');
        if (paren > 0) n = n.substring(0, paren);
        for (String prefix : new String[]{"get_", "is_", "has_", "can_", "should_",
                "get", "is", "has", "can", "should"}) {
            if (n.length() > prefix.length() && n.startsWith(prefix)) {
                // snake_case 前缀直接剥离;驼峰前缀要求后接大写,避免 getter→tter
                if (prefix.endsWith("_") || Character.isUpperCase(n.charAt(prefix.length()))) {
                    n = n.substring(prefix.length());
                    break;
                }
            }
        }
        List<String> words = splitCamel(n);
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** 生成“标签 · 方法名”的显示串（标签为空时只显示方法名） */
    public static String displayLabel(String label, String methodName) {
        if (label == null || label.isBlank()) return methodName == null ? "" : methodName;
        return label + " · " + methodName;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static List<String> splitCamel(String s) {
        Set<String> out = new LinkedHashSet<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            if (Character.isUpperCase(c) && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(Character.toLowerCase(c));
        }
        if (cur.length() > 0) out.add(cur.toString());
        return new ArrayList<>(out);
    }
}
