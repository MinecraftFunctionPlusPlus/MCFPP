package top.mcfpp.api.library;

import java.nio.file.Path;
import java.util.Map;
import top.mcfpp.library.api.PlaygroundCompilerKernel;

/**
 * Single-file compiler host with a prepared built-in library. One kernel exclusively owns the
 * process-global compiler until closed. Calls are synchronous; hosts must serialize them.
 * Each request receives a new project directory and returns detached file contents.
 */
public final class PlaygroundCompilerApi {
    private PlaygroundCompilerApi() { }

    public static int apiVersion() { return 1; }

    /** Reserve a kernel. Standard-library preparation occurs in the first compile and is timed. */
    public static AutoCloseable openKernel(Path directory, String minecraftVersion, String namespace) {
        return PlaygroundCompilerKernel.open(directory, minecraftVersion, namespace);
    }

    /**
     * Compile main.mcfpp. Returns success, diagnostics, files (relative path and UTF-8 content),
     * kernelUsable, and timings in nanoseconds. The request directory is cleaned after reading.
     */
    public static Map<String, Object> compile(AutoCloseable kernel, String source) {
        return PlaygroundCompilerKernel.compileSource(kernel, source);
    }
}
