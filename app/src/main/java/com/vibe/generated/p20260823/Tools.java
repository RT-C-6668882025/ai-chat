package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 括号指令的工具分发，以及每次全局修改的记录与撤销。
 *
 * 接口形状照原生 tool calling 设计（一个 type + 一个 args 对象），因此以后若把
 * Agent 的传输换成真正的 tool_calls，只需换 Agent，这一层不动。
 *
 * 每个会改变全局状态的工具都往 session.globalChanges 追加一条：
 *
 *   { timestamp, type, target, before, after, trigger }
 *
 * target 是撤销的落点（变量名、角色 id），没有它就不知道该往哪写回。
 * before/after 一律存字符串。读取一律 optJSONArray + 判空，因此老存档不用迁移。
 */
public class Tools {

    private Tools() {
    }

    // ---------- 分发 ----------

    /**
     * 执行一条已分类的括号指令。
     *
     * @param type    Agent 的类型常量；ACTION 不在这里处理（它没有副作用，由渲染层负责）
     * @param trigger 触发这条修改的原文，进变更记录供用户辨认
     * @return 给用户看的一句说明，无事发生时返回 null
     */
    public static String dispatch(String type, JSONObject args, JSONObject session,
                                  JSONObject character, String trigger) {
        if (type == null || session == null) return null;
        if (args == null) args = new JSONObject();
        try {
            if (Agent.SET_SCENE.equals(type)) return setScene(session, Json.str(args, "scene", trigger), trigger);
            if (Agent.UPDATE_VARIABLE.equals(type)) return updateVariable(session, args, trigger);
            if (Agent.UPDATE_PERSONA.equals(type)) return updatePersona(session, character, Json.str(args, "note", trigger), trigger);
            if (Agent.STORE_MEMORY.equals(type)) return storeMemory(session, args, trigger);
        } catch (Exception e) {
            AppLogger.e("TOOLS", "dispatch failed: " + type, e);
        }
        return null;
    }

    // ---------- 各工具 ----------

    public static String setScene(JSONObject session, String scene, String trigger) throws Exception {
        if (scene == null || scene.trim().length() == 0) return null;
        String before = session.optString("sceneNote", "");
        session.put("sceneNote", scene.trim());
        record(session, Agent.SET_SCENE, "sceneNote", before, scene.trim(), trigger);
        return "场景已设定：" + scene.trim();
    }

    static String updateVariable(JSONObject session, JSONObject args, String trigger) throws Exception {
        String name = Json.str(args, "name");
        String op = Json.str(args, "op", "=");
        String value = Json.str(args, "value");
        if (name.length() == 0 || value.length() == 0) return null;

        String key = Directive.normalizedName(name);
        String before = Directive.valueText(session, key);
        // 别名归一、好感度区间、数值格式化都在 Directive 里，这里不重复实现
        String note = Directive.applyVariable(session, name, op, value);
        if (note == null) return null;
        record(session, Agent.UPDATE_VARIABLE, key, before, Directive.valueText(session, key), trigger);
        return note;
    }

    /**
     * 人设修改一律**追加**到 privateNote，不碰 persona。
     *
     * 分类器可能判错，覆盖 persona 的代价是抹掉用户精心写的设定；追加最多是多一句
     * 无关的话，还能在变更记录里一键撤销。
     */
    static String updatePersona(JSONObject session, JSONObject character, String note, String trigger) throws Exception {
        if (character == null || note == null || note.trim().length() == 0) return null;
        String before = character.optString("privateNote", "");
        String add = note.trim();
        String after = before.length() == 0 ? add : before + "\n" + add;
        character.put("privateNote", after);
        Store.upsertChar(character);
        record(session, Agent.UPDATE_PERSONA, character.optString("id", ""), before, after, trigger);
        return "设定已补充：" + add;
    }

    static String storeMemory(JSONObject session, JSONObject args, String trigger) throws Exception {
        String content = Json.str(args, "content", trigger);
        if (content == null || content.trim().length() == 0) return null;
        double weight = args.optDouble("weight", 6);
        JSONObject mem = ChatEngine.addMemory(session, content, weight, null);
        if (mem == null) return null;
        record(session, Agent.STORE_MEMORY, mem.optString("id", ""), "", content.trim(), trigger);
        return "已记住：" + content.trim();
    }

