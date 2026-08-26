package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词市场数据客户端。
 *
 * 统一四种 GitHub 来源 + 自定义链接 + 本地文本，全部为阻塞式方法，
 * 必须由调用方放到子线程执行。返回的条目统一为 JSONObject：
 *   name        —— 提示词名称
 *   subtitle    —— 分类副标题（仅 Awesome 有，格式「分类 · 名称」）
 *   source      —— 来源标签
 *   downloadUrl —— raw 直链（Awesome / SillyTavern 需要按需拉取全文）
 *   content     —— 已拉取到的全文（CSV / DSH / 自定义单文件直接带内容）
 */
public class MarketClient {

    public static final String SRC_AWESOME = "Awesome-Prompts";
    public static final String SRC_CHATGPT = "ChatGPT Prompts";
    public static final String SRC_DSH = "DSH 角色";
    public static final String SRC_SILLY = "SillyTavern";
    public static final String SRC_LOCAL = "本地";
    public static final String SRC_CUSTOM = "自定义";

    private static final String AWESOME_ROOT =
            "https://api.github.com/repos/dongshuyan/Awesome-Prompts/contents/prompts";
    private static final String CHATGPT_CSV =
            "https://raw.githubusercontent.com/f/awesome-chatgpt-prompts/main/prompts.csv";
    private static final String DSH_YAML =
            "https://raw.githubusercontent.com/oliblue-evan/dsh-roleplay-preset/main/agent.cordis.yml";
    private static final String SILLY_LIST =
            "https://api.github.com/repos/SillyTavern/SillyTavern/contents/default/content/presets/sysprompt?ref=release";

    private static final int MAX_DIR_REQS = 30;   // 目录请求上限，防触发 60 次/小时限流
    private static final int MAX_ITEMS = 300;     // 条目上限
    private static final int MAX_DEPTH = 3;       // 递归深度上限（根为第 0 层）

    /** 带友好提示的网络/解析异常 */
    public static class MarketException extends Exception {
        public MarketException(String msg) {
            super(msg);
        }
    }

