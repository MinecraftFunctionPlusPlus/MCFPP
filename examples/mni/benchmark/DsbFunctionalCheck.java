import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import moe.afox.dpsandbox.cli.ExistingSandboxManifestResult;
import moe.afox.dpsandbox.cli.ManifestAttemptResult;
import moe.afox.dpsandbox.cli.ManifestOptions;
import moe.afox.dpsandbox.cli.ManifestResult;
import moe.afox.dpsandbox.cli.ManifestRunner;
import moe.afox.dpsandbox.cli.ManifestSchemaValidator;
import moe.afox.dpsandbox.core.CommandTraceMode;
import moe.afox.dpsandbox.core.DatapackCoverageOptions;
import moe.afox.dpsandbox.core.DatapackResourceSummary;
import moe.afox.dpsandbox.core.DatapackSandbox;
import moe.afox.dpsandbox.core.DatapackMissingResourceReference;
import moe.afox.dpsandbox.core.SandboxException;
import moe.afox.dpsandbox.core.SandboxKt;
import moe.afox.dpsandbox.core.SandboxLimits;
import moe.afox.dpsandbox.core.SandboxWorld;
import moe.afox.dpsandbox.core.UnsupportedFeatureMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes the existing DSB manifests against the public 26.3 runtime with
 * command tracing disabled. This adapter only runs the supplied manifests;
 * it does not implement or simulate any expected value.
 */
public final class DsbFunctionalCheck {
    private static final int MAX_COMMANDS = 200_000;
    private static final int MAX_FUNCTION_DEPTH = 64;
    private static final int MAX_TICKS = 100_000;
    private static final int MAX_OUTPUTS = 100_000;
    private static final int MAX_SNAPSHOT_BYTES = 16_000_000;

    public static void main(String[] args) throws Exception {
        Path output = null;
        List<Path> manifests = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            if ("--output".equals(args[index])) {
                if (++index >= args.length) throw new IllegalArgumentException("--output requires a path");
                output = Path.of(args[index]).toAbsolutePath().normalize();
            } else {
                manifests.add(Path.of(args[index]).toAbsolutePath().normalize());
            }
        }
        if (output == null) throw new IllegalArgumentException("Missing --output <report.json>");
        if (manifests.isEmpty()) throw new IllegalArgumentException("No manifests supplied");
        Files.createDirectories(output.getParent());

