package com.vibe.generated.p20260823;

import org.json.JSONObject;

/**
 * 变量写入：把「某个变量 + 运算符 + 值」落到 session.variables 上。
 *
 * 这里**不再判断**括号里写的是什么 —— 判断全部交给 Agent 让模型来做。曾经有一条
 * 正则在这里认变量写法，但它只接受字母数字下划线和汉字，凡是带标点、空格或其他
 * 符号的写法都匹配不上，等于在过滤用户能往括号里写什么。
 *
 * 留在这里的是「写入的语义」：好感度的别名归一、[-100, 200] 区间、数值格式化。
 * 模型判定出的 update_variable 经 Tools 调到这里，只此一份。
 */
public class Directive {

    /** 好感度的中文别名，映射到内部 affection 变量。 */
    private static final String[] AFFECTION_ALIASES = {"好感", "好感度", "affection", "Affection"};

    private Directive() {
    }

    /** 归一化变量名：好感度的几种写法都落到 affection 上。 */
    public static String normalizedName(String name) {
        for (String a : AFFECTION_ALIASES) {
            if (a.equals(name)) return "affection";
        }
        return name;
    }

    /** 变量的展示名，好感度用中文。 */
    public static String labelOf(String key) {
        return "affection".equals(key) ? "好感度" : key;
    }

    /** 当前值的格式化文本，用于变更记录的 before/after。 */
    public static String valueText(JSONObject session, String key) {
        JSONObject vars = session == null ? null : session.optJSONObject("variables");
        if (vars == null || !vars.has(key)) return "";
        Object v = vars.opt(key);
        if (v instanceof Number) return fmt(((Number) v).doubleValue());
        return String.valueOf(v);
    }

    /**
     * 把变量操作写进会话。数值变量做加减，非数值一律按赋值处理（可存文本，
     * 例如「天气=雨」），变量不存在时自动新建 —— 变量页遍历全部 variables，
     * 因此新变量会自动出现在那里。
     *
     * @return 给用户看的一句话，失败返回 null
     */
    public static String applyVariable(JSONObject session, String name, String op, String value) {
        if (session == null || name == null || op == null || value == null) return null;
        JSONObject vars = session.optJSONObject("variables");
        if (vars == null) {
            vars = new JSONObject();
            try {
                session.put("variables", vars);
            } catch (Exception e) {
                return null;
            }
        }
        String key = normalizedName(name);
        String label = labelOf(key);
        Double num = asDouble(value);

        try {
            if ("=".equals(op)) {
                if (num != null) {
                    vars.put(key, clamp(key, num.doubleValue()));
                    return label + " 设为 " + fmt(clamp(key, num.doubleValue()));
                }
                vars.put(key, value);
                return label + " 设为「" + value + "」";
            }
            // 加减：只对数值有意义
            if (num == null) return null;
            double delta = num.doubleValue();
            if ("-".equals(op) || "-=".equals(op)) delta = -delta;
            double cur = vars.optDouble(key, 0);
            double next = clamp(key, cur + delta);
            vars.put(key, next);
            return label + " " + (delta >= 0 ? "+" : "") + fmt(delta) + " → " + fmt(next);
        } catch (Exception e) {
            return null;
        }
    }

    /** 好感度沿用引擎的 [-100, 200] 区间，其他变量不设限。 */
    private static double clamp(String key, double v) {
        if ("affection".equals(key)) v = Math.max(-100, Math.min(200, v));
        return Math.round(v * 10) / 10.0;
    }

    private static Double asDouble(String s) {
        try {
            return Double.valueOf(Double.parseDouble(s));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
