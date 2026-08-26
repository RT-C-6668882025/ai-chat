package com.vibe.generated.p20260823;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal mustache-like renderer for the editable prompt template.
 * Supports:
 *   {{path.to.value}}            — value substitution
 *   {{#if path}}...{{/if}}       — conditional block
 *   {{#each path}}...{{/each}}   — array iteration ({{this}} or {{this.field}})
 */
public class Template {

    private static final Pattern EACH = Pattern.compile("\\{\\{#each ([A-Za-z0-9_.]+)\\}\\}([\\s\\S]*?)\\{\\{/each\\}\\}");
    private static final Pattern IF = Pattern.compile("\\{\\{#if ([A-Za-z0-9_.]+)\\}\\}([\\s\\S]*?)\\{\\{/if\\}\\}");
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Za-z0-9_.]+)\\}\\}");
    private static final Pattern THIS_FIELD = Pattern.compile("\\{\\{this\\.([A-Za-z0-9_]+)\\}\\}");
    private static final Pattern THIS = Pattern.compile("\\{\\{this\\}\\}");

    public static String render(String tpl, JSONObject ctx) {
        if (tpl == null) return "";
        String out = tpl;
        for (int pass = 0; pass < 6; pass++) {
            String next = expandEach(out, ctx);
            next = expandIf(next, ctx);
            if (next.equals(out)) break;
            out = next;
        }
        out = replaceTokens(out, ctx);
        return out.trim();
    }

    private static String expandEach(String tpl, JSONObject ctx) {
        Matcher m = EACH.matcher(tpl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String path = m.group(1);
            String body = m.group(2);
            Object arrObj = lookup(ctx, path);
            StringBuilder rep = new StringBuilder();
            if (arrObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) arrObj;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    String b = body;
                    if (item instanceof JSONObject) {
                        JSONObject jo = (JSONObject) item;
                        Matcher tm = THIS_FIELD.matcher(b);
                        StringBuffer s2 = new StringBuffer();
                        while (tm.find()) {
                            Object v = jo.opt(tm.group(1));
                            tm.appendReplacement(s2, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
                        }
                        tm.appendTail(s2);
                        b = s2.toString();
                    } else {
                        b = THIS.matcher(b).replaceAll(Matcher.quoteReplacement(String.valueOf(item)));
                    }
                    rep.append(b);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String expandIf(String tpl, JSONObject ctx) {
        Matcher m = IF.matcher(tpl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object v = lookup(ctx, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(isTruthy(v) ? m.group(2) : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceTokens(String tpl, JSONObject ctx) {
        Matcher m = TOKEN.matcher(tpl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object v = lookup(ctx, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v == JSONObject.NULL) return false;
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) return ((String) v).length() > 0;
        if (v instanceof JSONArray) return ((JSONArray) v).length() > 0;
        return true;
    }

    private static Object lookup(JSONObject ctx, String path) {
        String[] parts = path.split("\\.");
        Object cur = ctx;
        for (int i = 0; i < parts.length; i++) {
            if (cur == null || cur == JSONObject.NULL) return null;
            if (cur instanceof JSONObject) {
                cur = ((JSONObject) cur).opt(parts[i]);
            } else {
                return null;
            }
        }
        return cur;
    }
}
