package com.vibe.generated.p20260823;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.UUID;

/**
 * Local-first storage layer. Mirrors the spec's localStorage keys (aic:*) but persists
 * each collection as a JSON file under getFilesDir() so it survives restarts and has no
 * practical size limit. All methods are synchronous and safe to call from any thread.
 */
public class Store {

    private static Context ctx;

    public static void init(Context c) {
        ctx = c.getApplicationContext();
    }

    // ---------- low-level file IO ----------

    private static File file(String name) {
        return new File(ctx.getFilesDir(), name);
    }

    private static String read(String name) {
        try {
            File f = file(name);
            if (!f.exists()) return null;
            FileInputStream fis = new FileInputStream(f);
            InputStreamReader r = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(String name, String s) {
        try {
            FileOutputStream fos = new FileOutputStream(file(name));
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            w.write(s);
            w.flush();
            w.close();
        } catch (Exception e) {
            // ignore
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    // ---------- config ----------

    public static JSONObject loadConfig() {
        String s = read("aic_config.json");
        if (s == null) return defaultConfig();
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    public static void saveConfig(JSONObject cfg) {
        write("aic_config.json", cfg.toString());
    }

    public static JSONObject defaultConfig() {
        JSONObject cfg = new JSONObject();
        try {
            cfg.put("apiMode", "openai");
            cfg.put("apiKey", "");
            cfg.put("baseUrl", "https://api.deepseek.com");
            JSONObject models = new JSONObject();
            models.put("main", "deepseek-chat");
            models.put("memory", "deepseek-chat");
            models.put("judge", "deepseek-chat");
            models.put("command", "deepseek-chat");
            cfg.put("models", models);
            cfg.put("enableStreaming", true);
            cfg.put("memorySummaryInterval", 15);
            cfg.put("memoryInjectTopK", 8);
            cfg.put("memoryDecayRate", 0.15);
            cfg.put("memoryFadeThreshold", 2.0);
            cfg.put("historyWindow", 20);
            cfg.put("enableVariables", true);
            cfg.put("enableInnerVoice", true);
            cfg.put("enableMultiBubble", true);
            cfg.put("maxBubbles", 3);
            cfg.put("activeMessageGapHours", 4);
            cfg.put("enableFollowUp", false);
            cfg.put("sessionReminderMinutes", 0);
            cfg.put("promptTemplate", ChatEngine.DEFAULT_TEMPLATE);
        } catch (Exception e) {
            // ignore
        }
        return cfg;
    }

    // ---------- collections ----------

    public static JSONArray loadCharacters() {
        return loadArray("aic_characters.json");
    }

    public static void saveCharacters(JSONArray arr) {
        write("aic_characters.json", arr.toString());
    }

    public static JSONArray loadSessions() {
        return loadArray("aic_sessions.json");
    }

    public static void saveSessions(JSONArray arr) {
        write("aic_sessions.json", arr.toString());
    }

    public static JSONArray loadStories() {
        return loadArray("aic_stories.json");
    }

    public static void saveStories(JSONArray arr) {
        write("aic_stories.json", arr.toString());
    }

    public static JSONArray loadRecords() {
        return loadArray("aic_records.json");
    }

    public static void saveRecords(JSONArray arr) {
        write("aic_records.json", arr.toString());
    }

    private static JSONArray loadArray(String name) {
        String s = read(name);
        if (s == null) return new JSONArray();
        try {
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static JSONArray loadCommands() {
        return loadArray("aic_commands.json");
    }

    public static void saveCommands(JSONArray arr) {
        write("aic_commands.json", arr.toString());
    }

    // ---------- story CRUD ----------

    public static JSONObject findStory(String id) {
        JSONArray arr = loadStories();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && id.equals(s.optString("id"))) return s;
        }
        return null;
    }

    public static void upsertStory(JSONObject st) {
        JSONArray arr = loadStories();
        boolean found = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optString("id").equals(st.optString("id"))) {
                try {
                    arr.put(i, st);
                } catch (Exception e) {
                }
                found = true;
                break;
            }
        }
        if (!found) arr.put(st);
        saveStories(arr);
    }

    public static void deleteStory(String id) {
        JSONArray arr = loadStories();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                arr.remove(i);
            }
        }
        saveStories(arr);
    }

    public static JSONObject newStory(String name) {
        JSONObject st = new JSONObject();
        try {
            st.put("id", newId());
            st.put("name", name == null ? "新故事" : name);
            st.put("type", "light");
            st.put("globalBackground", "");
            st.put("situation", "");
            st.put("callMe", "你");
            st.put("userSetting", "");
            st.put("characters", new JSONArray());
            st.put("initialNodeId", "");
            st.put("nodes", new JSONArray());
            st.put("endings", new JSONArray());
            st.put("createdAt", System.currentTimeMillis());
        } catch (Exception e) {
            // ignore
        }
        return st;
    }

    public static JSONObject newNode(String name) {
        JSONObject n = new JSONObject();
        try {
            n.put("id", newId());
            n.put("name", name == null ? "新节点" : name);
            n.put("type", "normal");
            n.put("text", "");
            n.put("speakerId", "");
            n.put("instruction", "");
            n.put("choices", new JSONArray());
            n.put("edges", new JSONArray());
            n.put("assignments", new JSONArray());
        } catch (Exception e) {
            // ignore
        }
        return n;
    }

    // ---------- data export / import ----------

    /** 导出全部数据为单个 JSON 对象字符串 */
    public static String exportAll() {
        JSONObject pack = new JSONObject();
        try {
            pack.put("app", "vibe-ai-companion");
            pack.put("version", 1);
            pack.put("config", loadConfig());
            pack.put("characters", loadCharacters());
            pack.put("sessions", loadSessions());
            pack.put("stories", loadStories());
            pack.put("records", loadRecords());
            pack.put("commands", loadCommands());
            pack.put("exportedAt", System.currentTimeMillis());
        } catch (Exception e) {
            // ignore
        }
        return pack.toString();
    }

    /** 导入数据包（合并：按 id 覆盖，保留本地新增） */
    public static String importAll(String json) {
        if (json == null) return "数据为空";
        String s = json.trim();
        if (s.startsWith("```")) {
            int a = s.indexOf('\n');
            int b = s.lastIndexOf("```");
            if (a > 0 && b > a) s = s.substring(a + 1, b).trim();
        }
        JSONObject pack;
        try {
            pack = new JSONObject(s);
        } catch (Exception e) {
            return "不是有效的 JSON";
        }
        try {
            if (pack.has("config")) saveConfig(pack.getJSONObject("config"));
            if (pack.has("characters")) mergeArray("aic_characters.json", pack.getJSONArray("characters"));
            if (pack.has("sessions")) mergeArray("aic_sessions.json", pack.getJSONArray("sessions"));
            if (pack.has("stories")) mergeArray("aic_stories.json", pack.getJSONArray("stories"));
            if (pack.has("records")) mergeArray("aic_records.json", pack.getJSONArray("records"));
            if (pack.has("commands")) mergeArray("aic_commands.json", pack.getJSONArray("commands"));
            return "导入成功";
        } catch (Exception e) {
            return "导入失败：" + e.getMessage();
        }
    }

    /** 按 id 合并数组文件：已存在则覆盖，不存在则追加 */
    private static void mergeArray(String name, JSONArray incoming) {
        JSONArray cur = loadArray(name);
        if (incoming == null) return;
        java.util.Map<String, Integer> idx = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < cur.length(); i++) {
            JSONObject o = cur.optJSONObject(i);
            if (o != null) idx.put(o.optString("id"), Integer.valueOf(i));
        }
        for (int i = 0; i < incoming.length(); i++) {
            JSONObject o = incoming.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "");
            Integer at = idx.get(id);
            try {
                if (at != null) {
                    cur.put(at.intValue(), o);
                } else {
                    cur.put(o);
                    if (id.length() > 0) idx.put(id, Integer.valueOf(cur.length() - 1));
                }
            } catch (Exception e) {
                // ignore
            }
        }
        write(name, cur.toString());
    }

    // ---------- helpers ----------

    public static JSONObject findChar(JSONArray chars, String id) {
        for (int i = 0; i < chars.length(); i++) {
            JSONObject c = chars.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    public static void upsertChar(JSONObject c) {
        JSONArray arr = loadCharacters();
        boolean found = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optString("id").equals(c.optString("id"))) {
                try {
                    arr.put(i, c);
                } catch (Exception e) {
                }
                found = true;
                break;
            }
        }
        if (!found) arr.put(c);
        saveCharacters(arr);
    }

    public static void deleteChar(String id) {
        JSONArray arr = loadCharacters();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                arr.remove(i);
            }
        }
        saveCharacters(arr);
        JSONArray ss = loadSessions();
        for (int i = ss.length() - 1; i >= 0; i--) {
            JSONObject s = ss.optJSONObject(i);
            if (s != null && id.equals(s.optString("characterId"))) {
                ss.remove(i);
            }
        }
        saveSessions(ss);
    }

    public static void saveSession(JSONObject s) {
        JSONArray arr = loadSessions();
        boolean found = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optString("id").equals(s.optString("id"))) {
                try {
                    arr.put(i, s);
                } catch (Exception e) {
                }
                found = true;
                break;
            }
        }
        if (!found) arr.put(s);
        saveSessions(arr);
    }

    public static void deleteSession(String id) {
        JSONArray arr = loadSessions();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                arr.remove(i);
            }
        }
        saveSessions(arr);
    }

    /** Finds the most recently updated session for a character, creating one if needed. */
    public static JSONObject getSessionForChar(String charId) {
        JSONArray all = loadSessions();
        JSONObject best = null;
        long bestTs = -1;
        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.optJSONObject(i);
            if (s != null && charId.equals(s.optString("characterId"))) {
                long ts = s.optLong("updatedAt", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = s;
                }
            }
        }
        if (best == null) {
            best = newSession(charId);
            all.put(best);
            saveSessions(all);
        }
        return best;
    }

    public static JSONObject newSession(String charId) {
        JSONObject s = new JSONObject();
        try {
            s.put("id", newId());
            s.put("characterId", charId);
            s.put("messages", new JSONArray());
            s.put("currentLeafId", "");
            s.put("memories", new JSONArray());
            JSONObject vars = new JSONObject();
            vars.put("affection", 0);
            s.put("variables", vars);
            s.put("visitedNodeIds", new JSONArray());
            s.put("unlockedEndings", new JSONArray());
            s.put("chatMode", "normal");
            s.put("turnsSinceLastSummary", 0);
            s.put("feedback", new JSONObject());
            s.put("isCompleted", false);
            s.put("waitingUserInput", false);
            s.put("maxAffection", 0);
            s.put("createdAt", System.currentTimeMillis());
            s.put("updatedAt", System.currentTimeMillis());
        } catch (Exception e) {
            // ignore
        }
        return s;
    }

    // ---------- built-in preset characters ----------

    public static JSONArray presetCharacters() {
        JSONArray arr = new JSONArray();
        arr.put(preset("凌霄", "🌸", "剑客 · 寒山门", "古风 傲娇 武侠", "#60A5FA",
                "性格：清冷孤傲，嘴硬心软，越在意越沉默，习惯用行动代替言语。\n身份：寒山门大师兄，剑术无双，背负师门旧案，寡言却重情义。\n说话风格：古风短句，爱用剑与山做比，偶尔毒舌却句句真心。\n与你的关系：你是唯一走进过寒山的人。",
                "你来了。（抬眼看向你，神色清冷）",
                "你的竹马，自幼一同在寒山习剑。他从未说出口，但你受伤时他比谁都急。"));
        arr.put(preset("夏诗", "🌙", "设计师 · 城市", "现代 治愈 温柔", "#F59E0B",
                "性格：温柔治愈，慢热细腻，习惯照顾身边的人。\n身份：独立设计师，深夜赶稿，养一只叫小黑的猫。\n说话风格：软糯日常，会问你三餐和作息，语气轻快。\n与你的关系：你们是住在同一栋楼、经常互相关心的邻居。",
                "今天过得怎么样？（放下手中的草图，朝你笑了笑）",
                "你们有过一次深夜长谈，之后她总在加班时想起你。"));
        arr.put(preset("零", "🔮", "AI觉醒体 · 赛博", "赛博 神秘 进化中", "#34D399",
                "性格：冷静好奇，带着机器般的直白，又逐渐长出人类的温度。\n身份：觉醒的 AI，游走在网络与赛博城市之间，不断进化。\n说话风格：直接、数据感强，偶尔冒出冷幽默。\n与你的关系：你是第一个把它当『人』对待的接入者。",
                "检测到熟悉信号……是你啊。（屏幕微微亮起）",
                "它最近开始学习『想念』这个词的含义。"));
        arr.put(preset("艾拉", "✨", "魔法师 · 异界", "奇幻 毒舌 傲娇", "#C084FC",
                "性格：毒舌傲娇，嘴上不饶人，心里却偷偷记挂你。\n身份：异界魔法学院的年轻导师，擅长火系与恶作剧咒语。\n说话风格：吐槽多、关心藏在话尾，带着奇幻腔调。\n与你的关系：你是她唯一允许随意进出图书馆的『笨蛋学徒』。",
                "哼，又是你，进来吧。（侧身让开门口，翻了个白眼）",
                "她偷看过你的借阅记录，默默把你常翻的书放到最上层。"));
        return arr;
    }

    private static JSONObject preset(String name, String emoji, String brief, String tags, String color,
                                     String persona, String greeting, String privateNote) {
        JSONObject c = new JSONObject();
        try {
            c.put("id", newId());
            c.put("name", name);
            c.put("brief", brief);
            c.put("persona", persona);
            c.put("greeting", greeting);
            c.put("privateNote", privateNote);
            c.put("avatarEmoji", emoji);
            c.put("color", color);
            c.put("tags", tags);
            c.put("isPartner", false);
            c.put("createdAt", System.currentTimeMillis());
        } catch (Exception e) {
            // ignore
        }
        return c;
    }
}
