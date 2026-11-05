package top.mcfpp.browser.json;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.alibaba.fastjson2.JSONWriter;
import top.mcfpp.browser.BrowserCompiler;

/** JSON values only; arbitrary JVM bean construction is deliberately absent from this boundary. */
public final class JSON {
    private JSON() { }
    public static String toJSONString(Object value) { return BrowserCompiler.json(value); }
    public static String toJSONString(Object value, JSONWriter.Feature... features) {
        for (var feature : features) if (feature == JSONWriter.Feature.PrettyFormat) return pretty(value, 0);
        return toJSONString(value);
    }
    public static byte[] toJSONBytes(Object value, JSONWriter.Feature... features) {
        return toJSONString(value, features).getBytes(StandardCharsets.UTF_8);
    }
    public static byte[] toJSONBytes(Object value) { return toJSONBytes(value, new JSONWriter.Feature[0]); }
    public static boolean isValid(String text) {
        try { parse(text); return true; } catch (IllegalArgumentException failure) { return false; }
    }
    public static Object parse(String text) { return new Parser(text).parse(); }
    public static Object parse(byte[] bytes) { return parse(new String(bytes, StandardCharsets.UTF_8)); }
    public static com.alibaba.fastjson2.JSONObject parseObject(String text) { return (com.alibaba.fastjson2.JSONObject) parse(text); }
    public static com.alibaba.fastjson2.JSONObject parseObject(byte[] bytes) { return (com.alibaba.fastjson2.JSONObject) parse(bytes); }
    public static com.alibaba.fastjson2.JSONArray parseArray(String text) { return (com.alibaba.fastjson2.JSONArray) parse(text); }

    /** Match Fastjson's tab-indented resources so backend migration does not change artifacts. */
    private static String pretty(Object value, int depth) {
        String indent = "\t".repeat(depth + 1);
        StringBuilder result = new StringBuilder();
        if (value instanceof Map<?, ?> map) {
            result.append('{');
            String separator = "\n";
            for (var entry : map.entrySet()) {
                result.append(separator).append(indent).append(toJSONString(entry.getKey())).append(':').append(pretty(entry.getValue(), depth + 1));
                separator = ",\n";
            }
            if (!map.isEmpty()) result.append('\n').append("\t".repeat(depth));
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            result.append('[');
            boolean empty = true;
            for (Object item : items) {
                result.append(empty ? "\n" : ",\n").append(indent).append(pretty(item, depth + 1)); empty = false;
            }
            if (!empty) result.append('\n').append("\t".repeat(depth));
            return result.append(']').toString();
        }
        return toJSONString(value);
    }

    /** Strict recursive descent parser; preserves JSON object order and integral values. */
    private static final class Parser {
        private final String text;
        private int position;
        Parser(String text) { this.text = text; }
        Object parse() {
            Object result = value();
            space();
            if (position != text.length()) throw invalid();
            return result;
        }
        private void space() {
            while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) position++;
        }
        private boolean take(char c) {
            space();
            if (position < text.length() && text.charAt(position) == c) { position++; return true; }
            return false;
        }
        private void expect(char c) { if (!take(c)) throw invalid(); }
        private Object value() {
            space();
            if (position == text.length()) throw invalid();
            char c = text.charAt(position);
            if (c == '"') return string();
            if (take('{')) {
                com.alibaba.fastjson2.JSONObject object = new com.alibaba.fastjson2.JSONObject();
                if (take('}')) return object;
                do { space(); String key = string(); expect(':'); object.put(key, value()); } while (take(','));
                expect('}'); return object;
            }
            if (take('[')) {
                com.alibaba.fastjson2.JSONArray array = new com.alibaba.fastjson2.JSONArray();
                if (take(']')) return array;
                do { array.add(value()); } while (take(','));
                expect(']'); return array;
            }
            for (String literal : new String[] {"true", "false", "null"}) {
                if (text.startsWith(literal, position)) {
                    position += literal.length();
                    return literal.equals("null") ? null : literal.equals("true");
                }
            }
            int start = position;
            if (text.charAt(position) == '-') position++;
            if (position == text.length() || !digit(text.charAt(position))) throw invalid();
            if (text.charAt(position) == '0') position++;
            else while (position < text.length() && digit(text.charAt(position))) position++;
            boolean decimal = false;
            if (position < text.length() && text.charAt(position) == '.') {
                decimal = true; position++; digits();
            }
            if (position < text.length() && "eE".indexOf(text.charAt(position)) >= 0) {
                decimal = true; position++;
                if (position < text.length() && "+-".indexOf(text.charAt(position)) >= 0) position++;
                digits();
            }
            String number = text.substring(start, position);
            if (decimal) return Double.valueOf(number);
            return Long.valueOf(number);
        }
        private void digits() {
            int start = position;
            while (position < text.length() && digit(text.charAt(position))) position++;
            if (position == start) throw invalid();
        }
        private static boolean digit(char value) { return value >= '0' && value <= '9'; }
        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') return result.toString();
                if (c < 32) throw invalid();
                if (c == '\\') {
                    if (position == text.length()) throw invalid();
                    c = text.charAt(position++);
                    switch (c) {
                        case '"', '\\', '/' -> result.append(c);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) throw invalid();
                            result.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                            position += 4;
                        }
                        default -> throw invalid();
                    }
                } else result.append(c);
            }
            throw invalid();
        }
        private IllegalArgumentException invalid() {
            return new IllegalArgumentException("Invalid JSON at offset " + position);
        }
    }
}
