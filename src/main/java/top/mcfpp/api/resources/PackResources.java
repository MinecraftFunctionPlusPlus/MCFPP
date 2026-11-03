package top.mcfpp.api.resources;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Shared by the compiler and editors. Resource kinds are part of identity. Offsets are UTF-16. */
public final class PackResources {
    private PackResources() {}
    public record Key(String kind, String id) {}
    public record Reference(Key key, int start, int end, boolean optional, String suffix) {}
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static boolean validId(String id) {
        return ID.matcher(id).matches() && Arrays.stream(id.substring(id.indexOf(':') + 1).split("/", -1))
                .noneMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."));
    }
    public static Key key(String kind, String value) {
        if (value.startsWith("#")) { kind = "tag/" + kind; value = value.substring(1); }
        if (!value.contains(":")) value = "minecraft:" + value;
        return validId(value) ? new Key(kind, value) : null;
    }
    public static String singular(String folder) {
        return switch (folder) {
            case "functions" -> "function"; case "advancements" -> "advancement";
            case "predicates" -> "predicate"; case "recipes" -> "recipe";
            case "loot_tables" -> "loot_table"; case "item_modifiers" -> "item_modifier";
            default -> folder;
        };
    }
    public static Key definition(Path root, Path file, boolean assets) {
        if (root == null || !file.startsWith(root)) return null;
        String[] parts = root.relativize(file).toString().replace('\\', '/').split("/", 3);
        if (parts.length < 3) return null;
        String kind = singular(parts[1]), name = parts[2];
        if (assets) {
            kind = switch (parts[1]) {
                case "models" -> "model"; case "items" -> "item_model"; case "textures" -> "texture";
                case "sounds" -> "sound"; case "font" -> "font"; case "blockstates" -> "blockstate";
                default -> "assets/" + parts[1];
            };
        } else if (kind.equals("tags")) {
            int slash = name.indexOf('/');
            if (slash < 0) return null;
            kind = "tag/" + singular(name.substring(0, slash)); name = name.substring(slash + 1);
        }
        String extension = kind.equals("function") ? ".mcfunction" : kind.equals("texture") ? ".png" : kind.equals("sound") ? ".ogg" : ".json";
        if (!name.endsWith(extension)) return null;
        return key(kind, parts[0] + ":" + name.substring(0, name.length() - extension.length()));
    }

