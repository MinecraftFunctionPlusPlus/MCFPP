package top.mcfpp.api.library;

import java.util.List;
import java.util.Map;
import top.mcfpp.library.api.LibraryCompilerSession;

/**
 * Runs MCFPP compilations for JVM hosts through JDK maps and closeable sessions.
 * Call {@link #openSession}, then {@link #compile}. Close an unused session to release the compiler.
 * The API version describes this request and result format.
 */
public final class LibraryCompilerApi {
    private LibraryCompilerApi() { }

    /** Returns the version of the request and result format accepted by this API. */
    public static int apiVersion() { return 1; }

    /**
     * Returns supported library formats, source semantics, runtime interfaces, and the minimum Java version.
     * The keys are {@code apiVersion}, {@code formats}, {@code semantics}, {@code runtimeAbis}, and {@code minimumJava}.
     */
    public static Map<String, Object> capabilities() {
        return Map.of("apiVersion", 1, "formats", List.of(1),
                "semantics", List.of("mcfpp.source/1"), "runtimeAbis", List.of("mcfpp.runtime/1"),
                "minimumJava", 21);
    }

    /**
     * Opens an exclusive compiler session and copies the request data.
     *
     * @param request a map with {@code apiVersion = 1}, a {@code configuration} Path, and lists named
     *                {@code lockedPackages}, {@code libraries}, {@code plugins}, {@code resourcePacks}, and {@code datapacks}
     * @return a session for one compilation. {@link #compile} closes it after use.
     * @throws IllegalArgumentException if request fields or installed dependency metadata are invalid
     * @throws IllegalStateException if another compiler session is active
     * @see top.mcfpp.library.api.LibraryCompilerSession
     */
    public static AutoCloseable openSession(Map<String, Object> request) {
        return LibraryCompilerSession.openSession(request);
    }

    /**
     * Compiles the prepared project and releases its session.
     *
     * @param session an unused session from {@link #openSession}
     * @return {@code success}, {@code loadedLibraries}, {@code loadedModules}, {@code compilerErrors},
     *         {@code diagnostics}, and {@code outputs}. Diagnostics contain code, severity, and message.
     *         Each output contains its kind and Path.
     * @throws IllegalArgumentException if the session comes from another API
     * @throws IllegalStateException if the session is closed or already used
     */
    public static Map<String, Object> compile(AutoCloseable session) {
        return LibraryCompilerSession.compileSession(session);
    }
}
