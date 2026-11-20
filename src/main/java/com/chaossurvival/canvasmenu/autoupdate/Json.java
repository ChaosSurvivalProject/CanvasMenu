package com.chaossurvival.canvasmenu.autoupdate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（零第三方依赖）。
 *
 * <p>StarEnchant 以「零第三方依赖」为约定，paper-api 的 compileOnly 不会传递 gson，
 * 故此处提供最小解析器，仅支持更新清单所需的 object / array / string / number / bool / null。
 * 解析结果类型：Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Long / Double / Boolean / null。
 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** 解析失败抛 IllegalArgumentException，由调用方按 result_code 上报。 */
    static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw new IllegalArgumentException("JSON trailing content at " + p.i);
        }
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        if (i >= s.length()) throw new IllegalArgumentException("JSON unexpected end");
        return s.charAt(i);
    }

    private Object value() {
        char c = peek();
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private void literal(String lit) {
        if (!s.startsWith(lit, i)) {
            throw new IllegalArgumentException("JSON invalid literal at " + i);
        }
        i += lit.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (peek() != ':') throw new IllegalArgumentException("JSON expected ':' at " + i);
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; break; }
            throw new IllegalArgumentException("JSON expected ',' or '}' at " + i);
        }
        return m;
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++; // [
        ws();
        if (peek() == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; break; }
            throw new IllegalArgumentException("JSON expected ',' or ']' at " + i);
        }
        return a;
    }

    private String string() {
        if (peek() != '"') throw new IllegalArgumentException("JSON expected string at " + i);
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek();
            i++;
            if (c == '"') break;
            if (c == '\\') {
                char e = peek();
                i++;
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalArgumentException("JSON bad unicode at " + i);
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new IllegalArgumentException("JSON bad escape at " + i);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i))
                || s.charAt(i) == '-' || s.charAt(i) == '+' || s.charAt(i) == '.'
                || s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
        }
        if (start == i) throw new IllegalArgumentException("JSON expected number at " + i);
        String num = s.substring(start, i);
        if (num.contains(".") || num.contains("e") || num.contains("E")) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }

    // ---- 便捷取值 ----

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map<?, ?>)) throw new IllegalArgumentException("JSON expected object");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        if (!(o instanceof List<?>)) throw new IllegalArgumentException("JSON expected array");
        return (List<Object>) o;
    }

    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
