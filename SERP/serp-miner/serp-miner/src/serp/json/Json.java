package serp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser. Semantic Scholar, OpenAlex and CrossRef all
 * return JSON; parsing it by hand keeps the project buildable with plain {@code javac}
 * (no Gson/Jackson jar to place on the classpath — which matters given how fiddly
 * classpaths are on Windows).
 *
 * <p>{@link #parse(String)} returns a tree of {@link Map}{@code <String,Object>},
 * {@link List}{@code <Object>}, {@link String}, {@link Double}, {@link Boolean} and
 * {@code null}. The static accessors ({@link #str}, {@link #num}, {@link #obj},
 * {@link #arr}) read that tree defensively — a missing or wrong-typed key yields a
 * sensible default rather than an exception, because real API responses are ragged
 * (missing abstracts, absent fields, etc.).
 *
 * <p>Not a general-purpose library — just enough correct JSON to map three APIs. It
 * is covered by {@code JsonSmokeTest}.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        Json p = new Json(json);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) {
            throw new IllegalArgumentException("trailing characters at index " + p.i);
        }
        return v;
    }

    private Object value() {
        char c = peek();
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't', 'f' -> bool();
            case 'n' -> nul();
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        expect('{');
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value());
            ws();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw err("expected ',' or '}'");
        }
        return m;
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        expect('[');
        ws();
        if (peek() == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            char c = next();
            if (c == ']') break;
            if (c != ',') throw err("expected ',' or ']'");
        }
        return a;
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') break;
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> b.append('"');
                    case '\\' -> b.append('\\');
                    case '/' -> b.append('/');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        b.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw err("bad escape \\" + e);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        return Double.parseDouble(s.substring(start, i));
    }

    private Object bool() {
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        throw err("invalid literal");
    }

    private Object nul() {
        if (s.startsWith("null", i)) { i += 4; return null; }
        throw err("invalid literal");
    }

    // --- lexer helpers ---
    private char peek() { return s.charAt(i); }
    private char next() { return s.charAt(i++); }
    private void expect(char c) { if (next() != c) throw err("expected '" + c + "'"); }
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException("JSON parse error: " + m + " at index " + i);
    }

    // === typed, defensive accessors ==========================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return (o instanceof List) ? (List<Object>) o : List.of();
    }

    /** String value for a key, or "" if absent/null/non-string. */
    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof String s) ? s : "";
    }

    /** Numeric value for a key as int, or 0 if absent/non-numeric. */
    public static int num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Double d) ? (int) Math.round(d) : 0;
    }
}