        Gson gson = new Gson();
        JsonArray report = new JsonArray();
        boolean anyFailed = false;
        for (Path manifest : manifests) {
            JsonObject entry = runOne(manifest);
            report.add(entry);
            boolean passed = entry.get("passed").getAsBoolean();
            anyFailed |= !passed;
            System.out.println((passed ? "PASS " : "FAIL ") + manifest);
        }
        Files.writeString(output, escapeSurrogates(gson.toJson(report)), StandardCharsets.UTF_8);
        System.exit(anyFailed ? 3 : 0);
    }

    static String escapeSurrogates(String json) {
        StringBuilder escaped = new StringBuilder(json.length());
        for (int index = 0; index < json.length(); index++) {
            char value = json.charAt(index);
            if (Character.isSurrogate(value)) {
                escaped.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) {
                    escaped.append("0123456789abcdef".charAt((value >> shift) & 0xf));
                }
            } else {
                escaped.append(value);
            }
        }
        return escaped.toString();
    }

    private static JsonObject runOne(Path manifest) {
        JsonObject entry = new JsonObject();
        entry.addProperty("path", manifest.toString());
        entry.addProperty("commandTraceMode", "OFF");
        JsonArray diagnostics = new JsonArray();
        entry.add("diagnostics", diagnostics);
        entry.add("errors", diagnostics);

        DatapackSandbox sandbox = null;
        try {
            List<String> schemaFailures = ManifestSchemaValidator.INSTANCE.validateFileTree(manifest);
            if (!schemaFailures.isEmpty()) {
                for (String failure : schemaFailures) {
                    diagnostics.add(diagnostic("INPUT_FORMAT", failure));
                }
                return failedEntry(entry, "Manifest schema validation failed");
            }

            JsonObject root =
                JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
            String version = readVersion(root);
            List<Path> packs = readPacks(root, version, manifest.getParent());
            SandboxWorld world = new SandboxWorld();
            world.setCommandTraceMode(CommandTraceMode.OFF);
            SandboxLimits limits = new SandboxLimits(
                MAX_COMMANDS, MAX_FUNCTION_DEPTH, MAX_TICKS, MAX_OUTPUTS, MAX_SNAPSHOT_BYTES);
            sandbox = SandboxKt.createSandbox(
                version, packs, world, null, UnsupportedFeatureMode.ERROR, limits);
            if (sandbox.getWorld().getCommandTraceMode() != CommandTraceMode.OFF) {
                throw new IllegalStateException("DSB runtime did not retain CommandTraceMode.OFF");
            }
            ManifestOptions options = new ManifestOptions(
                0L, false, false, false, true, UnsupportedFeatureMode.ERROR, limits,
                new DatapackCoverageOptions());
            ExistingSandboxManifestResult executed =
                ManifestRunner.INSTANCE.runInExistingSandbox(manifest, sandbox, options);
            ManifestResult result = executed.getResult();
            entry.addProperty("passed", result.getPassed());
            entry.add("messages", stringArray(result.getMessages()));
            entry.add("results", results(result));
            entry.add("snapshot", snapshot(result));
            entry.add("resourceSummary", resourceSummary(result));
            entry.add("attempts", attempts(result));
            if (!result.getPassed()) {
                for (String message : result.getMessages()) {
                    diagnostics.add(diagnostic("ASSERTION_FAILED", message));
                }
            }
            return entry;
        } catch (Throwable error) {
            diagnostics.add(diagnostic(error));
            entry.add("snapshot", sandbox == null ? JsonNull.INSTANCE : safeSnapshot(sandbox));
            entry.add("resourceSummary", JsonNull.INSTANCE);
            entry.add("attempts", new JsonArray());
            return failedEntry(entry, error.toString());
        }
    }

    private static JsonObject failedEntry(JsonObject entry, String message) {
        entry.addProperty("passed", false);
        JsonArray messages = new JsonArray();
        messages.add(message);
        entry.add("messages", messages);
        entry.add("results", results(false, List.of(message)));
        return entry;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static JsonObject results(ManifestResult result) {
        if (result.getAttempts().isEmpty()) {
            return results(result.getPassed(), result.getMessages());
        }
        ManifestAttemptResult attempt = result.getAttempts().get(0);
        return results(attempt.getPassed(), attempt.getMessages());
    }

    private static JsonObject results(boolean passed, List<String> messages) {
        JsonObject results = new JsonObject();
        results.addProperty("passed", passed);
        results.add("messages", stringArray(messages));
        return results;
    }

    private static JsonElement snapshot(ManifestResult result) {
        if (result.getAttempts().isEmpty()) return JsonNull.INSTANCE;
        JsonObject snapshot = result.getAttempts().get(0).getSnapshot();
        return snapshot == null ? JsonNull.INSTANCE : snapshot;
    }

    private static JsonElement resourceSummary(ManifestResult result) {
        if (result.getAttempts().isEmpty()) return JsonNull.INSTANCE;
        return resourceSummary(result.getAttempts().get(0).getResourceSummary());
    }

    private static JsonArray attempts(ManifestResult result) {
        JsonArray attempts = new JsonArray();
        for (ManifestAttemptResult attempt : result.getAttempts()) {
            JsonObject json = new JsonObject();
            json.addProperty("version", attempt.getVersion());
            json.addProperty("passed", attempt.getPassed());
            json.add("messages", stringArray(attempt.getMessages()));
            json.add("snapshot", attempt.getSnapshot() == null ? JsonNull.INSTANCE : attempt.getSnapshot());
            json.add("resourceSummary", resourceSummary(attempt.getResourceSummary()));
            json.addProperty("commandTraceMode", "OFF");
            json.addProperty("traceCount", attempt.getTraces().size());
            attempts.add(json);
        }
        return attempts;
    }

    private static JsonElement resourceSummary(DatapackResourceSummary summary) {
        if (summary == null) return JsonNull.INSTANCE;
        JsonObject json = new JsonObject();
        json.addProperty("functions", summary.getFunctions());
        json.addProperty("lootTables", summary.getLootTables());
        json.addProperty("predicates", summary.getPredicates());
        json.addProperty("advancements", summary.getAdvancements());
        json.addProperty("recipes", summary.getRecipes());
        json.addProperty("itemModifiers", summary.getItemModifiers());
        json.addProperty("tags", summary.getTags());
        json.addProperty("rawResourceKinds", summary.getRawResourceKinds());
        json.addProperty("rawResources", summary.getRawResources());
        json.addProperty("resourceIndex", summary.getResourceIndex());
        json.addProperty("activeResources", summary.getActiveResources());
        json.addProperty("overriddenResources", summary.getOverriddenResources());
        json.addProperty("overlays", summary.getOverlays().size());
        JsonArray missing = new JsonArray();
        for (DatapackMissingResourceReference reference : summary.getMissingReferences()) {
            JsonObject item = new JsonObject();
            item.addProperty("source", reference.getSource());
            item.addProperty("type", reference.getType());
            item.addProperty("id", String.valueOf(reference.getId()));
            missing.add(item);
        }
        json.add("missingReferences", missing);
        return json;
    }

    private static JsonObject diagnostic(String code, String message) {
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", code);
        diagnostic.addProperty("message", message);
        return diagnostic;
    }

    private static JsonObject diagnostic(Throwable error) {
        JsonObject diagnostic = new JsonObject();
        if (error instanceof SandboxException sandboxError) {
            diagnostic.addProperty("code", sandboxError.getCode().name());
            diagnostic.addProperty("message", sandboxError.getMessage());
            diagnostic.addProperty("rendered", sandboxError.render());
            diagnostic.addProperty("version", sandboxError.getVersion());
            diagnostic.addProperty("command", sandboxError.getCommand());
            if (sandboxError.getLocation() != null) {
                JsonObject location = new JsonObject();
                location.addProperty("file", sandboxError.getLocation().getFile());
                if (sandboxError.getLocation().getLine() != null) {
                    location.addProperty("line", sandboxError.getLocation().getLine());
                }
                location.addProperty("command", sandboxError.getLocation().getCommand());
                diagnostic.add("location", location);
            }
        } else {
            diagnostic.addProperty("code", "EXCEPTION");
            diagnostic.addProperty("message", error.toString());
            diagnostic.addProperty("rendered", error.toString());
        }
        return diagnostic;
    }

    private static JsonElement safeSnapshot(DatapackSandbox sandbox) {
        try {
            return sandbox.snapshotJson();
        } catch (Throwable ignored) {
            return JsonNull.INSTANCE;
        }
    }

    private static String readVersion(JsonObject root) {
        if (root.has("version") && root.get("version").isJsonPrimitive()) {
            return root.get("version").getAsString();
        }
        if (root.has("versions") && root.get("versions").isJsonArray()) {
            JsonArray versions = root.getAsJsonArray("versions");
            if (versions.size() == 1) return versions.get(0).getAsString();
        }
        throw new IllegalArgumentException("Manifest must declare one actual version");
    }

    private static List<Path> readPacks(JsonObject root, String version, Path manifestParent) {
        JsonElement packsElement = root.get("packs");
        if (packsElement == null) throw new IllegalArgumentException("Manifest has no packs");
        List<String> names = new ArrayList<>();
        if (packsElement.isJsonArray()) {
            for (JsonElement value : packsElement.getAsJsonArray()) names.add(value.getAsString());
        } else if (packsElement.isJsonObject()) {
            JsonObject byVersion = packsElement.getAsJsonObject();
            JsonElement selected = byVersion.get(version);
            if (selected == null && byVersion.size() == 1) selected = byVersion.entrySet().iterator().next().getValue();
            if (selected == null || !selected.isJsonArray()) {
                throw new IllegalArgumentException("Manifest has no packs for version " + version);
            }
            for (JsonElement value : selected.getAsJsonArray()) names.add(value.getAsString());
        } else {
            throw new IllegalArgumentException("Manifest packs must be an array or version map");
        }
        List<Path> packs = new ArrayList<>();
        for (String name : names) {
            Path pack = manifestParent.resolve(name).normalize();
            if (!Files.exists(pack)) throw new IllegalArgumentException("Datapack does not exist: " + pack);
            packs.add(pack);
        }
        return packs;
    }
}
