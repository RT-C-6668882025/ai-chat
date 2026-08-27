package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified model-call layer. Supports Anthropic /v1/messages and OpenAI-compatible
 * /v1/chat/completions, both streaming (SSE) and non-streaming.
 *
 * All methods are blocking — call them from a background thread. The Callback's
 * onChunk/onDone/onError run on the calling thread.
 */
public class Api {

    public interface Callback {
        void onChunk(String text);
        void onDone(String fullText);
        void onError(String message);
    }

    public static void callModel(JSONObject config, String system, JSONArray messages, String model,
                                 int maxTokens, boolean stream, Callback cb) {
        String mode = config.optString("apiMode", "openai");
        String base = config.optString("baseUrl", "https://api.deepseek.com");
        String key = config.optString("apiKey", "");
        if (key == null || key.trim().length() == 0) {
            cb.onError("未配置 API Key");
            return;
        }
        try {
            if ("anthropic".equals(mode)) {
                anthropic(base, key, model, system, messages, maxTokens, stream, cb);
            } else {
                openai(base, key, model, system, messages, maxTokens, stream, cb);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            cb.onError(msg == null || msg.length() == 0 ? "网络错误" : msg);
        }
    }

    /**
     * 归一化 Base URL，让官网和中转站给的各种写法都能直接用。
     *
     * 调用处一律再拼 /v1/xxx，所以这里要把用户可能自带的 /v1 去掉，
     * 否则中转站常见的 https://xx.com/v1 会被拼成 /v1/v1/chat/completions 直接 404。
     * 没写协议头的补 https://。
     */
    static String trimBase(String base) {
        String s = base == null ? "" : base.trim();
        if (s.length() == 0) return s;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/v1")) s = s.substring(0, s.length() - 3);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ---------- 模型列表 ----------

    /**
     * 拉取端点可用的模型清单。GET {base}/v1/models —— OpenAI 的约定，
     * 中转站基本都照搬，Anthropic 官方也有同路径，只是鉴权头不同。
     *
     * 阻塞式，调用方自己放子线程。失败抛异常，消息里带 HTTP 状态与服务端原文，
     * 便于直接显示给用户排查。
     */
    public static List<String> listModels(JSONObject config) throws Exception {
        String mode = config.optString("apiMode", "openai");
        String base = config.optString("baseUrl", "");
        String key = config.optString("apiKey", "");
        if (trimBase(base).length() == 0) throw new Exception("请先填写 API 地址");
        if (key == null || key.trim().length() == 0) throw new Exception("请先填写 API Key");

        HttpURLConnection conn = (HttpURLConnection) new URL(trimBase(base) + "/v1/models").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        applyAuth(conn, mode, key);
        int code = conn.getResponseCode();
        if (code >= 400) {
            String err = readAll(conn.getErrorStream());
            conn.disconnect();
            throw new Exception("HTTP " + code + (err.length() > 0 ? "：" + truncate(err, 300) : ""));
        }
        InputStream is = conn.getInputStream();
        String body = readAll(is);
        is.close();
        conn.disconnect();

        // 标准返回是 {"data":[{"id":...}]}，也兼容顶层直接给数组的实现
        JSONArray arr;
        String t = body.trim();
        if (t.startsWith("[")) {
            arr = new JSONArray(t);
        } else {
            arr = new JSONObject(t).optJSONArray("data");
        }
        if (arr == null) throw new Exception("返回里没有模型列表");

        List<String> out = new ArrayList<String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            // 同样避开 optString 的 JSON null 陷阱，否则列表里会冒出一项 "null"
            String id;
            if (o != null) {
                id = Json.str(o, "id");
            } else {
                Object raw = arr.opt(i);
                id = raw instanceof String ? (String) raw : "";
            }
            if (id.length() > 0 && !out.contains(id)) out.add(id);
        }
        Collections.sort(out);
        if (out.isEmpty()) throw new Exception("端点返回了空的模型列表");
        return out;
    }

    private static void applyAuth(HttpURLConnection conn, String mode, String key) {
        if ("anthropic".equals(mode)) {
            conn.setRequestProperty("x-api-key", key);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    // ---------- 连接测试 ----------

    /** 测试结果：分两步，失败时能指出卡在哪一环。 */
    public static class TestResult {
        public boolean ok;
        /** 失败发生在哪一步：1 = 连通性/鉴权，2 = 模型可用性 */
        public int failedStep;
        public String detail = "";
        public int modelCount;
        public long millis;
    }

    /**
     * 两步测试：
     *   1. GET /v1/models —— 验 Base URL 与 Key
     *   2. 用给定 model 发一次 max_tokens=1 的最小请求 —— 验模型名与该 Key 的权限
     *
     * 传入的 config 由调用方用输入框当前值现拼，不必先保存。
     */
    public static TestResult testConnection(JSONObject config, String model) {
        TestResult r = new TestResult();
        long start = System.currentTimeMillis();

        List<String> models;
        try {
            models = listModels(config);
            r.modelCount = models.size();
        } catch (Exception e) {
            r.ok = false;
            r.failedStep = 1;
            r.detail = e.getMessage() == null ? "网络错误" : e.getMessage();
            r.millis = System.currentTimeMillis() - start;
            return r;
        }

        final StringBuilder err = new StringBuilder();
        final boolean[] done = {false};
        Callback cb = new Callback() {
            public void onChunk(String t) {
            }

            public void onDone(String full) {
                done[0] = true;
            }

            public void onError(String msg) {
                err.append(msg == null ? "未知错误" : msg);
            }
        };
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "user").put("content", "hi"));
            callModel(config, "", msgs, model, 1, false, cb);
        } catch (Exception e) {
            err.append(e.getMessage() == null ? "网络错误" : e.getMessage());
        }

        r.millis = System.currentTimeMillis() - start;
        if (err.length() > 0 || !done[0]) {
            r.ok = false;
            r.failedStep = 2;
            r.detail = err.length() > 0 ? truncate(err.toString(), 300) : "模型没有返回内容";
            return r;
        }
        r.ok = true;
        return r;
    }

