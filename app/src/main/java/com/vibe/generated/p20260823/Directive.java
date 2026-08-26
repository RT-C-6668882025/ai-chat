package com.vibe.generated.p20260823;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 括号指令：用户在输入框里打一对括号，内容不作为「你的角色说的话」，
 * 而是当场生效的设定。两种写法自动识别：
 *
 *   变量操作   （好感度+10）（信任-5）（天气=雨）（新变量=3）
 *              → 直接改 session.variables，全局立即生效，不发 API 请求
 *
 *   导演指令   （下起了雨，气氛忽然安静）
 *              → 存入 session.sceneNote，作为旁白插入对话，并注入后续 prompt
 *
 * 中英文括号都认，允许首尾空白。
 */
public class Directive {

    public static final int NONE = 0;
    public static final int VARIABLE = 1;
    public static final int SCENE = 2;

    /** 变量写法：名字 + 运算符 + 数值/文本。名字允许中英文数字下划线。 */
    private static final Pattern VAR = Pattern.compile(
            "^\\s*([\\w\\u4e00-\\u9fa5]+)\\s*(\\+=|-=|\\+|-|=)\\s*(-?[\\w\\u4e00-\\u9fa5.]+)\\s*$");

    /** 好感度的中文别名，映射到内部 affection 变量。 */
    private static final String[] AFFECTION_ALIASES = {"好感", "好感度", "affection", "Affection"};

    private final int kind;
    private final String name;
    private final String op;
    private final String value;
    private final String scene;

    private Directive(int kind, String name, String op, String value, String scene) {
        this.kind = kind;
        this.name = name;
        this.op = op;
        this.value = value;
        this.scene = scene;
    }

    public int kind() {
        return kind;
    }

    public String scene() {
        return scene;
    }

    // ---------- 解析 ----------

    /** 整条输入是否被一对括号包住 */
    public static boolean isBracketed(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.length() < 3) return false;
        char a = s.charAt(0), b = s.charAt(s.length() - 1);
        return (a == '(' || a == '（') && (b == ')' || b == '）');
    }

    /**
     * 解析输入。不是括号指令时返回 kind()==NONE。
     */
    public static Directive parse(String input) {
        if (!isBracketed(input)) return new Directive(NONE, null, null, null, null);
        String inner = input.trim();
        inner = inner.substring(1, inner.length() - 1).trim();
        if (inner.length() == 0) return new Directive(NONE, null, null, null, null);

        Matcher m = VAR.matcher(inner);
        if (m.matches()) {
            return new Directive(VARIABLE, m.group(1), m.group(2), m.group(3), null);
        }
        return new Directive(SCENE, null, null, null, inner);
    }

    // ---------- 应用 ----------

    /** 归一化变量名：好感度的几种写法都落到 affection 上。 */
    private String normalizedName() {
        for (String a : AFFECTION_ALIASES) {
            if (a.equals(name)) return "affection";
        }
        return name;
    }

    /**
     * 把变量操作写进会话。数值变量做加减，非数值一律按赋值处理（可存文本，
     * 例如「天气=雨」），变量不存在时自动新建 —— 变量页遍历全部 variables，
     * 因此新变量会自动出现在那里。
     *
     * @return 给用户看的一句话，失败返回 null
     */
    public String applyVariable(JSONObject session) {
        if (kind != VARIABLE || session == null) return null;
        JSONObject vars = session.optJSONObject("variables");
        if (vars == null) {
            vars = new JSONObject();
            try {
                session.put("variables", vars);
            } catch (Exception e) {
                return null;
            }
        }
        String key = normalizedName();
        String label = "affection".equals(key) ? "好感度" : key;
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

    /** 记录场景指令，后续 prompt 会带上它。 */
    public void applyScene(JSONObject session) {
        if (kind != SCENE || session == null) return;
        try {
            session.put("sceneNote", scene);
        } catch (Exception e) {
            // ignore
        }
    }
}