    // ================= HTTP =================

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "VibeCompanion");
        int code = conn.getResponseCode();
        if (code == 403 || code == 429) {
            conn.disconnect();
            throw new MarketException("GitHub API 限流（HTTP " + code + "，60 次/小时）。"
                    + "可稍后再试，或在「自定义链接」里粘贴 raw URL 降级（raw 域名不限流）");
        }
        if (code >= 400) {
            String err = readAll(conn.getErrorStream());
            conn.disconnect();
            throw new MarketException("HTTP " + code + (err.length() > 0 ? "：" + truncate(err, 120) : ""));
        }
        InputStream is = conn.getInputStream();
        String body = readAll(is);
        is.close();
        conn.disconnect();
        return body;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private static JSONObject item(String name, String subtitle, String source,
                                   String downloadUrl, String content) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name == null ? "" : name);
            o.put("subtitle", subtitle == null ? "" : subtitle);
            o.put("source", source == null ? "" : source);
            o.put("downloadUrl", downloadUrl == null ? "" : downloadUrl);
            o.put("content", content == null ? "" : content);
        } catch (Exception e) {
            // ignore
        }
        return o;
    }

    // ================= AWESOME（递归遍历） =================

    public static List<JSONObject> fetchAwesome() throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        int[] reqs = {0};
        traverseDir(AWESOME_ROOT, "", 0, out, reqs);
        return out;
    }

    private static void traverseDir(String urlStr, String category, int depth,
                                    List<JSONObject> out, int[] reqs) throws Exception {
        if (depth > MAX_DEPTH || reqs[0] >= MAX_DIR_REQS || out.size() >= MAX_ITEMS) return;
        reqs[0]++;
        String body = httpGet(urlStr);
        JSONArray arr;
        try {
            arr = new JSONArray(body);
        } catch (Exception e) {
            return; // 响应不是数组（如限流提示），静默跳过该目录
        }
        for (int i = 0; i < arr.length(); i++) {
            if (out.size() >= MAX_ITEMS) break;
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            String name = e.optString("name", "");
            if ("file".equals(type)) {
                String lower = name.toLowerCase();
                if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                    String base = name.substring(0, name.length() - 4);
                    out.add(item(base, category, SRC_AWESOME, e.optString("download_url", ""), ""));
                }
            } else if ("dir".equals(type)) {
                // 直接用条目自带的 url 字段发起下一层请求，不自己拼接路径
                String nextUrl = e.optString("url", "");
                if (nextUrl.length() == 0) nextUrl = e.optString("html_url", "");
                String subCat = category.length() > 0 ? category + " · " + name : name;
                traverseDir(nextUrl, subCat, depth + 1, out, reqs);
            }
            // submodule 等其他类型：跳过
        }
    }

    // ================= CHATGPT（CSV） =================

    public static List<JSONObject> fetchChatGPT() throws Exception {
        String body = httpGet(CHATGPT_CSV);
        List<String[]> rows = parseCsv(body);
        List<JSONObject> out = new ArrayList<JSONObject>();
        boolean first = true;
        for (String[] row : rows) {
            if (first) {
                first = false;
                continue; // 跳过表头
            }
            if (row.length < 2) continue;
            String act = row[0].trim();
            String prompt = row[1].trim();
            if (act.length() == 0) continue;
            out.add(item(act, "", SRC_CHATGPT, "", prompt));
        }
        return out;
    }

    /** 手写 RFC4180 CSV 解析：支持引号字段与 "" 转义 */
    private static List<String[]> parseCsv(String csv) {
        List<String[]> rows = new ArrayList<String[]>();
        List<String> cur = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int n = csv == null ? 0 : csv.length();
        for (int i = 0; i < n; i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    cur.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    cur.add(field.toString());
                    field.setLength(0);
                    rows.add(cur.toArray(new String[cur.size()]));
                    cur = new ArrayList<String>();
                } else if (c != '\r') {
                    field.append(c);
                }
            }
        }
        if (field.length() > 0 || cur.size() > 0) {
            cur.add(field.toString());
            rows.add(cur.toArray(new String[cur.size()]));
        }
        return rows;
    }

    // ================= DSH 角色（YAML） =================

    public static List<JSONObject> fetchDsh() throws Exception {
        String body = httpGet(DSH_YAML);
        String content = parseDshYaml(body);
        if (content == null || content.trim().length() == 0) {
            throw new MarketException("未在 YAML 中找到 complete: true 的 persona 块");
        }
        List<JSONObject> out = new ArrayList<JSONObject>();
        out.add(item("角色扮演预设（dsh）", "", SRC_DSH, "", content.trim()));
        return out;
    }

    /** 专用 YAML 解析：找 plugins 列表中 complete: true 的 persona 块的 content 多行文本 */
    private static String parseDshYaml(String yaml) {
        if (yaml == null) return null;
        String[] lines = yaml.split("\n");
        boolean inPlugins = false;
        boolean inPersona = false;
        boolean complete = false;
        boolean inContent = false;
        int contentIndent = -1;
        StringBuilder content = new StringBuilder();
        for (String raw : lines) {
            if (raw.trim().length() == 0) continue;
            String trimmed = raw.trim();
            int indent = countIndent(raw);
            if (!inPlugins) {
                if (trimmed.equals("plugins:") || trimmed.startsWith("plugins:")) {
                    inPlugins = true;
                }
                continue;
            }
            if (trimmed.startsWith("- type:")) {
                inPersona = trimmed.contains("persona");
                complete = false;
                inContent = false;
                content.setLength(0);
                contentIndent = -1;
                continue;
            }
            if (!inPersona) continue;
            if (trimmed.startsWith("complete:")) {
                complete = trimmed.contains("true");
                continue;
            }
            if (trimmed.startsWith("content:")) {
                inContent = true;
                contentIndent = indent + 1;
                String rest = trimmed.substring("content:".length()).trim();
                if (rest.length() > 0 && !rest.equals("|")) {
                    content.append(rest).append('\n');
                }
                continue;
            }
            if (inContent) {
                if (indent > contentIndent) {
                    content.append(trimmed).append('\n');
                } else {
                    inContent = false;
                    if (complete && content.length() > 0) return content.toString();
                    content.setLength(0);
                    contentIndent = -1;
                    continue;
                }
            }
        }
        if (inPersona && complete && content.length() > 0) return content.toString();
        return null;
    }

    private static int countIndent(String s) {
        int n = 0;
        while (n < s.length() && (s.charAt(n) == ' ' || s.charAt(n) == '\t')) n++;
        return n;
    }

    // ================= SillyTavern（列表 + 逐条 json） =================

    public static List<JSONObject> fetchSillyTavern() throws Exception {
        String body = httpGet(SILLY_LIST);
        JSONArray arr = new JSONArray(body);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String name = e.optString("name", "");
            if (!name.toLowerCase().endsWith(".json")) continue;
            out.add(item(name.substring(0, name.length() - 5), "", SRC_SILLY,
                    e.optString("download_url", ""), ""));
        }
        return out;
    }

    /** 解析单个 SillyTavern 预设 json：{name, content}，缺失时用文件名兜底 */
    public static JSONObject parseSillyJson(String json, String fallbackName) {
        try {
            JSONObject j = new JSONObject(json);
            String name = j.optString("name", fallbackName);
            String content = j.optString("content", "");
            return item(name.length() > 0 ? name : fallbackName, "", SRC_SILLY, "", content);
        } catch (Exception e) {
            return item(fallbackName, "", SRC_SILLY, "", json);
        }
    }

    // ================= 通用全文拉取 =================

    public static String fetchText(String urlStr) throws Exception {
        return httpGet(urlStr);
    }

    // ================= 自定义 GitHub 链接 =================

    public static List<JSONObject> fetchCustom(String input) throws Exception {
        String url = input == null ? "" : input.trim();
        if (url.length() == 0) throw new MarketException("请输入 GitHub 链接或 raw URL");
        if (url.contains("/blob/")) {
            String raw = blobToRaw(url);
            String content = httpGet(raw);
            String name = fileNameOf(raw);
            List<JSONObject> out = new ArrayList<JSONObject>();
            out.add(item(name, "", SRC_CUSTOM, "", content));
            return out;
        }
        if (url.contains("/tree/")) {
            String api = treeToApi(url);
            String body = httpGet(api);
            JSONArray arr = new JSONArray(body);
            List<JSONObject> out = new ArrayList<JSONObject>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                if ("file".equals(e.optString("type", ""))) {
                    out.add(item(e.optString("name", ""), "", SRC_CUSTOM,
                            e.optString("download_url", ""), ""));
                }
            }
            return out;
        }
        // 直接当 raw URL 处理
        String content = httpGet(url);
        String name = fileNameOf(url);
        List<JSONObject> out = new ArrayList<JSONObject>();
        out.add(item(name, "", SRC_CUSTOM, "", content));
        return out;
    }

    /** github.com blob 链接 → raw.githubusercontent.com 直链 */
    private static String blobToRaw(String url) {
        String s = url;
        if (s.startsWith("http://github.com/")) s = "https://github.com/" + s.substring("http://github.com/".length());
        s = s.replace("https://github.com/", "https://raw.githubusercontent.com/");
        int idx = s.indexOf("/blob/");
        if (idx >= 0) s = s.substring(0, idx) + s.substring(idx + "/blob/".length());
        return s;
    }

    /** github.com tree 链接 → Contents API */
    private static String treeToApi(String url) {
        String s = url;
        if (s.startsWith("https://github.com/")) s = s.substring("https://github.com/".length());
        if (s.startsWith("http://github.com/")) s = s.substring("http://github.com/".length());
        String[] parts = s.split("/");
        if (parts.length < 4) return url;
        StringBuilder sb = new StringBuilder();
        sb.append("https://api.github.com/repos/").append(parts[0]).append("/").append(parts[1]).append("/contents");
        for (int i = 4; i < parts.length; i++) sb.append("/").append(parts[i]);
        sb.append("?ref=").append(parts[3]);
        return sb.toString();
    }

    private static String fileNameOf(String rawUrl) {
        String name = rawUrl;
        int slash = rawUrl.lastIndexOf('/');
        if (slash >= 0 && slash < rawUrl.length() - 1) name = rawUrl.substring(slash + 1);
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.length() > 0 ? name : "提示词";
    }

    // ================= 占位符扫描 =================

    /** 扫描 {{变量名}} 格式占位符，去重保序返回 */
    public static List<String> scanPlaceholders(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        Pattern p = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher m = p.matcher(text);
        Set<String> seen = new LinkedHashSet<String>();
        while (m.find()) {
            String v = m.group(1).trim();
            if (v.length() > 0 && v.length() <= 30) seen.add(v);
        }
        out.addAll(seen);
        return out;
    }
}
