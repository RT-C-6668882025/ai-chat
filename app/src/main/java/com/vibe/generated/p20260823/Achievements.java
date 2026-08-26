package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 成就系统：对会话状态做纯函数判定，新解锁的写回 session。
 *
 * 会话侧字段：achievements: [{id,title,desc,at}]
 * 判定只读现有数据（消息、记忆、结局、里程碑、签到），因此老存档一旦继续使用
 * 就会自动补齐已经达成的成就，无需迁移。
 */
public class Achievements {

    /** {id, 标题, 说明} —— 顺序即纪念册展示顺序 */
    public static final String[][] DEFS = {
            {"first_talk", "初次见面", "发出第一条消息"},
            {"talk_50", "聊得来", "累计 50 条对话"},
            {"talk_200", "说不完的话", "累计 200 条对话"},
            {"talk_500", "长夜漫谈", "累计 500 条对话"},
            {"streak_3", "小小习惯", "连续陪伴 3 天"},
            {"streak_7", "一周之约", "连续陪伴 7 天"},
            {"streak_30", "满月同行", "连续陪伴 30 天"},
            {"days_10", "十日之交", "累计陪伴 10 天"},
            {"aff_20", "破冰", "好感度达到 20"},
            {"aff_60", "渐入佳境", "好感度达到 60"},
            {"aff_120", "亲密无间", "好感度达到 120"},
            {"aff_200", "圆满", "好感度达到 200"},
            {"memory_10", "记得住", "记忆库积累 10 条"},
            {"memory_pinned", "刻在心上", "置顶一条记忆"},
            {"first_ending", "第一个结局", "解锁任意一个结局"},
            {"all_endings", "全结局", "解锁全部结局"},
            {"first_rewrite", "导演之手", "第一次改写角色的回复"},
            {"milestone_half", "半程纪念", "解锁半数以上里程碑"},
    };

    private Achievements() {
    }

    public static JSONArray listOf(JSONObject session) {
        if (session == null) return new JSONArray();
        JSONArray a = session.optJSONArray("achievements");
        return a == null ? new JSONArray() : a;
    }

    public static boolean has(JSONObject session, String id) {
        JSONArray a = listOf(session);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return true;
        }
        return false;
    }

    public static int unlockedCount(JSONObject session) {
        return listOf(session).length();
    }

    public static int total() {
        return DEFS.length;
    }

    /**
     * 跑一遍全部判定，把新达成的写入 session。
     * 在发消息后、解锁结局后、签到后调用。
     *
     * @return 本次新解锁的成就数组（可能为空）
     */
    public static JSONArray evaluate(JSONObject session, JSONObject ch) {
        JSONArray unlocked = new JSONArray();
        if (session == null) return unlocked;

        int msgCount = countMessages(session);
        double aff = ChatEngine.affectionOf(session);
        int streak = session.optInt("streakDays", 0);
        int totalDays = session.optInt("totalDays", 0);
        int memCount = countMemories(session);
        int endings = session.optJSONArray("unlockedEndings") == null
                ? 0 : session.optJSONArray("unlockedEndings").length();

        for (int i = 0; i < DEFS.length; i++) {
            String id = DEFS[i][0];
            if (has(session, id)) continue;
            if (!test(id, session, ch, msgCount, aff, streak, totalDays, memCount, endings)) continue;
            JSONObject a = new JSONObject();
            try {
                a.put("id", id);
                a.put("title", DEFS[i][1]);
                a.put("desc", DEFS[i][2]);
                a.put("at", System.currentTimeMillis());
                JSONArray list = listOf(session);
                list.put(a);
                session.put("achievements", list);
            } catch (Exception e) {
                continue;
            }
            unlocked.put(a);
        }
        return unlocked;
    }

    private static boolean test(String id, JSONObject session, JSONObject ch,
                                int msgCount, double aff, int streak, int totalDays,
                                int memCount, int endings) {
        if ("first_talk".equals(id)) return msgCount >= 1;
        if ("talk_50".equals(id)) return msgCount >= 50;
        if ("talk_200".equals(id)) return msgCount >= 200;
        if ("talk_500".equals(id)) return msgCount >= 500;
        if ("streak_3".equals(id)) return streak >= 3;
        if ("streak_7".equals(id)) return streak >= 7;
        if ("streak_30".equals(id)) return streak >= 30;
        if ("days_10".equals(id)) return totalDays >= 10;
        if ("aff_20".equals(id)) return aff >= 20;
        if ("aff_60".equals(id)) return aff >= 60;
        if ("aff_120".equals(id)) return aff >= 120;
        if ("aff_200".equals(id)) return aff >= 200;
        if ("memory_10".equals(id)) return memCount >= 10;
        if ("memory_pinned".equals(id)) return hasPinnedMemory(session);
        if ("first_ending".equals(id)) return endings >= 1;
        if ("all_endings".equals(id)) return allEndingsUnlocked(session, ch, endings);
        if ("first_rewrite".equals(id)) return hasEditedMessage(session);
        if ("milestone_half".equals(id)) {
            return Milestones.unlockedCount(session) * 2 >= Milestones.total();
        }
        return false;
    }

    // ---------- 判定辅助 ----------

    private static int countMessages(JSONObject session) {
        JSONArray m = session.optJSONArray("messages");
        return m == null ? 0 : m.length();
    }

    private static int countMemories(JSONObject session) {
        JSONArray m = session.optJSONArray("memories");
        return m == null ? 0 : m.length();
    }

    private static boolean hasPinnedMemory(JSONObject session) {
        JSONArray m = session.optJSONArray("memories");
        if (m == null) return false;
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.optJSONObject(i);
            if (o != null && o.optBoolean("pinned", false)) return true;
        }
        return false;
    }

    private static boolean hasEditedMessage(JSONObject session) {
        JSONArray m = session.optJSONArray("messages");
        if (m == null) return false;
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.optJSONObject(i);
            if (o != null && o.optBoolean("edited", false)) return true;
        }
        return false;
    }

    /** 全结局：剧情里定义了结局，且都已解锁 */
    private static boolean allEndingsUnlocked(JSONObject session, JSONObject ch, int endings) {
        if (endings <= 0) return false;
        JSONObject story = session.optJSONObject("story");
        if (story == null && ch != null) story = Store.findStory(ch.optString("storyId", ""));
        if (story == null) return false;
        JSONArray all = StoryEngine.endingNodes(story);
        return all.length() > 0 && endings >= all.length();
    }
}
