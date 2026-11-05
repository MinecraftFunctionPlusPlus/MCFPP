package top.mcfpp.browser.logging;

/** Log4j's own status messages need no provider/listener registry in a worker. */
public final class StatusLogger extends LogManager.ConsoleLogger {
    private static final StatusLogger INSTANCE = new StatusLogger();
    private StatusLogger() { super("log4j-status"); }
    public static StatusLogger getLogger() { return INSTANCE; }
}
