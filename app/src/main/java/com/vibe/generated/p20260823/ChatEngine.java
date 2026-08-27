package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat engine: prompt assembly, response parsing, memory system, variables,
 * affection stages, crisis detection and message-tree helpers.
 */
public class ChatEngine {

    public static final String[] CRISIS_KEYWORDS = {
            "自杀", "想死", "不想活", "活不下去", "自残", "伤害自己", "轻生", "结束生命", "了断", "去死", "自尽"
    };

    public static final String[] FEEDBACK_REASONS = {
            "不符合人设", "太啰嗦", "太简短", "重复之前说过的", "答非所问", "出戏了", "语气不对"
    };

    public static final String DEFAULT_TEMPLATE =
            "你正在扮演【{{char.name}}】。\n" +
            "\n" +
            "## 角色设定\n" +
            "{{char.persona}}\n" +
            "\n" +
            "{{#if char.privateNote}}\n" +
            "## 补充设定\n" +
            "{{char.privateNote}}\n" +
            "{{/if}}\n" +
            "\n" +
            "{{#if story.globalBackground}}\n" +
            "## 世界背景\n" +
            "{{story.globalBackground}}\n" +
            "{{/if}}\n" +
            "\n" +
            "{{#if story.situation}}\n" +
            "## 情境\n" +
            "{{story.situation}}\n" +
            "{{/if}}\n" +
            "\n" +
            "## 对话者\n" +
            "你称呼对方为「{{user.callMe}}」。\n" +
            "{{#if user.setting}}\n" +
            "关于对方：{{user.setting}}\n" +
            "{{/if}}\n" +
            "\n" +
            "{{#if memories}}\n" +
            "## 你记得的事\n" +
            "{{#each memories}}\n" +
            "- {{this}}\n" +
            "{{/each}}\n" +
            "{{/if}}\n" +
            "\n" +
            "## 当前状态\n" +
            "{{#each visibleVariables}}\n" +
            "{{this.name}}：{{this.value}}\n" +
            "{{/each}}\n" +
            "{{affectionStage}}\n" +
            "\n" +
            "{{#if sceneNote}}\n" +
            "## 当前场景\n" +
            "{{sceneNote}}\n" +
            "这是用户给出的场景设定，直接按它演，不要复述也不要评论。\n" +
            "{{/if}}\n" +
            "\n" +
            "{{#if daily.mood}}\n" +
            "## 今日状态\n" +
            "你今天的心情是「{{daily.mood}}」：{{daily.moodHint}}。\n" +
            "这会自然体现在语气和主动性上，但不要直接说出「我今天心情是……」。\n" +
            "{{#if daily.streak}}\n" +
            "对方已经连续 {{daily.streak}} 天来找你了，你心里是记得这件事的。\n" +
            "{{/if}}\n" +
            "{{/if}}\n" +
            "\n" +
            "{{#if feedback.reasons}}\n" +
            "## 需要避免\n" +
            "用户曾指出以下问题：\n" +
            "{{#each feedback.reasons}}\n" +
            "- {{this.reason}}（{{this.count}} 次）\n" +
            "{{/each}}\n" +
            "{{/if}}\n" +
            "\n" +
            "## 输出要求\n" +
            "1. 保持角色，绝不提及 AI、模型、系统\n" +
            "2. 回复简短自然，像真人聊天，通常 1-3 句\n" +
            "3. 结尾用（）描述当下动作或表情\n" +
            "{{#if config.enableMultiBubble}}\n" +
            "4. 若要连发多句，用 ||| 分隔，最多 {{config.maxBubbles}} 条\n" +
            "{{/if}}\n" +
            "{{#if config.enableVariables}}\n" +
            "5. 回复末尾输出变量变化：<var name=\"affection\" delta=\"+2\"/>\n" +
            "{{/if}}\n" +
            "{{#if config.enableInnerVoice}}\n" +
            "6. 再输出：<inner>此刻没说出口的真实想法</inner>\n" +
            "{{/if}}";

    // ---------- system prompt ----------