    /** Scan known semantic fields, never arbitrary strings such as descriptions or /say arguments. */
    public static List<Reference> references(String text, Key owner, boolean mcfpp) {
        var result = new ArrayList<Reference>();
        if (owner == null || owner.kind().equals("function")) {
            int offset = 0;
            for (String line : text.split("\n", -1)) {
                int first = 0; while (first < line.length() && Character.isWhitespace(line.charAt(first))) first++;
                if (!mcfpp || first < line.length() && line.charAt(first) == '/') {
                    int start = first + (mcfpp ? 1 : 0);
                    if (start < line.length() && line.charAt(start) == '$') start++;
                    command(line.substring(start), offset + start, result);
                }
                offset += line.length() + 1;
            }
            return result;
        }
        if (!text.stripLeading().startsWith("{") && !text.stripLeading().startsWith("[")) return result;
        try { jsonRefs(new Parser(text).parse(), owner.kind(), result); }
        catch (IllegalArgumentException ignored) { /* An editor may be holding an incomplete JSON document. */ }
        return result;
    }
    private static void add(List<Reference> refs, String kind, Token token, boolean optional, String suffix) {
        if (token == null) return;
        String value = token.value;
        if (!suffix.isEmpty() && value.endsWith(suffix)) value = value.substring(0, value.length() - suffix.length());
        if (value.startsWith("#") && !kind.startsWith("tag/")) {
            // A texture variable is not a resource tag.
            if (kind.equals("texture")) return;
        }
        Key key = key(kind, value);
        if (key != null) refs.add(new Reference(key, token.start, token.end, optional, suffix));
    }
    private static void command(String line, int base, List<Reference> refs) {
        var words = new ArrayList<Token>();
        int start = -1, brackets = 0; char quote = 0; boolean escaped = false;
        for (int i = 0; i <= line.length(); i++) {
            char c = i == line.length() ? ' ' : line.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '[' || c == '{') brackets++;
            else if (c == ']' || c == '}') brackets--;
            if (start < 0 && !Character.isWhitespace(c)) start = i;
            if (start >= 0 && Character.isWhitespace(c) && quote == 0 && brackets == 0) {
                words.add(new Token(line.substring(start, i), base + start, base + i)); start = -1;
            }
        }
        command(words, 0, refs);
        if (!words.isEmpty() && Set.of("give", "item", "execute", "minecraft:give", "minecraft:item", "minecraft:execute").contains(words.getFirst().value)) {
            var component = Pattern.compile("(?:minecraft:)?item_model\\s*[=:]\\s*[\"']([a-z0-9_.-]+:[a-z0-9_./-]+)[\"']").matcher(line);
            while (component.find()) add(refs, "item_model", new Token(component.group(1), base + component.start(1), base + component.end(1)), false, "");
        }
    }
    private static void command(List<Token> w, int at, List<Reference> refs) {
        if (at >= w.size()) return;
        String head = w.get(at).value;
        if (head.startsWith("minecraft:")) head = head.substring(10);
        int index = -1; String kind = null;
        switch (head) {
            case "function" -> { index = at + 1; kind = "function"; }
            case "schedule" -> { index = at + 2; kind = "function"; }
            case "playsound" -> { index = at + 1; kind = "sound_event"; }
            case "stopsound" -> { index = at + 3; kind = "sound_event"; }
            case "execute" -> {
                for (int i = at + 1; i < w.size(); i++) {
                    if (w.get(i).value.equals("run")) { command(w, i + 1, refs); break; }
                    if ((w.get(i).value.equals("if") || w.get(i).value.equals("unless")) && i + 2 < w.size()
                            && Set.of("predicate", "function").contains(w.get(i + 1).value))
                        add(refs, w.get(i + 1).value, w.get(i + 2), false, "");
                }
            }
            case "advancement" -> {
                for (int i = at + 1; i + 1 < w.size(); i++)
                    if (Set.of("only", "from", "through", "until").contains(w.get(i).value)) {
                        index = i + 1; kind = "advancement"; break;
                    }
            }
            case "loot" -> {
                for (int i = at + 1; i + 1 < w.size(); i++) if (w.get(i).value.equals("loot")) {
                    index = i + 1; kind = "loot_table"; break;
                }
            }
        }
        if (index >= 0 && index < w.size()) add(refs, kind, w.get(index), false, "");
    }

    private static void jsonRefs(Node node, String owner, List<Reference> refs) {
        if (node.fields != null) {
            if (owner.startsWith("tag/")) {
                Node values = node.fields.get("values");
                if (values != null && values.items != null) for (Node item : values.items) {
                    boolean optional = item.fields != null && "false".equals(item.field("required").value);
                    add(refs, owner.substring(4), item.token != null ? item.token : item.field("id").token, optional, "");
                }
                return;
            }
            for (var field : node.fields.entrySet()) {
                String name = field.getKey(); Node value = field.getValue();
                if (name.equals("parent")) {
                    if (owner.equals("model")) add(refs, "model", value.token, false, "");
                    if (owner.equals("advancement")) add(refs, "advancement", value.token, false, "");
                }
                if (owner.equals("advancement") && name.equals("rewards"))
                    add(refs, "function", value.field("function").token, false, "");
                if ((owner.equals("model") || owner.equals("item_model") || owner.equals("blockstate")) && name.equals("model"))
                    add(refs, "model", value.token, false, "");
                if (owner.equals("model") && name.equals("textures") && value.fields != null)
                    value.fields.values().forEach(t -> add(refs, "texture", t.token, false, ""));
                if (name.equals("minecraft:item_model")) add(refs, "item_model", value.token, false, "");
                if (owner.equals("font")) {
                    if (name.equals("file") && node.field("type").value.endsWith("bitmap")) add(refs, "texture", value.token, false, ".png");
                    if (name.equals("id") && node.field("type").value.endsWith("reference")) add(refs, "font", value.token, false, "");
                }
                if (name.equals("name") && node.field("condition").value.endsWith(":reference")) add(refs, "predicate", value.token, false, "");
                if ((name.equals("name") || name.equals("value")) && node.field("type").value.endsWith(":loot_table")) add(refs, "loot_table", value.token, false, "");
                jsonRefs(value, owner, refs);
            }
        }
        if (node.items != null) node.items.forEach(item -> jsonRefs(item, owner, refs));
    }
    /** Custom sounds have multiple declarations in one sounds.json. */
    public static Map<Key, Integer> sounds(String namespace, String text) {
        var result = new LinkedHashMap<Key, Integer>();
        try {
            Node root = new Parser(text).parse();
            if (root.fields != null) root.keys.forEach((name, token) -> {
                Key key = key("sound_event", namespace + ":" + name); if (key != null) result.put(key, token.start);
            });
        } catch (IllegalArgumentException ignored) {}
        return result;
    }
    public static List<Reference> soundReferences(String text) {
        var refs = new ArrayList<Reference>();
        try {
            Node root = new Parser(text).parse();
            if (root.fields != null) for (Node event : root.fields.values()) {
                Node sounds = event.field("sounds");
                if (sounds.items != null) for (Node sound : sounds.items) {
                    add(refs, sound.field("type").value.equals("event") ? "sound_event" : "sound",
                            sound.token != null ? sound.token : sound.field("name").token, false, "");
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return refs;
    }
    private record Token(String value, int start, int end) {}
    private static final class Node {
        static final Node EMPTY = new Node();
        Token token; String value = ""; Map<String, Node> fields; Map<String, Token> keys; List<Node> items;
        Node field(String name) { return fields == null ? EMPTY : fields.getOrDefault(name, EMPTY); }
    }
    /** Small span-preserving JSON reader; no dependency on a particular IDE or compiler JSON library. */
    private static final class Parser {
        final String text; int at; int depth;
        Parser(String text) { this.text = text; }
        void ws() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
        Node parse() { Node node = read(); ws(); if (at != text.length()) throw new IllegalArgumentException(); return node; }
        Node read() {
            if (++depth > 256) throw new IllegalArgumentException();
            ws(); Node node = new Node(); char c = peek();
            if (c == '{') {
                at++; node.fields = new LinkedHashMap<>(); node.keys = new LinkedHashMap<>(); ws();
                if (peek() != '}') do {
                    ws(); Token key = string(); ws(); expect(':'); Node child = read();
                    node.fields.put(key.value, child); node.keys.put(key.value, key); ws();
                    if (peek() != ',') break; at++;
                } while (true);
                expect('}');
            } else if (c == '[') {
                at++; node.items = new ArrayList<>(); ws();
                if (peek() != ']') do { node.items.add(read()); ws(); if (peek() != ',') break; at++; } while (true);
                expect(']');
            } else if (c == '"') { node.token = string(); node.value = node.token.value; }
            else {
                int start = at; while (at < text.length() && ",]} \r\n\t".indexOf(text.charAt(at)) < 0) at++;
                if (start == at) throw new IllegalArgumentException(); node.value = text.substring(start, at);
            }
            depth--; return node;
        }
        char peek() { if (at >= text.length()) throw new IllegalArgumentException(); return text.charAt(at); }
        void expect(char c) { if (peek() != c) throw new IllegalArgumentException(); at++; }
        Token string() {
            expect('"'); int start = at; var value = new StringBuilder();
            while (peek() != '"') {
                char c = text.charAt(at++);
                if (c == '\\') {
                    c = peek(); at++;
                    if (c == 'u') { if (at + 4 > text.length()) throw new IllegalArgumentException();
                        c = (char) Integer.parseInt(text.substring(at, at + 4), 16); at += 4;
                    } else c = switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> c; };
                }
                value.append(c);
            }
            int end = at++; return new Token(value.toString(), start, end);
        }
    }
}
