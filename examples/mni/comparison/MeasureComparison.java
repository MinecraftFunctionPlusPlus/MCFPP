import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import top.mcfpp.nbt.tags.Tag;
import top.mcfpp.test.util.ContextBlock;
import top.mcfpp.test.util.ContextBlockTest;
import top.mcfpp.test.util.ContextCommandExecutor;
import top.mcfpp.test.util.ContextPosition;
import top.mcfpp.test.util.ContextRotation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes the actual CLI-generated MNI functions and the two literal baseline command maps with
 * the strict {@link ContextCommandExecutor}, checks behavior equivalence, and records counts.
 *
 * <p>Emitted files are never edited. Observation probes are appended to an in-memory copy of the
 * entry function immediately before its real caller-frame pop, and are removed from every count.
 * The driver only reads {@code --data-root}; it never writes into the CLI output tree.</p>
 */
public final class MeasureComparison {
    private static final String SCHEMA = "mcfpp-comparison-measurement/1";
    private static final String PUSH = "data modify storage mcfpp:system stack_frame prepend value {}";
    private static final String POP = "data remove storage mcfpp:system stack_frame[0]";
    private static final String PROBE_PREFIX = "data modify storage verification:comparison ";
    private static final String SCORE_PROBE_PREFIX = "execute store result storage verification:comparison ";
    private static final Pattern FUNCTION_CALL =
        Pattern.compile("\\bfunction\\s+([A-Za-z0-9_.+:/-]+)");
    private static final Pattern FROM_STORAGE =
        Pattern.compile("^data modify storage \\S+ \\S+ (?:set|merge|append|prepend) from storage ");
    private static final Pattern STORE_STORAGE =
        Pattern.compile("^execute store (?:result|success) storage ");
    private static final Pattern STACK_FRAME_ONE = Pattern.compile("stack_frame\\[1\\]");

    private MeasureComparison() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        Path dataRoot = Path.of(required(options, "--data-root"));
        Path baselinesPath = Path.of(required(options, "--baselines"));
        Path output = Path.of(required(options, "--out"));
        int commandBudget = Integer.parseInt(options.getOrDefault("--command-budget", "20000"));

        JSONObject baselines = JSON.parseObject(Files.readString(baselinesPath, StandardCharsets.UTF_8));
        Generated generated = readGenerated(dataRoot);
        World world = readWorld(baselines.getJSONObject("world"));
        String generatedNamespace = baselines.getString("generatedNamespace");

        JSONObject result = new JSONObject();
        result.put("schema", SCHEMA);
        result.put("generatedAtUtc", Instant.now().toString());
        result.put("commandBudget", commandBudget);
        result.put("world", baselines.getJSONObject("world"));
        result.put("definitions", definitions());

        JSONArray scenarioResults = new JSONArray();
        JSONArray kernels = new JSONArray();
        JSONArray scenarios = baselines.getJSONArray("scenarios");
        if (scenarios == null || scenarios.size() != 5) {
            throw new IllegalStateException("Expected exactly five comparison scenarios, found "
                + (scenarios == null ? "none" : scenarios.size()));
        }
        for (int index = 0; index < scenarios.size(); index++) {
            JSONObject scenario = scenarios.getJSONObject(index);
            kernels.add(scenario.getString("kernel"));
            scenarioResults.add(measureScenario(scenario, generated, world, commandBudget));
        }
        result.put("scenarios", scenarioResults);

