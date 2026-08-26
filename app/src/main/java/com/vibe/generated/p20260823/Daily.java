package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 每日互动：角色当日心情、连续陪伴天数、当日限定话题。
 *
 * 会话侧字段（全部用 opt* 读取，老存档无需迁移）：
 *   lastCheckInDate  —— 上次签到日期，形如 20260826
 *   streakDays       —— 连续陪伴天数（断签归 1）
 *   totalDays        —— 累计陪伴天数
 *
 * 心情由「日期 + 角色 id + 当前好感度档位」哈希得出，因此同一天内稳定不变，
 * 不写入存储，随时可重算。
 */
public class Daily {

    /** 心情表：名称 + emoji + 给模型的语气提示 */
    private static final String[][] MOODS = {
            {"元气", "☀️", "今天精神很好，说话轻快、主动挑起话头"},
            {"慵懒", "🌙", "今天有点懒散，语速慢、爱撒娇、话短"},
            {"温柔", "🌸", "今天格外温柔耐心，多用软和的措辞"},
            {"雀跃", "✨", "今天心情很好，容易兴奋，爱分享小事"},
            {"沉静", "🍃", "今天话不多但很专注，回应简短而认真"},
            {"低落", "☁️", "今天情绪偏低，需要对方主动关心才会敞开"},
            {"顽皮", "🔥", "今天想逗人，爱开玩笑、偶尔故意唱反调"},
    };

    private Daily() {
    }

    // ---------- 日期工具 ----------

    /** 今天的日期编号，形如 20260826 */
    public static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /** 两个日期编号是否相差正好一天（用于判断连续签到） */
    private static boolean isConsecutive(int prev, int cur) {
        if (prev <= 0 || prev >= cur) return false;
        Calendar a = Calendar.getInstance();
        a.set(prev / 10000, (prev / 100) % 100 - 1, prev % 100, 12, 0, 0);
        a.add(Calendar.DAY_OF_MONTH, 1);
        int next = a.get(Calendar.YEAR) * 10000 + (a.get(Calendar.MONTH) + 1) * 100 + a.get(Calendar.DAY_OF_MONTH);
        return next == cur;
    }

    // ---------- 心情 ----------

    private static int moodIndex(JSONObject session, String charId) {
        int date = today();
        // 好感度按档位参与哈希：关系推进时心情分布也会变，但当天仍然稳定
        int stage = (int) (ChatEngine.affectionOf(session) / 20);
        int h = date * 31 + stage * 17;
        if (charId != null) h += charId.hashCode();
        return Math.abs(h) % MOODS.length;
    }

    public static String moodName(JSONObject session, String charId) {
        return MOODS[moodIndex(session, charId)][0];
    }

    public static String moodEmoji(JSONObject session, String charId) {
        return MOODS[moodIndex(session, charId)][1];
    }

    public static String moodHint(JSONObject session, String charId) {
        return MOODS[moodIndex(session, charId)][2];
    }

    /** 主页角色卡上的心情胶囊文案，例如「🌸 温柔」 */
    public static String moodLabel(JSONObject session, String charId) {
        int i = moodIndex(session, charId);
        return MOODS[i][1] + " " + MOODS[i][0];
    }

    // ---------- 签到 ----------

    public static int streakOf(JSONObject session) {
        return session == null ? 0 : session.optInt("streakDays", 0);
    }

    public static boolean checkedInToday(JSONObject session) {
        return session != null && session.optInt("lastCheckInDate", 0) == today();
    }

    /**
     * 当日首次进入对话时签到。
     *
     * @return true 表示本次确实签到了（当天第一次），false 表示今天已签过
     */
    public static boolean checkIn(JSONObject session) {
        if (session == null) return false;
        int t = today();
        int last = session.optInt("lastCheckInDate", 0);
        if (last == t) return false;
        int streak = isConsecutive(last, t) ? session.optInt("streakDays", 0) + 1 : 1;
        try {
            session.put("lastCheckInDate", t);
            session.put("streakDays", streak);
            session.put("totalDays", session.optInt("totalDays", 0) + 1);
        } catch (Exception e) {
            // ignore
        }
        return true;
    }

    /** 签到后给用户看的一句话 */
    public static String checkInMessage(JSONObject session, String charId) {
        int streak = streakOf(session);
        String mood = moodLabel(session, charId);
        if (streak >= 2) return mood + " · 已连续陪伴 " + streak + " 天";
        return mood + " · 今天也来了";
    }

    // ---------- 当日话题 ----------

    private static final String[] TOPIC_POOL = {
            "今天最开心的一件小事", "最近睡得好吗", "有什么一直想做还没做的事",
            "如果现在能去任何地方", "最近听到过什么好听的歌", "今天吃了什么",
            "有没有什么想吐槽的", "最近在忙什么", "有什么想让我陪你做的",
            "说一件你从没告诉别人的小事", "今天有什么让你意外的", "现在最想要什么",
    };

    /** 当日限定话题（3 条，同一天稳定）。喂给聊天页的灵感 chips。 */
    public static JSONArray topics(JSONObject session, String charId) {
        JSONArray out = new JSONArray();
        int seed = today() * 13 + (charId == null ? 0 : charId.hashCode());
        int n = TOPIC_POOL.length;
        int step = 1 + Math.abs(seed / 7) % (n - 1);
        int idx = Math.abs(seed) % n;
        for (int i = 0; i < 3; i++) {
            out.put(TOPIC_POOL[idx]);
            idx = (idx + step) % n;
        }
        return out;
    }
}
