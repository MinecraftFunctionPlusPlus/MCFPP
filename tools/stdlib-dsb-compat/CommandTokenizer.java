package moe.afox.dpsandbox.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation-only adapter for the bundled DSB tokenizer. Minecraft NBT paths such as
 * "namespace:function".value are one argument. The bundled tokenizer splits at the
 * closing quote and also removes quotes from standalone NBT path nodes.
 * This adapter only tokenizes commands; all execution and assertions use DSB.
 */
public final class CommandTokenizer {
    public static final CommandTokenizer INSTANCE = new CommandTokenizer();

    public List<CommandToken> tokenize(String command, SourceLocation location) {
        var tokens = new ArrayList<CommandToken>();
        int index = 0;
        while (index < command.length()) {
            while (index < command.length() && Character.isWhitespace(command.charAt(index))) index++;
            if (index == command.length()) break;
            int start = index;
            char quote = 0;
            int objects = 0, arrays = 0;
            boolean escaped = false;
            while (index < command.length()) {
                char c = command.charAt(index);
                if (quote != 0) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == quote) quote = 0;
                } else {
                    if (Character.isWhitespace(c) && objects == 0 && arrays == 0) break;
                    if (c == '\'' || c == '"') quote = c;
                    else if (c == '{') objects++;
                    else if (c == '}') objects--;
                    else if (c == '[') arrays++;
                    else if (c == ']') arrays--;
                }
                index++;
            }
            if (quote != 0 || objects != 0 || arrays != 0) throw new IllegalArgumentException("Unterminated command token at " + start);
            String raw = command.substring(start, index);
            String text = raw;
            int size = tokens.size();
            boolean nbtPath = size >= 2 && (tokens.get(size - 2).getText().equals("storage") || tokens.get(size - 2).getText().equals("entity"))
                || size >= 4 && tokens.get(size - 4).getText().equals("block");
            char first = raw.charAt(0);
            if (!nbtPath && (first == '"' || first == '\'') && raw.length() >= 2 && raw.charAt(raw.length() - 1) == first) {
                var decoded = new StringBuilder();
                for (int i = 1; i < raw.length() - 1; i++) {
                    char c = raw.charAt(i);
                    if (c == '\\' && i + 1 < raw.length() - 1) c = raw.charAt(++i);
                    decoded.append(c);
                }
                text = decoded.toString();
            }
            tokens.add(new CommandToken(text, start, index));
        }
        return tokens;
    }

    public static List<CommandToken> tokenize$default(CommandTokenizer self, String command, SourceLocation location, int mask, Object unused) {
        return self.tokenize(command, (mask & 2) != 0 ? null : location);
    }

    public String tailFrom(String command, CommandToken token) { return command.substring(token.getStart()).trim(); }
    public String tailAfter(String command, CommandToken token) { return command.substring(token.getEnd()).stripLeading(); }

    public static void main(String[] args) {
        var suffix = INSTANCE.tokenize("data modify storage x:y \"root:call.with.dot\".value set value 1", null);
        if (suffix.size() != 8 || !suffix.get(4).getText().equals("\"root:call.with.dot\".value")) throw new AssertionError(suffix);
        var root = INSTANCE.tokenize("data get storage x:y \"root:call.with.dot\"", null);
        if (!root.get(4).getText().equals("\"root:call.with.dot\"")) throw new AssertionError(root);
    }
}