        JSONObject project = new JSONObject();
        long projectBytes = 0;
        for (Path file : generated.files.values()) projectBytes += Files.size(file);
        project.put("generatedNamespace", generatedNamespace);
        project.put("functionFiles", generated.files.size());
        project.put("functionBytes", projectBytes);
        project.put("macroFiles", generated.macros.size());
        project.put("kernels", kernels);
        result.put("project", project);

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat),
            StandardCharsets.UTF_8);
        System.out.println("Measured " + scenarioResults.size() + " scenarios x 3 variants; "
            + "generated project has " + generated.files.size() + " function files.");
    }

    private static JSONObject measureScenario(JSONObject scenario, Generated generated, World world,
                                              int commandBudget) throws IOException {
        JSONObject out = new JSONObject();
        String id = scenario.getString("id");
        String kernel = scenario.getString("kernel");
        out.put("id", id);
        out.put("domain", scenario.getString("domain"));
        out.put("kernel", kernel);
        out.put("probes", scenario.getJSONArray("probes"));
        out.put("expected", scenario.getJSONObject("expected"));
        Object expectedBlockTests = scenario.get("expectedBlockTests");
        out.put("expectedBlockTests", expectedBlockTests);
        out.put("expectedScores", scenario.getJSONObject("expectedScores"));

        List<String> probes = strings(scenario.getJSONArray("probes"));
        Map<String, Integer> expected = integers(scenario.getJSONObject("expected"));
        Map<String, Integer> expectedScores = integers(scenario.getJSONObject("expectedScores"));
        Integer blockTestExpectation = expectedBlockTests instanceof Number
            ? ((Number) expectedBlockTests).intValue() : null;

        JSONArray variants = new JSONArray();
        variants.add(measureVariant("mni", kernel, generated.functions, generated.macros,
            generated.files, probes, expected, blockTestExpectation, expectedScores, world,
            commandBudget));
        JSONObject baselineVariants = scenario.getJSONObject("variants");
        for (String name : baselineVariants.keySet()) {
            JSONObject variant = baselineVariants.getJSONObject(name);
            Map<String, List<String>> functions = readBaselineFunctions(variant, id, name);
            variants.add(measureVariant(name, variant.getString("entry"), functions, Map.of(),
                Map.of(), probes, expected, blockTestExpectation, expectedScores, world,
                commandBudget));
        }
        out.put("variants", variants);
        if (variants.size() != 3) {
            throw new IllegalStateException("Scenario '" + id + "' must have three variants, found "
                + variants.size());
        }
        return out;
    }

    private static JSONObject measureVariant(String name, String entry,
                                             Map<String, List<String>> functions,
                                             Map<String, String> macros,
                                             Map<String, Path> files,
                                             List<String> probes,
                                             Map<String, Integer> expected,
                                             Integer expectedBlockTests,
                                             Map<String, Integer> expectedScores,
                                             World world,
                                             int commandBudget) throws IOException {
        Map<String, List<String>> callMap = new LinkedHashMap<>(functions);
        List<String> entryBody = callMap.get(entry);
        if (entryBody == null) {
            String prefix = entry.contains(":")
                ? entry.substring(0, entry.indexOf(':') + 1) : "";
            List<String> candidates = callMap.keySet().stream()
                .filter(key -> key.contains(":"))
                .filter(key -> key.startsWith(prefix))
                .sorted()
                .toList();
            throw new IllegalStateException("Missing function '" + entry + "'. Available functions: "
                + candidates);
        }
        callMap = withProbes(entry, callMap, probes);

        ContextCommandExecutor machine;
        try {
            machine = new ContextCommandExecutor(
                List.of("function " + entry),
                callMap,
                List.of(),
                null,
                new ContextPosition(world.x, world.y, world.z),
                new ContextRotation(world.yaw, world.pitch),
                (x, y, z) -> world.stoneBlocks.contains(x + "," + y + "," + z)
                    ? new ContextBlock(world.block, Map.of(), Set.of()) : null,
                Map.of(),
                macros,
                commandBudget);
        } catch (RuntimeException | Error failure) {
            throw new IllegalStateException("Scenario variant '" + name
                + "' failed while executing '" + entry + "': " + failure, failure);
        }

        List<String> trace = new ArrayList<>(machine.getExecutedFunctionLines());
        List<String> executed = new ArrayList<>(machine.getExecuted());
        String rootCall = "function " + entry;
        if (!trace.isEmpty() && trace.get(0).equals(rootCall)) trace.remove(0);
        if (!executed.isEmpty() && executed.get(0).equals(rootCall)) executed.remove(0);
        trace.removeIf(MeasureComparison::isProbe);
        executed.removeIf(MeasureComparison::isProbe);

        Map<String, Integer> observed = new LinkedHashMap<>();
        for (String probe : probes) {
            Tag<?> tag;
            try {
                tag = machine.readStorage("verification:comparison", probe);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Scenario variant '" + name
                    + "' did not produce probe '" + probe + "' for '" + entry + "'", failure);
            }
            observed.put(probe, ((Number) tag.getValue()).intValue());
        }
        if (!observed.equals(expected)) {
            throw new IllegalStateException("Scenario variant '" + name + "' observed " + observed
                + " but expected " + expected);
        }

        int blockTests = machine.getBlockTests().size();
        if (expectedBlockTests != null && blockTests != expectedBlockTests) {
            throw new IllegalStateException("Scenario variant '" + name + "' executed " + blockTests
                + " block tests but expected " + expectedBlockTests);
        }
        if (expected.containsKey("hits")) {
            long matched = machine.getBlockTests().stream()
                .filter(ContextBlockTest::getMatched)
                .count();
            if (matched != expected.get("hits")) {
                throw new IllegalStateException("Scenario variant '" + name + "' matched " + matched
                    + " block tests but expected hits=" + expected.get("hits"));
            }
        }

        Map<String, Integer> observedScores = new LinkedHashMap<>();
        for (String key : expectedScores.keySet()) {
            Integer value = machine.getValues().get(key);
            if (value == null) {
                throw new IllegalStateException("Scenario variant '" + name + "' is missing score '"
                    + key + "'");
            }
            observedScores.put(key, value);
        }
        if (!observedScores.equals(expectedScores)) {
            throw new IllegalStateException("Scenario variant '" + name + "' observed scores "
                + observedScores + " but expected " + expectedScores);
        }

        int pushes = countExact(executed, PUSH);
        int pops = countExact(executed, POP);
        if (pushes != pops) {
            throw new IllegalStateException("Scenario variant '" + name + "' left "
                + pushes + " stack pushes and " + pops + " stack pops");
        }
        if (pushes < 1) {
            throw new IllegalStateException("Scenario variant '" + name
                + "' has no ordinary caller frame");
        }

        Map<String, List<String>> universe = new LinkedHashMap<>(functions);
        for (Map.Entry<String, String> macro : macros.entrySet()) {
            universe.put(macro.getKey(), Arrays.asList(macro.getValue().split("\\R", -1)));
        }
        Set<String> reachable = reachable(entry, universe);
        long reachableBytes = 0;
        for (String function : reachable) reachableBytes += bytesOf(function, universe, files);

        int parameterMoves = 0;
        for (String line : executed) {
            if (line.startsWith("data ") && FROM_STORAGE.matcher(line).find()
                && STACK_FRAME_ONE.matcher(line).find()) {
                parameterMoves++;
            }
        }

        JSONObject metrics = new JSONObject();
        metrics.put("commandLines", trace.size());
        metrics.put("functionCallLines", countMatches(trace, FUNCTION_CALL));
        metrics.put("parameterMoves", parameterMoves);
        metrics.put("storageCopies", countMatches(executed, FROM_STORAGE));
        metrics.put("directDataCommands", countPrefix(executed, "data "));
        metrics.put("executeStoreStorage", countMatches(executed, STORE_STORAGE));
        metrics.put("nbtCommands", countPrefix(executed, "data ")
            + countMatches(executed, STORE_STORAGE));
        metrics.put("scoreCommands", countPrefix(executed, "scoreboard "));
        metrics.put("stackPushes", pushes);
        metrics.put("stackPops", pops);
        metrics.put("extraAdapterFrames", pushes - 1);
        metrics.put("reachableFiles", reachable.size());
        metrics.put("reachableBytes", reachableBytes);
        JSONArray reachableFunctions = new JSONArray();
        reachableFunctions.addAll(reachable);
        metrics.put("reachableFunctions", reachableFunctions);

        JSONObject checks = new JSONObject();
        checks.put("observableValues", true);
        checks.put("blockTests", true);
        checks.put("scoreValues", true);
        checks.put("stackBalanced", true);

        JSONObject variant = new JSONObject();
        variant.put("variant", name);
        variant.put("entry", entry);
        variant.put("observed", integerObject(observed));
        variant.put("observedScores", integerObject(observedScores));
        variant.put("blockTests", blockTests);
        variant.put("checks", checks);
        variant.put("metrics", metrics);
        return variant;
    }

    private static Map<String, List<String>> withProbes(String entry,
                                                        Map<String, List<String>> functions,
                                                        List<String> probes) {
        Map<String, List<String>> instrumented = new LinkedHashMap<>(functions);
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> completed = new LinkedHashSet<>();
        if (!instrumentTerminal(entry, instrumented, probes, visiting, completed,
                entry.equals("example.comparison:damagecase"))) {
            throw new IllegalStateException("No terminal caller-frame cleanup reachable from entry '"
                + entry + "' through tail return continuations");
        }
        return instrumented;
    }

    private static boolean instrumentTerminal(String name, Map<String, List<String>> functions,
                                              List<String> probes, Set<String> visiting,
                                              Set<String> completed, boolean explicitDamageScore) {
        if (completed.contains(name)) return true;
        if (!visiting.add(name)) {
            throw new IllegalStateException("Cycle in tail return continuations at '" + name + "'");
        }
        try {
            List<String> body = functions.get(name);
            if (body == null) {
                throw new IllegalStateException("Tail continuation function is missing: " + name);
            }
            List<String> tails = new ArrayList<>();
            for (int index = lastExecutableIndex(body); index >= 0; index--) {
                String line = body.get(index).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!line.contains("return run function")) break;
                int before = tails.size();
                Matcher matcher = FUNCTION_CALL.matcher(line);
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (target.contains("$") || target.contains("(")) {
                        throw new IllegalStateException("Unsupported tail continuation target '"
                            + target + "' in '" + name + "'");
                    }
                    tails.add(target);
                }
                if (tails.size() == before) {
                    throw new IllegalStateException("Malformed tail return in '" + name + "': "
                        + line);
                }
            }
            if (!tails.isEmpty()) {
                for (String target : tails) {
                    if (!instrumentTerminal(target, functions, probes, visiting, completed, explicitDamageScore)) {
                        throw new IllegalStateException("Tail continuation '" + target + "' from '"
                            + name + "' has no terminal caller-frame cleanup");
                    }
                }
                completed.add(name);
                return true;
            }
            int pop = -1;
            for (int index = body.size() - 1; index >= 0; index--) {
                if (body.get(index).trim().equals(POP)) {
                    pop = index;
                    break;
                }
            }
            if (pop < 0) return false;
            List<String> result = new ArrayList<>(body);
            for (String probe : probes) {
                result.add(pop++, explicitDamageScore && probe.equals("health")
                    ? SCORE_PROBE_PREFIX + probe + " int 1 run scoreboard players get #comparison_health hp"
                    : PROBE_PREFIX + probe + " set from storage mcfpp:system stack_frame[0]." + probe);
            }
            functions.put(name, result);
            completed.add(name);
            return true;
        } finally {
            visiting.remove(name);
        }
    }

    private static int lastExecutableIndex(List<String> body) {
        for (int index = body.size() - 1; index >= 0; index--) {
            String line = body.get(index).trim();
            if (!line.isEmpty() && !line.startsWith("#")) return index;
        }
        return -1;
    }

    private static boolean isProbe(String line) {
        return line.startsWith(PROBE_PREFIX) || line.startsWith(SCORE_PROBE_PREFIX);
    }

    private static Map<String, List<String>> readBaselineFunctions(JSONObject variant,
                                                                   String scenario, String name) {
        JSONObject map = variant.getJSONObject("functions");
        if (map == null) {
            throw new IllegalStateException("Baseline " + scenario + "/" + name
                + " has no function map");
        }
        Map<String, List<String>> functions = new LinkedHashMap<>();
        for (String key : map.keySet()) {
            JSONArray lines = map.getJSONArray(key);
            List<String> body = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) body.add(lines.getString(index));
            functions.put(key, body);
        }
        return functions;
    }

    private static Generated readGenerated(Path dataRoot) throws IOException {
        if (!Files.isDirectory(dataRoot)) {
            throw new IllegalStateException("Generated data root does not exist: " + dataRoot);
        }
        Map<String, List<String>> functions = new LinkedHashMap<>();
        Map<String, String> macros = new LinkedHashMap<>();
        Map<String, Path> files = new LinkedHashMap<>();
        List<Path> namespaces;
        try (var stream = Files.list(dataRoot)) {
            namespaces = stream.filter(Files::isDirectory).sorted().toList();
        }
        for (Path namespaceDir : namespaces) {
            String namespace = namespaceDir.getFileName().toString();
            Path functionRoot = namespaceDir.resolve("function");
            if (!Files.isDirectory(functionRoot)) continue;
            List<Path> paths;
            try (var stream = Files.walk(functionRoot)) {
                paths = stream.filter(path -> path.toString().endsWith(".mcfunction"))
                    .sorted().toList();
            }
            for (Path file : paths) {
                String relative = functionRoot.relativize(file).toString().replace('\\', '/');
                relative = relative.substring(0, relative.length() - ".mcfunction".length());
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (namespace.equals("mcfpp") && relative.startsWith("dynamic/")) {
                    String key = "mcfpp:dynamic/" + relative.substring("dynamic/".length());
                    macros.put(key, String.join("\n", lines));
                    files.put(key, file);
                } else {
                    String key = namespace + ":" + relative;
                    functions.put(key, lines);
                    files.put(key, file);
                }
            }
        }
        return new Generated(functions, macros, files);
    }

    private static World readWorld(JSONObject world) {
        JSONArray position = world.getJSONArray("position");
        JSONArray blocks = world.getJSONArray("stoneBlocks");
        Set<String> stoneBlocks = new LinkedHashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            JSONArray block = blocks.getJSONArray(index);
            stoneBlocks.add(block.getIntValue(0) + "," + block.getIntValue(1) + ","
                + block.getIntValue(2));
        }
        return new World(position.getDoubleValue(0), position.getDoubleValue(1),
            position.getDoubleValue(2), world.getDoubleValue("yaw"), world.getDoubleValue("pitch"),
            world.getString("block"), stoneBlocks);
    }

    private static Set<String> reachable(String entry, Map<String, List<String>> universe) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            if (!seen.add(name)) continue;
            List<String> body = universe.get(name);
            if (body == null) {
                throw new IllegalStateException("Reachable function is missing: " + name
                    + ". Known functions: " + universe.keySet());
            }
            for (String line : body) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                Matcher matcher = FUNCTION_CALL.matcher(trimmed);
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (target.contains("$") || target.contains("(")) continue;
                    queue.addLast(target);
                }
            }
        }
        return seen;
    }

    private static long bytesOf(String name, Map<String, List<String>> universe,
                                Map<String, Path> files) throws IOException {
        Path file = files.get(name);
        if (file != null) return Files.size(file);
        List<String> body = universe.get(name);
        if (body == null) throw new IllegalStateException("Missing body for " + name);
        String text = String.join("\n", body);
        if (!text.isEmpty()) text += "\n";
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int countExact(List<String> lines, String command) {
        int count = 0;
        for (String line : lines) if (line.equals(command)) count++;
        return count;
    }

    private static int countPrefix(List<String> lines, String prefix) {
        int count = 0;
        for (String line : lines) if (line.startsWith(prefix)) count++;
        return count;
    }

    private static int countMatches(List<String> lines, Pattern pattern) {
        int count = 0;
        for (String line : lines) if (pattern.matcher(line).find()) count++;
        return count;
    }

    private static List<String> strings(JSONArray array) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) result.add(array.getString(index));
        return result;
    }

    private static Map<String, Integer> integers(JSONObject object) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (object != null) {
            for (String key : object.keySet()) result.put(key, object.getIntValue(key));
        }
        return result;
    }

    private static JSONObject integerObject(Map<String, Integer> values) {
        JSONObject result = new JSONObject();
        result.putAll(values);
        return result;
    }

    private static JSONObject definitions() {
        JSONObject definitions = new JSONObject();
        definitions.put("commandLines",
            "Non-comment function-body lines executed once, taken from the new "
                + "ContextCommandExecutor.executedFunctionLines trace. The synthetic root "
                + "'function <entry>' invocation is excluded. An execute wrapper line is counted "
                + "once and never together with the command it runs.");
        definitions.put("parameterMoves",
            "Executed direct data commands that copy from storage into or out of "
                + "stack_frame[1] (the caller frame). These are cross-frame data moves; they include "
                + "ordinary member call adapters, not only macro parameter adapters, and are counts, "
                + "never timings.");
        definitions.put("storageCopies",
            "All executed direct data commands matching 'data modify ... from storage', including "
                + "local materialization inside a frame.");
        definitions.put("nbtCommands",
            "Executed direct 'data ...' commands plus executed 'execute store ... storage' "
                + "writes. This counts actual NBT reads/writes/moves; it is not a timing unit.");
        definitions.put("stackPushesPops",
            "Executed ordinary stack-frame push/pop lines. Every entry has one ordinary caller "
                + "frame. extraAdapterFrames = stackPushes - 1 counts caller-beyond frames, including "
                + "ordinary generated load/save member calls; it is not purely macro-only adapter "
                + "overhead.");
        definitions.put("reachableFilesBytes",
            "Static traversal of literal function calls from the entry, including callbacks and "
                + "alternate dispatch helpers even when a branch is not taken. Probes are excluded; "
                + "generated bytes are the actual emitted UTF-8 file sizes, baseline bytes are the "
                + "authored command file serialization with one trailing newline.");
        return definitions;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }
            int equals = argument.indexOf('=');
            if (equals >= 0) {
                options.put(argument.substring(0, equals), argument.substring(equals + 1));
            } else {
                if (++index >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + argument);
                }
                options.put(argument, args[index]);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required option is missing: " + name);
        }
        return value;
    }

    private static final class Generated {
        final Map<String, List<String>> functions;
        final Map<String, String> macros;
        final Map<String, Path> files;

        Generated(Map<String, List<String>> functions, Map<String, String> macros,
                  Map<String, Path> files) {
            this.functions = functions;
            this.macros = macros;
            this.files = files;
        }
    }

    private static final class World {
        final double x;
        final double y;
        final double z;
        final double yaw;
        final double pitch;
        final String block;
        final Set<String> stoneBlocks;

        World(double x, double y, double z, double yaw, double pitch, String block,
              Set<String> stoneBlocks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.block = block;
            this.stoneBlocks = stoneBlocks;
        }
    }
}
