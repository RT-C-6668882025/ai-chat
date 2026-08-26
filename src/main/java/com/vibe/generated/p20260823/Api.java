package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

    private static String trimBase(String base) {
        String s = base == null ? "" : base;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
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
                            if (delta != null && delta.has("content")) {
                                String c = delta.optString("content", "");
                                if (c.length() > 0) {
                                    full.append(c);
                                    cb.onChunk(c);
                                }
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
                    if (msgObj != null) text = msgObj.optString("content", "");
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
                            if (delta != null && delta.has("text")) {
                                String c = delta.optString("text", "");
                                if (c.length() > 0) {
                                    full.append(c);
                                    cb.onChunk(c);
                                }
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
                if (content != null && content.length() > 0) {
                    text = content.optJSONObject(0).optString("text", "");
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
