package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内检查更新：读 GitHub Releases 的最新一条，跟当前 versionName 比对，
 * 有新版就下载 APK 交给系统安装器。
 *
 * 版本号由 CI 注入（见 app/build.gradle 与 build.yml），tag 形如 v1.0.42，
 * APK 的 versionName 是 1.0.42，因此去掉前导 v 后可以直接比。
 */
public class Updater {

    private static final String LATEST_URL =
            "https://api.github.com/repos/RT-C-6668882025/ai-chat/releases/latest";

    /** 一条待安装的更新 */
    public static class Release {
        public final String tag;
        public final String notes;
        public final String apkUrl;
        public final long size;

        Release(String tag, String notes, String apkUrl, long size) {
            this.tag = tag;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.size = size;
        }
    }

    public interface Progress {
        /** @param pct 0~100，总长度未知时为 -1 */
        void onProgress(int pct);
    }

    private Updater() {
    }

    // ---------- 查询 ----------

    /**
     * 拉取最新 Release。仓库还没有任何 Release 时 GitHub 返回 404，
     * 这属于正常状态而非错误，返回 null 让调用方提示「暂无发布版本」。
     */
    public static Release fetchLatest() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "VibeCompanion");
        int code = conn.getResponseCode();
        if (code == 404) {
            conn.disconnect();
            return null;
        }
        if (code == 403 || code == 429) {
            conn.disconnect();
            throw new Exception("GitHub 限流（HTTP " + code + "），过会儿再试");
        }
        if (code >= 400) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        InputStream is = conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        is.close();
        conn.disconnect();

        JSONObject o = new JSONObject(sb.toString());
        String tag = o.optString("tag_name", "");
        if (tag.length() == 0) return null;

        String apkUrl = "";
        long size = 0;
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String url = a.optString("browser_download_url", "");
                if (url.toLowerCase().endsWith(".apk")) {
                    apkUrl = url;
                    size = a.optLong("size", 0);
                    break;
                }
            }
        }
        return new Release(tag, o.optString("body", ""), apkUrl, size);
    }

    // ---------- 版本比较 ----------

    /**
     * latest 是否比 current 新。两边都去掉前导 v，按点分段比较：
     * 数值段按数值比（避免 1.0.9 > 1.0.10 的字典序错误），
     * 含非数字的段退化为字符串比较，段数不等时缺的补 0。
     */
    public static boolean isNewer(String latest, String current) {
        String[] a = normalize(latest).split("\\.");
        String[] b = normalize(current).split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            String sa = i < a.length ? a[i] : "0";
            String sb = i < b.length ? b[i] : "0";
            Long na = asLong(sa);
            Long nb = asLong(sb);
            int cmp;
            if (na != null && nb != null) {
                cmp = na.compareTo(nb);
            } else {
                cmp = sa.compareTo(sb);
            }
            if (cmp != 0) return cmp > 0;
        }
        return false;
    }

    private static String normalize(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        // 丢掉 1.0.0-dev 之类的后缀，只比数字主干
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);
        return s.length() == 0 ? "0" : s;
    }

    private static Long asLong(String s) {
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 下载 ----------

    /** 流式下载到 dest，边下边回报百分比。失败抛异常，调用方负责提示。 */
    public static void download(String url, File dest, Progress cb) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "VibeCompanion");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new Exception("下载失败：HTTP " + code);
        }
        long total = conn.getContentLength();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buf = new byte[16384];
        long done = 0;
        int lastPct = -1;
        int n;
        try {
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                if (cb != null) {
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct != lastPct) {
                        lastPct = pct;
                        cb.onProgress(pct);
                    }
                }
            }
            fos.flush();
        } finally {
            try {
                fos.close();
            } catch (Exception e) {
                // ignore
            }
            try {
                is.close();
            } catch (Exception e) {
                // ignore
            }
            conn.disconnect();
        }
    }
}