    public static String buildSystemPrompt(JSONObject cfg, JSONObject ch, JSONObject session, JSONObject story) {
        try {
            JSONObject ctx = new JSONObject();
            ctx.put("char", ch);

            JSONObject user = new JSONObject();
            String callMe = "你";
            String userSetting = "";
            if (story != null) {
                JSONObject up = story.optJSONObject("userPersona");
                if (up != null) {
                    callMe = up.optString("callMe", "你");
                    userSetting = up.optString("setting", "");
                }
            }
            user.put("callMe", callMe);
            user.put("setting", userSetting);
            ctx.put("user", user);

            ctx.put("story", story != null ? story : new JSONObject());
            ctx.put("node", JSONObject.NULL);
            ctx.put("memories", topMemories(session, cfg));

            JSONArray visVars = new JSONArray();
            double affection = affectionOf(session);
            visVars.put(new JSONObject().put("name", "好感度").put("value", String.valueOf((int) affection)));
            JSONObject variables = session.optJSONObject("variables");
            if (variables != null) {
                JSONArray names = variables.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String n = names.optString(i);
                        if ("affection".equals(n)) continue;
                        Object v = variables.opt(n);
                        visVars.put(new JSONObject().put("name", n).put("value", String.valueOf(v)));
                    }
                }
            }
            ctx.put("visibleVariables", visVars);
            ctx.put("affectionStage", stageDescription(affection));

            // 今日状态：当日心情 + 连续陪伴天数（模板里用 {{#if daily.mood}} 包裹，
            // 自定义过模板的用户不会受影响）
            JSONObject daily = new JSONObject();
            String charId = ch != null ? ch.optString("id", "") : "";
            daily.put("mood", Daily.moodName(session, charId));
            daily.put("moodHint", Daily.moodHint(session, charId));
            int streak = Daily.streakOf(session);
            daily.put("streak", streak > 1 ? String.valueOf(streak) : "");
            ctx.put("daily", daily);

            // 括号导演指令写入的当前场景
            ctx.put("sceneNote", session.optString("sceneNote", ""));

            JSONObject fb = new JSONObject();
            JSONObject feedback = session.optJSONObject("feedback");
            JSONArray reasons = feedback != null ? feedback.optJSONArray("reasons") : null;
            fb.put("reasons", reasons != null ? reasons : new JSONArray());
            ctx.put("feedback", fb);
            ctx.put("config", cfg);

