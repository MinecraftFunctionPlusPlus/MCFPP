package top.mcfpp.browser.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.AbstractLogger;

/** Keep compiler diagnostic capture, without starting Log4j's desktop discovery machinery. */
public final class LogManager {
    private LogManager() { }
    public static Logger getLogger(String name) { return new ConsoleLogger(name); }
    public static Logger getLogger(Class<?> type) { return getLogger(type.getName()); }
    public static Logger getLogger() { return getLogger("mcfpp"); }

    static class ConsoleLogger extends AbstractLogger {
        ConsoleLogger(String name) { super(name, new BrowserMessageFactory()); }
        @Override public Level getLevel() { return Level.ERROR; }
        private boolean enabled(Level level) { return level.isMoreSpecificThan(Level.ERROR); }
        @Override public boolean isEnabled(Level l, Marker m, Message v, Throwable t) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, CharSequence v, Throwable t) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, Object v, Throwable t) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Throwable t) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object... args) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { return enabled(l); }
        @Override public boolean isEnabled(Level l, Marker m, String v, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { return enabled(l); }
        @Override public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable thrown) {
            if (enabled(level)) System.err.println(message.getFormattedMessage());
        }
    }

    private static final class BrowserMessageFactory implements MessageFactory {
        public Message newMessage(Object value) { return new SimpleMessage(String.valueOf(value)); }
        public Message newMessage(String text) { return new SimpleMessage(text); }
        public Message newMessage(String text, Object... arguments) {
            StringBuilder output = new StringBuilder();
            int start = 0;
            for (Object value : arguments) {
                int at = text.indexOf("{}", start);
                if (at < 0) break;
                output.append(text, start, at).append(value); start = at + 2;
            }
            return new SimpleMessage(output.append(text.substring(start)).toString());
        }
    }
}
