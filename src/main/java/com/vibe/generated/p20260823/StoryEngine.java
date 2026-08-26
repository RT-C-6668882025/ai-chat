package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 剧情图引擎：节点/选项边/条件边/循环合流/变量赋值/结局判定。
 * 剧情图数据结构（故事 story）：
 * {
 *   id, name, type("light"|"full"),
 *   globalBackground, situation, callMe, userSetting,
 *   characters: [{id,name,emoji,persona}],
 *   initialNodeId,
 *   nodes: [{
 *     id, name, type("start"|"normal"|"ending"|"merge"),
 *     text,             // 进入节点时的剧情台词（可为空）
 *     speakerId,        // 说话角色 id（为空 = 主角色；"__narrator__" = 旁白）
 *     instruction,      // 自由聊天时的剧情指引
 *     choices: [{text,next,condition}],   // 选项边
 *     edges: [{type,next,condition,keywords,afterTurns}], // 条件边/关键词边/自动边
 *     assignments: [{name,value}]         // 变量赋值 "+5" / "-3" / "10"
 *   }],
 *   endings: [{nodeId,title,description,icon}]
 * }
 * 会话侧字段：story(内联对象), activeNodeId, turnsInNode, unlockedEndings, isCompleted
 */
public class StoryEngine {

    // ---------- 基础查询 ----------