            String tpl = cfg.optString("promptTemplate", DEFAULT_TEMPLATE);
            if (tpl == null || tpl.trim().length() == 0) tpl = DEFAULT_TEMPLATE;
            String out = Template.render(tpl, ctx);
            if (out == null || out.trim().length() == 0) out = fallbackPrompt(cfg, ch, session, story);
            // 剧情节点上下文（如处于剧情驱动会话）
            if (StoryEngine.isStoryDriven(story)) {
                JSONObject node = StoryEngine.nodeOf(story, session.optString("activeNodeId", ""));
                String nodeCtx = StoryEngine.nodeContext(story, node);
                if (nodeCtx.length() > 0) out = out + nodeCtx;
            }
            return out;
        } catch (Exception e) {
            return fallbackPrompt(cfg, ch, session, story);
        }
    }

    private static String fallbackPrompt(JSONObject cfg, JSONObject ch, JSONObject session, JSONObject story) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在扮演【").append(ch.optString("name", "角色")).append("】。\n\n");
        sb.append("## 角色设定\n").append(ch.optString("persona", "")).append("\n\n");
        String priv = ch.optString("privateNote", "");
        if (priv.length() > 0) sb.append("## 补充设定\n").append(priv).append("\n\n");
        if (story != null) {
            String bg = story.optString("globalBackground", "");
            if (bg.length() > 0) sb.append("## 世界背景\n").append(bg).append("\n\n");
            String sit = story.optString("situation", "");
            if (sit.length() > 0) sb.append("## 情境\n").append(sit).append("\n\n");
        }
        sb.append("## 对话者\n你称呼对方为「").append("你").append("」。\n\n");
        JSONArray mems = topMemories(session, cfg);
        if (mems.length() > 0) {
            sb.append("## 你记得的事\n");
            for (int i = 0; i < mems.length(); i++) sb.append("- ").append(mems.optString(i)).append("\n");
            sb.append("\n");
        }
        double affection = affectionOf(session);
        sb.append("## 当前状态\n好感度：").append((int) affection).append("\n").append(stageDescription(affection)).append("\n\n");
        sb.append("## 输出要求\n1. 保持角色，绝不提及 AI、模型、系统\n2. 回复简短自然，通常 1-3 句\n3. 结尾用（）描述当下动作或表情\n");
        if (cfg.optBoolean("enableVariables", true)) sb.append("4. 回复末尾输出变量变化：<var name=\"affection\" delta=\"+2\"/>\n");
        if (cfg.optBoolean("enableInnerVoice", true)) sb.append("5. 再输出：<inner>此刻没说出口的真实想法</inner>\n");
        return sb.toString().trim();
    }

    // ---------- memories ----------

    public static JSONArray topMemories(JSONObject session, JSONObject cfg) {
        JSONArray out = new JSONArray();
        JSONArray mems = session.optJSONArray("memories");
        if (mems == null) return out;
        double fade = cfg.optDouble("memoryFadeThreshold", 2.0);
        int topK = cfg.optInt("memoryInjectTopK", 8);
        List<JSONObject> list = new ArrayList<JSONObject>();
        for (int i = 0; i < mems.length(); i++) {
            JSONObject m = mems.optJSONObject(i);
            if (m != null && m.optDouble("weight", 0) >= fade) list.add(m);
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                return Double.compare(b.optDouble("weight", 0), a.optDouble("weight", 0));
            }
        });
        int n = Math.min(topK, list.size());
        for (int i = 0; i < n; i++) out.put(list.get(i).optString("content", ""));
        return out;
    }

    public static void decayMemories(JSONObject session, JSONObject cfg) {
        JSONArray mems = session.optJSONArray("memories");
        if (mems == null) return;
        double rate = cfg.optDouble("memoryDecayRate", 0.15);
        for (int i = 0; i < mems.length(); i++) {
            JSONObject m = mems.optJSONObject(i);
            if (m.optBoolean("isPinned", false)) continue;
            double w = m.optDouble("weight", 5.0) - rate;
            if (w < 0) w = 0;
            try {
                m.put("weight", Math.round(w * 10) / 10.0);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /** Blocking memory summarization call — run on a background thread. */
    public static void summarizeMemory(JSONObject cfg, JSONObject session) {
        try {
            JSONArray path = pathMessages(session);
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, path.length() - 60);
            JSONArray recentIds = new JSONArray();
            for (int i = start; i < path.length(); i++) {
                JSONObject m = path.optJSONObject(i);
                String role = m.optString("role", "");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                recentIds.put(m.optString("id"));
                sb.append("user".equals(role) ? "用户" : "角色").append("：").append(m.optString("content", "")).append("\n");
            }
            if (sb.length() == 0) return;
            String system = "你是对话分析器。从以下对话中提取值得长期记住的事件。\n" +
                    "要求：\n1. 每条一句话，客观陈述，不加评论\n2. 只提取具体事件、偏好、承诺、关系变化\n" +
                    "3. 忽略寒暄和无信息量的对话\n4. weight 表示重要性 1-10\n" +
                    "5. 严格输出 JSON 数组，无其他内容\n\n格式：[{\"event\":\"...\",\"weight\":8}]";
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "user").put("content", sb.toString()));
            String model = modelOf(cfg, "memory");
            Api.Callback cb = new Api.Callback() {
                public void onChunk(String t) {
                }

                public void onDone(String full) {
                    applySummary(session, full, recentIds);
                }

                public void onError(String msg) {
                }
            };
            Api.callModel(cfg, system, msgs, model, 1024, false, cb);
        } catch (Exception e) {
            // silent — memory summarization must never break the main flow
        }
    }

    /**
     * 追加一条记忆。自动总结与括号指令的 store_memory 都走这里，
     * 记忆的字段形状只此一份。
     *
     * @param weight 会被夹到 [1, 10]
     * @return 新建的记忆对象，session 为空时返回 null
     */
    public static JSONObject addMemory(JSONObject session, String content, double weight, JSONArray sourceIds) {
        if (session == null || content == null || content.trim().length() == 0) return null;
        try {
            JSONArray mems = session.optJSONArray("memories");
            if (mems == null) {
                mems = new JSONArray();
                session.put("memories", mems);
            }
            JSONObject mem = new JSONObject();
            mem.put("id", Store.newId());
            mem.put("content", content.trim());
            mem.put("weight", Math.max(1, Math.min(10, weight)));
            mem.put("createdAt", System.currentTimeMillis());
            mem.put("lastRecalledAt", 0);
            mem.put("isPinned", false);
            mem.put("isEdited", false);
            mem.put("isStale", false);
            mem.put("sourceMessageIds", sourceIds == null ? new JSONArray() : sourceIds);
            mems.put(mem);
            return mem;
        } catch (Exception e) {
            return null;
        }
    }

    private static void applySummary(JSONObject session, String raw, JSONArray recentIds) {
        try {
            String s = raw.trim();
            if (s.startsWith("```")) {
                int a = s.indexOf('\n');
                int b = s.lastIndexOf("```");
                if (a > 0 && b > a) s = s.substring(a + 1, b).trim();
            }
            int start = s.indexOf('[');
            int end = s.lastIndexOf(']');
            if (start < 0 || end <= start) return;
            s = s.substring(start, end + 1);
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String content = e.optString("event", "").trim();
                if (content.length() == 0) continue;
                addMemory(session, content, e.optDouble("weight", 5), recentIds);
            }
            Store.saveSession(session);
        } catch (Exception e) {
            // silent
        }
    }

    public static void markStaleMemories(JSONObject session, String messageId) {
        JSONArray mems = session.optJSONArray("memories");
        if (mems == null) return;
        for (int i = 0; i < mems.length(); i++) {
            JSONObject m = mems.optJSONObject(i);
            JSONArray src = m.optJSONArray("sourceMessageIds");
            if (src != null) {
                for (int j = 0; j < src.length(); j++) {
                    if (messageId.equals(src.optString(j))) {
                        try {
                            m.put("isStale", true);
                        } catch (Exception e) {
                        }
                        break;
                    }
                }
            }
        }
    }

    // ---------- message tree ----------

    public static JSONArray pathMessages(JSONObject session) {
        JSONArray msgs = session.optJSONArray("messages");
        JSONArray path = new JSONArray();
        if (msgs == null) return path;
        String cur = session.optString("currentLeafId", "");
        List<JSONObject> rev = new ArrayList<JSONObject>();
        HashSet<String> seen = new HashSet<String>();
        int guard = 0;
        while (cur != null && cur.length() > 0 && !seen.contains(cur) && guard++ < 5000) {
            seen.add(cur);
            JSONObject msg = null;
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject mm = msgs.optJSONObject(i);
                if (mm != null && cur.equals(mm.optString("id"))) {
                    msg = mm;
                    break;
                }
            }
            if (msg == null) break;
            rev.add(msg);
            if (msg.isNull("parentId")) break;
            cur = msg.optString("parentId", "");
        }
        for (int i = rev.size() - 1; i >= 0; i--) path.put(rev.get(i));
        return path;
    }

    public static JSONArray historyMessages(JSONObject session, int window) {
        JSONArray path = pathMessages(session);
        JSONArray hist = new JSONArray();
        for (int i = 0; i < path.length(); i++) {
            JSONObject m = path.optJSONObject(i);
            String role = m.optString("role", "");
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            StringBuilder c = new StringBuilder(m.optString("content", ""));
            String action = m.optString("action", "");
            if (action.length() > 0) c.append("（").append(action).append("）");
            JSONObject h = new JSONObject();
            try {
                h.put("role", role);
                h.put("content", c.toString());
            } catch (Exception e) {
            }
            hist.put(h);
        }
        int n = Math.max(1, window) * 2;
        if (hist.length() > n) {
            JSONArray trimmed = new JSONArray();
            for (int i = hist.length() - n; i < hist.length(); i++) trimmed.put(hist.opt(i));
            return trimmed;
        }
        return hist;
    }

    /** Deepest descendant of fromId, following the latest-timestamp child at each step. */
    public static String findLeaf(JSONObject session, String fromId) {
        JSONArray msgs = session.optJSONArray("messages");
        if (msgs == null) return fromId;
        String cur = fromId;
        int guard = 0;
        while (guard++ < 5000) {
            String child = null;
            long childTs = -1;
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m != null && cur.equals(m.optString("parentId", ""))) {
                    long ts = m.optLong("timestamp", 0);
                    if (ts > childTs) {
                        childTs = ts;
                        child = m.optString("id");
                    }
                }
            }
            if (child == null) return cur;
            cur = child;
        }
        return cur;
    }

    // ---------- response parsing ----------

    public static JSONObject parseResponse(String raw) {
        JSONObject out = new JSONObject();
        try {
            String r = raw == null ? "" : raw;
            JSONObject varDeltas = new JSONObject();
            Matcher varM = Pattern.compile("<var\\s+name=\"(\\w+)\"\\s+delta=\"([+-]?\\d+)\"\\s*/?>").matcher(r);
            while (varM.find()) {
                try {
                    varDeltas.put(varM.group(1), Integer.parseInt(varM.group(2)));
                } catch (Exception e) {
                }
            }
            String inner = null;
            Matcher innerM = Pattern.compile("<inner>([\\s\\S]*?)</inner>").matcher(r);
            if (innerM.find()) inner = innerM.group(1).trim();

            String text = r
                    .replaceAll("<var\\s+[^>]*/?>", "")
                    .replaceAll("<inner>[\\s\\S]*?</inner>", "")
                    .trim();

            String[] parts = text.split("\\|\\|\\|");
            JSONArray bubbles = new JSONArray();
            Pattern actP = Pattern.compile("[（(]([^）)]+)[）)]\\s*$");
            for (String p : parts) {
                String s = p.trim();
                if (s.length() == 0) continue;
                String action = null;
                Matcher am = actP.matcher(s);
                if (am.find()) {
                    action = am.group(1).trim();
                    s = s.substring(0, am.start()).trim();
                }
                JSONObject b = new JSONObject();
                b.put("text", s);
                if (action != null) b.put("action", action);
                bubbles.put(b);
            }
            out.put("bubbles", bubbles);
            out.put("inner", inner != null ? inner : "");
            out.put("varDeltas", varDeltas);
        } catch (Exception e) {
            // ignore
        }
        return out;
    }

    // ---------- variables / affection ----------

    public static double affectionOf(JSONObject session) {
        JSONObject vars = session.optJSONObject("variables");
        if (vars == null) return 0;
        return vars.optDouble("affection", 0);
    }

    public static void applyVarDeltas(JSONObject session, JSONObject deltas) {
        if (deltas == null || deltas.length() == 0) return;
        JSONObject vars = session.optJSONObject("variables");
        if (vars == null) {
            vars = new JSONObject();
            try {
                session.put("variables", vars);
            } catch (Exception e) {
            }
        }
        JSONArray names = deltas.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i);
            double delta = deltas.optDouble(name, 0);
            double cur = vars.optDouble(name, 0);
            double nv = cur + delta;
            if ("affection".equals(name)) nv = Math.max(-100, Math.min(200, nv));
            try {
                vars.put(name, Math.round(nv * 10) / 10.0);
            } catch (Exception e) {
            }
        }
        double aff = affectionOf(session);
        if (aff > session.optDouble("maxAffection", 0)) {
            try {
                session.put("maxAffection", aff);
            } catch (Exception e) {
            }
        }
    }

    public static String stageName(double a) {
        if (a < 20) return "初识";
        if (a < 60) return "熟悉";
        if (a < 120) return "亲近";
        return "深交";
    }

    public static String stageDescription(double a) {
        if (a < 20) return "当前阶段：初识 —— 礼貌有距离，保持好奇但不越界。";
        if (a < 60) return "当前阶段：熟悉 —— 放松自然，会开玩笑，主动关心日常。";
        if (a < 120) return "当前阶段：亲近 —— 有默契和专属称呼，会主动分享感受。";
        return "当前阶段：深交 —— 无话不谈，在意对方的一切，情绪外露。";
    }

    // ---------- crisis ----------

    public static boolean hasCrisis(String text) {
        if (text == null) return false;
        for (String k : CRISIS_KEYWORDS) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    // ---------- proactive message ----------

    /**
     * 主动消息判定（规格 §4.10）：距上次更新超过 activeMessageGapHours 小时，
     * 且最后一条消息是角色（assistant）时，才发起主动消息。
     */
    public static boolean shouldSendActive(JSONObject session, JSONObject cfg) {
        if (session == null || cfg == null) return false;
        long updated = session.optLong("updatedAt", 0);
        if (updated <= 0) return false;
        JSONArray msgs = session.optJSONArray("messages");
        if (msgs == null || msgs.length() == 0) return false;
        JSONObject last = msgs.optJSONObject(msgs.length() - 1);
        if (last == null || !"assistant".equals(last.optString("role"))) return false;
        double gapHours = cfg.optDouble("activeMessageGapHours", 4.0);
        if (gapHours <= 0) return false;
        long gapMs = System.currentTimeMillis() - updated;
        return gapMs >= (long) (gapHours * 3600L * 1000L);
    }

    // ---------- misc ----------

    public static String modelOf(JSONObject cfg, String role) {
        JSONObject models = cfg.optJSONObject("models");
        if (models == null) return "";
        String m = models.optString(role, "");
        if (m.length() == 0) return models.optString("main", "");
        return m;
    }
}
