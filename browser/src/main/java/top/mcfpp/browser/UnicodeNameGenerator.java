package top.mcfpp.browser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Build-time JDK 25 Unicode names for SNBT's named character escapes. Never runs in the browser. */
public final class UnicodeNameGenerator {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (int code = 0; code <= Character.MAX_CODE_POINT; code++) {
                String name = Character.getName(code);
                if (name != null) { writer.write(Integer.toHexString(code) + "\t" + name); writer.newLine(); }
            }
        }
    }
}
