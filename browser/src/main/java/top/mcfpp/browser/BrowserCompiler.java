package top.mcfpp.browser;

import java.nio.file.Path;
import java.util.Map;
import org.teavm.jso.JSExport;
import top.mcfpp.api.library.PlaygroundCompilerApi;

/** TeaVM entry point. One worker owns one kernel and serializes its requests. */
public final class BrowserCompiler {
    private static AutoCloseable kernel;

    private BrowserCompiler() { }

    public static void main(String[] args) {
        top.mcfpp.library.compiler.BuiltinNativeClasses.install(GeneratedNativeRoots.classes());
    }

    @JSExport
    public static String compile(String source) {
        if (kernel == null) {
            kernel = PlaygroundCompilerApi.openKernel(Path.of("/playground"), "1.21.8", "playground");
        }
        return json(PlaygroundCompilerApi.compile(kernel, source));
    }

    // The wire result contains only maps, lists, strings, numbers and booleans.
    // Avoid a reflective JSON serializer in this closed browser boundary.
    public static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) {
            StringBuilder result = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    case '\b' -> result.append("\\b");
                    case '\f' -> result.append("\\f");
                    default -> {
                        if (c < 32) result.append(String.format("\\u%04x", (int) c));
                        else result.append(c);
                    }
                }
            }
            return result.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            String separator = "";
            for (var entry : map.entrySet()) {
                result.append(separator).append(json(entry.getKey())).append(':').append(json(entry.getValue()));
                separator = ",";
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder result = new StringBuilder("[");
            String separator = "";
            for (Object item : items) {
                result.append(separator).append(json(item));
                separator = ",";
            }
            return result.append(']').toString();
        }
        return value.toString();
    }
}
