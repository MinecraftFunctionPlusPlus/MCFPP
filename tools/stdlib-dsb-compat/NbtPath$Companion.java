package moe.afox.dpsandbox.core;

import java.util.ArrayList;
import kotlin.jvm.internal.DefaultConstructorMarker;

/** Validation-only quoted-field parser; all path evaluation uses the bundled DSB NbtPath. */
public final class NbtPath$Companion {
    public NbtPath$Companion(DefaultConstructorMarker ignored) {}

    public NbtPath parse(String path) {
        var parts = new ArrayList<PathPart>();
        int index = 0;
        while (index < path.length()) {
            char current = path.charAt(index);
            if (current == '.') { index++; continue; }
            if (current == '"' || current == '\'') {
                char quote = current;
                var field = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < path.length()) {
                    char value = path.charAt(index++);
                    if (value == quote) { closed = true; break; }
                    if (value == '\\' && index < path.length()) value = path.charAt(index++);
                    field.append(value);
                }
                if (!closed) throw new IllegalArgumentException("Unclosed NBT field: " + path);
                parts.add(new PathPart.Field(field.toString()));
                continue;
            }
            if (current == '[') {
                int start = ++index;
                int depth = 0;
                char quote = 0;
                boolean escaped = false;
                while (index < path.length()) {
                    char value = path.charAt(index);
                    if (quote != 0) {
                        if (escaped) escaped = false;
                        else if (value == '\\') escaped = true;
                        else if (value == quote) quote = 0;
                    } else if (value == '"' || value == '\'') quote = value;
                    else if (value == '[' || value == '{') depth++;
                    else if (value == ']' && depth == 0) break;
                    else if (value == ']' || value == '}') depth--;
                    index++;
                }
                if (index == path.length()) throw new IllegalArgumentException("Unclosed NBT index: " + path);
                String value = path.substring(start, index++).trim();
                if (value.isEmpty()) parts.add(PathPart.All.INSTANCE);
                else if (value.startsWith("{")) parts.add(new PathPart.Match(JsonValues.INSTANCE.parse(value, null).getAsJsonObject()));
                else parts.add(new PathPart.Index(Integer.parseInt(value)));
                continue;
            }
            int start = index;
            while (index < path.length() && path.charAt(index) != '.' && path.charAt(index) != '[') index++;
            parts.add(new PathPart.Field(path.substring(start, index)));
        }
        return new NbtPath(parts);
    }

    public static void main(String[] args) {
        var object = new com.google.gson.JsonObject();
        JsonPaths.INSTANCE.set(object, "\"namespace:call.with.dot\".value", new com.google.gson.JsonPrimitive(7));
        if (JsonPaths.INSTANCE.get(object, "\"namespace:call.with.dot\".value").getAsInt() != 7) throw new AssertionError(object);
        if (!object.has("namespace:call.with.dot")) throw new AssertionError(object);
    }
}
