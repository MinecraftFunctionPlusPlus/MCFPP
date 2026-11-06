package top.mcfpp.browser.benchmark;

import com.alibaba.fastjson2.JSON;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import top.mcfpp.api.library.PlaygroundCompilerApi;

/** Same sources and reusable kernel as browser acceptance; includes first-use preparation. */
public final class NativeBenchmark {
    public static void main(String[] args) throws Exception {
        top.mcfpp.library.compiler.BuiltinNativeClasses.install(top.mcfpp.browser.GeneratedNativeRoots.classes());
        var cases = JSON.parseArray(Files.readString(Path.of(args[0])));
        Path report = Path.of(args[1]);
        Files.createDirectories(report.getParent());
        Path workspace = Files.createTempDirectory(report.getParent(), "native-");
        var results = new ArrayList<Object>();
        try (var kernel = PlaygroundCompilerApi.openKernel(workspace.resolve("kernel"), "1.21.8", "playground")) {
            for (Object value : cases) {
                var item = (com.alibaba.fastjson2.JSONObject) value;
                String name = item.getString("name"), source = item.getString("source");
                long mark = System.nanoTime();
                var result = PlaygroundCompilerApi.compile(kernel, source);
                double elapsed = (System.nanoTime() - mark) / 1e6;
                boolean expectedSuccess = !name.equals("error") && !Boolean.FALSE.equals(item.get("expectedSuccess"));
                if (!Boolean.valueOf(expectedSuccess).equals(result.get("success")) || !Boolean.TRUE.equals(result.get("kernelUsable"))) {
                    throw new AssertionError(name + ": " + result.get("diagnostics"));
                }
                if (!expectedSuccess && !((java.util.List<?>) result.get("files")).isEmpty()) {
                    throw new AssertionError(name + ": failed compilation retained files");
                }
                if (java.util.Set.of("cold", "warm", "modified", "recovered", "final-default").contains(name)) {
                    var main = ((java.util.List<?>) result.get("files")).stream().map(file -> (Map<?, ?>) file)
                            .filter(file -> "data/playground/function/main.mcfunction".equals(file.get("path")))
                            .findFirst().orElseThrow();
                    var lines = ((String) main.get("content")).lines().toList();
                    if (lines.size() != 2 || lines.stream().anyMatch(line -> !line.startsWith("tellraw @a "))) {
                        throw new AssertionError(name + ": default example must reduce to two output commands: " + lines);
                    }
                }
                results.add(Map.of("name", name, "source", source, "wallMs", elapsed, "result", result));
                System.out.printf("%s: %.2f ms; success=%s%n", name, elapsed, result.get("success"));
            }
        } finally {
            // Only the fresh workspace created by this invocation is eligible for deletion.
            try (var paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.writeString(report, JSON.toJSONString(results));
    }
}
