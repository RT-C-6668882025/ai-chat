package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 括号指令：整行只写括号时，内容不算台词，而是直接拼进 system prompt 去影响回复。
 *
 * 不做任何分类 —— 括号里写什么就原样交给模型，由它自己理解该怎么演。因此这条路
 * **不发任何 API 请求**，零延迟。
 *
 * 指令是**持续叠加**的：每条追加进 session.directives，一直随后续每次请求走，
 * 直到用户在聊天菜单里删掉。所以「以后她说话更冷淡」和「下起了雨」能同时生效。
 *
 * 读取一律 optJSONArray + 判空，老存档没有 directives 字段也不用迁移。
 */
public class Directive {

    /** 一条指令最长多少字，防止把整篇小说塞进 system prompt */
    private static final int MAX_LEN = 500;
    /** 最多留多少条，超出丢最旧的 —— prompt 不能无限膨胀 */
    private static final int MAX_COUNT = 30;

    private Directive() {
    }

    // ---------- 括号扫描 ----------

    /**
     * 扫描出全部**最外层**括号段，中英文括号都认。
     *
     * 用深度计数而不是正则：正则匹配不了嵌套，
     * 「（以后她说话更冷淡（除了对我））」会在第一个右括号处截断，
     * 把用户写的内容改掉。嵌套内容原样保留在段内。
     *
     * 没有配对的括号不成段，其字符留在段外，因此 isOnlyBrackets 会正确地
     * 把「（未闭合」判成普通消息。
     */
    private static List<int[]> spans(String text) {
        List<int[]> out = new ArrayList<int[]>();
        if (text == null) return out;
        int depth = 0, start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '（') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == ')' || c == '）') {
                if (depth == 0) continue;          // 没有开头的右括号，当普通字符
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(new int[]{start, i});
                    start = -1;
                }
            }
        }
        return out;   // depth 未归零说明有未闭合的括号，那一段不计入
    }

    /** 抠出文本里全部括号片段，顺序即出现顺序。 */
    public static List<String> extractBrackets(String text) {
        List<String> out = new ArrayList<String>();
        for (int[] sp : spans(text)) {
            String s = text.substring(sp[0] + 1, sp[1]).trim();
            if (s.length() > 0) out.add(s);
        }
        return out;
    }

    /**
     * 整行是否只由括号（和空白）组成 —— 这样的输入整条都是指令，不当台词发出去。
     *
     * 检查括号段之外还剩什么，而不是「首字符是( 且尾字符是)」：后者会把
     * 「（她笑了）你好（好感度+10）」误判成一整条指令，中间的台词被吞掉。
     */
    public static boolean isOnlyBrackets(String text) {
        if (text == null) return false;
        List<int[]> sp = spans(text);
        if (sp.isEmpty()) return false;
        StringBuilder rest = new StringBuilder();
        int at = 0;
        for (int[] s : sp) {
            rest.append(text, at, s[0]);
            at = s[1] + 1;
        }
        rest.append(text, at, text.length());
        return rest.toString().trim().length() == 0 && !extractBrackets(text).isEmpty();
    }

    // ---------- 指令列表 ----------

    /** 生效中的指令原文，按加入顺序。 */
    public static List<String> listOf(JSONObject session) {
        List<String> out = new ArrayList<String>();
        if (session == null) return out;
        JSONArray a = session.optJSONArray("directives");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            if (d == null) continue;
            String t = d.optString("text", "");
            if (t.length() > 0) out.add(t);
        }
        return out;
    }

    /**
     * 追加一条指令。
     *
     * 内容相同的不重复加 —— 同一条设定说两遍不该在 prompt 里出现两次。
     *
     * @return 实际加进去了才返回 true
     */
    public static boolean add(JSONObject session, String text) {
        if (session == null || text == null) return false;
        String t = text.trim();
        if (t.length() == 0) return false;
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);
        if (listOf(session).contains(t)) return false;
        try {
            JSONArray a = session.optJSONArray("directives");
            if (a == null) {
                a = new JSONArray();
                session.put("directives", a);
            }
            JSONObject d = new JSONObject();
            d.put("text", t);
            d.put("at", System.currentTimeMillis());
            a.put(d);
            while (a.length() > MAX_COUNT) a.remove(0);
            return true;
        } catch (Exception e) {
            AppLogger.e("DIRECTIVE", "add failed", e);
            return false;
        }
    }

    public static boolean removeAt(JSONObject session, int index) {
        if (session == null) return false;
        JSONArray a = session.optJSONArray("directives");
        if (a == null || index < 0 || index >= a.length()) return false;
        a.remove(index);
        return true;
    }

    /**
     * 拼成注入 prompt 用的文本，每条一行。没有指令时返回空串，
     * 模板里的 {{#if}} 会因此整段不输出。
     */
    public static String promptText(JSONObject session) {
        StringBuilder sb = new StringBuilder();
        for (String t : listOf(session)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("- ").append(t);
        }
        return sb.toString();
    }
}
