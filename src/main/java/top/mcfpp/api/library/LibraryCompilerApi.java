package top.mcfpp.api.library;

import java.util.List;
import java.util.Map;
import top.mcfpp.library.api.LibraryCompilerSession;

/** Stable compiler ABI. All public signatures use types available to a Java 17 host. */
public final class LibraryCompilerApi {
    private LibraryCompilerApi() { }

    public static int apiVersion() { return 1; }

    public static Map<String, Object> capabilities() {
        return Map.of("apiVersion", 1, "formats", List.of(1),
                "semantics", List.of("mcfpp.source/1"), "runtimeAbis", List.of("mcfpp.runtime/1"),
                "minimumJava", 21);
    }

    public static AutoCloseable openSession(Map<String, Object> request) {
        return LibraryCompilerSession.openSession(request);
    }

    public static Map<String, Object> compile(AutoCloseable session) {
        return LibraryCompilerSession.compileSession(session);
    }
}
