package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 好感度里程碑：跨过阈值时记录一张纪念卡片。
 *
 * 阈值对齐 ChatEngine.stageName 的档位边界（20/60/120）并在中间加密，
 * 让长线成长有更密的正反馈。
 *
 * 会话侧字段：milestones: [{threshold,title,message,at,quote}]
 * 只追加不删除，因此老存档天然兼容。
 */
public class Milestones {

    /** {阈值, 标题, 寄语} */
    private static final String[][] DEFS = {
            {"10", "初次心动", "从陌生到愿意多说一句话，故事开始了。"},
            {"20", "不再是陌生人", "你们之间有了默契的雏形。"},
            {"40", "会想起你", "空下来的时候，脑海里会浮现你的名字。"},
            {"60", "熟悉的温度", "相处变得自然，不需要刻意找话题了。"},
            {"90", "藏不住的在意", "情绪开始为你波动，掩饰也不太成功。"},
            {"120", "彼此的例外", "在所有人之中，你被放在了不一样的位置。"},
            {"160", "无需言说", "很多话不用说完，对方就已经懂了。"},
            {"200", "此刻圆满", "走到了这段关系能抵达的最深处。"},
    };

    private Milestones() {
    }

    public static JSONArray listOf(JSONObject session) {
        if (session == null) return new JSONArray();
        JSONArray a = session.optJSONArray("milestones");
        return a == null ? new JSONArray() : a;
    }

    private static boolean alreadyUnlocked(JSONObject session, int threshold) {
        JSONArray a = listOf(session);
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m != null && m.optInt("threshold", -1) == threshold) return true;
        }
        return false;
    }

    /**
     * 检查好感度从 before 变到 after 是否跨过了新的里程碑。
     * 在 ChatEngine.applyVarDeltas 之后调用。
     *
     * @param quote 当时角色说的话，存进卡片作为回忆锚点
     * @return 新解锁的里程碑（已写入 session），没有则返回 null
     */
    public static JSONObject checkCross(JSONObject session, double before, double after, String quote) {
        if (session == null || after <= before) return null;
        for (int i = 0; i < DEFS.length; i++) {
            int th = Integer.parseInt(DEFS[i][0]);
            if (before < th && after >= th && !alreadyUnlocked(session, th)) {
                JSONObject m = new JSONObject();
                try {
                    m.put("threshold", th);
                    m.put("title", DEFS[i][1]);
                    m.put("message", DEFS[i][2]);
                    m.put("at", System.currentTimeMillis());
                    m.put("quote", quote == null ? "" : truncate(quote, 60));
                    JSONArray a = listOf(session);
                    a.put(m);
                    session.put("milestones", a);
                } catch (Exception e) {
                    return null;
                }
                return m;
            }
        }
        return null;
    }

    /** 已解锁数量 / 总数，用于纪念册进度展示 */
    public static int total() {
        return DEFS.length;
    }

    public static int unlockedCount(JSONObject session) {
        return listOf(session).length();
    }

    /** 下一个里程碑还差多少好感度；已全部解锁返回 -1 */
    public static int toNext(JSONObject session) {
        double a = ChatEngine.affectionOf(session);
        for (int i = 0; i < DEFS.length; i++) {
            int th = Integer.parseInt(DEFS[i][0]);
            if (a < th) return (int) Math.ceil(th - a);
        }
        return -1;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
