package com.z80z99.z80zhealthbar.datasource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 候选排序（纯函数，可单测）：给扫描出来的值方法打分并给出人话标签/推荐标记。
 *
 * <p>评分只用确定性信号——实测读数、上限配对、命名规则；语义词表只加减分，
 * **不参与方法解析**。第一名领先不足 {@link #CONFIDENT_GAP} 分时不打推荐星，
 * 避免给出虚假的确定感。
 */
public final class CandidateRanker {

    /** 首名领先第二名达到此分差才显示推荐星 */
    public static final int CONFIDENT_GAP = 20;

    /** 排序输入：方法名 + 返回类型 + 实测值（null = 读取失败）+ 上限（null = 未知） */
    public record Input(String methodName, String returnType, Float value, Float max) {}

    /** 排序输出：分数、标签、是否推荐、推荐理由 */
    public record Ranked(Input input, int score, String label, boolean recommended,
                         List<String> reasons) {}

    private CandidateRanker() {}

    /**
     * 排序：分数降序 → 方法名字母序。读取失败的候选排在最后。
     */
    public static List<Ranked> rank(List<Input> inputs) {
        List<Ranked> out = new ArrayList<>();
        if (inputs == null) return out;
        for (Input in : inputs) {
            if (in == null || in.methodName() == null) continue;
            out.add(score(in));
        }
        out.sort(Comparator.comparingInt(Ranked::score).reversed()
                .thenComparing(r -> r.input().methodName(), String.CASE_INSENSITIVE_ORDER));
        // 推荐星：仅当第一名领先第二名达到 CONFIDENT_GAP 且自身分数足够（有实测读数）
        if (!out.isEmpty()) {
            Ranked first = out.get(0);
            int gap = out.size() == 1 ? Integer.MAX_VALUE : first.score() - out.get(1).score();
            boolean recommend = gap >= CONFIDENT_GAP && first.input().value() != null;
            out.set(0, new Ranked(first.input(), first.score(), first.label(), recommend,
                    first.reasons()));
        }
        return out;
    }

    static Ranked score(Input in) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        String name = in.methodName();
        Float value = in.value();
        Float max = in.max();

        if (value != null) {
            score += 100;
            reasons.add("read_ok");
        }
        if (value != null && max != null && max > 0 && value >= 0 && value <= max) {
            score += 20;
            reasons.add("in_range");
        }
        if (max != null && max > 0) {
            score += 25;
            reasons.add("has_max");
        }
        SemanticDictionary.Entry entry = SemanticDictionary.match(name);
        if (entry != null) {
            if (SemanticDictionary.isNegative(entry, name)) {
                score -= 30;
                reasons.add("excluded_word");
            } else {
                score += 40;
                reasons.add("semantic_match");
            }
        }
        if (SemanticDictionary.isDerivedName(name)) {
            score -= 10;
            reasons.add("derived_name");
        }

        String label = resolveLabel(entry, name);
        return new Ranked(in, score, label, false, reasons);
    }

    /** 标签：命中语义 → 限定名 → 驼峰分词 */
    static String resolveLabel(SemanticDictionary.Entry entry, String methodName) {
        if (entry != null) {
            String q = SemanticDictionary.qualifiedLabel(entry, methodName);
            if (q != null && !q.isBlank()) return q;
        }
        return SemanticDictionary.humanize(methodName);
    }

    /** 推荐理由 → 语言键（面板 tooltip 用） */
    public static String reasonKey(String reason) {
        return switch (reason) {
            case "read_ok" -> "z80zhealthbar.editor.datasource.reason.read_ok";
            case "in_range" -> "z80zhealthbar.editor.datasource.reason.in_range";
            case "has_max" -> "z80zhealthbar.editor.datasource.reason.has_max";
            case "semantic_match" -> "z80zhealthbar.editor.datasource.reason.semantic";
            case "excluded_word" -> "z80zhealthbar.editor.datasource.reason.excluded";
            case "derived_name" -> "z80zhealthbar.editor.datasource.reason.derived";
            default -> "";
        };
    }
}