    private static void openai(String base, String key, String model, String system, JSONArray messages,
                               int maxTokens, boolean stream, Callback cb) throws Exception {
        URL url = new URL(trimBase(base) + "/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (stream) body.put("stream", true);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", system == null ? "" : system));
        for (int i = 0; i < messages.length(); i++) msgs.put(messages.getJSONObject(i));
        body.put("messages", msgs);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code >= 400) {
            String err = readAll(conn.getErrorStream());
            conn.disconnect();
            cb.onError("HTTP " + code + (err.length() > 0 ? "：" + err : ""));
            return;
        }
        InputStream is = conn.getInputStream();
        if (stream) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder full = new StringBuilder();
            String line;
            boolean done = false;
            while ((line = br.readLine()) != null) {
                if (done) continue;
                String t = line.trim();
                if (t.startsWith("data:")) {
                    String data = t.substring(5).trim();
                    if (data.equals("[DONE]")) {
                        done = true;
                        break;
                    }
                    try {
                        JSONObject j = new JSONObject(data);
                        JSONArray choices = j.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject delta = choices.optJSONObject(0).optJSONObject("delta");
                            // content 为 JSON null 的分片很常见：首个分片固定是
                            // {"role":"assistant","content":null}，而推理模型在整个思考阶段
                            // 每个分片都是 {"reasoning_content":"…","content":null}。
                            // 必须走 Json.str，否则每个这样的分片都会往回复里塞一个 "null"。
                            String c = Json.str(delta, "content");
                            if (c.length() > 0) {
                                full.append(c);
                                cb.onChunk(c);
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
            br.close();
            is.close();
            conn.disconnect();
            cb.onDone(full.toString());
        } else {
            String full = readAll(is);
            is.close();
            conn.disconnect();
            String text = "";
            try {
                JSONObject j = new JSONObject(full);
                JSONArray choices = j.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject msgObj = choices.optJSONObject(0).optJSONObject("message");
                    text = Json.str(msgObj, "content");
                }
            } catch (Exception e) {
                text = full;
            }
            cb.onDone(text);
        }
    }

    private static void anthropic(String base, String key, String model, String system, JSONArray messages,
                                  int maxTokens, boolean stream, Callback cb) throws Exception {
        URL url = new URL(trimBase(base) + "/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", key);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (stream) body.put("stream", true);
        if (system != null) body.put("system", system);
        body.put("messages", messages);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code >= 400) {
            String err = readAll(conn.getErrorStream());
            conn.disconnect();
            cb.onError("HTTP " + code + (err.length() > 0 ? "：" + err : ""));
            return;
        }
        InputStream is = conn.getInputStream();
        if (stream) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder full = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("data:")) {
                    String data = t.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    try {
                        JSONObject j = new JSONObject(data);
                        if ("content_block_delta".equals(j.optString("type"))) {
                            JSONObject delta = j.optJSONObject("delta");
                            // 同 openai 分支：JSON null 必须走 Json.str，不能用 optString
                            String c = Json.str(delta, "text");
                            if (c.length() > 0) {
                                full.append(c);
                                cb.onChunk(c);
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
            br.close();
            is.close();
            conn.disconnect();
            cb.onDone(full.toString());
        } else {
            String full = readAll(is);
            is.close();
            conn.disconnect();
            String text = "";
            try {
                JSONObject j = new JSONObject(full);
                JSONArray content = j.optJSONArray("content");
                if (content != null) {
                    // 取第一个 text 块，而不是无条件取 content[0]：开了 thinking 的响应里
                    // content[0] 是 thinking 块，没有 text 键，照旧取 [0] 会得到空回复。
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject blk = content.optJSONObject(i);
                        if (blk == null || !"text".equals(Json.str(blk, "type"))) continue;
                        text = Json.str(blk, "text");
                        break;
                    }
                }
            } catch (Exception e) {
                text = full;
            }
            cb.onDone(text);
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = br.readLine()) != null) sb.append(l).append("\n");
        br.close();
        return sb.toString().trim();
    }
}
