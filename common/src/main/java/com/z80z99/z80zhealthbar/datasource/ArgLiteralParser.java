package com.z80z99.z80zhealthbar.datasource;

import java.util.ArrayList;
import java.util.List;

/**
 * 反射调用的实参字面量解析（自定义数据源的带参数方法）。
 *
 * <p>语法：{@code 123}（整型）、{@code 1.5}（浮点）、{@code true/false}、{@code "文本"}、
 * {@code EnumClass.CONSTANT}（枚举常量，枚举类名可省略包名按已加载类解析失败则报错）。
 *
 * <p>纯函数、无 MC 依赖，可单测。解析失败返回 {@code null}（调用方按“参数不合法”静默失效）。
 */
public final class ArgLiteralParser {

    private ArgLiteralParser() {}

    /** 解析结果：值 + 声明的目标类型名（可能为空 = 让调用方按值推断） */
    public record Arg(Object value, String typeHint) {}

    /** 解析 "/" 分隔的实参串（空串 = 无参） */
    public static List<Arg> parseList(String args) {
        List<Arg> out = new ArrayList<>();
        if (args == null || args.isBlank()) return out;
        for (String part : args.split("/", -1)) {
            Arg a = parseOne(part.trim());
            if (a == null) return null;
            out.add(a);
        }
        return out;
    }

    /** 解析单个字面量；无法识别返回 null */
    public static Arg parseOne(String token) {
        if (token == null || token.isEmpty()) return null;
        // 字符串字面量（去引号）
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return new Arg(token.substring(1, token.length() - 1), "java.lang.String");
        }
        if (token.equalsIgnoreCase("true")) return new Arg(Boolean.TRUE, "boolean");
        if (token.equalsIgnoreCase("false")) return new Arg(Boolean.FALSE, "boolean");
        // 枚举常量：Xxx.YYY（类名可含包名，取最后一个 '.' 前的部分）
        int dot = token.lastIndexOf('.');
        if (dot > 0 && !token.startsWith("-") && !token.startsWith("+")
                && !isNumeric(token)) {
            String type = token.substring(0, dot);
            String constant = token.substring(dot + 1);
            if (!constant.isEmpty() && Character.isJavaIdentifierStart(constant.charAt(0))
                    && constant.chars().allMatch(Character::isJavaIdentifierPart)) {
                return new Arg(token, type); // 值 = 原 token，调用方用枚举类解析
            }
        }
        try {
            if (token.endsWith("L") || token.endsWith("l")) {
                return new Arg(Long.parseLong(token.substring(0, token.length() - 1)), "long");
            }
            if (token.endsWith("F") || token.endsWith("f")) {
                return new Arg(Float.parseFloat(token.substring(0, token.length() - 1)), "float");
            }
            if (token.endsWith("D") || token.endsWith("d")) {
                return new Arg(Double.parseDouble(token.substring(0, token.length() - 1)), "double");
            }
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return new Arg(Double.parseDouble(token), "double");
            }
            return new Arg(Long.parseLong(token), "long");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 按目标类型把解析值转换成反射调用需要的类型（失败返回 null） */
    public static Object coerce(Arg arg, Class<?> target) {
        if (arg == null || target == null) return null;
        Object v = arg.value();
        try {
            if (target == int.class || target == Integer.class) {
                return (v instanceof Number n) ? n.intValue() : null;
            }
            if (target == long.class || target == Long.class) {
                return (v instanceof Number n) ? n.longValue() : null;
            }
            if (target == float.class || target == Float.class) {
                return (v instanceof Number n) ? n.floatValue() : null;
            }
            if (target == double.class || target == Double.class) {
                return (v instanceof Number n) ? n.doubleValue() : null;
            }
            if (target == short.class || target == Short.class) {
                return (v instanceof Number n) ? n.shortValue() : null;
            }
            if (target == byte.class || target == Byte.class) {
                return (v instanceof Number n) ? n.byteValue() : null;
            }
            if (target == boolean.class || target == Boolean.class) {
                return v instanceof Boolean ? v : null;
            }
            if (target == String.class) {
                return v == null ? null : String.valueOf(v);
            }
            if (target.isEnum()) {
                return v == null ? null : enumConstant(target, String.valueOf(v));
            }
            return target.isInstance(v) ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 从 "类名.常量" / "类名#常量" / "常量" 中取出常量名并解析成枚举 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> enumType, String raw) {
        String name = raw;
        int dot = raw.lastIndexOf('.');
        if (dot >= 0) name = raw.substring(dot + 1);
        int hash = name.lastIndexOf('#');
        if (hash >= 0) name = name.substring(hash + 1);
        try {
            return Enum.valueOf((Class<? extends Enum>) enumType, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 生成默认实参（扫描器选中带参数方法时用）：数值 0、布尔 false、字符串空、枚举首个常量 */
    public static String defaultLiteral(Class<?> type) {
        if (type == null) return "0";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type == String.class) return "\"\"";
        if (type == float.class || type == Float.class) return "0.0f";
        if (type == double.class || type == Double.class) return "0.0";
        if (type == long.class || type == Long.class) return "0L";
        if (type.isEnum()) {
            Object[] consts = type.getEnumConstants();
            if (consts != null && consts.length > 0) {
                return type.getName() + "." + ((Enum<?>) consts[0]).name();
            }
            return "0";
        }
        if (type == int.class || type == Integer.class || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) {
            return "0";
        }
        return "0";
    }
}