    public static JSONObject nodeOf(JSONObject story, String nodeId) {
        if (story == null || nodeId == null || nodeId.length() == 0) return null;
        JSONArray nodes = story.optJSONArray("nodes");
        if (nodes == null) return null;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n != null && nodeId.equals(n.optString("id"))) return n;
        }
        return null;
    }

    public static JSONObject initialNode(JSONObject story) {
        if (story == null) return null;
        return nodeOf(story, story.optString("initialNodeId", ""));
    }

    /** 是否为剧情驱动（有节点即驱动） */
    public static boolean isStoryDriven(JSONObject story) {
        if (story == null) return false;
        JSONArray nodes = story.optJSONArray("nodes");
        return nodes != null && nodes.length() > 0;
    }

    public static boolean hasChoices(JSONObject node) {
        if (node == null) return false;
        JSONArray c = node.optJSONArray("choices");
        return c != null && c.length() > 0;
    }

    /** 满足条件的可见选项边 */
    public static JSONArray visibleChoices(JSONObject node, JSONObject session) {
        JSONArray out = new JSONArray();
        if (node == null) return out;
        JSONArray choices = node.optJSONArray("choices");
        if (choices == null) return out;
        for (int i = 0; i < choices.length(); i++) {
            JSONObject c = choices.optJSONObject(i);
            if (c == null) continue;
            String cond = c.optString("condition", "");
            if (cond.length() > 0 && !evalCondition(cond, session)) continue;
            out.put(c);
        }
        return out;
    }

    // ---------- 条件判定 ----------

    /** 条件语法：var>=10 / var<=5 / var==3 / var!=3 / var>0 / var<0 / var（非0为真）/ !var */
    public static boolean evalCondition(String cond, JSONObject session) {
        if (cond == null || cond.trim().length() == 0) return true;
        String c = cond.trim();
        JSONObject vars = session == null ? null : session.optJSONObject("variables");
        if (vars == null) vars = new JSONObject();
        boolean neg = c.startsWith("!");
        if (neg) c = c.substring(1).trim();

        String[] ops = {">=", "<=", "!=", "==", ">", "<"};
        for (int i = 0; i < ops.length; i++) {
            String op = ops[i];
            int idx = c.indexOf(op);
            if (idx > 0) {
                String name = c.substring(0, idx).trim();
                String rhs = c.substring(idx + op.length()).trim();
                double rv = 0;
                try {
                    rv = Double.parseDouble(rhs);
                } catch (Exception e) {
                    rv = 0;
                }
                double lv = vars.optDouble(name, 0);
                boolean ok;
                if (">=".equals(op)) ok = lv >= rv;
                else if ("<=".equals(op)) ok = lv <= rv;
                else if ("!=".equals(op)) ok = lv != rv;
                else if ("==".equals(op)) ok = lv == rv;
                else if (">".equals(op)) ok = lv > rv;
                else ok = lv < rv;
                return neg ? !ok : ok;
            }
        }
        // 裸变量：非 0 为真
        boolean truthy = vars.optDouble(c, 0) != 0;
        return neg ? !truthy : truthy;
    }

    // ---------- 变量赋值 ----------

    public static void applyAssignments(JSONObject session, JSONArray assigns) {
        if (assigns == null || assigns.length() == 0) return;
        JSONObject vars = session.optJSONObject("variables");
        if (vars == null) {
            vars = new JSONObject();
            try {
                session.put("variables", vars);
            } catch (Exception e) {
            }
        }
        for (int i = 0; i < assigns.length(); i++) {
            JSONObject a = assigns.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            String val = a.optString("value", "");
            if (name.length() == 0) continue;
            double cur = vars.optDouble(name, 0);
            double nv = cur;
            try {
                if (val.startsWith("+")) nv = cur + Double.parseDouble(val.substring(1));
                else if (val.startsWith("-")) nv = cur - Double.parseDouble(val.substring(1));
                else nv = Double.parseDouble(val);
            } catch (Exception e) {
                nv = cur;
            }
            try {
                vars.put(name, Math.round(nv * 10) / 10.0);
            } catch (Exception e) {
            }
        }
        double aff = vars.optDouble("affection", 0);
        if (aff > session.optDouble("maxAffection", 0)) {
            try {
                session.put("maxAffection", aff);
            } catch (Exception e) {
            }
        }
    }

    // ---------- 边解析（非选项边：关键词/条件/自动） ----------

    /**
     * 在自由聊天后尝试推进。返回下一个节点 id；无可推进返回 null（继续自由对话）。
     * 优先级：自动边(afterTurns) > 关键词边 > 条件边。
     */
    public static String resolveEdge(JSONObject session, JSONObject node, String userText) {
        if (node == null) return null;
        int turns = session.optInt("turnsInNode", 0);
        JSONArray edges = node.optJSONArray("edges");
        if (edges == null) return null;

        // 1) 自动边
        for (int i = 0; i < edges.length(); i++) {
            JSONObject e = edges.optJSONObject(i);
            if (e == null) continue;
            if (!"auto".equals(e.optString("type", ""))) continue;
            String cond = e.optString("condition", "");
            if (cond.length() > 0 && !evalCondition(cond, session)) continue;
            int after = e.optInt("afterTurns", 1);
            if (turns >= after) return e.optString("next", "");
        }
        // 2) 关键词边
        if (userText != null && userText.length() > 0) {
            for (int i = 0; i < edges.length(); i++) {
                JSONObject e = edges.optJSONObject(i);
                if (e == null) continue;
                if (!"keyword".equals(e.optString("type", ""))) continue;
                String cond = e.optString("condition", "");
                if (cond.length() > 0 && !evalCondition(cond, session)) continue;
                JSONArray kws = e.optJSONArray("keywords");
                if (kws != null) {
                    for (int j = 0; j < kws.length(); j++) {
                        String kw = kws.optString(j);
                        if (kw.length() > 0 && userText.contains(kw)) return e.optString("next", "");
                    }
                }
            }
        }
        // 3) 条件边（任意满足即推进）
        for (int i = 0; i < edges.length(); i++) {
            JSONObject e = edges.optJSONObject(i);
            if (e == null) continue;
            if (!"condition".equals(e.optString("type", ""))) continue;
            String cond = e.optString("condition", "");
            if (cond.length() > 0 && evalCondition(cond, session)) return e.optString("next", "");
        }
        return null;
    }

    // ---------- 结局 ----------

    public static boolean isEnding(JSONObject node) {
        return node != null && "ending".equals(node.optString("type", ""));
    }

    public static void unlockEnding(JSONObject session, JSONObject node) {
        if (node == null) return;
        JSONArray unlocked = session.optJSONArray("unlockedEndings");
        if (unlocked == null) {
            unlocked = new JSONArray();
            try {
                session.put("unlockedEndings", unlocked);
            } catch (Exception e) {
            }
        }
        String nodeId = node.optString("id", "");
        for (int i = 0; i < unlocked.length(); i++) {
            if (nodeId.equals(unlocked.optString(i))) return;
        }
        unlocked.put(nodeId);
        try {
            session.put("isCompleted", true);
        } catch (Exception e) {
        }
    }

    public static boolean hasUnlocked(JSONObject session, String nodeId) {
        JSONArray unlocked = session == null ? null : session.optJSONArray("unlockedEndings");
        if (unlocked == null) return false;
        for (int i = 0; i < unlocked.length(); i++) {
            if (nodeId.equals(unlocked.optString(i))) return true;
        }
        return false;
    }

    /** 所有结局节点（含元信息，可含未解锁） */
    public static JSONArray endingNodes(JSONObject story) {
        JSONArray out = new JSONArray();
        if (story == null) return out;
        JSONArray nodes = story.optJSONArray("nodes");
        if (nodes == null) return out;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n != null && "ending".equals(n.optString("type", ""))) out.put(n);
        }
        return out;
    }

    public static String nodeTypeName(String type) {
        if ("start".equals(type)) return "开始";
        if ("ending".equals(type)) return "结局";
        if ("merge".equals(type)) return "合流";
        return "普通";
    }

    // ---------- 说话人 ----------

    /** 节点的说话角色对象（可能为旁白或主角色） */
    public static JSONObject speakerOf(JSONObject story, JSONObject node) {
        if (story == null || node == null) return null;
        String sid = node.optString("speakerId", "");
        if (sid.length() == 0 || "__narrator__".equals(sid)) return null;
        JSONArray chars = story.optJSONArray("characters");
        if (chars != null) {
            for (int i = 0; i < chars.length(); i++) {
                JSONObject c = chars.optJSONObject(i);
                if (c != null && sid.equals(c.optString("id"))) return c;
            }
        }
        return null;
    }

    public static boolean isNarrator(JSONObject node) {
        return node != null && "__narrator__".equals(node.optString("speakerId", ""));
    }

    // ---------- 提示词注入 ----------

    /** 拼接到系统提示词末尾的当前剧情节点上下文 */
    public static String nodeContext(JSONObject story, JSONObject node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 当前剧情节点\n");
        String name = node.optString("name", "");
        if (name.length() > 0) sb.append("节点：").append(name).append("\n");
        String instr = node.optString("instruction", "");
        if (instr.length() > 0) sb.append("剧情指引：").append(instr).append("\n");
        String text = node.optString("text", "");
        if (text.length() > 0) sb.append("已说台词：").append(text).append("\n");
        if (isEnding(node)) sb.append("（当前处于结局节点，对话应自然走向收尾）\n");
        return sb.toString();
    }
}
