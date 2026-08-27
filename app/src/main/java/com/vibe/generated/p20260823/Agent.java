package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 括号片段的类型分类器。
 *
 * 用户输入和 AI 回复里的 （内容） 不再靠正则猜，而是交给模型判断该走哪个工具。
 * 传输走结构化 JSON 而不是原生 tool calling：分类结果一样，但不依赖端点支持
 * tools，Ollama 和老中转也能用，且 Api 的协议层一行都不用动。
 *
 * 分类是「一回合一次」的批量调用 —— 本回合的全部片段一起发，无论几个括号都只
 * 多一次网络往返。
 *
 * 失败一律降级为 ACTION：那是纯渲染、无副作用的类型，分类器出问题时最差退回
 * 本功能上线前的行为，绝不会误改存档。
 */
public class Agent {

    /** 动作 / 神态，不调工具，作为 action 渲染 */
    public static final String ACTION = "action";
    /** 旁白 / 场景 */
    public static final String SET_SCENE = "set_scene";
    /** 变量调整 */
    public static final String UPDATE_VARIABLE = "update_variable";
    /** 人设修改，追加到 privateNote */
    public static final String UPDATE_PERSONA = "update_persona";
    /** 记忆写入 */
    public static final String STORE_MEMORY = "store_memory";

    private static final Pattern BRACKET = Pattern.compile("[（(]([^）)]{1,200})[）)]");

    private Agent() {
    }

    // ---------- 抽取 ----------

    /** 抠出文本里全部括号片段（中英文括号都认），顺序即出现顺序。 */
    public static List<String> extractBrackets(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        Matcher m = BRACKET.matcher(text);
        while (m.find()) {
            String s = m.group(1).trim();
            if (s.length() > 0) out.add(s);
        }
        return out;
    }

    // ---------- 分类 ----------

    /**
     * 批量分类。阻塞，必须在后台线程调用。
     *
     * @return 与 segs 等长的数组，每项 {type, args}；任何异常都返回全 ACTION 的兜底
     */
    public static JSONArray classify(JSONObject cfg, JSONObject ch, JSONObject session, List<String> segs) {
        if (segs == null || segs.isEmpty()) return new JSONArray();
        JSONArray fallback = allActions(segs.size());
        if (cfg == null || cfg.optString("apiKey", "").length() == 0) return fallback;

        final StringBuilder raw = new StringBuilder();
        final boolean[] ok = {false};
        Api.Callback cb = new Api.Callback() {
            public void onChunk(String t) {
            }

            public void onDone(String full) {
                raw.append(full == null ? "" : full);
                ok[0] = true;
            }

            public void onError(String msg) {
                AppLogger.e("AGENT", "classify failed: " + msg, null);
            }
        };
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "user").put("content", userPrompt(ch, session, segs)));
            // judge 模型本就是「语义判定」这一路，分类正是它的活
            Api.callModel(cfg, systemPrompt(), msgs, ChatEngine.modelOf(cfg, "judge"), 1024, false, cb);
        } catch (Exception e) {
            AppLogger.e("AGENT", "classify threw", e);
            return fallback;
        }
        if (!ok[0]) return fallback;
        return normalize(Json.extractArray(raw.toString()), segs.size());
    }

    /**
     * 把模型返回的数组整理成与 segs 等长、type 合法的数组。
     * 缺项、多项、type 拼错、整段解析失败，一律补成 ACTION。
     */
    static JSONArray normalize(JSONArray parsed, int n) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < n; i++) {
            JSONObject src = parsed == null ? null : parsed.optJSONObject(i);
            String type = src == null ? ACTION : canonicalType(Json.str(src, "type"));
            JSONObject args = src == null ? null : src.optJSONObject("args");
            JSONObject o = new JSONObject();
            try {
                o.put("type", type);
                o.put("args", args == null ? new JSONObject() : args);
            } catch (Exception e) {
                // put 只在 key 为 null 时抛，这里不会
            }
            out.put(o);
        }
        return out;
    }

    /** 认识的类型原样返回，其余一律 ACTION。 */
    static String canonicalType(String t) {
        if (t == null) return ACTION;
        String s = t.trim().toLowerCase();
        if (SET_SCENE.equals(s)) return SET_SCENE;
        if (UPDATE_VARIABLE.equals(s)) return UPDATE_VARIABLE;
        if (UPDATE_PERSONA.equals(s)) return UPDATE_PERSONA;
        if (STORE_MEMORY.equals(s)) return STORE_MEMORY;
        return ACTION;
    }

    private static JSONArray allActions(int n) {
        return normalize(null, n);
    }

    // ---------- prompt ----------

    private static String systemPrompt() {
        return "你是一个分类器。用户会给你若干条括号内容，每条都出自一段角色扮演对话。\n"
                + "为每一条判断它属于哪一类，并给出参数。只输出 JSON 数组，不要任何解释文字。\n\n"
                + "类型：\n"
                + "- action：动作、神态、语气描写。例如「她低下头」「笑了笑」。**这是默认类型，拿不准就用它。**\n"
                + "- set_scene：环境、场景、时间的设定或转换。例如「下起了雨」「场景切换到深夜的书房」。\n"
                + "  args: {\"scene\":\"场景描述\"}\n"
                + "- update_variable：明确要改某个数值或状态。例如「好感度+10」「天气=雨」。\n"
                + "  args: {\"name\":\"变量名\",\"op\":\"+|-|=\",\"value\":\"数值或文本\"}\n"
                + "- update_persona：对角色性格、说话方式、背景设定的**持久**修改。例如「以后她说话更冷淡」。\n"
                + "  args: {\"note\":\"要追加的设定，一句话\"}\n"
                + "  注意：只有明确要改变角色本身时才用，单次的情绪波动属于 action。\n"
                + "- store_memory：要求记住某件事。例如「记住她怕黑」。\n"
                + "  args: {\"content\":\"要记住的事\",\"weight\":1-10 的重要度}\n\n"
                + "输出格式，数组长度必须与输入条数一致，顺序一一对应：\n"
                + "[{\"index\":0,\"type\":\"action\",\"args\":{}}, ...]";
    }

    private static String userPrompt(JSONObject ch, JSONObject session, List<String> segs) {
        StringBuilder sb = new StringBuilder();
        if (ch != null) {
            String name = ch.optString("name", "");
            if (name.length() > 0) sb.append("角色：").append(name).append('\n');
        }
        String vars = variableNames(session);
        if (vars.length() > 0) sb.append("已有变量：").append(vars).append('\n');
        sb.append("\n需要分类的").append(segs.size()).append("条：\n");
        for (int i = 0; i < segs.size(); i++) {
            sb.append(i).append(". ").append(segs.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static String variableNames(JSONObject session) {
        JSONObject vars = session == null ? null : session.optJSONObject("variables");
        if (vars == null) return "";
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = vars.keys();
        while (it.hasNext()) {
            if (sb.length() > 0) sb.append('、');
            sb.append(it.next());
        }
        return sb.toString();
    }
}
