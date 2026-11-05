package top.mcfpp.browser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** JDK operations missing from TeaVM 0.16, kept behind the build-only adapter plugin. */
public final class BrowserJdk {
    private BrowserJdk() { }
    public static Path fileToPath(File file) { return Path.of(file.getPath()); }
    public static String property(String key) { return System.getProperty(key); }
    public static Stream<String> lines(String text) {
        if (text.isEmpty()) return Stream.empty();
        String[] parts = text.split("\\r\\n|\\r|\\n", -1);
        return Arrays.stream(parts, 0, parts.length - (parts[parts.length - 1].isEmpty() ? 1 : 0));
    }
    public static IntStream intStream(Spliterator.OfInt items, boolean parallel) {
        var builder = IntStream.builder(); items.forEachRemaining((int value) -> builder.accept(value)); return builder.build();
    }
    public static long uuidMost(UUID value) { return uuidWord(value.toString().replace("-", ""), 0); }
    public static long uuidLeast(UUID value) { return uuidWord(value.toString().replace("-", ""), 16); }
    private static long uuidWord(String text, int start) {
        long result = 0;
        for (int i = start; i < start + 16; i++) result = (result << 4) | Character.digit(text.charAt(i), 16);
        return result;
    }
    public static long transferTo(Reader input, Writer output) throws IOException {
        char[] buffer = new char[8192];
        long total = 0;
        for (int count; (count = input.read(buffer)) >= 0;) {
            output.write(buffer, 0, count); total += count;
        }
        return total;
    }
    public static boolean trySetAccessible(AccessibleObject member) { member.setAccessible(true); return true; }
    public static Class<?> loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
    public static String genericMethodText(Method method) { return method.toString(); }
    public static Method getDeclaredMethod(Class<?> type, String name, Class<?>[] args) throws NoSuchMethodException {
        try { return type.getDeclaredMethod(name, args); }
        catch (NoSuchMethodException failure) { throw new NoSuchMethodException(type.getName() + "." + name + Arrays.toString(args)); }
    }
    public static Method getMethod(Class<?> type, String name, Class<?>[] args) throws NoSuchMethodException {
        try { return type.getMethod(name, args); }
        catch (NoSuchMethodException failure) { throw new NoSuchMethodException(type.getName() + "." + name + Arrays.toString(args)); }
    }
    public static Constructor<?> getDeclaredConstructor(Class<?> type, Class<?>[] args) throws NoSuchMethodException {
        try { return type.getDeclaredConstructor(args); }
        catch (NoSuchMethodException failure) { throw new NoSuchMethodException(type.getName() + ".<init>" + Arrays.toString(args)); }
    }
    public static Constructor<?> getConstructor(Class<?> type, Class<?>[] args) throws NoSuchMethodException {
        try { return type.getConstructor(args); }
        catch (NoSuchMethodException failure) { throw new NoSuchMethodException(type.getName() + ".<init>" + Arrays.toString(args)); }
    }
    public static boolean isAnonymousClass(Class<?> type) { return type.getSimpleName().isEmpty(); }
    public static String characterString(int value) { return new String(Character.toChars(value)); }
    public static int parseUnsignedInt(String text, int radix) {
        long value = Long.parseLong(text, radix);
        if (value < 0 || value > 0xffffffffL) throw new NumberFormatException(text);
        return (int) value;
    }
    public static long parseUnsignedLong(String text, int radix) {
        BigInteger value = new BigInteger(text, radix);
        if (value.signum() < 0 || value.bitLength() > 64) throw new NumberFormatException(text);
        return value.longValue();
    }

    private static Map<String, Integer> unicodeNames;
    public static int codePointOf(String name) {
        if (unicodeNames == null) {
            unicodeNames = new HashMap<>();
            try (var stream = BrowserJdk.class.getClassLoader().getResourceAsStream("unicode-names.txt")) {
                if (stream == null) throw new IllegalStateException("Missing Unicode name index");
                new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                    int tab = line.indexOf('\t');
                    unicodeNames.put(line.substring(tab + 1), Integer.parseInt(line.substring(0, tab), 16));
                });
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }
        Integer value = unicodeNames.get(name.trim().toUpperCase(Locale.ROOT));
        if (value == null) throw new IllegalArgumentException("Unrecognized character name: " + name);
        return value;
    }
}
