package top.mcfpp.browser;

/** Host diagnostic output; compiler state and bundled resources live in the compiler itself. */
public final class BrowserPlatform {
    private BrowserPlatform() { }

    /** Runtime failures remain concise in the editor and retain a stack in developer tools. */
    public static java.util.Map<String, Object> failureDiagnostic(Throwable failure) {
        failure.printStackTrace();
        return java.util.Map.of("code", "MCFPP_COMPILATION_FAILED", "severity", "error", "message",
                failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
    }
}