    // ---------- 变更记录 ----------

    public static JSONArray changesOf(JSONObject session) {
        if (session == null) return new JSONArray();
        JSONArray a = session.optJSONArray("globalChanges");
        return a == null ? new JSONArray() : a;
    }

    /** 追加一条变更记录。变量指令的本地快路也走这里，记录才是完整的。 */
    public static void record(JSONObject session, String type, String target,
                              String before, String after, String trigger) {
        if (session == null) return;
        try {
            JSONArray a = session.optJSONArray("globalChanges");
            if (a == null) {
                a = new JSONArray();
                session.put("globalChanges", a);
            }
            JSONObject c = new JSONObject();
            c.put("timestamp", System.currentTimeMillis());
            c.put("type", type);
            c.put("target", target == null ? "" : target);
            c.put("before", before == null ? "" : before);
            c.put("after", after == null ? "" : after);
            c.put("trigger", trigger == null ? "" : trigger);
            a.put(c);
        } catch (Exception e) {
            AppLogger.e("TOOLS", "record failed", e);
        }
    }

    /**
     * 该条之后是否还有针对同一目标的变更。
     *
     * 有的话撤销会把那些一并抹掉（因为直接写回 before），调用方应当先问用户。
     */
    public static int laterChangesFor(JSONObject session, int index) {
        JSONArray a = changesOf(session);
        JSONObject c = a.optJSONObject(index);
        if (c == null) return 0;
        String type = c.optString("type", "");
        String target = c.optString("target", "");
        int n = 0;
        for (int i = index + 1; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (type.equals(o.optString("type", "")) && target.equals(o.optString("target", ""))) n++;
        }
        return n;
    }

    /**
     * 撤销一条：把 before 写回去，并从记录里移除该条。
     *
     * @param character 该条是人设修改时必须传，且必须是 target 指向的那个角色
     * @return 是否撤销成功
     */
    public static boolean undo(JSONObject session, JSONObject character, int index) {
        JSONArray a = changesOf(session);
        JSONObject c = a.optJSONObject(index);
        if (c == null) return false;
        String type = c.optString("type", "");
        String target = c.optString("target", "");
        String before = c.optString("before", "");
        try {
            if (Agent.SET_SCENE.equals(type)) {
                session.put("sceneNote", before);
            } else if (Agent.UPDATE_VARIABLE.equals(type)) {
                JSONObject vars = session.optJSONObject("variables");
                if (vars == null) {
                    vars = new JSONObject();
                    session.put("variables", vars);
                }
                if (before.length() == 0) {
                    // 该条创建了这个变量，撤销即删除
                    vars.remove(target);
                } else {
                    Double num = parseDouble(before);
                    if (num != null) vars.put(target, num.doubleValue());
                    else vars.put(target, before);
                }
            } else if (Agent.UPDATE_PERSONA.equals(type)) {
                if (character == null || !target.equals(character.optString("id", ""))) return false;
                character.put("privateNote", before);
                Store.upsertChar(character);
            } else if (Agent.STORE_MEMORY.equals(type)) {
                // target 是记忆 id，撤销即删掉那条记忆
                JSONArray mems = session.optJSONArray("memories");
                if (mems != null) {
                    for (int i = mems.length() - 1; i >= 0; i--) {
                        JSONObject m = mems.optJSONObject(i);
                        if (m != null && target.equals(m.optString("id", ""))) mems.remove(i);
                    }
                }
            } else {
                return false;
            }
            a.remove(index);
            Store.saveSession(session);
            return true;
        } catch (Exception e) {
            AppLogger.e("TOOLS", "undo failed", e);
            return false;
        }
    }

    private static Double parseDouble(String s) {
        try {
            return Double.valueOf(Double.parseDouble(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** 变更类型的中文标签，列表里显示用。 */
    public static String labelOf(String type) {
        if (Agent.SET_SCENE.equals(type)) return "场景";
        if (Agent.UPDATE_VARIABLE.equals(type)) return "变量";
        if (Agent.UPDATE_PERSONA.equals(type)) return "人设";
        if (Agent.STORE_MEMORY.equals(type)) return "记忆";
        return type == null ? "" : type;
    }
}
