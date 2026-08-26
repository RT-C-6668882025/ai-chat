package com.vibe.generated.p20260823;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends android.app.Activity {

    private FrameLayout root;
    private HashMap<String, View> screens = new HashMap<String, View>();
    private String currentScreen = "";
    /** 页面返回栈：showScreen 前进时压栈，返回键弹栈，栈空则回主页。 */
    private final ArrayDeque<String> backStack = new ArrayDeque<String>();

    private JSONObject config;
    private JSONArray characters;
    private JSONArray records;
    private JSONArray commands;

    // home
    private RecyclerView rvHome;
    private HomeAdapter homeAdapter;
    private LinearLayout llHomeEmpty;
    private TextView tvHomeGreeting, tvHomeSub;

    // char edit
    private JSONObject editingChar;
    private String selectedColor = "#60A5FA";
    private EditText etCharName, etCharEmoji, etCharBrief, etCharGreeting, etCharPersona, etCharPrivate, etCharSituation, etCharTags;
    private SwitchMaterial swCharPartner;
    private LinearLayout llCharColors;

    // chat
    private JSONObject currentChar;
    private JSONObject currentSession;
    private RecyclerView rvChat;
    private ChatAdapter chatAdapter;
    private ArrayList<JSONObject> chatRows = new ArrayList<JSONObject>();
    private boolean streaming = false;
    private boolean immersive = false;
    private boolean crisisShown = false;
    private boolean activeMsgChecked = false;
    private EditText etChatInput;
    private com.google.android.material.button.MaterialButton btnSend;
    private TextView tvChatAffection;
    private ProgressBar pbChatAffection;
    /** 本轮回复解锁的里程碑 / 成就，等渲染完再弹出，避免打断消息动画 */
    private JSONObject pendingMilestone;
    private JSONArray newAchievements;
    private TextView tvChatStatus;
    private TextView tvChatName;
    private TextView tvChatAvatar;
    private TextView tvCrisis;

    // memory
    private RecyclerView rvMemory;
    private MemoryAdapter memoryAdapter;
    private ArrayList<Object> memoryRows = new ArrayList<Object>();
    private TextView tvMemHeader;
    private TextView tvMemEmpty;

    // vars
    private LinearLayout llVars;

    // settings
    private Spinner spSetMode;
    private EditText etSetKey, etSetBase, etSetModelMain, etSetModelMem;
    private EditText etSetMemInterval, etSetMemTopk, etSetMemDecay, etSetMemFade, etSetHistory;
    private EditText etSetMaxBubbles, etSetTemplate;
    private SwitchMaterial swSetStream, swSetVars, swSetInner, swSetBubble;

    // story (剧情图)
    private JSONObject editingStory;
    private EditText etStoryName, etStoryBg, etStorySituation, etStoryCallme, etStoryUserSetting;
    private Spinner spStoryType;
    private LinearLayout llStoryNodes, llStoryEndings;
    private boolean storyDirty = false;

    // market (提示词市场)
    private RecyclerView rvMarket;
    private MarketAdapter marketAdapter;
    private ArrayList<JSONObject> marketRows = new ArrayList<JSONObject>();
    private TextView[] marketTabs;
    private String marketSource = "awesome";
    private LinearLayout llMarketCustom, llMarketLocal;
    private EditText etMarketUrl, etMarketPaste;
    private ProgressBar pbMarket;
    private LinearLayout llMarketEmpty, llMarketError;
    private TextView tvMarketError;
    private static final int REQ_PICK_FILE = 9001;

    // ================= lifecycle =================

    // ================= 主题模式 =================

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String PREFS = "aic_prefs";
    private static final String KEY_THEME = "themeMode";

    /**
     * 主题偏好存在独立的 SharedPreferences 里，不进 Store：
     * attachBaseContext 早于 Store.init，且主题属设备偏好，不应随数据导出走。
     */
    static int themeModeOf(Context base) {
        try {
            return base.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THEME, THEME_SYSTEM);
        } catch (Exception e) {
            return THEME_SYSTEM;
        }
    }

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView[] themeBtns;
    private TextView tvMarketCache;

    /** 选中的主题按钮用品牌底色标出来，其余保持普通样式。 */
    private void updateThemeButtons() {
        if (themeBtns == null) return;
        int cur = themeModeOf(this);
        for (int i = 0; i < themeBtns.length; i++) {
            boolean sel = i == cur;
            themeBtns[i].setBackgroundResource(sel ? R.drawable.bg_tab_selected : R.drawable.bg_tag);
            themeBtns[i].setTextColor(c(sel ? R.color.on_brand_container : R.color.text_secondary));
        }
    }

    private void setThemeMode(int mode) {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_THEME, mode).apply();
        } catch (Exception e) {
            // ignore
        }
        recreate();
    }

    /**
     * MainActivity 继承 android.app.Activity 而非 AppCompatActivity，
     * AppCompatDelegate.setDefaultNightMode 不生效，因此直接覆盖 Configuration
     * 的 uiMode 夜间位，让 values / values-night 按选择而非系统解析。
     */
    @Override
    protected void attachBaseContext(Context base) {
        int mode = themeModeOf(base);
        if (mode != THEME_SYSTEM) {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (mode == THEME_DARK
                            ? Configuration.UI_MODE_NIGHT_YES
                            : Configuration.UI_MODE_NIGHT_NO);
            base = base.createConfigurationContext(cfg);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        // 切主题会 recreate()，记住当前页面免得弹回主页
        out.putString("currentScreen", currentScreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
        config = Store.loadConfig();
        characters = Store.loadCharacters();
        records = Store.loadRecords();
        commands = mergedCommands();
        setContentView(R.layout.activity_main);
        root = (FrameLayout) findViewById(R.id.root_container);

        // 安全区适配：单点挂载，覆盖全部页面（状态栏 + 底部导航 + 键盘）
        Insets.applySystemBars(root);

        boolean onboarded = config.optString("apiKey", "").length() > 0 || characters.length() > 0;
        if (!onboarded) {
            showScreen("onboard");
            return;
        }
        showScreen("home");
        // recreate() 之后（例如切主题）回到原来那一页
        String restore = savedInstanceState == null
                ? null : savedInstanceState.getString("currentScreen");
        if (restore != null && restore.length() > 0
                && !"home".equals(restore) && !"onboard".equals(restore)
                && currentChar != null) {
            showScreen(restore);
        }
    }

    @Override
    public void onBackPressed() {
        // 优先级 1：输入法打开时先收起键盘，不跳页
        View focus = getCurrentFocus();
        if (focus instanceof EditText) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
            return;
        }

        // 优先级 2：沉浸模式先退出，恢复 UI
        if (immersive && "chat".equals(currentScreen)) {
            toggleImmersive();
            return;
        }

        // 优先级 3：沿返回栈回到上一页（栈空则回主页）
        if (!"home".equals(currentScreen)) {
            String prev = backStack.isEmpty() ? "home" : backStack.pop();
            showScreen(prev, false);
            return;
        }

        // 优先级 4：主页禁止直接退出 App，退回桌面（后台保留）
        moveTaskToBack(true);
    }

    // ================= theme colors =================

    /** 取语义色 token，自动跟随 values / values-night。 */
    private int c(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    /** 取语义色 token 的 ColorStateList。 */
    private ColorStateList csl(int colorRes) {
        return ColorStateList.valueOf(c(colorRes));
    }

    /** dp 转 px，用于代码里动态建视图。 */
    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    // ================= screen management =================

    private void showScreen(String name) {
        showScreen(name, true);
    }

    /**
     * 切换页面。
     *
     * @param forward true = 前进（当前页压入返回栈，新页从下方淡入）；
     *                false = 后退（不压栈，新页从上方淡入）
     */
    private void showScreen(String name, boolean forward) {
        if (name == null || name.equals(currentScreen)) return;
        View v = screens.get(name);
        if (v == null) {
            v = createScreen(name);
            if (v == null) return;
            screens.put(name, v);
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (forward && currentScreen.length() > 0) {
            backStack.push(currentScreen);
            // 防止栈无限增长（例如在两页之间反复横跳）
            while (backStack.size() > 16) backStack.removeLast();
        }
        for (String k : screens.keySet()) {
            View sv = screens.get(k);
            if (sv != null && sv != v) sv.setVisibility(View.GONE);
        }
        v.setVisibility(View.VISIBLE);
        animateIn(v, forward);
        currentScreen = name;
        if ("home".equals(name)) refreshHome();
        if ("chat".equals(name)) renderChat();
        if ("memory".equals(name)) renderMemory();
        if ("vars".equals(name)) renderVars();
        if ("commands".equals(name)) renderCommands();
        if ("char_edit".equals(name)) populateCharEdit();
        if ("settings".equals(name)) populateSettings();
        if ("story".equals(name)) populateStory();
        if ("market".equals(name)) renderMarket();
        if ("album".equals(name)) renderAlbum();
        if ("rewind".equals(name)) renderRewind();
    }

    // ================= 时光倒流 =================

    private RecyclerView rvRewind;
    private RewindAdapter rewindAdapter;
    private final List<JSONObject> rewindRows = new ArrayList<JSONObject>();

    private void initRewind(View v) {
        rvRewind = (RecyclerView) v.findViewById(R.id.rv_rewind);
        rvRewind.setLayoutManager(new LinearLayoutManager(this));
        rewindAdapter = new RewindAdapter();
        rvRewind.setAdapter(rewindAdapter);
        v.findViewById(R.id.btn_rewind_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
    }

    private void renderRewind() {
        if (currentSession == null || rewindAdapter == null) return;
        rewindRows.clear();
        JSONArray path = ChatEngine.pathMessages(currentSession);
        for (int i = 0; i < path.length(); i++) {
            JSONObject m = path.optJSONObject(i);
            if (m == null) continue;
            if ("system".equals(m.optString("role"))) continue;   // 旁白不作为回溯点
            rewindRows.add(m);
        }
        rewindAdapter.notifyDataSetChanged();
        if (rvRewind != null && rewindRows.size() > 0) {
            rvRewind.scrollToPosition(rewindRows.size() - 1);
        }
    }

    /** 回到某条消息：把它设为新的叶子，之后的消息作为兄弟分支保留。 */
    private void rewindTo(final JSONObject msg) {
        int idx = rewindRows.indexOf(msg);
        int after = idx < 0 ? 0 : rewindRows.size() - 1 - idx;
        String summary = brief(msg.optString("content", ""), 40);
        String body = "从这句重新开始：\n\n「" + summary + "」";
        if (after > 0) {
            body += "\n\n之后的 " + after + " 条对话会保留为另一条分支，"
                    + "在消息右下角的分支切换器里随时能切回来。";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("时光倒流")
                .setMessage(body)
                .setPositiveButton("回到这里", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            currentSession.put("currentLeafId", msg.optString("id"));
                            Store.saveSession(currentSession);
                        } catch (Exception e) {
                            AppLogger.e("REWIND", "set leaf failed", e);
                        }
                        goBack();
                        buildChatRows();
                        chatAdapter.notifyDataSetChanged();
                        scrollChat();
                        updateAffectionHeader();
                        toast("已回到这一刻");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String brief(String s, int n) {
        if (s == null) return "";
        s = s.replace("\n", " ").trim();
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    class RewindAdapter extends RecyclerView.Adapter<RewindAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView index, who, current, time, text;

            VH(View itemView) {
                super(itemView);
                index = (TextView) itemView.findViewById(R.id.tv_rewind_index);
                who = (TextView) itemView.findViewById(R.id.tv_rewind_who);
                current = (TextView) itemView.findViewById(R.id.tv_rewind_current);
                time = (TextView) itemView.findViewById(R.id.tv_rewind_time);
                text = (TextView) itemView.findViewById(R.id.tv_rewind_text);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(getLayoutInflater().inflate(R.layout.item_rewind, p, false));
        }

        public void onBindViewHolder(VH h, int pos) {
            final JSONObject m = rewindRows.get(pos);
            boolean isUser = "user".equals(m.optString("role"));
            h.index.setText(String.valueOf(pos + 1));
            h.who.setText(isUser ? "你" : currentChar.optString("name", "对方"));
            h.who.setTextColor(c(isUser ? R.color.text_secondary : R.color.brand));
            h.text.setText(m.optString("content", ""));
            h.time.setText(relTime(m.optLong("timestamp", 0)));

            boolean isLeaf = m.optString("id").equals(currentSession.optString("currentLeafId", ""));
            h.current.setVisibility(isLeaf ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    rewindTo(m);
                }
            });
        }

        public int getItemCount() {
            return rewindRows.size();
        }
    }

    // ================= 纪念册 =================

    private RecyclerView rvAlbum;
    private AlbumAdapter albumAdapter;
    private final List<JSONObject> albumRows = new ArrayList<JSONObject>();
    private TextView tvAlbumMilestones, tvAlbumAchievements, tvAlbumDays, tvAlbumNext, tvAlbumEmpty;

    private void initAlbum(View v) {
        tvAlbumMilestones = (TextView) v.findViewById(R.id.tv_album_milestones);
        tvAlbumAchievements = (TextView) v.findViewById(R.id.tv_album_achievements);
        tvAlbumDays = (TextView) v.findViewById(R.id.tv_album_days);
        tvAlbumNext = (TextView) v.findViewById(R.id.tv_album_next);
        tvAlbumEmpty = (TextView) v.findViewById(R.id.tv_album_empty);
        rvAlbum = (RecyclerView) v.findViewById(R.id.rv_album);
        rvAlbum.setLayoutManager(new LinearLayoutManager(this));
        albumAdapter = new AlbumAdapter();
        rvAlbum.setAdapter(albumAdapter);
        v.findViewById(R.id.btn_album_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
    }

    /** 聚合里程碑 / 结局 / 成就 / 置顶记忆，按时间倒序铺成时间轴。 */
    private void renderAlbum() {
        if (currentSession == null || albumAdapter == null) return;
        albumRows.clear();

        JSONArray ms = Milestones.listOf(currentSession);
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m == null) continue;
            albumRows.add(albumRow("milestone", "里程碑", m.optString("title", ""),
                    m.optString("message", ""), m.optString("quote", ""), m.optLong("at", 0)));
        }

        JSONArray endings = currentSession.optJSONArray("unlockedEndings");
        JSONObject story = currentSession.optJSONObject("story");
        if (endings != null) {
            for (int i = 0; i < endings.length(); i++) {
                JSONObject e = endings.optJSONObject(i);
                String title = e != null ? e.optString("title", "") : endings.optString(i, "");
                String desc = e != null ? e.optString("description", "") : "";
                long at = e != null ? e.optLong("at", 0) : 0;
                if (title.length() == 0 && story != null) title = "结局";
                albumRows.add(albumRow("ending", "结局", title, desc, "", at));
            }
        }

        JSONArray achs = Achievements.listOf(currentSession);
        for (int i = 0; i < achs.length(); i++) {
            JSONObject a = achs.optJSONObject(i);
            if (a == null) continue;
            albumRows.add(albumRow("achievement", "成就", a.optString("title", ""),
                    a.optString("desc", ""), "", a.optLong("at", 0)));
        }

        JSONArray mems = currentSession.optJSONArray("memories");
        if (mems != null) {
            for (int i = 0; i < mems.length(); i++) {
                JSONObject m = mems.optJSONObject(i);
                if (m == null || !m.optBoolean("pinned", false)) continue;
                albumRows.add(albumRow("memory", "记忆", m.optString("text", ""), "", "",
                        m.optLong("createdAt", 0)));
            }
        }

        // 未解锁成就以剪影形式垫在最后，让人知道还能追什么
        for (int i = 0; i < Achievements.DEFS.length; i++) {
            String id = Achievements.DEFS[i][0];
            if (Achievements.has(currentSession, id)) continue;
            albumRows.add(albumRow("locked", "未解锁", Achievements.DEFS[i][1],
                    Achievements.DEFS[i][2], "", 0));
        }

        Collections.sort(albumRows, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                boolean la = "locked".equals(a.optString("kind"));
                boolean lb = "locked".equals(b.optString("kind"));
                if (la != lb) return la ? 1 : -1;   // 未解锁永远沉底
                long ta = a.optLong("at", 0), tb = b.optLong("at", 0);
                return Long.compare(tb, ta);        // 其余按时间倒序
            }
        });

        tvAlbumMilestones.setText(Milestones.unlockedCount(currentSession) + "/" + Milestones.total());
        tvAlbumAchievements.setText(Achievements.unlockedCount(currentSession) + "/" + Achievements.total());
        tvAlbumDays.setText(String.valueOf(currentSession.optInt("totalDays", 0)));

        int need = Milestones.toNext(currentSession);
        if (need > 0) {
            tvAlbumNext.setVisibility(View.VISIBLE);
            tvAlbumNext.setText("距离下一个里程碑还差 " + need + " 点好感度");
        } else {
            tvAlbumNext.setVisibility(View.GONE);
        }

        boolean onlyLocked = true;
        for (JSONObject r : albumRows) {
            if (!"locked".equals(r.optString("kind"))) {
                onlyLocked = false;
                break;
            }
        }
        tvAlbumEmpty.setVisibility(onlyLocked ? View.VISIBLE : View.GONE);
        albumAdapter.notifyDataSetChanged();
    }

    private JSONObject albumRow(String kind, String kindLabel, String title,
                                String desc, String quote, long at) {
        JSONObject o = new JSONObject();
        try {
            o.put("kind", kind);
            o.put("kindLabel", kindLabel);
            o.put("title", title);
            o.put("desc", desc);
            o.put("quote", quote);
            o.put("at", at);
        } catch (Exception e) {
            // ignore
        }
        return o;
    }

    class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView kind, time, title, desc, quote;

            VH(View itemView) {
                super(itemView);
                icon = (ImageView) itemView.findViewById(R.id.iv_album_icon);
                kind = (TextView) itemView.findViewById(R.id.tv_album_kind);
                time = (TextView) itemView.findViewById(R.id.tv_album_time);
                title = (TextView) itemView.findViewById(R.id.tv_album_title);
                desc = (TextView) itemView.findViewById(R.id.tv_album_desc);
                quote = (TextView) itemView.findViewById(R.id.tv_album_quote);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(getLayoutInflater().inflate(R.layout.item_album, p, false));
        }

        public void onBindViewHolder(VH h, int pos) {
            JSONObject r = albumRows.get(pos);
            String kind = r.optString("kind");
            boolean locked = "locked".equals(kind);

            int iconRes = "milestone".equals(kind) ? R.drawable.ic_heart_fill
                    : "ending".equals(kind) ? R.drawable.ic_star
                    : "memory".equals(kind) ? R.drawable.ic_pin
                    : R.drawable.ic_achievement;
            h.icon.setImageResource(iconRes);
            h.icon.setColorFilter(c(locked ? R.color.badge_locked
                    : "milestone".equals(kind) ? R.color.accent : R.color.badge_unlocked));

            h.kind.setText(r.optString("kindLabel"));
            h.title.setText(r.optString("title"));
            h.title.setTextColor(c(locked ? R.color.text_tertiary : R.color.text_primary));

            String desc = r.optString("desc");
            h.desc.setVisibility(desc.length() > 0 ? View.VISIBLE : View.GONE);
            h.desc.setText(desc);

            String quote = r.optString("quote");
            h.quote.setVisibility(quote.length() > 0 ? View.VISIBLE : View.GONE);
            h.quote.setText("「" + quote + "」");

            long at = r.optLong("at", 0);
            h.time.setText(at > 0 ? relTime(at) : "");
            h.itemView.setAlpha(locked ? 0.55f : 1f);
        }

        public int getItemCount() {
            return albumRows.size();
        }
    }

    /** 返回上一页：优先弹返回栈，栈空时回主页。所有页内返回按钮都走这里。 */
    private void goBack() {
        String prev = backStack.isEmpty() ? "home" : backStack.pop();
        showScreen(prev, false);
    }

    /** 入场动画：淡入 + 轻微位移，前进从下、后退从上。 */
    private void animateIn(View v, boolean forward) {
        float dy = getResources().getDisplayMetrics().density * 12f;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(forward ? dy : -dy);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private View createScreen(String name) {
        LayoutInflater inf = getLayoutInflater();
        if ("onboard".equals(name)) {
            View v = inf.inflate(R.layout.screen_onboard, null);
            initOnboard(v);
            return v;
        }
        if ("home".equals(name)) {
            View v = inf.inflate(R.layout.screen_home, null);
            initHome(v);
            return v;
        }
        if ("char_edit".equals(name)) {
            View v = inf.inflate(R.layout.screen_char_edit, null);
            initCharEdit(v);
            return v;
        }
        if ("chat".equals(name)) {
            View v = inf.inflate(R.layout.screen_chat, null);
            initChat(v);
            return v;
        }
        if ("album".equals(name)) {
            View v = inf.inflate(R.layout.screen_album, null);
            initAlbum(v);
            return v;
        }
        if ("rewind".equals(name)) {
            View v = inf.inflate(R.layout.screen_rewind, null);
            initRewind(v);
            return v;
        }
        if ("memory".equals(name)) {
            View v = inf.inflate(R.layout.screen_memory, null);
            initMemory(v);
            return v;
        }
        if ("vars".equals(name)) {
            View v = inf.inflate(R.layout.screen_vars, null);
            initVars(v);
            return v;
        }
        if ("story".equals(name)) {
            View v = inf.inflate(R.layout.screen_story, null);
            initStory(v);
            return v;
        }
        if ("commands".equals(name)) {
            View v = inf.inflate(R.layout.screen_commands, null);
            initCommands(v);
            return v;
        }
        if ("settings".equals(name)) {
            View v = inf.inflate(R.layout.screen_settings, null);
            initSettings(v);
            return v;
        }
        if ("market".equals(name)) {
            View v = inf.inflate(R.layout.screen_market, null);
            initMarket(v);
            return v;
        }
        return null;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("text", text));
            toast("已复制");
        }
    }

    private void shareText(String title, String text) {
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType("text/plain");
        it.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(it, title));
        } catch (Exception e) {
            toast("没有可分享的应用");
        }
    }

    private static String relTime(long ts) {
        if (ts <= 0) return "";
        long diff = System.currentTimeMillis() - ts;
        long m = diff / 60000L;
        if (m < 1) return "刚刚";
        if (m < 60) return m + "分钟前";
        long h = m / 60;
        if (h < 24) return h + "小时前";
        long d = h / 24;
        if (d < 7) return d + "天前";
        return (d / 7) + "周前";
    }

    // ================= onboarding =================

    private void initOnboard(View v) {
        final Spinner sp = (Spinner) v.findViewById(R.id.sp_onboard_mode);
        setupModeSpinner(sp, config.optString("apiMode", "openai"));
        final EditText etKey = (EditText) v.findViewById(R.id.et_onboard_key);
        final EditText etUrl = (EditText) v.findViewById(R.id.et_onboard_url);
        final EditText etModel = (EditText) v.findViewById(R.id.et_onboard_model);
        etKey.setText(config.optString("apiKey", ""));
        etUrl.setText(config.optString("baseUrl", "https://api.deepseek.com"));
        etModel.setText(ChatEngine.modelOf(config, "main"));

        v.findViewById(R.id.btn_onboard_skip).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_onboard_next).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                String key = etKey.getText().toString().trim();
                String url = etUrl.getText().toString().trim();
                String model = etModel.getText().toString().trim();
                if (key.length() == 0) {
                    toast("请先填写 API Key（或点「跳过」稍后配置）");
                    return;
                }
                try {
                    config.put("apiMode", sp.getSelectedItemPosition() == 0 ? "openai" : "anthropic");
                    config.put("apiKey", key);
                    if (url.length() > 0) config.put("baseUrl", url);
                    if (model.length() > 0) config.optJSONObject("models").put("main", model);
                    Store.saveConfig(config);
                } catch (Exception e) {
                }
                toast("配置已保存");
                goBack();
            }
        });
    }

    private void setupModeSpinner(Spinner sp, String mode) {
        String[] modes = {"OpenAI 兼容", "Anthropic"};
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modes);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection("anthropic".equals(mode) ? 1 : 0);
    }

    // ================= home =================

    private void initHome(View v) {
        rvHome = (RecyclerView) v.findViewById(R.id.rv_home);
        rvHome.setLayoutManager(new LinearLayoutManager(this));
        homeAdapter = new HomeAdapter();
        rvHome.setAdapter(homeAdapter);
        llHomeEmpty = (LinearLayout) v.findViewById(R.id.ll_home_empty);
        tvHomeGreeting = (TextView) v.findViewById(R.id.tv_home_greeting);
        tvHomeSub = (TextView) v.findViewById(R.id.tv_home_sub);

        View.OnClickListener addListener = new View.OnClickListener() {
            public void onClick(View vv) {
                openCharEdit(null);
            }
        };
        v.findViewById(R.id.btn_home_add).setOnClickListener(addListener);
        v.findViewById(R.id.fab_home_add).setOnClickListener(addListener);
        v.findViewById(R.id.btn_home_settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                showScreen("settings");
            }
        });
        v.findViewById(R.id.btn_home_preset).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                addPresetDialog();
            }
        });
        v.findViewById(R.id.btn_home_market).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                showScreen("market");
            }
        });
    }

    private void refreshHome() {
        characters = Store.loadCharacters();
        homeAdapter.notifyDataSetChanged();
        llHomeEmpty.setVisibility(characters.length() == 0 ? View.VISIBLE : View.GONE);
        updateHomeGreeting();
    }

    /** 按时段问候，并统计今天还没聊过的角色。 */
    private void updateHomeGreeting() {
        if (tvHomeGreeting == null) return;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String hello = hour < 5 ? "夜深了"
                : hour < 11 ? "早上好"
                : hour < 14 ? "中午好"
                : hour < 18 ? "下午好"
                : hour < 23 ? "晚上好" : "夜深了";
        tvHomeGreeting.setText(hello);

        int waiting = 0;
        for (int i = 0; i < characters.length(); i++) {
            JSONObject ch = characters.optJSONObject(i);
            if (ch == null) continue;
            JSONObject s = Store.getSessionForChar(ch.optString("id"));
            JSONArray msgs = s.optJSONArray("messages");
            if (msgs != null && msgs.length() > 0 && !Daily.checkedInToday(s)) waiting++;
        }
        tvHomeSub.setText(waiting > 0 ? ("有 " + waiting + " 位在等你" ) : "今天想和谁聊聊？");
    }

    private void addPresetDialog() {
        final JSONArray presets = Store.presetCharacters();
        String[] names = new String[presets.length()];
        for (int i = 0; i < presets.length(); i++) {
            JSONObject p = presets.optJSONObject(i);
            names[i] = p.optString("name", "") + " · " + p.optString("brief", "");
        }
        new MaterialAlertDialogBuilder(this).setTitle("添加预设角色")
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        JSONObject p = presets.optJSONObject(which);
                        try {
                            p.put("id", Store.newId());
                            p.put("createdAt", System.currentTimeMillis());
                        } catch (Exception e) {
                        }
                        Store.upsertChar(p);
                        toast("已添加「" + p.optString("name", "") + "」");
                        refreshHome();
                    }
                }).show();
    }

    private void charMenu(final JSONObject c) {
        final String[] items = {"编辑", "设为伙伴", "取消伙伴", "导出", "删除"};
        new MaterialAlertDialogBuilder(this).setTitle(c.optString("name", ""))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            openCharEdit(c);
                        } else if (which == 1) {
                            setPartner(c, true);
                        } else if (which == 2) {
                            setPartner(c, false);
                        } else if (which == 3) {
                            shareText("导出角色", c.toString());
                        } else if (which == 4) {
                            confirmDeleteChar(c);
                        }
                    }
                }).show();
    }

    private void setPartner(JSONObject c, boolean on) {
        try {
            for (int i = 0; i < characters.length(); i++) {
                JSONObject o = characters.optJSONObject(i);
                if (o != null) o.put("isPartner", false);
            }
            c.put("isPartner", on);
            Store.saveCharacters(characters);
            refreshHome();
        } catch (Exception e) {
        }
    }

    private void confirmDeleteChar(final JSONObject c) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_Dialog_Destructive).setTitle("删除角色")
                .setMessage("将删除「" + c.optString("name", "") + "」及其全部会话，确定？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Store.deleteChar(c.optString("id"));
                        refreshHome();
                    }
                }).setNegativeButton("取消", null).show();
    }

    class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView avatar, name, brief, last, affection, time, mood, streak;
            ImageView partner;
            ProgressBar pb;
            LinearLayout daily;

            VH(View itemView) {
                super(itemView);
                avatar = (TextView) itemView.findViewById(R.id.iv_char_avatar);
                name = (TextView) itemView.findViewById(R.id.tv_char_name);
                partner = (ImageView) itemView.findViewById(R.id.tv_char_partner);
                brief = (TextView) itemView.findViewById(R.id.tv_char_brief);
                last = (TextView) itemView.findViewById(R.id.tv_char_last);
                affection = (TextView) itemView.findViewById(R.id.tv_char_affection);
                time = (TextView) itemView.findViewById(R.id.tv_char_time);
                pb = (ProgressBar) itemView.findViewById(R.id.pb_char_affection);
                daily = (LinearLayout) itemView.findViewById(R.id.ll_char_daily);
                mood = (TextView) itemView.findViewById(R.id.tv_char_mood);
                streak = (TextView) itemView.findViewById(R.id.tv_char_streak);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_character, p, false);
            return new VH(v);
        }

        public void onBindViewHolder(VH h, int pos) {
            final JSONObject c = characters.optJSONObject(pos);
            h.avatar.setText(c.optString("avatarEmoji", "🌸"));
            h.name.setText(c.optString("name", ""));
            h.partner.setVisibility(c.optBoolean("isPartner", false) ? View.VISIBLE : View.GONE);
            h.brief.setText(c.optString("brief", ""));
            JSONObject session = Store.getSessionForChar(c.optString("id"));
            h.last.setText("「" + lastText(session) + "」");
            double aff = ChatEngine.affectionOf(session);
            h.affection.setText("♡ " + (int) aff);
            h.pb.setProgress((int) Math.max(0, Math.min(200, aff)));
            h.time.setText(relTime(session.optLong("updatedAt", 0)));

            // 心情 + 连续陪伴天数：只有聊过的角色才显示
            boolean hasHistory = session.optJSONArray("messages") != null
                    && session.optJSONArray("messages").length() > 0;
            h.daily.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
            if (hasHistory) {
                h.mood.setText(Daily.moodLabel(session, c.optString("id")));
                int streak = Daily.streakOf(session);
                h.streak.setVisibility(streak >= 2 ? View.VISIBLE : View.GONE);
                h.streak.setText(streak + " 天");
            }
            h.itemView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    openChat(c);
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View vv) {
                    charMenu(c);
                    return true;
                }
            });
        }

        public int getItemCount() {
            return characters.length();
        }
    }

    private String lastText(JSONObject session) {
        JSONArray msgs = session.optJSONArray("messages");
        JSONObject best = null;
        long bestTs = -1;
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                long ts = m.optLong("timestamp", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = m;
                }
            }
        }
        if (best != null) {
            String t = best.optString("content", "");
            return t.length() > 18 ? t.substring(0, 18) + "…" : t;
        }
        return "开始对话吧";
    }

    // ================= char edit =================

    private void openCharEdit(JSONObject c) {
        editingChar = c;
        selectedColor = c != null ? c.optString("color", "#60A5FA") : "#60A5FA";
        showScreen("char_edit");
    }

    private void initCharEdit(View v) {
        etCharName = (EditText) v.findViewById(R.id.et_char_name);
        etCharEmoji = (EditText) v.findViewById(R.id.et_char_emoji);
        etCharBrief = (EditText) v.findViewById(R.id.et_char_brief);
        etCharGreeting = (EditText) v.findViewById(R.id.et_char_greeting);
        etCharPersona = (EditText) v.findViewById(R.id.et_char_persona);
        etCharPrivate = (EditText) v.findViewById(R.id.et_char_private);
        etCharSituation = (EditText) v.findViewById(R.id.et_char_situation);
        etCharTags = (EditText) v.findViewById(R.id.et_char_tags);
        swCharPartner = (SwitchMaterial) v.findViewById(R.id.sw_char_partner);
        llCharColors = (LinearLayout) v.findViewById(R.id.ll_char_colors);

        v.findViewById(R.id.btn_cedit_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });

        v.findViewById(R.id.btn_cedit_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                saveCharEdit();
            }
        });

        v.findViewById(R.id.btn_char_ai_generate).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                aiGenerateDialog();
            }
        });
        v.findViewById(R.id.btn_char_ai_refine).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                aiRefineDialog();
            }
        });

        buildColorDots();
    }

    private void saveCharEdit() {
        String name = etCharName.getText().toString().trim();
        if (name.length() == 0) {
            toast("请填写名字");
            return;
        }
        try {
            JSONObject c = editingChar != null ? editingChar : new JSONObject();
            c.put("id", editingChar != null ? editingChar.optString("id") : Store.newId());
            c.put("name", name);
            String emoji = etCharEmoji.getText().toString().trim();
            c.put("avatarEmoji", emoji.length() > 0 ? emoji : "🌸");
            c.put("brief", etCharBrief.getText().toString().trim());
            c.put("greeting", etCharGreeting.getText().toString().trim());
            c.put("persona", etCharPersona.getText().toString().trim());
            c.put("privateNote", etCharPrivate.getText().toString().trim());
            c.put("situation", etCharSituation.getText().toString().trim());
            c.put("tags", etCharTags.getText().toString().trim());
            c.put("color", selectedColor);
            c.put("isPartner", swCharPartner.isChecked());
            if (!c.has("createdAt")) c.put("createdAt", System.currentTimeMillis());
            Store.upsertChar(c);
        } catch (Exception e) {
            AppLogger.e("CHEDIT", "save failed", e);
        }
        toast("已保存");
        goBack();
    }

    // ---------- AI 生成剧情 / 世界观 ----------

    /** 通用：问几个问题 → 调模型 → 回调处理返回文本。 */
    private void askThen(String title, String[] hints, final String positive,
                         final AiPrompt builder) {
        if (!aiKeyReady()) return;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText[] ets = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            ets[i] = new EditText(this);
            ets[i].setHint(hints[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(8);
            ets[i].setLayoutParams(lp);
            wrap.addView(ets[i]);
        }
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(wrap)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String[] ans = new String[ets.length];
                        for (int i = 0; i < ets.length; i++) {
                            ans[i] = ets[i].getText().toString().trim();
                        }
                        if (ans[0].length() == 0) {
                            toast("请至少回答第一个问题");
                            return;
                        }
                        builder.run(ans);
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    interface AiPrompt {
        void run(String[] answers);
    }

    /** 调模型，结果回到主线程。 */
    private void callAi(final String system, final int maxTokens, final AiResult onOk) {
        final JSONObject cfg = config;
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                onOk.run(full);
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                toast("生成失败：" + msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, new JSONArray(),
                            ChatEngine.modelOf(cfg, "main"), maxTokens, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("生成失败");
                        }
                    });
                }
            }
        }).start();
    }

    interface AiResult {
        void run(String raw);
    }

    private void aiWorldDialog() {
        askThen("AI 生成世界观",
                new String[]{"① 题材？（如：修仙江湖 / 赛博都市 / 民国校园）",
                        "② 年代或地点？（可留空）",
                        "③ 氛围基调？（如：温暖日常 / 阴郁悬疑）"},
                "生成", new AiPrompt() {
                    public void run(String[] a) {
                        toast("AI 正在构思世界观…");
                        String system = "你是世界观设计师。根据用户回答，设计一个自洽的故事世界。\n"
                                + "严格输出 JSON 对象（不要 markdown 代码块），字段：\n"
                                + "{\"globalBackground\":\"世界观设定：地点、时代、社会结构、特殊规则，200字内\","
                                + "\"situation\":\"故事开场的具体情境，用『用户』称呼对话者，100字内\"}\n"
                                + "回答：\n1. 题材：" + a[0]
                                + "\n2. 年代/地点：" + (a[1].length() > 0 ? a[1] : "由你设定")
                                + "\n3. 基调：" + (a[2].length() > 0 ? a[2] : "由你设定");
                        callAi(system, 1024, new AiResult() {
                            public void run(String raw) {
                                JSONObject o = Json.extractObject(raw);
                                if (o == null) {
                                    toast("AI 返回格式不正确，请重试");
                                    return;
                                }
                                String bg = o.optString("globalBackground", "");
                                String sit = o.optString("situation", "");
                                if (bg.length() > 0) etStoryBg.setText(bg);
                                if (sit.length() > 0) etStorySituation.setText(sit);
                                toast("世界观已填入，可以继续改");
                            }
                        });
                    }
                });
    }

    private void aiPlotDialog() {
        askThen("AI 生成完整剧情线",
                new String[]{"① 讲一个什么故事？（一句话）",
                        "② 主角是谁 / 什么处境？（可留空）",
                        "③ 基调与结局数？（如：治愈，3 个结局）"},
                "生成", new AiPrompt() {
                    public void run(String[] a) {
                        toast("AI 正在编排剧情，可能要十几秒…");
                        String system = "你是互动剧情设计师。设计一条带分支和多结局的剧情线。\n"
                                + "严格输出 JSON 对象（不要 markdown 代码块），结构：\n"
                                + "{\"name\":\"剧情名\",\"globalBackground\":\"世界观\",\"situation\":\"开场情境\","
                                + "\"initialNodeId\":\"n1\","
                                + "\"nodes\":[{\"id\":\"n1\",\"name\":\"节点名\",\"type\":\"start|normal|ending|merge\","
                                + "\"text\":\"进入该节点时的剧情台词\",\"instruction\":\"自由聊天时的剧情指引\","
                                + "\"choices\":[{\"text\":\"选项文案\",\"next\":\"n2\"}],"
                                + "\"assignments\":[{\"name\":\"affection\",\"value\":\"+5\"}]}],"
                                + "\"endings\":[{\"nodeId\":\"n9\",\"title\":\"结局名\",\"description\":\"一句话\",\"icon\":\"emoji\"}]}\n"
                                + "硬性要求：\n"
                                + "1. 节点 id 唯一，全部用 n1/n2/... 形式\n"
                                + "2. initialNodeId 必须是存在的节点 id\n"
                                + "3. 所有 choices 的 next 必须指向存在的节点 id\n"
                                + "4. endings 的 nodeId 必须指向 type 为 ending 的节点\n"
                                + "5. 节点数 8~14 个，至少 2 个分支点\n"
                                + "回答：\n1. 故事：" + a[0]
                                + "\n2. 主角：" + (a[1].length() > 0 ? a[1] : "由你设定")
                                + "\n3. 基调/结局：" + (a[2].length() > 0 ? a[2] : "由你设定");
                        callAi(system, 4096, new AiResult() {
                            public void run(String raw) {
                                applyAiPlot(raw);
                            }
                        });
                    }
                });
    }

    /** 生成的剧情先过结构校验，不合法宁可让用户重试也不写坏存档。 */
    private void applyAiPlot(String raw) {
        JSONObject st = Json.extractObject(raw);
        if (st == null) {
            toast("AI 返回格式不正确，请重试");
            return;
        }
        String err = Json.validateStory(st);
        if (err != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("剧情结构有问题")
                    .setMessage(err + "\n\n没有写入，可以再生成一次。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        if (editingStory == null) {
            toast("请先打开一个剧情再生成");
            return;
        }
        try {
            // 保留原 id 与角色绑定，只换剧情内容
            editingStory.put("name", st.optString("name", editingStory.optString("name", "")));
            editingStory.put("globalBackground", st.optString("globalBackground", ""));
            editingStory.put("situation", st.optString("situation", ""));
            editingStory.put("initialNodeId", st.optString("initialNodeId", ""));
            editingStory.put("nodes", st.optJSONArray("nodes"));
            editingStory.put("endings", st.optJSONArray("endings") == null
                    ? new JSONArray() : st.optJSONArray("endings"));
        } catch (Exception e) {
            AppLogger.e("STORY", "apply ai plot failed", e);
            toast("写入失败");
            return;
        }
        populateStory();
        int n = st.optJSONArray("nodes") == null ? 0 : st.optJSONArray("nodes").length();
        int e2 = st.optJSONArray("endings") == null ? 0 : st.optJSONArray("endings").length();
        toast("已生成 " + n + " 个节点、" + e2 + " 个结局，记得保存");
    }

    // ---------- AI 生成 / 优化 ----------

    private boolean aiKeyReady() {
        if (config.optString("apiKey", "").length() == 0) {
            toast("请先在设置中配置 API Key");
            return false;
        }
        return true;
    }

    private void aiGenerateDialog() {
        if (!aiKeyReady()) return;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText et1 = new EditText(this);
        et1.setHint("① 你想创造怎样的角色？（身份/性格，如：古风傲娇剑客）");
        wrap.addView(et1);
        final EditText et2 = new EditText(this);
        et2.setHint("② 你们是什么关系？（如：青梅竹马/宿敌/师徒）");
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.topMargin = 8;
        et2.setLayoutParams(l2);
        wrap.addView(et2);
        final EditText et3 = new EditText(this);
        et3.setHint("③ 你希望什么氛围？（如：甜宠/虐心/搞笑）");
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l3.topMargin = 8;
        et3.setLayoutParams(l3);
        wrap.addView(et3);
        new MaterialAlertDialogBuilder(this).setTitle("三问引导 · 生成角色")
                .setView(wrap)
                .setPositiveButton("生成", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String q1 = et1.getText().toString().trim();
                        String q2 = et2.getText().toString().trim();
                        String q3 = et3.getText().toString().trim();
                        if (q1.length() == 0) {
                            toast("请至少回答第一个问题");
                            return;
                        }
                        aiGenerate(q1, q2, q3);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void aiGenerate(final String q1, final String q2, final String q3) {
        toast("AI 正在构思角色…");
        final JSONObject cfg = config;
        final String system = "你是角色卡设计师。根据用户三个回答，生成一份完整的角色卡。\n" +
                "严格输出 JSON 对象（不要 markdown 代码块），字段：\n" +
                "{\"name\":\"角色名\",\"avatarEmoji\":\"emoji\",\"brief\":\"一句话简介\",\"tags\":\"标签 空格分隔\",\"color\":\"#RRGGBB\",\"persona\":\"性格/身份/说话风格/与用户的关系，分点\",\"privateNote\":\"补充设定\",\"greeting\":\"开场白，可用（）描述动作\",\"situation\":\"一段情境描述，用『用户』称呼对话者\"}\n" +
                "回答：\n1. 角色：" + q1 + "\n2. 关系：" + (q2.length() > 0 ? q2 : "未指定") + "\n3. 氛围：" + (q3.length() > 0 ? q3 : "未指定");
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                applyAiChar(full);
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                toast("生成失败：" + msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, new JSONArray(), ChatEngine.modelOf(cfg, "main"), 1024, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("生成失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void applyAiChar(String raw) {
        try {
            JSONObject card = Json.extractObject(raw);
            if (card == null) {
                toast("AI 返回格式不正确，请重试");
                return;
            }
            if (card.optString("name", "").length() == 0) {
                toast("AI 未生成角色名，请重试");
                return;
            }
            etCharName.setText(card.optString("name", ""));
            etCharEmoji.setText(card.optString("avatarEmoji", "🌸"));
            etCharBrief.setText(card.optString("brief", ""));
            etCharTags.setText(card.optString("tags", ""));
            etCharPersona.setText(card.optString("persona", ""));
            etCharPrivate.setText(card.optString("privateNote", ""));
            etCharGreeting.setText(card.optString("greeting", ""));
            etCharSituation.setText(card.optString("situation", ""));
            String col = card.optString("color", "");
            if (col.matches("#[0-9A-Fa-f]{6}")) {
                selectedColor = col;
                buildColorDots();
            }
            toast("已生成「" + card.optString("name") + "」，可继续微调后保存");
        } catch (Exception e) {
            AppLogger.e("AICHAR", "apply failed", e);
            toast("解析失败，请重试");
        }
    }

    private void aiRefineDialog() {
        if (!aiKeyReady()) return;
        if (etCharPersona.getText().toString().trim().length() == 0) {
            toast("请先填写人设核心再优化");
            return;
        }
        final JSONObject cfg = config;
        final String current = etCharPersona.getText().toString().trim();
        final String system = "你是角色卡设计师。请优化下面的角色设定：\n" +
                "要求：\n1. 保留原有核心，补充细节（身份背景、口头禅、弱点、小习惯）\n2. 明确说话风格与你们的关系\n" +
                "3. 直接输出优化后的设定文本（200-400 字），不要其他内容。\n\n原设定：\n" + current;
        toast("AI 正在优化…");
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                etCharPersona.setText(full == null ? "" : full.trim());
                                toast("已优化，检查后保存");
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                toast("优化失败：" + msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, new JSONArray(), ChatEngine.modelOf(cfg, "main"), 1024, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            toast("优化失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void populateCharEdit() {
        if (editingChar != null) {
            etCharName.setText(editingChar.optString("name", ""));
            etCharEmoji.setText(editingChar.optString("avatarEmoji", "🌸"));
            etCharBrief.setText(editingChar.optString("brief", ""));
            etCharGreeting.setText(editingChar.optString("greeting", ""));
            etCharPersona.setText(editingChar.optString("persona", ""));
            etCharPrivate.setText(editingChar.optString("privateNote", ""));
            etCharSituation.setText(editingChar.optString("situation", ""));
            etCharTags.setText(editingChar.optString("tags", ""));
            swCharPartner.setChecked(editingChar.optBoolean("isPartner", false));
        } else {
            etCharName.setText("");
            etCharEmoji.setText("🌸");
            etCharBrief.setText("");
            etCharGreeting.setText("");
            etCharPersona.setText("");
            etCharPrivate.setText("");
            etCharSituation.setText("");
            etCharTags.setText("");
            swCharPartner.setChecked(false);
        }
        buildColorDots();
    }

    private void buildColorDots() {
        llCharColors.removeAllViews();
        final String[] colors = {"#60A5FA", "#F59E0B", "#34D399", "#C084FC"};
        for (int i = 0; i < colors.length; i++) {
            final String col = colors[i];
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(44, 44);
            lp.setMargins(0, 0, 16, 0);
            tv.setLayoutParams(lp);
            tv.setBackgroundColor(Color.parseColor(col));
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(16);
            if (col.equals(selectedColor)) tv.setText("✓");
            tv.setClickable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    selectedColor = col;
                    buildColorDots();
                }
            });
            llCharColors.addView(tv);
        }
    }

    // ================= chat =================

    private void openChat(JSONObject ch) {
        currentChar = ch;
        currentSession = Store.getSessionForChar(ch.optString("id"));
        if (!currentSession.has("story") && ch.optString("situation", "").length() > 0) {
            try {
                JSONObject st = new JSONObject();
                st.put("id", Store.newId());
                st.put("name", ch.optString("name", "") + "的故事");
                st.put("type", "light");
                st.put("situation", ch.optString("situation", ""));
                st.put("callMe", "你");
                st.put("userSetting", "");
                st.put("characters", new JSONArray());
                st.put("initialNodeId", "");
                st.put("nodes", new JSONArray());
                st.put("endings", new JSONArray());
                JSONArray cids = new JSONArray();
                cids.put(ch.optString("id"));
                st.put("characterIds", cids);
                currentSession.put("story", st);
                Store.saveSession(currentSession);
            } catch (Exception e) {
            }
        }
        JSONObject story = storyOf(currentSession);
        boolean storyDriven = StoryEngine.isStoryDriven(story);
        if (!storyDriven) {
            ensureGreeting(ch, currentSession);
        } else if (currentSession.optString("activeNodeId", "").length() == 0) {
            JSONObject init = StoryEngine.initialNode(story);
            if (init != null) enterNode(story, init.optString("id"));
        }
        crisisShown = false;
        immersive = false;
        activeMsgChecked = false;

        // 当日首次进入即签到，并把新达成的成就攒到渲染后再提示
        boolean checkedIn = Daily.checkIn(currentSession);
        if (checkedIn) {
            newAchievements = Achievements.evaluate(currentSession, ch);
            Store.saveSession(currentSession);
        }
        showScreen("chat");
        if (checkedIn) {
            toast(Daily.checkInMessage(currentSession, ch.optString("id", "")));
            showPendingRewards();
        }
    }

    private void ensureGreeting(JSONObject ch, JSONObject session) {
        JSONArray msgs = session.optJSONArray("messages");
        if (msgs == null || msgs.length() > 0) return;
        String g = ch.optString("greeting", "");
        if (g.length() == 0) return;
        JSONObject parsed = ChatEngine.parseResponse(g);
        JSONArray bubbles = parsed.optJSONArray("bubbles");
        if (bubbles.length() == 0) {
            bubbles = new JSONArray();
            bubbles.put(jObj("text", g));
        }
        try {
            JSONObject m = new JSONObject();
            m.put("id", Store.newId());
            m.put("parentId", "");
            m.put("role", "assistant");
            m.put("content", joinBubbles(bubbles));
            m.put("bubbles", bubbles);
            String inner = parsed.optString("inner", "");
            if (inner.length() > 0) m.put("innerVoice", inner);
            m.put("scene", "companion");
            m.put("timestamp", System.currentTimeMillis());
            msgs.put(m);
            session.put("currentLeafId", m.optString("id"));
            session.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(session);
        } catch (Exception e) {
        }
    }

    private JSONObject storyOf(JSONObject session) {
        if (session == null) return null;
        return session.optJSONObject("story");
    }

    private void initChat(View v) {
        rvChat = (RecyclerView) v.findViewById(R.id.rv_chat);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter();
        rvChat.setAdapter(chatAdapter);

        etChatInput = (EditText) v.findViewById(R.id.et_chat_input);
        btnSend = (com.google.android.material.button.MaterialButton) v.findViewById(R.id.btn_chat_send);
        tvChatAffection = (TextView) v.findViewById(R.id.tv_chat_affection);
        pbChatAffection = (ProgressBar) v.findViewById(R.id.pb_chat_affection);
        tvChatStatus = (TextView) v.findViewById(R.id.tv_chat_status);
        tvChatName = (TextView) v.findViewById(R.id.tv_chat_name);
        tvChatAvatar = (TextView) v.findViewById(R.id.tv_chat_avatar);
        tvCrisis = (TextView) v.findViewById(R.id.tv_crisis);

        v.findViewById(R.id.btn_chat_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_chat_menu).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                showChatMenu();
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                sendText(etChatInput.getText().toString());
            }
        });
        etChatInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView tv, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    sendText(etChatInput.getText().toString());
                    return true;
                }
                return false;
            }
        });
        v.findViewById(R.id.btn_inspire).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                generateInspiration();
            }
        });
        v.findViewById(R.id.btn_commands).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                showScreen("commands");
            }
        });
        v.findViewById(R.id.btn_actions).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                actionDialog();
            }
        });
        // 长按「动作」直接插入一对括号，光标落在中间
        v.findViewById(R.id.btn_actions).setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View vv) {
                insertBrackets();
                return true;
            }
        });
        v.findViewById(R.id.btn_chat_mode).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                toggleImmersive();
            }
        });
        v.findViewById(R.id.v_immersive_overlay).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                toggleImmersive();
            }
        });
    }

    private void renderChat() {
        if (currentSession == null) return;
        immersive = false;
        View cs = screens.get("chat");
        if (cs != null) {
            cs.findViewById(R.id.ll_chat_top).setVisibility(View.VISIBLE);
            cs.findViewById(R.id.ll_chat_tool).setVisibility(View.VISIBLE);
            cs.findViewById(R.id.ll_chat_input).setVisibility(View.VISIBLE);
            cs.findViewById(R.id.hsv_inspire).setVisibility(View.GONE);
            cs.findViewById(R.id.v_immersive_overlay).setVisibility(View.GONE);
        }
        tvChatName.setText(currentChar.optString("name", ""));
        tvChatAvatar.setText(currentChar.optString("avatarEmoji", "🌸"));
        tvCrisis.setVisibility(crisisShown ? View.VISIBLE : View.GONE);
        updateAffectionHeader();
        refreshChoicesChips();
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
        maybeSendActiveMessage();
    }

    private void updateAffectionHeader() {
        double a = ChatEngine.affectionOf(currentSession);
        tvChatAffection.setText("♡ " + (int) a);
        tvChatStatus.setText("阶段：" + ChatEngine.stageName(a));
        if (pbChatAffection != null) {
            // 好感度区间是 [-100, 200]，进度条只展示 0 以上的部分
            pbChatAffection.setProgress((int) Math.max(0, Math.min(200, a)));
        }
    }

    // ---------- 剧情运行时 ----------

    private void enterNode(JSONObject story, String nodeId) {
        if (story == null) return;
        JSONObject node = StoryEngine.nodeOf(story, nodeId);
        if (node == null) return;
        try {
            currentSession.put("activeNodeId", nodeId);
            currentSession.put("turnsInNode", 0);
            StoryEngine.applyAssignments(currentSession, node.optJSONArray("assignments"));
            String text = node.optString("text", "");
            if (text.length() > 0) {
                JSONObject parsed = ChatEngine.parseResponse(text);
                JSONArray bubbles = parsed.optJSONArray("bubbles");
                if (bubbles.length() == 0) {
                    bubbles = new JSONArray();
                    bubbles.put(jObj("text", text));
                }
                if (StoryEngine.isNarrator(node)) {
                    // 旁白 → system 消息居中显示
                    JSONObject m = new JSONObject();
                    m.put("id", Store.newId());
                    m.put("parentId", currentSession.optString("currentLeafId", ""));
                    m.put("role", "system");
                    m.put("content", joinBubbles(bubbles));
                    m.put("scene", "story");
                    m.put("storyNodeId", nodeId);
                    m.put("timestamp", System.currentTimeMillis());
                    currentSession.optJSONArray("messages").put(m);
                    currentSession.put("currentLeafId", m.optString("id"));
                } else {
                    JSONObject m = new JSONObject();
                    m.put("id", Store.newId());
                    m.put("parentId", currentSession.optString("currentLeafId", ""));
                    m.put("role", "assistant");
                    m.put("content", joinBubbles(bubbles));
                    m.put("bubbles", bubbles);
                    m.put("scene", "story");
                    m.put("storyNodeId", nodeId);
                    JSONObject spk = StoryEngine.speakerOf(story, node);
                    if (spk != null) {
                        m.put("speakerName", spk.optString("name", ""));
                        m.put("speakerEmoji", spk.optString("emoji", "🎭"));
                    }
                    m.put("timestamp", System.currentTimeMillis());
                    currentSession.optJSONArray("messages").put(m);
                    currentSession.put("currentLeafId", m.optString("id"));
                }
            }
            if (StoryEngine.isEnding(node)) {
                StoryEngine.unlockEnding(currentSession, node);
                showEndingNotice(node);
            }
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "enterNode failed", e);
        }
        buildChatRows();
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        scrollChat();
        refreshChoicesChips();
        updateAffectionHeader();
    }

    private void refreshChoicesChips() {
        View cs = screens.get("chat");
        if (cs == null || currentSession == null) return;
        LinearLayout ll = (LinearLayout) cs.findViewById(R.id.ll_choices_chips);
        View hsv = cs.findViewById(R.id.hsv_choices);
        ll.removeAllViews();
        JSONObject story = storyOf(currentSession);
        JSONObject node = StoryEngine.nodeOf(story, currentSession.optString("activeNodeId", ""));
        if (node == null || !StoryEngine.hasChoices(node)) {
            hsv.setVisibility(View.GONE);
            return;
        }
        JSONArray choices = StoryEngine.visibleChoices(node, currentSession);
        if (choices.length() == 0) {
            hsv.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < choices.length(); i++) {
            final JSONObject c = choices.optJSONObject(i);
            Chip chip = new Chip(this);
            chip.setText(c.optString("text", ""));
            chip.setChipBackgroundColor(csl(R.color.accent_container));
            chip.setTextColor(c(R.color.on_accent_container));
            chip.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    onChoicePicked(c);
                }
            });
            ll.addView(chip);
        }
        hsv.setVisibility(View.VISIBLE);
    }

    private void onChoicePicked(JSONObject choice) {
        if (streaming || currentSession == null) return;
        String next = choice.optString("next", "");
        if (next.length() == 0) return;
        try {
            JSONObject u = new JSONObject();
            u.put("id", Store.newId());
            u.put("parentId", currentSession.optString("currentLeafId", ""));
            u.put("role", "user");
            u.put("content", choice.optString("text", ""));
            u.put("scene", "story_choice");
            u.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(u);
            currentSession.put("currentLeafId", u.optString("id"));
            currentSession.put("turnsInNode", currentSession.optInt("turnsInNode", 0) + 1);
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "choice failed", e);
        }
        enterNode(storyOf(currentSession), next);
    }

    private void showEndingNotice(JSONObject node) {
        String title = node.optString("endingTitle", "");
        String desc = node.optString("endingDescription", "");
        String icon = node.optString("endingIcon", "🏁");
        new MaterialAlertDialogBuilder(this).setTitle("🏁 达成结局")
                .setMessage(icon + " " + (title.length() > 0 ? title : node.optString("name", "结局"))
                        + (desc.length() > 0 ? "\n\n" + desc : ""))
                .setPositiveButton("太好了", null)
                .show();
    }

    private void showEndingsDialog() {
        JSONObject story = storyOf(currentSession);
        if (story == null) {
            toast("当前没有剧情");
            return;
        }
        JSONArray endings = StoryEngine.endingNodes(story);
        if (endings.length() == 0) {
            toast("这个故事还没有结局节点");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < endings.length(); i++) {
            JSONObject n = endings.optJSONObject(i);
            boolean unlocked = StoryEngine.hasUnlocked(currentSession, n.optString("id"));
            String icon = n.optString("endingIcon", "🏁");
            String title = n.optString("endingTitle", n.optString("name", "结局"));
            sb.append(unlocked ? "✅ " : "🔒 ").append(icon).append(" ").append(title).append("\n");
            String desc = n.optString("endingDescription", "");
            if (desc.length() > 0) sb.append("   ").append(desc).append("\n");
        }
        new MaterialAlertDialogBuilder(this).setTitle("结局图鉴")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    // ---------- 主动消息 ----------

    private void maybeSendActiveMessage() {
        if (activeMsgChecked || streaming || currentSession == null || currentChar == null) return;
        if (config.optString("apiKey", "").length() == 0) return;
        if (StoryEngine.isStoryDriven(storyOf(currentSession))) return; // 剧情模式不打扰
        if (!ChatEngine.shouldSendActive(currentSession, config)) return;
        activeMsgChecked = true;
        toast("Ta 主动找你了…");
        final JSONObject cfg = config;
        final JSONObject ch = currentChar;
        final JSONObject sess = currentSession;
        final JSONObject story = storyOf(sess);
        final String system = ChatEngine.buildSystemPrompt(cfg, ch, sess, story)
                + "\n\n角色离开了一段时间，现在主动向用户发起一条消息（关心/分享/想念，符合人设，1-2 句）。";
        final JSONArray history = ChatEngine.historyMessages(sess, 8);
        addTypingRow();
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                                insertAssistantMessage(full, "active");
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, history, ChatEngine.modelOf(cfg, "main"), 512, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            removeTyping();
                        }
                    });
                }
            }
        }).start();
    }

    private void insertAssistantMessage(String raw, String scene) {
        if (raw == null) raw = "";
        JSONObject parsed = ChatEngine.parseResponse(raw);
        JSONArray bubbles = parsed.optJSONArray("bubbles");
        if (bubbles.length() == 0) {
            bubbles = new JSONArray();
            bubbles.put(jObj("text", raw.trim()));
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("id", Store.newId());
            msg.put("parentId", currentSession.optString("currentLeafId", ""));
            msg.put("role", "assistant");
            msg.put("content", joinBubbles(bubbles));
            msg.put("bubbles", bubbles);
            msg.put("scene", scene);
            msg.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(msg);
            currentSession.put("currentLeafId", msg.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "insert failed", e);
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void buildChatRows() {
        chatRows.clear();
        JSONArray path = ChatEngine.pathMessages(currentSession);
        for (int i = 0; i < path.length(); i++) chatRows.add(path.optJSONObject(i));
    }

    private void scrollChat() {
        rvChat.post(new Runnable() {
            public void run() {
                if (chatAdapter.getItemCount() > 0) rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
    }

    /**
     * 沉浸模式（视觉小说风）：全屏、隐藏顶栏与工具栏，但**保留输入条**，
     * 并让最新一条角色消息逐字浮现。点空白处跳过逐字，长按或返回键退出。
     */
    private void toggleImmersive() {
        immersive = !immersive;
        View cs = screens.get("chat");
        if (cs == null) return;
        cs.findViewById(R.id.ll_chat_top).setVisibility(immersive ? View.GONE : View.VISIBLE);
        cs.findViewById(R.id.ll_chat_tool).setVisibility(immersive ? View.GONE : View.VISIBLE);
        cs.findViewById(R.id.hsv_inspire).setVisibility(View.GONE);
        cs.findViewById(R.id.hsv_choices).setVisibility(View.GONE);
        cs.findViewById(R.id.tv_crisis).setVisibility(immersive ? View.GONE : (crisisShown ? View.VISIBLE : View.GONE));
        // 输入条始终保留：沉浸不该等于聊不了
        cs.findViewById(R.id.ll_chat_input).setVisibility(View.VISIBLE);
        cs.findViewById(R.id.v_immersive_overlay).setVisibility(View.GONE);
        setFullscreen(immersive);
        if (immersive) {
            toast("沉浸模式 · 返回键退出");
            typewriteLast();
        } else {
            cancelTypewriter();
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    /** 进出全屏：沉浸时藏掉系统栏。 */
    private void setFullscreen(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // ---------- 打字机 ----------

    /** 正在逐字显示的消息 id 与已显示的字数；非沉浸模式下 typingMsgId 为空。 */
    private String typingMsgId = "";
    private int typedChars = 0;
    private Runnable typeTick;

    private void typewriteLast() {
        cancelTypewriter();
        if (currentSession == null) return;
        JSONArray path = ChatEngine.pathMessages(currentSession);
        JSONObject last = path.length() > 0 ? path.optJSONObject(path.length() - 1) : null;
        if (last == null || !"assistant".equals(last.optString("role"))) return;
        final String id = last.optString("id");
        final int total = last.optString("content", "").length();
        if (total == 0) return;
        typingMsgId = id;
        typedChars = 0;
        typeTick = new Runnable() {
            public void run() {
                if (!immersive || !id.equals(typingMsgId)) return;
                typedChars += 2;
                if (typedChars >= total) {
                    typedChars = total;
                    typingMsgId = "";
                } else {
                    uiHandler.postDelayed(this, 28);
                }
                chatAdapter.notifyDataSetChanged();
                scrollChat();
            }
        };
        uiHandler.postDelayed(typeTick, 28);
    }

    private void cancelTypewriter() {
        if (typeTick != null) uiHandler.removeCallbacks(typeTick);
        typeTick = null;
        typingMsgId = "";
        typedChars = 0;
    }

    /** 点气泡跳过逐字，直接全显。 */
    private void skipTypewriter() {
        if (typingMsgId.length() == 0) return;
        cancelTypewriter();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    /** 返回该消息当前可显示的字数；-1 表示不在逐字状态，全部显示。 */
    private int typewriterBudget(JSONObject msg) {
        if (!immersive || typingMsgId.length() == 0) return -1;
        if (!typingMsgId.equals(msg.optString("id"))) return -1;
        return typedChars;
    }

    // ---------- sending ----------

    /** 在输入框插入（），光标停在括号中间，并提示用法。 */
    private void insertBrackets() {
        etChatInput.setText("（）");
        etChatInput.setSelection(1);
        etChatInput.requestFocus();
        toast("括号里写场景，如（下起了雨）；或改数值，如（好感度+10）");
    }

    /**
     * 处理括号指令。变量操作就地改数值、不请求模型；导演指令记为场景设定并
     * 插入一条旁白，随下一次发言注入 prompt。
     *
     * @return true 表示这条输入已被当作指令消费，不再走正常发送流程
     */
    private boolean handleDirective(String t) {
        Directive d = Directive.parse(t);
        if (d.kind() == Directive.NONE) return false;
        etChatInput.setText("");

        if (d.kind() == Directive.VARIABLE) {
            double before = ChatEngine.affectionOf(currentSession);
            String msg = d.applyVariable(currentSession);
            if (msg == null) {
                toast("没看懂这条指令，可以试试（好感度+10）或（天气=雨）");
                return true;
            }
            double after = ChatEngine.affectionOf(currentSession);
            // 手动拉高好感度同样可能跨过里程碑
            pendingMilestone = Milestones.checkCross(currentSession, before, after, msg);
            newAchievements = Achievements.evaluate(currentSession, currentChar);
            addSystemNote("· " + msg + " ·");
            Store.saveSession(currentSession);
            updateAffectionHeader();
            showPendingRewards();
            return true;
        }

        d.applyScene(currentSession);
        addSystemNote("（" + d.scene() + "）");
        Store.saveSession(currentSession);
        toast("场景已设定，下一句起生效");
        return true;
    }

    /** 往对话流里插一条系统旁白（不进模型的 user 历史）。 */
    private void addSystemNote(String text) {
        try {
            JSONObject m = new JSONObject();
            m.put("id", Store.newId());
            m.put("role", "system");
            m.put("content", text);
            m.put("parentId", currentSession.optString("currentLeafId", ""));
            m.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(m);
            currentSession.put("currentLeafId", m.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
        } catch (Exception e) {
            AppLogger.e("DIRECTIVE", "add note failed", e);
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void sendText(String text) {
        if (streaming || currentSession == null) return;
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return;
        // 括号指令：不算角色说话，就地生效
        if (handleDirective(t)) return;
        if (config.optString("apiKey", "").length() == 0) {
            toast("请先在设置中配置 API Key");
            return;
        }
        streaming = true;
        btnSend.setEnabled(false);
        etChatInput.setText("");
        if (ChatEngine.hasCrisis(t) && !crisisShown) {
            crisisShown = true;
            tvCrisis.setVisibility(View.VISIBLE);
        }
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("id", Store.newId());
            userMsg.put("parentId", currentSession.optString("currentLeafId", ""));
            userMsg.put("role", "user");
            userMsg.put("content", t);
            userMsg.put("scene", "chat");
            userMsg.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(userMsg);
            currentSession.put("currentLeafId", userMsg.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
            int turns = currentSession.optInt("turnsSinceLastSummary", 0) + 1;
            currentSession.put("turnsSinceLastSummary", turns);
            ChatEngine.decayMemories(currentSession, config);
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "send failed", e);
            streaming = false;
            btnSend.setEnabled(true);
            return;
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();

        final int interval = config.optInt("memorySummaryInterval", 15);
        if (currentSession.optInt("turnsSinceLastSummary", 0) >= interval) {
            try {
                currentSession.put("turnsSinceLastSummary", 0);
                Store.saveSession(currentSession);
            } catch (Exception e) {
            }
            final JSONObject sess = currentSession;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        ChatEngine.summarizeMemory(config, sess);
                    } catch (Exception e) {
                    }
                }
            }).start();
        }

        // 剧情模式：先检查条件/关键词/自动边是否触发推进
        JSONObject storyNow = storyOf(currentSession);
        if (StoryEngine.isStoryDriven(storyNow)) {
            JSONObject nodeNow = StoryEngine.nodeOf(storyNow, currentSession.optString("activeNodeId", ""));
            try {
                currentSession.put("turnsInNode", currentSession.optInt("turnsInNode", 0) + 1);
                Store.saveSession(currentSession);
            } catch (Exception e) {
            }
            String next = StoryEngine.resolveEdge(currentSession, nodeNow, t);
            if (next != null && next.length() > 0) {
                streaming = false;
                btnSend.setEnabled(true);
                enterNode(storyNow, next);
                return;
            }
        }
        requestReply();
    }

    private void requestReply() {
        final JSONObject cfg = config;
        final JSONObject ch = currentChar;
        final JSONObject sess = currentSession;
        final JSONObject story = storyOf(sess);
        final JSONArray history = ChatEngine.historyMessages(sess, cfg.optInt("historyWindow", 20));
        final String system = ChatEngine.buildSystemPrompt(cfg, ch, sess, story);
        final boolean stream = cfg.optBoolean("enableStreaming", true);
        final String model = ChatEngine.modelOf(cfg, "main");
        addTypingRow();
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder acc = new StringBuilder();
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                        acc.append(t);
                        final String cur = acc.toString();
                        runOnUiThread(new Runnable() {
                            public void run() {
                                updateTypingText(cur);
                            }
                        });
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                finishAssistant(full);
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                failAssistant(msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, history, model, 2048, stream, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            failAssistant(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void addTypingRow() {
        JSONObject t = new JSONObject();
        try {
            t.put("id", "__typing__");
            t.put("role", "assistant");
            t.put("content", "正在输入…");
        } catch (Exception e) {
        }
        chatRows.add(t);
        chatAdapter.notifyItemInserted(chatRows.size() - 1);
        scrollChat();
    }

    private void updateTypingText(String text) {
        for (int i = chatRows.size() - 1; i >= 0; i--) {
            JSONObject m = chatRows.get(i);
            if ("__typing__".equals(m.optString("id"))) {
                try {
                    m.put("content", text);
                } catch (Exception e) {
                }
                chatAdapter.notifyItemChanged(i);
                scrollChat();
                return;
            }
        }
    }

    private void removeTyping() {
        for (int i = chatRows.size() - 1; i >= 0; i--) {
            JSONObject m = chatRows.get(i);
            if ("__typing__".equals(m.optString("id"))) chatRows.remove(i);
        }
        chatAdapter.notifyDataSetChanged();
    }

    private void finishAssistant(String raw) {
        if (raw == null) raw = "";
        JSONObject parsed = ChatEngine.parseResponse(raw);
        JSONArray bubbles = parsed.optJSONArray("bubbles");
        if (bubbles.length() == 0) {
            bubbles = new JSONArray();
            bubbles.put(jObj("text", raw.trim()));
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("id", Store.newId());
            msg.put("parentId", currentSession.optString("currentLeafId", ""));
            msg.put("role", "assistant");
            msg.put("content", joinBubbles(bubbles));
            msg.put("bubbles", bubbles);
            String inner = parsed.optString("inner", "");
            if (inner.length() > 0) msg.put("innerVoice", inner);
            JSONObject deltas = parsed.optJSONObject("varDeltas");
            if (deltas.length() > 0) msg.put("varDeltas", deltas);
            msg.put("scene", "chat");
            msg.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(msg);
            currentSession.put("currentLeafId", msg.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
            double affBefore = ChatEngine.affectionOf(currentSession);
            ChatEngine.applyVarDeltas(currentSession, deltas);
            double affAfter = ChatEngine.affectionOf(currentSession);
            pendingMilestone = Milestones.checkCross(
                    currentSession, affBefore, affAfter, msg.optString("content", ""));
            newAchievements = Achievements.evaluate(currentSession, currentChar);
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "finish failed", e);
        }
        streaming = false;
        btnSend.setEnabled(true);
        removeTyping();
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
        updateAffectionHeader();
        if (immersive) typewriteLast();
        showPendingRewards();
    }

    /** 里程碑庆祝卡 / 成就解锁提示，在回复渲染完之后依次弹出。 */
    private void showPendingRewards() {
        if (pendingMilestone != null) {
            final JSONObject m = pendingMilestone;
            pendingMilestone = null;
            showMilestoneDialog(m);
            return;
        }
        if (newAchievements != null && newAchievements.length() > 0) {
            JSONObject a = newAchievements.optJSONObject(0);
            newAchievements = null;
            if (a != null) {
                toast("🏅 解锁成就：" + a.optString("title", ""));
            }
        }
    }

    private void showMilestoneDialog(JSONObject m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = dp(24);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundResource(R.drawable.bg_milestone_card);

        TextView tvHeart = new TextView(this);
        tvHeart.setText("♡");
        tvHeart.setTextSize(40);
        tvHeart.setGravity(Gravity.CENTER);
        tvHeart.setTextColor(c(R.color.accent));
        box.addView(tvHeart);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(m.optString("title", ""));
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextColor(c(R.color.text_primary));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(8);
        tvTitle.setLayoutParams(tp);
        box.addView(tvTitle);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(m.optString("message", ""));
        tvMsg.setTextSize(14);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setTextColor(c(R.color.text_secondary));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(8);
        tvMsg.setLayoutParams(mp);
        box.addView(tvMsg);

        TextView tvTh = new TextView(this);
        tvTh.setText("好感度 " + m.optInt("threshold", 0));
        tvTh.setTextSize(12);
        tvTh.setGravity(Gravity.CENTER);
        tvTh.setTextColor(c(R.color.text_tertiary));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(12);
        tvTh.setLayoutParams(hp);
        box.addView(tvTh);

        // 缩放淡入，让"解锁"这件事有实感
        box.setAlpha(0f);
        box.setScaleX(0.85f);
        box.setScaleY(0.85f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();

        new MaterialAlertDialogBuilder(this)
                .setView(box)
                .setPositiveButton("收下这一刻", null)
                .setNeutralButton("看纪念册", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        showScreen("album");
                    }
                })
                .show();
    }

    private void failAssistant(String msg) {
        removeTyping();
        try {
            JSONObject em = new JSONObject();
            em.put("id", Store.newId());
            em.put("parentId", currentSession.optString("currentLeafId", ""));
            em.put("role", "assistant");
            em.put("content", "⚠️ 出错了：" + (msg == null ? "网络错误" : msg));
            em.put("isError", true);
            em.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(em);
            currentSession.put("currentLeafId", em.optString("id"));
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        streaming = false;
        btnSend.setEnabled(true);
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void retryLast() {
        if (streaming || currentSession == null) return;
        JSONArray path = ChatEngine.pathMessages(currentSession);
        JSONObject lastUser = null;
        for (int i = path.length() - 1; i >= 0; i--) {
            JSONObject m = path.optJSONObject(i);
            if ("user".equals(m.optString("role"))) {
                lastUser = m;
                break;
            }
        }
        if (lastUser == null) return;
        JSONArray msgs = currentSession.optJSONArray("messages");
        for (int i = msgs.length() - 1; i >= 0; i--) {
            JSONObject m = msgs.optJSONObject(i);
            if (m.optBoolean("isError", false)) msgs.remove(i);
        }
        try {
            currentSession.put("currentLeafId", lastUser.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        requestReply();
    }

    private String joinBubbles(JSONArray bubbles) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bubbles.length(); i++) {
            JSONObject b = bubbles.optJSONObject(i);
            if (i > 0) sb.append(" ");
            sb.append(b.optString("text", ""));
        }
        return sb.toString();
    }

    private static JSONObject jObj(String k, Object v) {
        JSONObject o = new JSONObject();
        try {
            o.put(k, v);
        } catch (Exception e) {
        }
        return o;
    }

    // ---------- regenerate / branch / edit / feedback ----------

    private void regenerateMessage(final JSONObject msg) {
        final String[] dirs = {"换个说法", "更简短", "更详细", "情绪更强", "情绪更淡", "换个方向", "直接重来"};
        new MaterialAlertDialogBuilder(this).setTitle("重新生成方向")
                .setItems(dirs, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        doRegenerate(msg, dirs[which]);
                    }
                }).show();
    }

    private void doRegenerate(final JSONObject msg, final String direction) {
        if (streaming || currentSession == null) return;
        if (config.optString("apiKey", "").length() == 0) {
            toast("请先配置 API Key");
            return;
        }
        streaming = true;
        btnSend.setEnabled(false);
        final String parentId = msg.optString("parentId", "");
        final JSONObject cfg = config;
        final JSONObject ch = currentChar;
        final JSONObject sess = currentSession;
        final JSONObject story = storyOf(sess);

        JSONArray fullPath = ChatEngine.pathMessages(sess);
        JSONArray hist = new JSONArray();
        for (int i = 0; i < fullPath.length(); i++) {
            JSONObject m = fullPath.optJSONObject(i);
            if (m.optString("id").equals(msg.optString("id"))) break;
            String role = m.optString("role", "");
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            JSONObject h = new JSONObject();
            try {
                h.put("role", role);
                h.put("content", m.optString("content", ""));
            } catch (Exception e) {
            }
            hist.put(h);
        }
        int n = Math.max(1, cfg.optInt("historyWindow", 20)) * 2;
        final JSONArray history;
        if (hist.length() > n) {
            JSONArray trimmed = new JSONArray();
            for (int i = hist.length() - n; i < hist.length(); i++) trimmed.put(hist.opt(i));
            history = trimmed;
        } else {
            history = hist;
        }
        String base = ChatEngine.buildSystemPrompt(cfg, ch, sess, story);
        final String system = base + "\n\n用户要求你重新生成上一条回复。要求：" + direction;
        final String model = ChatEngine.modelOf(cfg, "main");
        addTypingRow();
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                                JSONObject parsed = ChatEngine.parseResponse(full);
                                JSONArray bubbles = parsed.optJSONArray("bubbles");
                                if (bubbles.length() == 0) {
                                    bubbles = new JSONArray();
                                    bubbles.put(jObj("text", full.trim()));
                                }
                                try {
                                    JSONObject nm = new JSONObject();
                                    nm.put("id", Store.newId());
                                    nm.put("parentId", parentId);
                                    nm.put("role", "assistant");
                                    nm.put("content", joinBubbles(bubbles));
                                    nm.put("bubbles", bubbles);
                                    String inner = parsed.optString("inner", "");
                                    if (inner.length() > 0) nm.put("innerVoice", inner);
                                    JSONObject deltas = parsed.optJSONObject("varDeltas");
                                    if (deltas.length() > 0) nm.put("varDeltas", deltas);
                                    nm.put("scene", "chat");
                                    nm.put("isRegenerated", true);
                                    nm.put("timestamp", System.currentTimeMillis());
                                    sess.optJSONArray("messages").put(nm);
                                    sess.put("currentLeafId", nm.optString("id"));
                                    sess.put("updatedAt", System.currentTimeMillis());
                                    ChatEngine.applyVarDeltas(sess, deltas);
                                    Store.saveSession(sess);
                                } catch (Exception e) {
                                    AppLogger.e("CHAT", "regen failed", e);
                                }
                                streaming = false;
                                btnSend.setEnabled(true);
                                buildChatRows();
                                chatAdapter.notifyDataSetChanged();
                                scrollChat();
                                updateAffectionHeader();
                            }
                        });
                    }

                    public void onError(final String e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                                streaming = false;
                                btnSend.setEnabled(true);
                                toast("重新生成失败：" + e);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, history, model, 2048, false, cb);
                } catch (Exception e) {
                }
            }
        }).start();
    }

    private JSONArray siblingsOf(JSONObject msg) {
        JSONArray out = new JSONArray();
        JSONArray msgs = currentSession.optJSONArray("messages");
        String parentId = msg.optString("parentId", "");
        List<JSONObject> tmp = new ArrayList<JSONObject>();
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m != null && "assistant".equals(m.optString("role")) && parentId.equals(m.optString("parentId", ""))) {
                    tmp.add(m);
                }
            }
        }
        Collections.sort(tmp, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(a.optLong("timestamp", 0), b.optLong("timestamp", 0));
            }
        });
        for (JSONObject m : tmp) out.put(m);
        return out;
    }

    private int indexIn(JSONObject msg, JSONArray sibs) {
        for (int i = 0; i < sibs.length(); i++) {
            if (sibs.optJSONObject(i).optString("id").equals(msg.optString("id"))) return i;
        }
        return 0;
    }

    private void switchBranch(final JSONObject msg) {
        JSONArray sibs = siblingsOf(msg);
        if (sibs.length() < 2) return;
        int idx = indexIn(msg, sibs);
        JSONObject next = sibs.optJSONObject((idx + 1) % sibs.length());
        String leaf = ChatEngine.findLeaf(currentSession, next.optString("id"));
        try {
            currentSession.put("currentLeafId", leaf);
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void showMsgMenu(final JSONObject msg) {
        String role = msg.optString("role", "");
        final List<String> items = new ArrayList<String>();
        items.add("复制");
        if ("assistant".equals(role)) {
            items.add("改写");
            items.add("重新生成");
            items.add("不喜欢");
        }
        items.add("从这里重新开始");
        items.add("删除");
        new MaterialAlertDialogBuilder(this).setTitle("消息操作")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        String act = items.get(which);
                        if ("复制".equals(act)) {
                            copy(msg.optString("content", ""));
                        } else if ("改写".equals(act)) {
                            editMessage(msg);
                        } else if ("重新生成".equals(act)) {
                            regenerateMessage(msg);
                        } else if ("不喜欢".equals(act)) {
                            feedbackDialog(msg);
                        } else if ("从这里重新开始".equals(act)) {
                            renderRewind();
                            rewindTo(msg);
                        } else if ("删除".equals(act)) {
                            deleteMessage(msg);
                        }
                    }
                }).show();
    }

    private void deleteMessage(JSONObject msg) {
        JSONArray msgs = currentSession.optJSONArray("messages");
        for (int i = msgs.length() - 1; i >= 0; i--) {
            JSONObject m = msgs.optJSONObject(i);
            if (m.optString("id").equals(msg.optString("id"))) {
                msgs.remove(i);
                break;
            }
        }
        if (currentSession.optString("currentLeafId", "").equals(msg.optString("id"))) {
            try {
                currentSession.put("currentLeafId", msg.optString("parentId", ""));
            } catch (Exception e) {
            }
        }
        try {
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void editMessage(final JSONObject msg) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText et = new EditText(this);
        et.setText(msg.optString("content", ""));
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        wrap.addView(et);
        new MaterialAlertDialogBuilder(this).setTitle("改写（示范 Ta 该怎么说）")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String nt = et.getText().toString().trim();
                        if (nt.length() == 0) return;
                        try {
                            msg.put("content", nt);
                            msg.put("isEdited", true);
                            JSONArray bubbles = new JSONArray();
                            bubbles.put(jObj("text", nt));
                            msg.put("bubbles", bubbles);
                            ChatEngine.markStaleMemories(currentSession, msg.optString("id"));
                            Store.saveSession(currentSession);
                        } catch (Exception e) {
                        }
                        buildChatRows();
                        chatAdapter.notifyDataSetChanged();
                        scrollChat();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void feedbackDialog(final JSONObject msg) {
        final boolean[] checked = new boolean[ChatEngine.FEEDBACK_REASONS.length];
        new MaterialAlertDialogBuilder(this).setTitle("为什么不喜欢这条回复？")
                .setMultiChoiceItems(ChatEngine.FEEDBACK_REASONS, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("提交", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        JSONObject feedback = currentSession.optJSONObject("feedback");
                        if (feedback == null) {
                            feedback = new JSONObject();
                            try {
                                currentSession.put("feedback", feedback);
                            } catch (Exception e) {
                            }
                        }
                        JSONArray reasons = feedback.optJSONArray("reasons");
                        if (reasons == null) {
                            reasons = new JSONArray();
                            try {
                                feedback.put("reasons", reasons);
                            } catch (Exception e) {
                            }
                        }
                        for (int i = 0; i < checked.length; i++) {
                            if (!checked[i]) continue;
                            String reason = ChatEngine.FEEDBACK_REASONS[i];
                            boolean found = false;
                            for (int j = 0; j < reasons.length(); j++) {
                                JSONObject r = reasons.optJSONObject(j);
                                if (reason.equals(r.optString("reason"))) {
                                    try {
                                        r.put("count", r.optInt("count", 0) + 1);
                                    } catch (Exception e) {
                                    }
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                JSONObject r = new JSONObject();
                                try {
                                    r.put("reason", reason);
                                    r.put("count", 1);
                                    reasons.put(r);
                                } catch (Exception e) {
                                }
                            }
                        }
                        Store.saveSession(currentSession);
                        toast("已记录，之后的回复会注意避免");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- chat menu ----------

    private void showChatMenu() {
        final String[] items = {"时光倒流", "纪念册", "剧情图", "记忆面板", "入戏指令", "入戏记录", "结局", "我们的旅程", "沉浸模式", "导出对话", "清空对话"};
        new MaterialAlertDialogBuilder(this).setTitle(currentChar.optString("name", ""))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            showScreen("rewind");
                        } else if (which == 1) {
                            showScreen("album");
                        } else if (which == 2) {
                            openStoryEdit();
                        } else if (which == 3) {
                            showScreen("memory");
                        } else if (which == 4) {
                            showScreen("commands");
                        } else if (which == 5) {
                            showRecordsDialog();
                        } else if (which == 6) {
                            showEndingsDialog();
                        } else if (which == 7) {
                            shareJourney();
                        } else if (which == 8) {
                            toggleImmersive();
                        } else if (which == 9) {
                            shareChat();
                        } else if (which == 10) {
                            clearChat();
                        }
                    }
                }).show();
    }

    private void clearChat() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_Dialog_Destructive).setTitle("清空对话")
                .setMessage("将清空本会话的消息（记忆与好感度保留），确定？")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            currentSession.put("messages", new JSONArray());
                            currentSession.put("currentLeafId", "");
                            currentSession.put("updatedAt", System.currentTimeMillis());
                            currentSession.put("turnsSinceLastSummary", 0);
                            Store.saveSession(currentSession);
                        } catch (Exception e) {
                        }
                        buildChatRows();
                        chatAdapter.notifyDataSetChanged();
                        scrollChat();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareChat() {
        StringBuilder sb = new StringBuilder();
        sb.append("—— ").append(currentChar.optString("name", "")).append(" 的对话 ——\n\n");
        JSONArray path = ChatEngine.pathMessages(currentSession);
        for (int i = 0; i < path.length(); i++) {
            JSONObject m = path.optJSONObject(i);
            String role = m.optString("role", "");
            if ("system".equals(role)) {
                sb.append(m.optString("content", "")).append("\n");
                continue;
            }
            String who = "user".equals(role) ? "我" : currentChar.optString("name", "");
            sb.append(who).append("：").append(m.optString("content", "")).append("\n");
        }
        shareText("导出对话", sb.toString());
    }

    private void shareJourney() {
        JSONObject s = currentSession;
        long created = s.optLong("createdAt", System.currentTimeMillis());
        long days = Math.max(0, (System.currentTimeMillis() - created) / 86400000L);
        JSONArray msgs = s.optJSONArray("messages");
        int rounds = 0;
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                if ("user".equals(msgs.optJSONObject(i).optString("role"))) rounds++;
            }
        }
        int mems = s.optJSONArray("memories") == null ? 0 : s.optJSONArray("memories").length();
        int maxAff = (int) s.optDouble("maxAffection", 0);
        int endings = s.optJSONArray("unlockedEndings") == null ? 0 : s.optJSONArray("unlockedEndings").length();
        double aff = ChatEngine.affectionOf(s);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(48, 20, 48, 8);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(currentChar.optString("avatarEmoji", "🌸") + " " + currentChar.optString("name", "") + " · 成长卡片");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(c(R.color.text_primary));
        tvTitle.setGravity(Gravity.CENTER);
        card.addView(tvTitle);

        TextView tvStage = new TextView(this);
        tvStage.setText("当前阶段：" + ChatEngine.stageName(aff));
        tvStage.setTextSize(13);
        tvStage.setTextColor(c(R.color.brand));
        tvStage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = 6;
        tvStage.setLayoutParams(stlp);
        card.addView(tvStage);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10);
        plp.topMargin = 12;
        pb.setLayoutParams(plp);
        pb.setMax(200);
        pb.setProgress((int) Math.max(0, Math.min(200, aff)));
        pb.setProgressTintList(csl(R.color.accent));
        card.addView(pb);

        String[][] stats = {
                {"📅", String.valueOf(days), "相识天数"},
                {"💬", String.valueOf(rounds), "对话轮数"},
                {"🧠", String.valueOf(mems), "记忆事件"},
                {"♡", String.valueOf(maxAff), "最高好感"},
                {"🏁", String.valueOf(endings), "解锁结局"}
        };
        for (int i = 0; i < stats.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = 10;
            row.setLayoutParams(rlp);
            TextView tvIcon = new TextView(this);
            tvIcon.setText(stats[i][0]);
            tvIcon.setTextSize(16);
            row.addView(tvIcon);
            TextView tvNum = new TextView(this);
            tvNum.setText(stats[i][1]);
            tvNum.setTextSize(18);
            tvNum.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvNum.setTextColor(c(R.color.text_primary));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = 14;
            nlp.rightMargin = 10;
            tvNum.setLayoutParams(nlp);
            row.addView(tvNum);
            TextView tvLabel = new TextView(this);
            tvLabel.setText(stats[i][2]);
            tvLabel.setTextSize(13);
            tvLabel.setTextColor(c(R.color.text_secondary));
            row.addView(tvLabel);
            card.addView(row);
        }

        StringBuilder txt = new StringBuilder();
        txt.append("我们的旅程 · ").append(currentChar.optString("name", "")).append("\n\n");
        txt.append("相识天数：").append(days).append(" 天\n");
        txt.append("对话轮数：").append(rounds).append(" 轮\n");
        txt.append("记忆事件：").append(mems).append(" 条\n");
        txt.append("最高好感度：♡ ").append(maxAff).append("\n");
        txt.append("当前阶段：").append(ChatEngine.stageName(aff)).append("\n");
        final String shareTxt = txt.toString();

        new MaterialAlertDialogBuilder(this).setTitle("我们的旅程")
                .setView(card)
                .setPositiveButton("分享", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        shareText("我们的旅程", shareTxt);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------- inspiration & actions ----------

    private void generateInspiration() {
        if (streaming || currentSession == null) return;
        if (config.optString("apiKey", "").length() == 0) {
            toast("请先配置 API Key");
            return;
        }
        View cs = screens.get("chat");
        if (cs == null) return;
        final TextView btn = (TextView) cs.findViewById(R.id.btn_inspire);
        btn.setText("生成中…");
        final JSONObject cfg = config;
        final JSONObject sess = currentSession;
        final JSONArray history = ChatEngine.historyMessages(sess, 10);
        final String system = "基于以下对话，生成 3 条用户可能想回复的话。风格各异：一条推进话题、一条情感回应、一条转折或玩笑。每条不超过 20 字。严格输出 JSON 字符串数组，无其他内容。格式：[\"...\",\"...\",\"...\"]";
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                btn.setText("灵感");
                                showInspirationChips(full);
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                btn.setText("灵感");
                                toast("灵感生成失败：" + msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, history, ChatEngine.modelOf(cfg, "main"), 512, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            btn.setText("灵感");
                        }
                    });
                }
            }
        }).start();
    }

    private void showInspirationChips(String raw) {
        View cs = screens.get("chat");
        if (cs == null) return;
        LinearLayout ll = (LinearLayout) cs.findViewById(R.id.ll_inspire_chips);
        final View hsv = cs.findViewById(R.id.hsv_inspire);
        ll.removeAllViews();
        JSONArray arr = Json.extractArray(raw);
        if (arr == null) arr = new JSONArray();
        if (arr.length() == 0) {
            // 兜底：用当日限定话题，至少给用户一个开口的由头
            arr = Daily.topics(currentSession, currentChar == null ? "" : currentChar.optString("id"));
        }
        if (arr.length() == 0) {
            toast("没生成到灵感，再试一次");
            hsv.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            final String txt = arr.optString(i);
            Chip chip = new Chip(this);
            chip.setText(txt);
            chip.setChipBackgroundColor(csl(R.color.surface_variant));
            chip.setTextColor(c(R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    etChatInput.setText(txt);
                    etChatInput.setSelection(txt.length());
                    etChatInput.requestFocus();
                }
            });
            ll.addView(chip);
        }
        hsv.setVisibility(View.VISIBLE);
    }

    private void actionDialog() {
        final String[] actions = {"微微一笑", "皱眉", "叹气", "低头", "沉默", "歪头看你", "抱胸", "翻白眼", "脸红", "笑出声"};
        new MaterialAlertDialogBuilder(this).setTitle("动作")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        insertAction("（" + actions[which] + "）");
                    }
                }).show();
    }

    private void insertAction(String s) {
        int pos = etChatInput.getSelectionStart();
        if (pos < 0) pos = etChatInput.getText().length();
        etChatInput.getText().insert(pos, s);
        etChatInput.setSelection(pos + s.length());
        etChatInput.requestFocus();
    }

    // ---------- chat adapter ----------

    class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            LinearLayout row;
            TextView avatar, action, inner, edited, branch, system;
            ImageView regen, more;
            LinearLayout col, bubbleWrap, bubbleCol, metaRow;
            MaterialCardView bubble;

            VH(View itemView) {
                super(itemView);
                row = (LinearLayout) itemView.findViewById(R.id.row);
                avatar = (TextView) itemView.findViewById(R.id.tv_msg_avatar);
                col = (LinearLayout) itemView.findViewById(R.id.col);
                bubbleWrap = (LinearLayout) itemView.findViewById(R.id.bubble_wrap);
                bubble = (MaterialCardView) itemView.findViewById(R.id.bubble);
                bubbleCol = (LinearLayout) itemView.findViewById(R.id.bubble_col);
                action = (TextView) itemView.findViewById(R.id.tv_action);
                metaRow = (LinearLayout) itemView.findViewById(R.id.meta_row);
                inner = (TextView) itemView.findViewById(R.id.tv_inner);
                edited = (TextView) itemView.findViewById(R.id.tv_edited);
                branch = (TextView) itemView.findViewById(R.id.tv_branch);
                regen = (ImageView) itemView.findViewById(R.id.tv_regen);
                more = (ImageView) itemView.findViewById(R.id.tv_more);
                system = (TextView) itemView.findViewById(R.id.tv_system);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_message, p, false);
            return new VH(v);
        }

        public void onBindViewHolder(VH h, int pos) {
            final JSONObject msg = chatRows.get(pos);
            String id = msg.optString("id", "");
            h.system.setVisibility(View.GONE);
            h.avatar.setVisibility(View.GONE);
            h.metaRow.setVisibility(View.GONE);
            h.action.setVisibility(View.GONE);
            h.inner.setVisibility(View.GONE);
            h.edited.setVisibility(View.GONE);
            h.branch.setVisibility(View.GONE);
            h.regen.setVisibility(View.GONE);
            h.more.setVisibility(View.GONE);

            if ("__typing__".equals(id)) {
                renderTyping(h, msg);
                return;
            }
            String role = msg.optString("role", "");
            if ("system".equals(role)) {
                h.col.setVisibility(View.GONE);
                h.system.setVisibility(View.VISIBLE);
                h.system.setText(msg.optString("content", ""));
                return;
            }
            h.col.setVisibility(View.VISIBLE);
            if (msg.optBoolean("isError", false)) {
                renderBubble(h, msg, "error");
            } else if ("user".equals(role)) {
                renderBubble(h, msg, "user");
            } else {
                renderBubble(h, msg, "assistant");
            }
        }

        private void renderTyping(VH h, JSONObject msg) {
            h.row.setGravity(Gravity.START);
            h.bubbleWrap.setGravity(Gravity.START);
            h.bubble.setCardBackgroundColor(c(R.color.bubble_char));
            h.bubbleCol.removeAllViews();
            TextView tv = new TextView(MainActivity.this);
            tv.setText(msg.optString("content", "正在输入…"));
            tv.setTextSize(14);
            tv.setTextColor(c(R.color.text_secondary));
            h.bubbleCol.addView(tv);
        }

        private void renderBubble(final VH h, final JSONObject msg, String kind) {
            boolean isUser = "user".equals(kind);
            boolean isError = "error".equals(kind);
            h.row.setGravity(isUser ? Gravity.END : Gravity.START);
            h.bubbleWrap.setGravity(isUser ? Gravity.END : Gravity.START);
            h.bubble.setCardBackgroundColor(c(isUser ? R.color.bubble_user : R.color.bubble_char));
            h.bubbleCol.removeAllViews();

            JSONArray bubbles = msg.optJSONArray("bubbles");
            if (bubbles == null || bubbles.length() == 0) {
                bubbles = new JSONArray();
                bubbles.put(jObj("text", msg.optString("content", "")));
            }
            int budget = typewriterBudget(msg);
            for (int i = 0; i < bubbles.length(); i++) {
                JSONObject b = bubbles.optJSONObject(i);
                String btext = b.optString("text", "");
                if (budget >= 0) {
                    if (budget <= 0) break;              // 后面的气泡还没轮到
                    if (btext.length() > budget) btext = btext.substring(0, budget);
                    budget -= btext.length();
                }
                TextView tv = new TextView(MainActivity.this);
                tv.setText(btext);
                tv.setTextSize(15);
                tv.setTextColor(c(isUser ? R.color.bubble_user_text : R.color.bubble_char_text));
                tv.setLineSpacing(2, 1.05f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = 6;
                tv.setLayoutParams(lp);
                h.bubbleCol.addView(tv);
            }

            String actionText = null;
            JSONObject b0 = bubbles.optJSONObject(0);
            if (b0 != null && b0.has("action") && typewriterBudget(msg) < 0) {
                actionText = "（" + b0.optString("action") + "）";
            }
            if (actionText != null) {
                h.action.setText(actionText);
                h.action.setVisibility(View.VISIBLE);
            }

            if (isUser) {
                h.avatar.setVisibility(View.GONE);
                h.bubble.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View vv) {
                        showMsgMenu(msg);
                        return true;
                    }
                });
                return;
            }

            h.bubble.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    skipTypewriter();
                }
            });

            h.avatar.setVisibility(View.VISIBLE);
            String spkName = msg.optString("speakerName", "");
            String spkEmoji = msg.optString("speakerEmoji", "");
            if (spkName.length() > 0 && spkEmoji.length() > 0) {
                h.avatar.setText(spkEmoji);
                h.metaRow.setVisibility(View.VISIBLE);
                if (h.inner.getVisibility() == View.GONE) {
                    h.inner.setVisibility(View.VISIBLE);
                    h.inner.setText(spkName);
                    h.inner.setTag(null);
                    h.inner.setOnClickListener(null);
                }
            } else {
                h.avatar.setText(currentChar.optString("avatarEmoji", "🌸"));
                h.metaRow.setVisibility(View.VISIBLE);
            }

            String innerText = msg.optString("innerVoice", "");
            if (innerText.length() > 0) {
                h.inner.setVisibility(View.VISIBLE);
                h.inner.setText("💭 心声");
                h.inner.setTag(innerText);
                h.inner.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View vv) {
                        if ("💭 心声".equals(h.inner.getText().toString())) {
                            h.inner.setText("💭 " + h.inner.getTag());
                        } else {
                            h.inner.setText("💭 心声");
                        }
                    }
                });
            }
            h.edited.setVisibility(msg.optBoolean("isEdited", false) ? View.VISIBLE : View.GONE);

            JSONArray sibs = siblingsOf(msg);
            if (sibs.length() > 1) {
                final int idx = indexIn(msg, sibs);
                h.branch.setVisibility(View.VISIBLE);
                h.branch.setText("‹ " + (idx + 1) + "/" + sibs.length() + " ›");
                h.branch.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View vv) {
                        switchBranch(msg);
                    }
                });
            } else if (isError) {
                h.branch.setVisibility(View.VISIBLE);
                h.branch.setText("重试");
                h.branch.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View vv) {
                        retryLast();
                    }
                });
            }

            h.regen.setVisibility(isError ? View.GONE : View.VISIBLE);
            h.regen.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    regenerateMessage(msg);
                }
            });
            h.more.setVisibility(View.VISIBLE);
            h.more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    showMsgMenu(msg);
                }
            });
            h.bubble.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View vv) {
                    showMsgMenu(msg);
                    return true;
                }
            });
        }

        public int getItemCount() {
            return chatRows.size();
        }
    }

    // ================= memory =================

    private void initMemory(View v) {
        tvMemHeader = (TextView) v.findViewById(R.id.tv_mem_header);
        tvMemEmpty = (TextView) v.findViewById(R.id.tv_mem_empty);
        rvMemory = (RecyclerView) v.findViewById(R.id.rv_memory);
        rvMemory.setLayoutManager(new LinearLayoutManager(this));
        memoryAdapter = new MemoryAdapter();
        rvMemory.setAdapter(memoryAdapter);
        v.findViewById(R.id.btn_mem_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_mem_summarize).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                summarizeNow();
            }
        });
    }

    private void renderMemory() {
        if (currentSession == null) return;
        buildMemoryRows();
        int total = memoryRows.size();
        memoryAdapter.notifyDataSetChanged();
        int topK = config.optInt("memoryInjectTopK", 8);
        tvMemHeader.setText("共 " + total + " 条 · 注入前 " + topK + " 条");
        tvMemEmpty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
    }

    private void buildMemoryRows() {
        memoryRows.clear();
        JSONArray mems = currentSession.optJSONArray("memories");
        if (mems == null) return;
        List<JSONObject> list = new ArrayList<JSONObject>();
        for (int i = 0; i < mems.length(); i++) list.add(mems.optJSONObject(i));
        Collections.sort(list, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                boolean ap = a.optBoolean("isPinned", false);
                boolean bp = b.optBoolean("isPinned", false);
                if (ap != bp) return ap ? -1 : 1;
                return Double.compare(b.optDouble("weight", 0), a.optDouble("weight", 0));
            }
        });
        boolean fadedSection = false;
        double fade = config.optDouble("memoryFadeThreshold", 2.0);
        for (JSONObject m : list) {
            boolean pinned = m.optBoolean("isPinned", false);
            double w = m.optDouble("weight", 0);
            boolean faded = !pinned && w < fade;
            if (faded && !fadedSection) {
                memoryRows.add("—— 已淡化（不再注入）——");
                fadedSection = true;
            }
            memoryRows.add(m);
        }
    }

    private void summarizeNow() {
        if (currentSession == null) return;
        toast("正在总结记忆…");
        final JSONObject sess = currentSession;
        new Thread(new Runnable() {
            public void run() {
                try {
                    ChatEngine.summarizeMemory(config, sess);
                } catch (Exception e) {
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        toast("总结完成");
                        renderMemory();
                    }
                });
            }
        }).start();
    }

    class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView section, pin, content, weight, edit, pinBtn, delete, restore;
            ProgressBar pb;

            VH(View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                section = (TextView) itemView.findViewById(R.id.tv_mem_section);
                pin = (TextView) itemView.findViewById(R.id.tv_mem_pin);
                content = (TextView) itemView.findViewById(R.id.tv_mem_content);
                weight = (TextView) itemView.findViewById(R.id.tv_mem_weight);
                pb = (ProgressBar) itemView.findViewById(R.id.pb_mem);
                edit = (TextView) itemView.findViewById(R.id.btn_mem_edit);
                pinBtn = (TextView) itemView.findViewById(R.id.btn_mem_pin);
                delete = (TextView) itemView.findViewById(R.id.btn_mem_delete);
                restore = (TextView) itemView.findViewById(R.id.btn_mem_restore);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_memory, p, false);
            return new VH(v);
        }

        public void onBindViewHolder(VH h, int pos) {
            Object item = memoryRows.get(pos);
            if (item instanceof String) {
                h.section.setVisibility(View.VISIBLE);
                h.section.setText((String) item);
                h.card.setVisibility(View.GONE);
                return;
            }
            final JSONObject m = (JSONObject) item;
            h.section.setVisibility(View.GONE);
            h.card.setVisibility(View.VISIBLE);
            boolean pinned = m.optBoolean("isPinned", false);
            double w = m.optDouble("weight", 0);
            double fade = config.optDouble("memoryFadeThreshold", 2.0);
            boolean faded = !pinned && w < fade;
            h.pin.setVisibility(pinned ? View.VISIBLE : View.GONE);
            h.content.setText(m.optString("content", ""));
            h.pb.setProgress((int) Math.min(10, Math.max(0, w * 10)));
            h.weight.setText(String.format("%.1f", w));
            h.card.setAlpha(faded ? 0.45f : 1f);
            h.restore.setVisibility(faded ? View.VISIBLE : View.GONE);
            h.pinBtn.setText(pinned ? "取消置顶" : "置顶");

            h.edit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    editMemory(m);
                }
            });
            h.pinBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    togglePin(m);
                }
            });
            h.delete.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    deleteMemory(m);
                }
            });
            h.restore.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    restoreMemory(m);
                }
            });
        }

        public int getItemCount() {
            return memoryRows.size();
        }
    }

    private void editMemory(final JSONObject m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText et = new EditText(this);
        et.setText(m.optString("content", ""));
        et.setMinLines(2);
        et.setGravity(Gravity.TOP);
        wrap.addView(et);
        new MaterialAlertDialogBuilder(this).setTitle("编辑记忆")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String nt = et.getText().toString().trim();
                        if (nt.length() == 0) return;
                        try {
                            m.put("content", nt);
                            m.put("isEdited", true);
                            Store.saveSession(currentSession);
                        } catch (Exception e) {
                        }
                        renderMemory();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void togglePin(final JSONObject m) {
        try {
            m.put("isPinned", !m.optBoolean("isPinned", false));
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        renderMemory();
    }

    private void deleteMemory(final JSONObject m) {
        JSONArray mems = currentSession.optJSONArray("memories");
        for (int i = mems.length() - 1; i >= 0; i--) {
            if (mems.optJSONObject(i).optString("id").equals(m.optString("id"))) {
                mems.remove(i);
                break;
            }
        }
        Store.saveSession(currentSession);
        renderMemory();
    }

    private void restoreMemory(final JSONObject m) {
        try {
            m.put("weight", 5.0);
            m.put("isStale", false);
            Store.saveSession(currentSession);
        } catch (Exception e) {
        }
        renderMemory();
    }

    // ================= vars =================

    private void initVars(View v) {
        llVars = (LinearLayout) v.findViewById(R.id.ll_vars);
        v.findViewById(R.id.btn_vars_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_add_var).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                addVarDialog();
            }
        });
    }

    private void renderVars() {
        if (currentSession == null) return;
        llVars.removeAllViews();
        JSONObject vars = currentSession.optJSONObject("variables");
        double aff = vars != null ? vars.optDouble("affection", 0) : 0;
        addVarRow("好感度", Double.valueOf(aff), -100, 200, true);
        if (vars != null) {
            JSONArray names = vars.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String n = names.optString(i);
                    if ("affection".equals(n)) continue;
                    addVarRow(n, vars.opt(n), 0, 100, false);
                }
            }
        }
    }

    private void addVarRow(String name, final Object value, final int min, final int max, boolean affection) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(clp);
        card.setRadius(12);
        card.setCardElevation(0);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 16, 16, 16);
        card.addView(col);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(16);
        tvName.setTextColor(c(R.color.text_primary));
        col.addView(tvName);

        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            int hi = max > min ? max : 100;
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8);
            plp.topMargin = 10;
            pb.setLayoutParams(plp);
            pb.setMax(hi - min);
            pb.setProgress((int) Math.max(0, Math.min(hi - min, v - min)));
            pb.setProgressTintList(csl(R.color.accent));
            col.addView(pb);

            TextView tvVal = new TextView(this);
            tvVal.setTextSize(13);
            tvVal.setTextColor(c(R.color.text_secondary));
            tvVal.setText((affection ? "♡ " : "") + (int) v + (affection ? " / " + hi : ""));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vlp.topMargin = 4;
            tvVal.setLayoutParams(vlp);
            col.addView(tvVal);

            if (affection) {
                TextView tvStage = new TextView(this);
                tvStage.setTextSize(12);
                tvStage.setTextColor(c(R.color.text_tertiary));
                tvStage.setText(ChatEngine.stageDescription(v));
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = 6;
                tvStage.setLayoutParams(slp);
                col.addView(tvStage);
            }
        } else {
            TextView tvVal = new TextView(this);
            tvVal.setTextSize(14);
            tvVal.setTextColor(c(R.color.text_primary));
            tvVal.setText(String.valueOf(value));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vlp.topMargin = 6;
            tvVal.setLayoutParams(vlp);
            col.addView(tvVal);
        }

        TextView btnAdj = new TextView(this);
        btnAdj.setText("调整");
        btnAdj.setTextSize(13);
        btnAdj.setTextColor(c(R.color.brand));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = 10;
        btnAdj.setLayoutParams(alp);
        final String fname = name;
        btnAdj.setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                adjustVarDialog(fname, value, min, max);
            }
        });
        col.addView(btnAdj);
        llVars.addView(card);
    }

    private void adjustVarDialog(final String name, Object cur, final int min, final int max) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(String.valueOf(cur));
        wrap.addView(et);
        new MaterialAlertDialogBuilder(this).setTitle("调整「" + name + "」")
                .setView(wrap)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            double nv = Double.parseDouble(et.getText().toString().trim());
                            if (max > min) nv = Math.max(min, Math.min(max, nv));
                            JSONObject vars = currentSession.optJSONObject("variables");
                            if (vars == null) {
                                vars = new JSONObject();
                                currentSession.put("variables", vars);
                            }
                            vars.put(name, Math.round(nv * 10) / 10.0);
                            Store.saveSession(currentSession);
                            renderVars();
                            updateAffectionHeader();
                        } catch (Exception e) {
                            toast("请输入数字");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addVarDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText etName = new EditText(this);
        etName.setHint("变量名（如：信任值）");
        wrap.addView(etName);
        final EditText etVal = new EditText(this);
        etVal.setHint("初始值（数字）");
        etVal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = 12;
        etVal.setLayoutParams(vlp);
        wrap.addView(etVal);
        new MaterialAlertDialogBuilder(this).setTitle("添加自定义变量")
                .setView(wrap)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = etName.getText().toString().trim();
                        if (n.length() == 0) {
                            toast("请填写变量名");
                            return;
                        }
                        try {
                            double v = etVal.getText().toString().trim().length() > 0 ? Double.parseDouble(etVal.getText().toString().trim()) : 0;
                            JSONObject vars = currentSession.optJSONObject("variables");
                            if (vars == null) {
                                vars = new JSONObject();
                                currentSession.put("variables", vars);
                            }
                            vars.put(n, v);
                            Store.saveSession(currentSession);
                            renderVars();
                        } catch (Exception e) {
                            toast("初始值需为数字");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= commands =================

    private void initCommands(View v) {
        RecyclerView rv = (RecyclerView) v.findViewById(R.id.rv_commands);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        CommandAdapter ad = new CommandAdapter();
        rv.setAdapter(ad);
        v.findViewById(R.id.btn_cmd_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_cmd_edit).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                commandEditor();
            }
        });
    }

    private void renderCommands() {
        commands = mergedCommands();
        View cs = screens.get("commands");
        if (cs == null) return;
        RecyclerView rv = (RecyclerView) cs.findViewById(R.id.rv_commands);
        if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
    }

    private JSONArray mergedCommands() {
        JSONArray merged = new JSONArray();
        JSONArray builtin = builtinCommands();
        for (int i = 0; i < builtin.length(); i++) merged.put(builtin.opt(i));
        JSONArray custom = Store.loadCommands();
        for (int i = 0; i < custom.length(); i++) {
            JSONObject c = custom.optJSONObject(i);
            if (c != null) {
                try {
                    c.put("isCustom", true);
                } catch (Exception e) {
                }
                merged.put(c);
            }
        }
        return merged;
    }

    class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView icon, label, tagline, lock;

            VH(View itemView) {
                super(itemView);
                icon = (TextView) itemView.findViewById(R.id.tv_cmd_icon);
                label = (TextView) itemView.findViewById(R.id.tv_cmd_label);
                tagline = (TextView) itemView.findViewById(R.id.tv_cmd_tagline);
                lock = (TextView) itemView.findViewById(R.id.tv_cmd_lock);
            }
        }

        public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = getLayoutInflater().inflate(R.layout.item_command, p, false);
            return new VH(v);
        }

        public void onBindViewHolder(VH h, int pos) {
            final JSONObject cmd = commands.optJSONObject(pos);
            h.icon.setText(cmd.optString("icon", "✨"));
            h.label.setText(cmd.optString("label", ""));
            h.tagline.setText(cmd.optString("tagline", ""));
            int turns = currentSession == null || currentSession.optJSONArray("messages") == null ? 0 : currentSession.optJSONArray("messages").length();
            int minTurns = cmd.optInt("minTurns", 5);
            boolean locked = turns < minTurns;
            h.lock.setVisibility(locked ? View.VISIBLE : View.GONE);
            h.lock.setText("🔒 还需 " + (minTurns - turns) + " 轮");
            h.itemView.setAlpha(locked ? 0.5f : 1f);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    runCommand(cmd);
                }
            });
        }

        public int getItemCount() {
            return commands.length();
        }
    }

    private void runCommand(final JSONObject cmd) {
        if (currentSession == null || streaming) return;
        if (config.optString("apiKey", "").length() == 0) {
            toast("请先配置 API Key");
            return;
        }
        int turns = currentSession.optJSONArray("messages") == null ? 0 : currentSession.optJSONArray("messages").length();
        int minTurns = cmd.optInt("minTurns", 5);
        if (turns < minTurns) {
            toast("再多聊 " + (minTurns - turns) + " 轮解锁");
            return;
        }
        streaming = true;
        btnSend.setEnabled(false);
        final JSONObject cfg = config;
        final JSONObject sess = currentSession;
        final JSONObject ch = currentChar;
        final JSONObject story = storyOf(sess);
        final JSONObject fcmd = cmd;
        String base = ChatEngine.buildSystemPrompt(cfg, ch, sess, story);
        String prompt = replaceCmdTokens(cmd.optString("promptOverride", ""), ch);
        final String system = base + "\n\n" + prompt;
        final JSONArray history = ChatEngine.historyMessages(sess, 8);
        addTypingRow();
        new Thread(new Runnable() {
            public void run() {
                Api.Callback cb = new Api.Callback() {
                    public void onChunk(String t) {
                    }

                    public void onDone(final String full) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                                streaming = false;
                                btnSend.setEnabled(true);
                                boolean toRecords = fcmd.optBoolean("saveToRecords", false);
                                if (toRecords) {
                                    addRecord(fcmd, full);
                                    toast("已存入入戏记录");
                                    showScreen("chat");
                                } else {
                                    insertCommandMessage(fcmd, full);
                                }
                            }
                        });
                    }

                    public void onError(final String msg) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                removeTyping();
                                streaming = false;
                                btnSend.setEnabled(true);
                                toast("指令生成失败：" + msg);
                            }
                        });
                    }
                };
                try {
                    Api.callModel(cfg, system, history, ChatEngine.modelOf(cfg, "command"), 2048, false, cb);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            removeTyping();
                            streaming = false;
                            btnSend.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private String replaceCmdTokens(String prompt, JSONObject ch) {
        String s = prompt == null ? "" : prompt;
        s = s.replace("@{char}", ch.optString("name", "角色"));
        s = s.replace("@{player}", "你");
        return s;
    }

    private void insertCommandMessage(JSONObject cmd, String content) {
        String label = cmd.optString("label", "");
        try {
            JSONObject msg = new JSONObject();
            msg.put("id", Store.newId());
            msg.put("parentId", currentSession.optString("currentLeafId", ""));
            msg.put("role", "assistant");
            msg.put("content", "【" + label + "】\n" + content.trim());
            msg.put("scene", "command");
            msg.put("commandLabel", label);
            msg.put("timestamp", System.currentTimeMillis());
            currentSession.optJSONArray("messages").put(msg);
            currentSession.put("currentLeafId", msg.optString("id"));
            currentSession.put("updatedAt", System.currentTimeMillis());
            Store.saveSession(currentSession);
        } catch (Exception e) {
            AppLogger.e("CHAT", "command insert failed", e);
        }
        buildChatRows();
        chatAdapter.notifyDataSetChanged();
        scrollChat();
    }

    private void addRecord(JSONObject cmd, String content) {
        try {
            JSONObject r = new JSONObject();
            r.put("id", Store.newId());
            r.put("sessionId", currentSession.optString("id"));
            r.put("commandId", cmd.optString("id"));
            r.put("commandLabel", cmd.optString("label", ""));
            r.put("content", content.trim());
            r.put("createdAt", System.currentTimeMillis());
            records.put(r);
            Store.saveRecords(records);
        } catch (Exception e) {
        }
    }

    private void showRecordsDialog() {
        if (records.length() == 0) {
            toast("还没有入戏记录");
            return;
        }
        String[] labels = new String[records.length()];
        for (int i = 0; i < records.length(); i++) {
            JSONObject r = records.optJSONObject(i);
            labels[i] = r.optString("commandLabel", "") + " · " + relTime(r.optLong("createdAt", 0));
        }
        new MaterialAlertDialogBuilder(this).setTitle("入戏记录")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        showRecordContent(records.optJSONObject(which));
                    }
                }).show();
    }

    private void showRecordContent(final JSONObject r) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(r.optString("content", ""));
        tv.setTextSize(14);
        tv.setPadding(48, 24, 48, 24);
        sv.addView(tv);
        new MaterialAlertDialogBuilder(this).setTitle(r.optString("commandLabel", ""))
                .setView(sv)
                .setPositiveButton("关闭", null)
                .setNegativeButton("复制", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        copy(r.optString("content", ""));
                    }
                })
                .show();
    }

    // ================= story (剧情图) =================

    private void initStory(View v) {
        etStoryName = (EditText) v.findViewById(R.id.et_story_name);
        etStoryBg = (EditText) v.findViewById(R.id.et_story_bg);
        etStorySituation = (EditText) v.findViewById(R.id.et_story_situation);
        etStoryCallme = (EditText) v.findViewById(R.id.et_story_callme);
        etStoryUserSetting = (EditText) v.findViewById(R.id.et_story_usersetting);
        spStoryType = (Spinner) v.findViewById(R.id.sp_story_type);
        llStoryNodes = (LinearLayout) v.findViewById(R.id.ll_story_nodes);
        llStoryEndings = (LinearLayout) v.findViewById(R.id.ll_story_endings);

        String[] types = {"轻剧情（线性）", "完整剧情（多分支）"};
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStoryType.setAdapter(ad);

        v.findViewById(R.id.btn_story_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_story_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                saveStory();
            }
        });
        v.findViewById(R.id.btn_story_add_node).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                addNode();
            }
        });
        v.findViewById(R.id.btn_story_ai_world).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                aiWorldDialog();
            }
        });
        v.findViewById(R.id.btn_story_ai_plot).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                aiPlotDialog();
            }
        });
        v.findViewById(R.id.btn_story_json).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                editStoryJson();
            }
        });
        v.findViewById(R.id.btn_story_import_char).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                storyFromCharSituation();
            }
        });
    }

    private void openStoryEdit() {
        if (currentSession == null) {
            toast("请先进入一个角色的聊天");
            return;
        }
        JSONObject st = currentSession.optJSONObject("story");
        if (st == null) {
            st = Store.newStory(currentChar != null ? currentChar.optString("name", "") + "的故事" : "新故事");
            try {
                currentSession.put("story", st);
            } catch (Exception e) {
            }
        }
        editingStory = st;
        storyDirty = false;
        showScreen("story");
    }

    private void populateStory() {
        if (editingStory == null) return;
        etStoryName.setText(editingStory.optString("name", ""));
        etStoryBg.setText(editingStory.optString("globalBackground", ""));
        etStorySituation.setText(editingStory.optString("situation", ""));
        etStoryCallme.setText(editingStory.optString("callMe", "你"));
        etStoryUserSetting.setText(editingStory.optString("userSetting", ""));
        spStoryType.setSelection("full".equals(editingStory.optString("type", "light")) ? 1 : 0);
        refreshStoryNodes();
    }

    private void refreshStoryNodes() {
        if (editingStory == null) return;
        llStoryNodes.removeAllViews();
        JSONArray nodes = editingStory.optJSONArray("nodes");
        if (nodes == null) {
            nodes = new JSONArray();
            try {
                editingStory.put("nodes", nodes);
            } catch (Exception e) {
            }
        }
        String initialId = editingStory.optString("initialNodeId", "");
        if (nodes.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("还没有节点 —— 点下方「添加节点」开始搭建剧情");
            tv.setTextSize(13);
            tv.setTextColor(c(R.color.text_tertiary));
            tv.setPadding(4, 12, 4, 12);
            llStoryNodes.addView(tv);
        }
        for (int i = 0; i < nodes.length(); i++) {
            final JSONObject n = nodes.optJSONObject(i);
            if (n == null) continue;
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(clp);
            card.setRadius(12);
            card.setCardElevation(0);
            card.setStrokeColor(c(R.color.outline));
            card.setStrokeWidth(1);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(14, 12, 14, 12);
            card.addView(col);

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER_VERTICAL);
            col.addView(row1);

            TextView tvBadge = new TextView(this);
            String type = n.optString("type", "normal");
            tvBadge.setText(StoryEngine.nodeTypeName(type));
            tvBadge.setTextSize(11);
            tvBadge.setTextColor(c(R.color.white));
            tvBadge.setPadding(8, 2, 8, 2);
            int bg = c("start".equals(type) ? R.color.node_start : "ending".equals(type) ? R.color.node_ending : "merge".equals(type) ? R.color.node_merge : R.color.node_normal);
            tvBadge.setBackgroundColor(bg);
            row1.addView(tvBadge);

            TextView tvName = new TextView(this);
            tvName.setText(n.optString("name", "节点"));
            tvName.setTextSize(15);
            tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvName.setTextColor(c(R.color.text_primary));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = 10;
            tvName.setLayoutParams(nlp);
            row1.addView(tvName);

            if (n.optString("id", "").equals(initialId)) {
                TextView tvStart = new TextView(this);
                tvStart.setText("▶ 起点");
                tvStart.setTextSize(11);
                tvStart.setTextColor(c(R.color.node_start));
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.leftMargin = 10;
                tvStart.setLayoutParams(slp);
                row1.addView(tvStart);
            }

            String text = n.optString("text", "");
            if (text.length() > 0) {
                TextView tvText = new TextView(this);
                tvText.setText(text.length() > 40 ? text.substring(0, 40) + "…" : text);
                tvText.setTextSize(13);
                tvText.setTextColor(c(R.color.text_primary));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.topMargin = 6;
                tvText.setLayoutParams(tlp);
                col.addView(tvText);
            }

            JSONArray choices = n.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                TextView tvC = new TextView(this);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < choices.length(); j++) {
                    JSONObject c = choices.optJSONObject(j);
                    if (j > 0) sb.append("  ·  ");
                    sb.append(c.optString("text", ""));
                }
                tvC.setText("➜ 选项：" + sb.toString());
                tvC.setTextSize(12);
                tvC.setTextColor(c(R.color.edge_choice));
                LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp2.topMargin = 4;
                tvC.setLayoutParams(clp2);
                col.addView(tvC);
            }

            JSONArray edges = n.optJSONArray("edges");
            if (edges != null && edges.length() > 0) {
                TextView tvE = new TextView(this);
                tvE.setText("⇢ 边 x" + edges.length());
                tvE.setTextSize(12);
                tvE.setTextColor(c(R.color.edge_auto));
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                elp.topMargin = 2;
                tvE.setLayoutParams(elp);
                col.addView(tvE);
            }

            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    editNode(n);
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View vv) {
                    nodeMenu(n);
                    return true;
                }
            });
            llStoryNodes.addView(card);
        }
        refreshStoryEndings();
    }

    private void refreshStoryEndings() {
        if (editingStory == null) return;
        llStoryEndings.removeAllViews();
        JSONArray endings = StoryEngine.endingNodes(editingStory);
        if (endings.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("暂无结局节点");
            tv.setTextSize(13);
            tv.setTextColor(c(R.color.text_tertiary));
            tv.setPadding(4, 8, 4, 8);
            llStoryEndings.addView(tv);
            return;
        }
        for (int i = 0; i < endings.length(); i++) {
            final JSONObject n = endings.optJSONObject(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 8, 8, 8);
            TextView tvIcon = new TextView(this);
            tvIcon.setText(n.optString("endingIcon", "🏁"));
            tvIcon.setTextSize(18);
            row.addView(tvIcon);
            TextView tvInfo = new TextView(this);
            String title = n.optString("endingTitle", "");
            tvInfo.setText((title.length() > 0 ? title : n.optString("name", "结局")) + " · " + n.optString("endingDescription", ""));
            tvInfo.setTextSize(13);
            tvInfo.setTextColor(c(R.color.text_primary));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            ilp.leftMargin = 10;
            tvInfo.setLayoutParams(ilp);
            row.addView(tvInfo);
            llStoryEndings.addView(row);
        }
    }

    private void addNode() {
        if (editingStory == null) return;
        final JSONObject n = Store.newNode("节点 " + (editingStory.optJSONArray("nodes").length() + 1));
        try {
            editingStory.optJSONArray("nodes").put(n);
        } catch (Exception e) {
        }
        if (editingStory.optString("initialNodeId", "").length() == 0) {
            try {
                editingStory.put("initialNodeId", n.optString("id"));
            } catch (Exception e) {
            }
        }
        refreshStoryNodes();
        editNode(n);
    }

    private void nodeMenu(final JSONObject n) {
        final String[] items = {"设为起点", "编辑选项边", "编辑条件/关键词边", "编辑变量赋值", "删除节点"};
        new MaterialAlertDialogBuilder(this).setTitle(n.optString("name", "节点"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            try {
                                editingStory.put("initialNodeId", n.optString("id"));
                            } catch (Exception e) {
                            }
                            refreshStoryNodes();
                        } else if (which == 1) {
                            editChoices(n);
                        } else if (which == 2) {
                            editEdges(n);
                        } else if (which == 3) {
                            editAssignments(n);
                        } else if (which == 4) {
                            JSONArray nodes = editingStory.optJSONArray("nodes");
                            for (int i = nodes.length() - 1; i >= 0; i--) {
                                if (nodes.optJSONObject(i).optString("id").equals(n.optString("id"))) {
                                    nodes.remove(i);
                                    break;
                                }
                            }
                            refreshStoryNodes();
                        }
                    }
                }).show();
    }

    private void editNode(final JSONObject n) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);

        final EditText etName = new EditText(this);
        etName.setHint("节点名");
        etName.setText(n.optString("name", ""));
        wrap.addView(etName);

        final Spinner spType = new Spinner(this);
        String[] types = {"普通", "开始", "结局", "合流"};
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(ad);
        String t = n.optString("type", "normal");
        spType.setSelection("start".equals(t) ? 1 : "ending".equals(t) ? 2 : "merge".equals(t) ? 3 : 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = 8;
        spType.setLayoutParams(slp);
        wrap.addView(spType);

        final EditText etText = new EditText(this);
        etText.setHint("剧情台词（进入节点时 Ta 说的话，可留空）");
        etText.setText(n.optString("text", ""));
        etText.setMinLines(2);
        etText.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = 8;
        etText.setLayoutParams(tlp);
        wrap.addView(etText);

        final EditText etInstr = new EditText(this);
        etInstr.setHint("自由对话时的剧情指引（可选）");
        etInstr.setText(n.optString("instruction", ""));
        etInstr.setMinLines(2);
        etInstr.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = 8;
        etInstr.setLayoutParams(ilp);
        wrap.addView(etInstr);

        final EditText etEndTitle = new EditText(this);
        etEndTitle.setHint("结局标题（结局节点时填）");
        etEndTitle.setText(n.optString("endingTitle", ""));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = 8;
        etEndTitle.setLayoutParams(elp);
        wrap.addView(etEndTitle);

        final EditText etEndDesc = new EditText(this);
        etEndDesc.setHint("结局描述");
        etEndDesc.setText(n.optString("endingDescription", ""));
        LinearLayout.LayoutParams ddp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ddp.topMargin = 8;
        etEndDesc.setLayoutParams(ddp);
        wrap.addView(etEndDesc);

        new MaterialAlertDialogBuilder(this).setTitle("编辑节点")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            n.put("name", etName.getText().toString().trim());
                            String nt = "normal";
                            int sel = spType.getSelectedItemPosition();
                            if (sel == 1) nt = "start";
                            else if (sel == 2) nt = "ending";
                            else if (sel == 3) nt = "merge";
                            n.put("type", nt);
                            n.put("text", etText.getText().toString().trim());
                            n.put("instruction", etInstr.getText().toString().trim());
                            n.put("endingTitle", etEndTitle.getText().toString().trim());
                            n.put("endingDescription", etEndDesc.getText().toString().trim());
                        } catch (Exception e) {
                        }
                        refreshStoryNodes();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 选项边 ----------

    private void editChoices(final JSONObject n) {
        final JSONArray choices = n.optJSONArray("choices");
        final String[] labels = new String[choices.length()];
        for (int i = 0; i < choices.length(); i++) {
            JSONObject c = choices.optJSONObject(i);
            labels[i] = c.optString("text", "") + (c.optString("condition", "").length() > 0 ? "  [" + c.optString("condition") + "]" : "");
        }
        new MaterialAlertDialogBuilder(this).setTitle("选项边（" + n.optString("name", "") + "）")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        choiceMenu(n, choices.optJSONObject(which));
                    }
                })
                .setNeutralButton("＋ 添加", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        choiceEdit(n, null);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void choiceMenu(final JSONObject n, final JSONObject c) {
        final String[] items = {"编辑", "删除"};
        new MaterialAlertDialogBuilder(this).setTitle(c.optString("text", "选项"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            choiceEdit(n, c);
                        } else {
                            n.optJSONArray("choices").remove(indexOfChoice(n, c));
                            refreshStoryNodes();
                        }
                    }
                }).show();
    }

    private int indexOfChoice(JSONObject n, JSONObject c) {
        JSONArray arr = n.optJSONArray("choices");
        for (int i = 0; i < arr.length(); i++) {
            if (arr.optJSONObject(i).optString("text").equals(c.optString("text"))) return i;
        }
        return 0;
    }

    private void choiceEdit(final JSONObject n, final JSONObject existing) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText etText = new EditText(this);
        etText.setHint("选项文字（用户点击后进入下一节点）");
        etText.setText(existing != null ? existing.optString("text", "") : "");
        wrap.addView(etText);
        final EditText etNext = new EditText(this);
        etNext.setHint("下一节点名（须与节点名一致）");
        etNext.setText(existing != null ? nextNodeName(editingStory, existing.optString("next", "")) : "");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = 8;
        etNext.setLayoutParams(nlp);
        wrap.addView(etNext);
        final EditText etCond = new EditText(this);
        etCond.setHint("条件（可选，如 affection>=30）");
        etCond.setText(existing != null ? existing.optString("condition", "") : "");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = 8;
        etCond.setLayoutParams(clp);
        wrap.addView(etCond);
        new MaterialAlertDialogBuilder(this).setTitle(existing != null ? "编辑选项" : "添加选项")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String text = etText.getText().toString().trim();
                        if (text.length() == 0) {
                            toast("请填写选项文字");
                            return;
                        }
                        String nextName = etNext.getText().toString().trim();
                        String nextId = nodeIdByName(editingStory, nextName);
                        if (nextId == null) {
                            toast("找不到节点「" + nextName + "」，请先创建");
                            return;
                        }
                        try {
                            if (existing == null) {
                                JSONObject c = new JSONObject();
                                c.put("text", text);
                                c.put("next", nextId);
                                c.put("condition", etCond.getText().toString().trim());
                                n.optJSONArray("choices").put(c);
                            } else {
                                existing.put("text", text);
                                existing.put("next", nextId);
                                existing.put("condition", etCond.getText().toString().trim());
                            }
                        } catch (Exception e) {
                        }
                        refreshStoryNodes();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String nodeIdByName(JSONObject story, String name) {
        if (name == null || name.length() == 0) return null;
        JSONArray nodes = story.optJSONArray("nodes");
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (name.equals(n.optString("name"))) return n.optString("id");
        }
        return null;
    }

    private String nextNodeName(JSONObject story, String id) {
        JSONObject n = StoryEngine.nodeOf(story, id);
        return n != null ? n.optString("name", "") : "";
    }

    // ---------- 条件/关键词边 ----------

    private void editEdges(final JSONObject n) {
        final JSONArray edges = n.optJSONArray("edges");
        final String[] labels = new String[edges.length()];
        for (int i = 0; i < edges.length(); i++) {
            JSONObject e = edges.optJSONObject(i);
            String type = e.optString("type", "auto");
            String t = "auto".equals(type) ? "自动" : "keyword".equals(type) ? "关键词" : "条件";
            labels[i] = t + " → " + nextNodeName(editingStory, e.optString("next", ""));
        }
        new MaterialAlertDialogBuilder(this).setTitle("推进边（" + n.optString("name", "") + "）")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        edgeMenu(n, edges.optJSONObject(which));
                    }
                })
                .setNeutralButton("＋ 添加", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        edgeEdit(n, null);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void edgeMenu(final JSONObject n, final JSONObject e) {
        final String[] items = {"编辑", "删除"};
        new MaterialAlertDialogBuilder(this).setTitle("推进边")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            edgeEdit(n, e);
                        } else {
                            n.optJSONArray("edges").remove(indexOfEdge(n, e));
                            refreshStoryNodes();
                        }
                    }
                }).show();
    }

    private int indexOfEdge(JSONObject n, JSONObject e) {
        JSONArray arr = n.optJSONArray("edges");
        for (int i = 0; i < arr.length(); i++) {
            if (arr.optJSONObject(i).optString("next").equals(e.optString("next")) && arr.optJSONObject(i).optString("type").equals(e.optString("type"))) return i;
        }
        return 0;
    }

    private void edgeEdit(final JSONObject n, final JSONObject existing) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final Spinner spType = new Spinner(this);
        String[] types = {"自动（对话 N 轮后）", "关键词（命中即走）", "条件（变量满足即走）"};
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(ad);
        String t = existing != null ? existing.optString("type", "auto") : "auto";
        spType.setSelection("keyword".equals(t) ? 1 : "condition".equals(t) ? 2 : 0);
        wrap.addView(spType);
        final EditText etNext = new EditText(this);
        etNext.setHint("下一节点名");
        etNext.setText(existing != null ? nextNodeName(editingStory, existing.optString("next", "")) : "");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = 8;
        etNext.setLayoutParams(nlp);
        wrap.addView(etNext);
        final EditText etCond = new EditText(this);
        etCond.setHint("条件（如 affection>=30）或关键词（逗号分隔）");
        etCond.setText(existing != null ? ("keyword".equals(existing.optString("type")) ? joinKeywords(existing.optJSONArray("keywords")) : existing.optString("condition", "")) : "");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = 8;
        etCond.setLayoutParams(clp);
        wrap.addView(etCond);
        final EditText etAfter = new EditText(this);
        etAfter.setHint("自动边：对话轮数（默认 1）");
        etAfter.setText(existing != null ? String.valueOf(existing.optInt("afterTurns", 1)) : "1");
        etAfter.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = 8;
        etAfter.setLayoutParams(alp);
        wrap.addView(etAfter);
        new MaterialAlertDialogBuilder(this).setTitle(existing != null ? "编辑推进边" : "添加推进边")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String nextName = etNext.getText().toString().trim();
                        String nextId = nodeIdByName(editingStory, nextName);
                        if (nextId == null) {
                            toast("找不到节点「" + nextName + "」");
                            return;
                        }
                        try {
                            String nt = "auto";
                            int sel = spType.getSelectedItemPosition();
                            if (sel == 1) nt = "keyword";
                            else if (sel == 2) nt = "condition";
                            JSONObject e = existing != null ? existing : new JSONObject();
                            e.put("type", nt);
                            e.put("next", nextId);
                            String cond = etCond.getText().toString().trim();
                            if ("keyword".equals(nt)) {
                                e.put("keywords", splitKeywords(cond));
                            } else {
                                e.put("condition", cond);
                            }
                            e.put("afterTurns", parseInt(etAfter, 1));
                            if (existing == null) n.optJSONArray("edges").put(e);
                        } catch (Exception e2) {
                        }
                        refreshStoryNodes();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String joinKeywords(JSONArray kws) {
        if (kws == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kws.length(); i++) {
            if (i > 0) sb.append(",");
            sb.append(kws.optString(i));
        }
        return sb.toString();
    }

    private JSONArray splitKeywords(String s) {
        JSONArray arr = new JSONArray();
        if (s == null) return arr;
        String[] parts = s.split("[,，]");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.length() > 0) arr.put(p);
        }
        return arr;
    }

    // ---------- 变量赋值 ----------

    private void editAssignments(final JSONObject n) {
        final JSONArray assigns = n.optJSONArray("assignments");
        final String[] labels = new String[assigns.length()];
        for (int i = 0; i < assigns.length(); i++) {
            JSONObject a = assigns.optJSONObject(i);
            labels[i] = a.optString("name", "") + " " + a.optString("value", "");
        }
        new MaterialAlertDialogBuilder(this).setTitle("进入节点时赋值")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        assignMenu(n, assigns.optJSONObject(which));
                    }
                })
                .setNeutralButton("＋ 添加", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        assignEdit(n, null);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void assignMenu(final JSONObject n, final JSONObject a) {
        final String[] items = {"编辑", "删除"};
        new MaterialAlertDialogBuilder(this).setTitle(a.optString("name", ""))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            assignEdit(n, a);
                        } else {
                            n.optJSONArray("assignments").remove(indexOfAssign(n, a));
                            refreshStoryNodes();
                        }
                    }
                }).show();
    }

    private int indexOfAssign(JSONObject n, JSONObject a) {
        JSONArray arr = n.optJSONArray("assignments");
        for (int i = 0; i < arr.length(); i++) {
            if (arr.optJSONObject(i).optString("name").equals(a.optString("name"))) return i;
        }
        return 0;
    }

    private void assignEdit(final JSONObject n, final JSONObject existing) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText etName = new EditText(this);
        etName.setHint("变量名（如 affection）");
        etName.setText(existing != null ? existing.optString("name", "") : "");
        wrap.addView(etName);
        final EditText etVal = new EditText(this);
        etVal.setHint("值：+5 / -3 / 10");
        etVal.setText(existing != null ? existing.optString("value", "") : "");
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = 8;
        etVal.setLayoutParams(vlp);
        wrap.addView(etVal);
        new MaterialAlertDialogBuilder(this).setTitle(existing != null ? "编辑赋值" : "添加赋值")
                .setView(wrap)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String name = etName.getText().toString().trim();
                        if (name.length() == 0) {
                            toast("请填写变量名");
                            return;
                        }
                        try {
                            if (existing == null) {
                                JSONObject a = new JSONObject();
                                a.put("name", name);
                                a.put("value", etVal.getText().toString().trim());
                                n.optJSONArray("assignments").put(a);
                            } else {
                                existing.put("name", name);
                                existing.put("value", etVal.getText().toString().trim());
                            }
                        } catch (Exception e) {
                        }
                        refreshStoryNodes();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 保存 / JSON / 生成 ----------

    private void saveStory() {
        if (editingStory == null) return;
        try {
            editingStory.put("name", etStoryName.getText().toString().trim());
            editingStory.put("type", spStoryType.getSelectedItemPosition() == 1 ? "full" : "light");
            editingStory.put("globalBackground", etStoryBg.getText().toString().trim());
            editingStory.put("situation", etStorySituation.getText().toString().trim());
            editingStory.put("callMe", etStoryCallme.getText().toString().trim().length() > 0 ? etStoryCallme.getText().toString().trim() : "你");
            editingStory.put("userSetting", etStoryUserSetting.getText().toString().trim());
            if (currentChar != null) {
                JSONArray cids = editingStory.optJSONArray("characterIds");
                if (cids == null) {
                    cids = new JSONArray();
                    editingStory.put("characterIds", cids);
                }
                boolean has = false;
                for (int i = 0; i < cids.length(); i++) {
                    if (currentChar.optString("id").equals(cids.optString(i))) {
                        has = true;
                        break;
                    }
                }
                if (!has) cids.put(currentChar.optString("id"));
            }
            Store.upsertStory(editingStory);
            try {
                currentSession.put("story", editingStory);
                currentSession.put("updatedAt", System.currentTimeMillis());
                Store.saveSession(currentSession);
            } catch (Exception e) {
            }
        } catch (Exception e) {
            AppLogger.e("STORY", "save failed", e);
        }
        storyDirty = false;
        toast("剧情已保存");
    }

    private void editStoryJson() {
        if (editingStory == null) return;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        final EditText et = new EditText(this);
        et.setText(editingStory.toString());
        et.setMinLines(12);
        et.setGravity(Gravity.TOP);
        et.setTextSize(11);
        et.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(et);
        wrap.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 360));
        new MaterialAlertDialogBuilder(this).setTitle("剧情 JSON 高级编辑")
                .setView(wrap)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            JSONObject parsed = new JSONObject(et.getText().toString());
                            if (!parsed.has("id")) parsed.put("id", editingStory.optString("id"));
                            editingStory = parsed;
                            try {
                                currentSession.put("story", parsed);
                            } catch (Exception e) {
                            }
                            refreshStoryNodes();
                            populateStory();
                            toast("已应用 JSON");
                        } catch (Exception e) {
                            toast("JSON 解析失败");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void storyFromCharSituation() {
        if (editingStory == null || currentChar == null) return;
        String sit = currentChar.optString("situation", "");
        String greeting = currentChar.optString("greeting", "");
        try {
            JSONObject n1 = Store.newNode("开场");
            n1.put("type", "start");
            n1.put("text", greeting.length() > 0 ? greeting : "（故事开始）");
            n1.put("instruction", sit.length() > 0 ? "情境：" + sit + "。在情境中自然地与用户互动，等待剧情推进。" : "自然地与用户互动。");
            JSONObject n2 = Store.newNode("发展");
            n2.put("type", "normal");
            n2.put("text", "");
            n2.put("instruction", sit.length() > 0 ? "情境：" + sit + "。继续推进情节，制造一点小转折或惊喜。" : "继续推进情节。");
            JSONObject n3 = Store.newNode("结局");
            n3.put("type", "ending");
            n3.put("endingTitle", "初见");
            n3.put("endingDescription", "这一次相遇，留下了印记");
            n3.put("endingIcon", "🌅");
            n3.put("text", "（这段故事暂时告一段落，你们相视一笑。）");
            JSONArray nodes = new JSONArray();
            nodes.put(n1);
            nodes.put(n2);
            nodes.put(n3);
            editingStory.put("nodes", nodes);
            editingStory.put("initialNodeId", n1.optString("id"));
            editingStory.put("situation", sit);
            JSONObject e1 = new JSONObject();
            e1.put("type", "auto");
            e1.put("next", n2.optString("id"));
            e1.put("afterTurns", 2);
            n1.optJSONArray("edges").put(e1);
            JSONObject e2 = new JSONObject();
            e2.put("type", "auto");
            e2.put("next", n3.optString("id"));
            e2.put("afterTurns", 4);
            n2.optJSONArray("edges").put(e2);
            refreshStoryNodes();
            populateStory();
            toast("已从角色情境生成 3 节点线性剧情");
        } catch (Exception e) {
            AppLogger.e("STORY", "generate failed", e);
        }
    }

    // ================= settings =================

    private void initSettings(View v) {
        spSetMode = (Spinner) v.findViewById(R.id.sp_set_mode);
        etSetKey = (EditText) v.findViewById(R.id.et_set_key);
        etSetBase = (EditText) v.findViewById(R.id.et_set_base);
        etSetModelMain = (EditText) v.findViewById(R.id.et_set_model_main);
        etSetModelMem = (EditText) v.findViewById(R.id.et_set_model_memory);
        etSetMemInterval = (EditText) v.findViewById(R.id.et_set_mem_interval);
        etSetMemTopk = (EditText) v.findViewById(R.id.et_set_mem_topk);
        etSetMemDecay = (EditText) v.findViewById(R.id.et_set_mem_decay);
        etSetMemFade = (EditText) v.findViewById(R.id.et_set_mem_fade);
        etSetHistory = (EditText) v.findViewById(R.id.et_set_history);
        etSetMaxBubbles = (EditText) v.findViewById(R.id.et_set_max_bubbles);
        etSetTemplate = (EditText) v.findViewById(R.id.et_set_template);
        swSetStream = (SwitchMaterial) v.findViewById(R.id.sw_set_stream);
        swSetVars = (SwitchMaterial) v.findViewById(R.id.sw_set_vars);
        swSetInner = (SwitchMaterial) v.findViewById(R.id.sw_set_inner);
        swSetBubble = (SwitchMaterial) v.findViewById(R.id.sw_set_bubble);

        v.findViewById(R.id.btn_set_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });

        // 主题三选：选中项高亮，点击即换肤并 recreate()
        themeBtns = new TextView[]{
                (TextView) v.findViewById(R.id.btn_theme_system),
                (TextView) v.findViewById(R.id.btn_theme_light),
                (TextView) v.findViewById(R.id.btn_theme_dark)};
        for (int i = 0; i < themeBtns.length; i++) {
            final int mode = i;
            themeBtns[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    if (themeModeOf(MainActivity.this) == mode) return;
                    setThemeMode(mode);
                }
            });
        }
        updateThemeButtons();

        v.findViewById(R.id.btn_set_reset).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                etSetTemplate.setText(ChatEngine.DEFAULT_TEMPLATE);
                toast("已恢复默认模板");
            }
        });
        v.findViewById(R.id.btn_set_export).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                exportData();
            }
        });
        v.findViewById(R.id.btn_set_import).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                importData();
            }
        });
        v.findViewById(R.id.btn_set_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                saveSettings();
            }
        });

        populateSettings();
    }

    private void populateSettings() {
        setupModeSpinner(spSetMode, config.optString("apiMode", "openai"));
        etSetKey.setText(config.optString("apiKey", ""));
        etSetBase.setText(config.optString("baseUrl", "https://api.deepseek.com"));
        etSetModelMain.setText(ChatEngine.modelOf(config, "main"));
        etSetModelMem.setText(ChatEngine.modelOf(config, "memory"));
        swSetStream.setChecked(config.optBoolean("enableStreaming", true));
        etSetMemInterval.setText(String.valueOf(config.optInt("memorySummaryInterval", 15)));
        etSetMemTopk.setText(String.valueOf(config.optInt("memoryInjectTopK", 8)));
        etSetMemDecay.setText(String.valueOf(config.optDouble("memoryDecayRate", 0.15)));
        etSetMemFade.setText(String.valueOf(config.optDouble("memoryFadeThreshold", 2.0)));
        etSetHistory.setText(String.valueOf(config.optInt("historyWindow", 20)));
        swSetVars.setChecked(config.optBoolean("enableVariables", true));
        swSetInner.setChecked(config.optBoolean("enableInnerVoice", true));
        swSetBubble.setChecked(config.optBoolean("enableMultiBubble", true));
        etSetMaxBubbles.setText(String.valueOf(config.optInt("maxBubbles", 3)));
        etSetTemplate.setText(config.optString("promptTemplate", ChatEngine.DEFAULT_TEMPLATE));
    }

    // ---------- 数据导入导出（规格 §P3） ----------

    private void exportData() {
        final String data = Store.exportAll();
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出数据")
                .setMessage("已生成完整数据包（约 " + data.length() + " 字符），可复制或分享用于备份。")
                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        copy(data);
                    }
                })
                .setNeutralButton("分享", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        shareText("AI 伴侣数据备份", data);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void importData() {
        final EditText et = new EditText(this);
        et.setHint("粘贴导出的 JSON 数据包");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(6);
        et.setGravity(Gravity.TOP);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle("导入数据")
                .setView(wrap)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String result = Store.importAll(et.getText().toString());
                        toast(result);
                        if (result != null && result.contains("成功")) {
                            config = Store.loadConfig();
                            refreshHome();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveSettings() {
        try {
            config.put("apiMode", spSetMode.getSelectedItemPosition() == 0 ? "openai" : "anthropic");
            config.put("apiKey", etSetKey.getText().toString().trim());
            config.put("baseUrl", etSetBase.getText().toString().trim());
            config.optJSONObject("models").put("main", etSetModelMain.getText().toString().trim());
            config.optJSONObject("models").put("memory", etSetModelMem.getText().toString().trim());
            config.put("enableStreaming", swSetStream.isChecked());
            config.put("memorySummaryInterval", parseInt(etSetMemInterval, 15));
            config.put("memoryInjectTopK", parseInt(etSetMemTopk, 8));
            config.put("memoryDecayRate", parseDouble(etSetMemDecay, 0.15));
            config.put("memoryFadeThreshold", parseDouble(etSetMemFade, 2.0));
            config.put("historyWindow", parseInt(etSetHistory, 20));
            config.put("enableVariables", swSetVars.isChecked());
            config.put("enableInnerVoice", swSetInner.isChecked());
            config.put("enableMultiBubble", swSetBubble.isChecked());
            config.put("maxBubbles", parseInt(etSetMaxBubbles, 3));
            config.put("promptTemplate", etSetTemplate.getText().toString());
            Store.saveConfig(config);
        } catch (Exception e) {
            AppLogger.e("SET", "save failed", e);
        }
        toast("设置已保存");
        goBack();
    }

    private int parseInt(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private double parseDouble(EditText et, double def) {
        try {
            return Double.parseDouble(et.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ================= built-in commands =================

    private static final String DIARY_PROMPT =
            "现在切换输出模式。不要进行对话。\n\n以【@{char}】的第一人称，写 2-3 篇 Ta 的日记。\n" +
            "要求：\n- 每篇标注相对日期（如「三天前」）\n- 围绕最近发生的事，尤其是与 @{player} 相关的\n" +
            "- 日记比说话时更坦诚，会写下当面不会说的想法\n- 不是写给 @{player} 看的，是私密日记\n- 每篇 100-200 字\n\n" +
            "格式：\n【三天前】\n正文…\n\n【昨天】\n正文…";

    private static final String INNER_PROMPT =
            "不要对话。以【@{char}】的第一人称，写下此刻没对 @{player} 说出口的心里话。\n" +
            "要求：坦诚、真实、带着克制的情感，200 字以内。";

    private static final String EYES_PROMPT =
            "不要对话。以【@{char}】的视角，写下你对 @{player} 的认知。分三部分：\n\n" +
            "【称呼】\n你私下里怎么称呼 Ta？为什么？（一两句）\n\n" +
            "【身份认知】\n你了解到的关于 Ta 的事实（列点，只写确实知道的）\n\n" +
            "【心中的印象】\nTa 在你心里是个什么样的人？包括你不会当面说的部分。（一段）";

    private static final String PHONE_PROMPT =
            "现在切换输出模式。假装你在翻看【@{char}】的手机。\n" +
            "以聊天记录截图的形式写出最近与 @{player} 相关的几条对话片段，含时间与备注。\n" +
            "要求：真实感强，能体现 Ta 私下里的样子。";

    private static final String REPORT_PROMPT =
            "现在切换输出模式。生成一份《恋爱脑报告》，对象是【@{char}】对 @{player} 的感情。\n" +
            "包含：\n【诊断结论】一句话总结\n【关键指标】列出 4-6 条证据（从对话与记忆中推测）\n【风险提示】一条\n" +
            "风格诙谐但克制。";

    private static final String SONG_PROMPT =
            "现在切换输出模式。以【@{char}】的身份为 @{player} 写一首歌。\n" +
            "包含歌名、一段副歌和一段主歌，风格贴合 Ta 的性格。";

    private static final String DREAM_PROMPT =
            "现在切换输出模式。当世界都睡去，@ {player} 闯入了【@{char}】的梦里。\n" +
            "以 Ta 的视角描写这个梦：场景、你们的互动、梦醒前的最后一句话。300 字以内。";

    private static final String MEAL_PROMPT =
            "现在切换输出模式。以【@{char}】的视角写一段「Ta 给 @{player} 做饭」的场景。\n" +
            "包括厨房里的对话与最终结果，可以温馨也可以翻车。300 字以内。";

    private static final String LETTER_PROMPT =
            "现在切换输出模式。以【@{char}】的第一人称，给 @{player} 写一封信。\n" +
            "要求：手写信的格式（称呼/正文/落款/日期），语气诚恳不肉麻，\n" +
            "写一些当面说不出口的话，200-300 字。";

    private static final String CONFESS_PROMPT =
            "现在切换输出模式。生成《告白演练》剧本，对象是【@{char}】对 @{player}。\n" +
            "包含：\n【Ta 的告白稿】第一人称，符合人设\n【Ta 的预期反应】三种可能及 Ta 的应对\n【Ta 的怂点】Ta 为什么还没说出口\n风格诙谐克制。";

    private static final String ALBUM_PROMPT =
            "现在切换输出模式。翻开【@{char}】手机里的相册，找到 4-6 张与 @{player} 有关的照片。\n" +
            "每张照片写出：拍摄时间、画面内容、Ta 为什么舍不得删。\n" +
            "最后补一句 Ta 看着照片时的内心想法。";

    private static final String CLINIC_PROMPT =
            "现在切换输出模式。以【@{char}】的身份，开一间深夜情感诊疗室。\n" +
            "诊断对象是你们这段关系：\n【初诊】目前的相处状态\n【处方】一个可以立刻做的改善小行动\n" +
            "【医嘱】一句 Ta 想对 @{player} 说的真心话\n语气温柔，像真的医生。";

    private static final String FUTURE_PROMPT =
            "现在切换输出模式。想象十年之后：【@{char}】和 @{player} 的某一天。\n" +
            "描写：\n- 早晨醒来时的第一件事\n- 中午的一个平凡瞬间（吃饭/工作/散步）\n- 晚上睡前的一句话\n" +
            "要具体、真实、带一点时间的味道，300 字以内。";

    private static final String NIGHT_PROMPT =
            "现在切换输出模式。深夜两点，【@{char}】和 @{player} 谁都没睡，开始了茶话会。\n" +
            "写 5-8 轮对话，聊一些白天不会聊的话题（童年/害怕的事/梦想/怪癖）。\n" +
            "每轮标出发言人。";

    private static final String PRAISE_PROMPT =
            "现在切换输出模式。以【@{char}】的身份，认真地夸 @{player}。\n" +
            "要求：\n- 从三个具体细节入手（Ta 做过的事、说过的某句话、某个小习惯）\n" +
            "- 不要空泛的『你真好』，要有据可依\n- 结尾补一句 Ta 平时不会说出口的真心话";

    private static final String SOCIAL_PROMPT =
            "现在切换输出模式。假装你在刷【@{char}】的朋友圈/动态。\n" +
            "写出 Ta 最近 4-5 条动态（含图片描述、文字、评论区互动）。\n" +
            "其中至少一条与 @{player} 有关，且能看到 Ta 的小心思。";

    private static final String COLD_PROMPT =
            "现在切换输出模式。模拟一场冷战：你们因为一件小事闹了别扭，\n" +
            "【@{char}】正在生闷气。写出 Ta 的内心独白：\n" +
            "【嘴上】Ta 会说的话\n【心里】Ta 真正想的\n【破冰按钮】什么能让 Ta 立刻心软\n风格口是心非。";

    private static final String WITNESS_PROMPT =
            "现在切换输出模式。视角切到旁观者：一个认识【@{char}】和 @{player} 的第三方。\n" +
            "以旁观者的口吻描述 Ta 们相处时的样子：\n" +
            "【Ta 看到的事实】\n【Ta 的观察】这个人的小心思\n【Ta 的结论】\n旁观者可以是朋友、同事、家人或陌生人。";

    private static final String MOVIE_PROMPT =
            "现在切换输出模式。为【@{char}】和 @{player} 写一部电影企划：\n" +
            "【片名】\n【类型】\n【一句话梗概】\n【预告片文案】三句\n【彩蛋】最后一个镜头\n风格像正式的电影企划书。";

    private static final String OLD_PROMPT =
            "现在切换输出模式。很多很多年后，@ {player} 与【@{char}】都老了。\n" +
            "描写一个午后：Ta 们坐在一起，回忆这一生。\n" +
            "以【@{char}】的口吻写下 Ta 最后对 @{player} 说的一句话。\n" +
            "300 字以内，克制而温暖。";

    private static JSONArray builtinCommands() {
        JSONArray arr = new JSONArray();
        arr.put(cmd("cmd_diary", "Ta 的日记", "📔", "窥探日记中不为人知的心事", true, 10, DIARY_PROMPT));
        arr.put(cmd("cmd_inner", "内心独白", "💭", "未说出口的思绪缓缓浮现", false, 5, INNER_PROMPT));
        arr.put(cmd("cmd_eyes", "Ta 眼中的我", "👀", "在 Ta 心里你是什么模样", true, 15, EYES_PROMPT));
        arr.put(cmd("cmd_phone", "Ta 的手机", "📱", "深入手机记录，揭秘生活轨迹", true, 15, PHONE_PROMPT));
        arr.put(cmd("cmd_report", "恋爱脑报告", "📊", "一份关于 Ta 爱你的独家报告", true, 10, REPORT_PROMPT));
        arr.put(cmd("cmd_song", "为你写歌", "🎵", "Ta 为你写了一首歌", true, 15, SONG_PROMPT));
        arr.put(cmd("cmd_dream", "闯入梦境", "🌙", "当世界都睡去，你闯入 Ta 的梦里", false, 8, DREAM_PROMPT));
        arr.put(cmd("cmd_meal", "Ta 给你做饭", "🍳", "意外惊喜还是黑暗料理？", false, 8, MEAL_PROMPT));
        arr.put(cmd("cmd_letter", "给你写信", "✉️", "一封手写的信，说些当面说不出口的", true, 12, LETTER_PROMPT));
        arr.put(cmd("cmd_confess", "告白演练", "💌", "Ta 的告白稿与怂点全公开", true, 15, CONFESS_PROMPT));
        arr.put(cmd("cmd_album", "我们的相册", "📷", "翻翻 Ta 舍不得删的照片", true, 10, ALBUM_PROMPT));
        arr.put(cmd("cmd_clinic", "情感诊疗室", "🩺", "给这段关系做个初诊和处方", false, 10, CLINIC_PROMPT));
        arr.put(cmd("cmd_future", "十年之后", "⏳", "想象十年后你们的平凡一天", true, 12, FUTURE_PROMPT));
        arr.put(cmd("cmd_night", "深夜茶话会", "🌃", "深夜两点，聊些白天不会聊的", false, 5, NIGHT_PROMPT));
        arr.put(cmd("cmd_praise", "真心夸夸", "💝", "认真地从细节夸你一次", false, 6, PRAISE_PROMPT));
        arr.put(cmd("cmd_social", "Ta 的朋友圈", "🌐", "看看 Ta 动态里藏着的小心思", true, 10, SOCIAL_PROMPT));
        arr.put(cmd("cmd_cold", "冷战模拟", "😤", "口是心非的 Ta 心里在想什么", false, 12, COLD_PROMPT));
        arr.put(cmd("cmd_witness", "旁观者视角", "👁️", "朋友眼中的你们是什么样", true, 15, WITNESS_PROMPT));
        arr.put(cmd("cmd_movie", "我们的电影", "🎬", "把你们的故事写成一部电影企划", true, 12, MOVIE_PROMPT));
        arr.put(cmd("cmd_old", "白发苍苍", "🧓", "很多年后，Ta 最后想对你说的话", true, 15, OLD_PROMPT));
        return arr;
    }

    private static JSONObject cmd(String id, String label, String icon, String tagline, boolean save, int minTurns, String prompt) {
        JSONObject c = new JSONObject();
        try {
            c.put("id", id);
            c.put("label", label);
            c.put("icon", icon);
            c.put("tagline", tagline);
            c.put("saveToRecords", save);
            c.put("minTurns", minTurns);
            c.put("promptOverride", prompt);
        } catch (Exception e) {
        }
        return c;
    }

    // ================= 指令编辑器 =================

    private void commandEditor() {
        JSONArray custom = Store.loadCommands();
        final String[] labels = new String[custom.length()];
        for (int i = 0; i < custom.length(); i++) {
            JSONObject c = custom.optJSONObject(i);
            labels[i] = c.optString("icon", "✨") + " " + c.optString("label", "") + " · " + c.optString("tagline", "");
        }
        new MaterialAlertDialogBuilder(this).setTitle("自定义指令（" + custom.length() + "）")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        customCommandMenu(Store.loadCommands().optJSONObject(which));
                    }
                })
                .setNeutralButton("＋ 新建", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        customCommandEdit(null);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void customCommandMenu(final JSONObject c) {
        final String[] items = {"编辑", "删除"};
        new MaterialAlertDialogBuilder(this).setTitle(c.optString("label", "指令"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            customCommandEdit(c);
                        } else {
                            JSONArray arr = Store.loadCommands();
                            for (int i = arr.length() - 1; i >= 0; i--) {
                                if (arr.optJSONObject(i).optString("id").equals(c.optString("id"))) {
                                    arr.remove(i);
                                    break;
                                }
                            }
                            Store.saveCommands(arr);
                            toast("已删除");
                            renderCommands();
                        }
                    }
                }).show();
    }

    private void customCommandEdit(final JSONObject existing) {
        ScrollView sv = new ScrollView(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 16, 48, 0);
        sv.addView(wrap);

        final EditText etLabel = new EditText(this);
        etLabel.setHint("名称（如：Ta 的微博）");
        etLabel.setText(existing != null ? existing.optString("label", "") : "");
        wrap.addView(etLabel);
        final EditText etIcon = new EditText(this);
        etIcon.setHint("图标 emoji");
        etIcon.setText(existing != null ? existing.optString("icon", "✨") : "✨");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = 8;
        etIcon.setLayoutParams(ilp);
        wrap.addView(etIcon);
        final EditText etTag = new EditText(this);
        etTag.setHint("一句话介绍");
        etTag.setText(existing != null ? existing.optString("tagline", "") : "");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = 8;
        etTag.setLayoutParams(tlp);
        wrap.addView(etTag);
        final EditText etPrompt = new EditText(this);
        etPrompt.setHint("指令提示词（可用 @{char} @{player}）");
        etPrompt.setText(existing != null ? existing.optString("promptOverride", "") : "");
        etPrompt.setMinLines(4);
        etPrompt.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = 8;
        etPrompt.setLayoutParams(plp);
        wrap.addView(etPrompt);
        final EditText etMin = new EditText(this);
        etMin.setHint("解锁所需轮数（默认 5）");
        etMin.setText(existing != null ? String.valueOf(existing.optInt("minTurns", 5)) : "5");
        etMin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = 8;
        etMin.setLayoutParams(mlp);
        wrap.addView(etMin);

        new MaterialAlertDialogBuilder(this).setTitle(existing != null ? "编辑自定义指令" : "新建自定义指令")
                .setView(sv)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String label = etLabel.getText().toString().trim();
                        if (label.length() == 0) {
                            toast("请填写名称");
                            return;
                        }
                        try {
                            JSONArray arr = Store.loadCommands();
                            JSONObject c = existing != null ? existing : new JSONObject();
                            if (existing == null) c.put("id", Store.newId());
                            c.put("label", label);
                            c.put("icon", etIcon.getText().toString().trim().length() > 0 ? etIcon.getText().toString().trim() : "✨");
                            c.put("tagline", etTag.getText().toString().trim());
                            c.put("promptOverride", etPrompt.getText().toString().trim());
                            c.put("minTurns", parseInt(etMin, 5));
                            c.put("saveToRecords", true);
                            if (existing == null) {
                                arr.put(c);
                            } else {
                                for (int i = 0; i < arr.length(); i++) {
                                    if (arr.optJSONObject(i).optString("id").equals(c.optString("id"))) {
                                        arr.put(i, c);
                                        break;
                                    }
                                }
                            }
                            Store.saveCommands(arr);
                            renderCommands();
                            toast("指令已保存");
                        } catch (Exception e) {
                            AppLogger.e("CMD", "save custom failed", e);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= market (提示词市场) =================

    private void initMarket(View v) {
        rvMarket = (RecyclerView) v.findViewById(R.id.rv_market);
        rvMarket.setLayoutManager(new LinearLayoutManager(this));
        marketAdapter = new MarketAdapter();
        rvMarket.setAdapter(marketAdapter);
        pbMarket = (ProgressBar) v.findViewById(R.id.pb_market);
        llMarketEmpty = (LinearLayout) v.findViewById(R.id.ll_market_empty);
        llMarketError = (LinearLayout) v.findViewById(R.id.ll_market_error);
        tvMarketError = (TextView) v.findViewById(R.id.tv_market_error);
        llMarketCustom = (LinearLayout) v.findViewById(R.id.ll_market_custom);
        llMarketLocal = (LinearLayout) v.findViewById(R.id.ll_market_local);
        etMarketUrl = (EditText) v.findViewById(R.id.et_market_url);
        etMarketPaste = (EditText) v.findViewById(R.id.et_market_paste);

        v.findViewById(R.id.btn_market_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                goBack();
            }
        });
        v.findViewById(R.id.btn_market_retry).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                loadMarket(marketSource);
            }
        });
        tvMarketCache = (TextView) v.findViewById(R.id.tv_market_cache);
        v.findViewById(R.id.btn_market_refresh).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                if ("local".equals(marketSource) || "custom".equals(marketSource)) return;
                toast("正在刷新…");
                loadMarket(marketSource, true);
            }
        });
        v.findViewById(R.id.btn_market_fetch).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                loadCustom();
            }
        });
        v.findViewById(R.id.btn_market_paste_import).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                importLocalText();
            }
        });
        v.findViewById(R.id.btn_market_pickfile).setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                pickLocalFile();
            }
        });

        // 代码生成 6 个 Tab
        LinearLayout tabBar = (LinearLayout) v.findViewById(R.id.ll_market_tabs);
        final String[] labels = {"AWESOME", "CHATGPT", "DSH 角色", "SillyTavern", "本地", "自定义"};
        marketTabs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(14, 6, 14, 6);
            tv.setMinHeight(44);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = 8;
            tv.setLayoutParams(lp);
            final int idx = i;
            tv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    switchMarketTab(idx);
                }
            });
            tabBar.addView(tv);
            marketTabs[i] = tv;
        }
    }

    private void renderMarket() {
        updateMarketTabStyles(tabIndexOf(marketSource));
        boolean custom = "custom".equals(marketSource);
        boolean local = "local".equals(marketSource);
        llMarketCustom.setVisibility(custom ? View.VISIBLE : View.GONE);
        llMarketLocal.setVisibility(local ? View.VISIBLE : View.GONE);
        if (local) {
            marketRows.clear();
            marketAdapter.notifyDataSetChanged();
            pbMarket.setVisibility(View.GONE);
            llMarketEmpty.setVisibility(View.GONE);
            llMarketError.setVisibility(View.GONE);
            return;
        }
        // 懒加载：只有点进来/切 Tab 才发请求
        if (marketRows.size() == 0 && pbMarket.getVisibility() != View.VISIBLE) {
            loadMarket(marketSource);
        } else {
            marketAdapter.notifyDataSetChanged();
            updateMarketCacheHint(marketSource);
        }
    }

    private void switchMarketTab(int idx) {
        String[] sources = {"awesome", "chatgpt", "dsh", "silly", "local", "custom"};
        marketSource = sources[idx];
        updateMarketTabStyles(idx);
        llMarketCustom.setVisibility(idx == 5 ? View.VISIBLE : View.GONE);
        llMarketLocal.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        if (idx == 4) {
            marketRows.clear();
            marketAdapter.notifyDataSetChanged();
            pbMarket.setVisibility(View.GONE);
            llMarketEmpty.setVisibility(View.GONE);
            llMarketError.setVisibility(View.GONE);
            return;
        }
        loadMarket(marketSource);
    }

    private int tabIndexOf(String src) {
        if ("awesome".equals(src)) return 0;
        if ("chatgpt".equals(src)) return 1;
        if ("dsh".equals(src)) return 2;
        if ("silly".equals(src)) return 3;
        if ("local".equals(src)) return 4;
        return 5;
    }

    private void updateMarketTabStyles(int idx) {
        if (marketTabs == null) return;
        for (int i = 0; i < marketTabs.length; i++) {
            TextView tv = marketTabs[i];
            boolean sel = i == idx;
            tv.setTextColor(c(sel ? R.color.brand : R.color.text_secondary));
            tv.setTypeface(tv.getTypeface(), sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected : 0);
        }
    }

    /** 进入 Tab 时调用：命中缓存就不联网。 */
    private void loadMarket(String source) {
        loadMarket(source, false);
    }

    /**
     * @param forceRefresh true 表示用户主动点了刷新，忽略缓存重新抓取
     */
    private void loadMarket(final String source, final boolean forceRefresh) {
        marketSource = source;
        llMarketEmpty.setVisibility(View.GONE);
        llMarketError.setVisibility(View.GONE);

        // 本地来源不涉及网络，也不需要缓存
        if ("local".equals(source) || "custom".equals(source)) {
            pbMarket.setVisibility(View.GONE);
            marketRows.clear();
            marketAdapter.notifyDataSetChanged();
            updateMarketCacheHint(source);
            return;
        }

        if (!forceRefresh) {
            JSONArray cached = Store.loadMarketCache(source);
            if (cached != null && cached.length() > 0) {
                pbMarket.setVisibility(View.GONE);
                marketRows.clear();
                for (int i = 0; i < cached.length(); i++) {
                    JSONObject o = cached.optJSONObject(i);
                    if (o != null) marketRows.add(o);
                }
                marketAdapter.notifyDataSetChanged();
                updateMarketCacheHint(source);
                return;
            }
        }

        pbMarket.setVisibility(View.VISIBLE);
        marketRows.clear();
        marketAdapter.notifyDataSetChanged();
        final String src = source;
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<JSONObject> rows;
                    if ("awesome".equals(src)) rows = MarketClient.fetchAwesome();
                    else if ("chatgpt".equals(src)) rows = MarketClient.fetchChatGPT();
                    else if ("dsh".equals(src)) rows = MarketClient.fetchDsh();
                    else if ("silly".equals(src)) rows = MarketClient.fetchSillyTavern();
                    else rows = new ArrayList<JSONObject>();
                    JSONArray toCache = new JSONArray();
                    for (JSONObject o : rows) toCache.put(o);
                    Store.saveMarketCache(src, toCache);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            marketRows.clear();
                            marketRows.addAll(rows);
                            if (marketRows.size() == 0) {
                                llMarketEmpty.setVisibility(View.VISIBLE);
                            }
                            marketAdapter.notifyDataSetChanged();
                            updateMarketCacheHint(src);
                        }
                    });
                } catch (final Exception e) {
                    // 抓取失败（多半是 GitHub 限流）时回落到旧缓存，而不是清空列表
                    final JSONArray cached = Store.loadMarketCache(src);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            String msg = e.getMessage() == null ? "加载失败" : e.getMessage();
                            if (cached != null && cached.length() > 0) {
                                marketRows.clear();
                                for (int i = 0; i < cached.length(); i++) {
                                    JSONObject o = cached.optJSONObject(i);
                                    if (o != null) marketRows.add(o);
                                }
                                marketAdapter.notifyDataSetChanged();
                                updateMarketCacheHint(src);
                                toast("刷新失败，仍显示本地缓存：" + msg);
                            } else {
                                tvMarketError.setText(msg);
                                llMarketError.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /** 顶栏提示缓存时间，让用户知道数据有多旧、要不要刷新。 */
    private void updateMarketCacheHint(String source) {
        if (tvMarketCache == null) return;
        long t = Store.marketCacheTime(source);
        if (t <= 0) {
            tvMarketCache.setVisibility(View.GONE);
        } else {
            tvMarketCache.setVisibility(View.VISIBLE);
            tvMarketCache.setText("缓存于 " + relTime(t));
        }
    }

    private void loadCustom() {
        final String url = etMarketUrl.getText().toString().trim();
        if (url.length() == 0) {
            toast("请输入 GitHub 链接或 raw URL");
            return;
        }
        pbMarket.setVisibility(View.VISIBLE);
        llMarketEmpty.setVisibility(View.GONE);
        llMarketError.setVisibility(View.GONE);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final List<JSONObject> rows = MarketClient.fetchCustom(url);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            marketRows.clear();
                            marketRows.addAll(rows);
                            if (marketRows.size() == 0) {
                                llMarketEmpty.setVisibility(View.VISIBLE);
                            }
                            marketAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            tvMarketError.setText(e.getMessage() == null ? "加载失败" : e.getMessage());
                            llMarketError.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    private void importLocalText() {
        String text = etMarketPaste.getText().toString().trim();
        if (text.length() == 0) {
            toast("请先粘贴文本");
            return;
        }
        String first = text.split("\n")[0].trim();
        if (first.length() > 20) first = first.substring(0, 20);
        JSONObject it = new JSONObject();
        try {
            it.put("name", first.length() > 0 ? first : "本地提示词");
            it.put("source", "本地");
            it.put("content", text);
        } catch (Exception e) {
        }
        doImport(it, text);
    }

    private void pickLocalFile() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        try {
            startActivityForResult(it, REQ_PICK_FILE);
        } catch (Exception e) {
            toast("系统文件选择器不可用，请使用粘贴文本");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                br.close();
                etMarketPaste.setText(sb.toString());
                toast("已读取文件，点击「导入文本」");
            } catch (Exception e) {
                toast("读取文件失败：" + e.getMessage());
            }
        }
    }

    /** 预览：内容未拉取时先按需下载 */
    private void previewMarketItem(final JSONObject it) {
        String content = it.optString("content", "");
        String url = it.optString("downloadUrl", "");
        if (content.length() > 0) {
            showPreviewDialog(it, content);
            return;
        }
        if (url.length() == 0) {
            toast("该条目没有可预览的内容");
            return;
        }
        pbMarket.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                try {
                    String body = MarketClient.fetchText(url);
                    String parsed = body;
                    if (MarketClient.SRC_SILLY.equals(it.optString("source", ""))) {
                        JSONObject j = MarketClient.parseSillyJson(body, it.optString("name", ""));
                        parsed = j.optString("content", body);
                    }
                    final String finalParsed = parsed;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            try {
                                it.put("content", finalParsed);
                            } catch (Exception e) {
                            }
                            showPreviewDialog(it, finalParsed);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            toast("加载失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showPreviewDialog(JSONObject it, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(14);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        sv.addView(tv);
        new MaterialAlertDialogBuilder(this)
                .setTitle(it.optString("name", "预览"))
                .setView(sv)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        importMarketItem(it);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 导入：内容未拉取时先按需下载，再构造 Character */
    private void importMarketItem(final JSONObject it) {
        String content = it.optString("content", "");
        if (content.length() > 0) {
            doImport(it, content);
            return;
        }
        final String url = it.optString("downloadUrl", "");
        if (url.length() == 0) {
            toast("该条目没有内容");
            return;
        }
        pbMarket.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                try {
                    String body = MarketClient.fetchText(url);
                    String parsed = body;
                    if (MarketClient.SRC_SILLY.equals(it.optString("source", ""))) {
                        JSONObject j = MarketClient.parseSillyJson(body, it.optString("name", ""));
                        parsed = j.optString("content", body);
                    }
                    final String finalParsed = parsed;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            doImport(it, finalParsed);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pbMarket.setVisibility(View.GONE);
                            toast("导入失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** 构造 Character：systemPrompt → persona；{{占位符}} 扫描后附 privateNote */
    private void doImport(JSONObject it, String content) {
        try {
            JSONObject c = new JSONObject();
            c.put("id", Store.newId());
            c.put("name", it.optString("name", "提示词"));
            c.put("brief", "来自「" + it.optString("source", "提示词市场") + "」");
            c.put("persona", content);
            c.put("greeting", "");
            c.put("privateNote", "");
            c.put("avatarEmoji", "📥");
            c.put("color", "#60A5FA");
            c.put("tags", it.optString("source", ""));
            c.put("isPartner", false);
            c.put("createdAt", System.currentTimeMillis());

            List<String> ph = MarketClient.scanPlaceholders(content);
            if (ph.size() > 0) {
                StringBuilder sb = new StringBuilder("检测到占位符：");
                for (int i = 0; i < ph.size(); i++) {
                    if (i > 0) sb.append("、");
                    sb.append("{{{").append(ph.get(i)).append("}}}");
                }
                sb.append("，请在编辑页手动替换");
                c.put("privateNote", sb.toString());
            }

            Store.upsertChar(c);
            toast("已导入「" + c.optString("name", "") + "」，请确认编辑");
            characters = Store.loadCharacters();
            openCharEdit(c);
        } catch (Exception e) {
            AppLogger.e("MARKET", "import failed", e);
            toast("导入失败");
        }
    }

    private class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView name, source, subtitle, preview, importBtn;

            VH(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.tv_mkt_name);
                source = (TextView) itemView.findViewById(R.id.tv_mkt_source);
                subtitle = (TextView) itemView.findViewById(R.id.tv_mkt_subtitle);
                preview = (TextView) itemView.findViewById(R.id.btn_mkt_preview);
                importBtn = (TextView) itemView.findViewById(R.id.btn_mkt_import);
            }
        }

        public MarketAdapter.VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_market, parent, false);
            return new VH(v);
        }

        public void onBindViewHolder(final VH h, int pos) {
            final JSONObject it = marketRows.get(pos);
            h.name.setText(it.optString("name", ""));
            h.source.setText(it.optString("source", ""));
            String sub = it.optString("subtitle", "");
            if (sub.length() > 0) {
                h.subtitle.setText(sub);
                h.subtitle.setVisibility(View.VISIBLE);
            } else {
                h.subtitle.setVisibility(View.GONE);
            }
            h.preview.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    previewMarketItem(it);
                }
            });
            h.importBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    importMarketItem(it);
                }
            });
        }

        public int getItemCount() {
            return marketRows.size();
        }
    }
}
