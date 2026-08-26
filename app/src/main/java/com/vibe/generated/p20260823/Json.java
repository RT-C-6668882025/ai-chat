package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 从模型返回的自由文本里抠出 JSON，并对生成的剧情结构做校验。
 *
 * 模型经常把 JSON 包在 ``` 围栏里、或前后带一段说明，因此不能直接 parse。
 */
public class Json {

    private Json() {
    }

    /** 去掉 markdown 围栏，取第一个 { 到最后一个 } 之间的内容。失败返回 null。 */
    public static JSONObject extractObject(String raw) {
        String s = strip(raw);
        int st = s.indexOf('{');
        int en = s.lastIndexOf('}');
        if (st < 0 || en <= st) return null;
        try {
            return new JSONObject(s.substring(st, en + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** 同上，取数组。 */
    public static JSONArray extractArray(String raw) {
        String s = strip(raw);
        int st = s.indexOf('[');
        int en = s.lastIndexOf(']');
        if (st < 0 || en <= st) return null;
        try {
            return new JSONArray(s.substring(st, en + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String strip(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int a = s.indexOf('\n');
            int b = s.lastIndexOf("```");
            if (a > 0 && b > a) s = s.substring(a + 1, b).trim();
        }
        return s;
    }

    /**
     * 校验 AI 生成的剧情结构是否自洽，不合法就别写进存档。
     *
     * @return null 表示通过，否则返回给用户看的错误原因
     */
    public static String validateStory(JSONObject story) {
        if (story == null) return "没有解析到剧情数据";
        JSONArray nodes = story.optJSONArray("nodes");
        if (nodes == null || nodes.length() == 0) return "剧情里没有任何节点";

        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n == null) return "第 " + (i + 1) + " 个节点不是有效对象";
            String id = n.optString("id", "");
            if (id.length() == 0) return "第 " + (i + 1) + " 个节点缺少 id";
            if (!ids.add(id)) return "节点 id 重复：" + id;
        }

        String init = story.optString("initialNodeId", "");
        if (init.length() == 0) return "缺少起始节点 initialNodeId";
        if (!ids.contains(init)) return "起始节点不存在：" + init;

        // 所有边和选项都必须指向存在的节点，否则运行时会走进死路
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            String from = n.optString("id", "");
            String bad = checkTargets(n.optJSONArray("choices"), ids, from, "选项");
            if (bad != null) return bad;
            bad = checkTargets(n.optJSONArray("edges"), ids, from, "边");
            if (bad != null) return bad;
        }

        JSONArray endings = story.optJSONArray("endings");
        if (endings != null) {
            for (int i = 0; i < endings.length(); i++) {
                JSONObject e = endings.optJSONObject(i);
                if (e == null) continue;
                String nid = e.optString("nodeId", "");
                if (nid.length() > 0 && !ids.contains(nid)) {
                    return "结局指向了不存在的节点：" + nid;
                }
            }
        }
        return null;
    }

    private static String checkTargets(JSONArray arr, Set<String> ids, String from, String label) {
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String next = o.optString("next", "");
            if (next.length() > 0 && !ids.contains(next)) {
                return "节点「" + from + "」的" + label + "指向了不存在的节点：" + next;
            }
        }
        return null;
    }
}
