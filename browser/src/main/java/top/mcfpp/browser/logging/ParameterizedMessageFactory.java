package top.mcfpp.browser.logging;

import org.apache.logging.log4j.message.AbstractMessageFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;

/** String-only log formatting; avoids Log4j's runtime provider and property discovery. */
public final class ParameterizedMessageFactory extends AbstractMessageFactory {
    public static final ParameterizedMessageFactory INSTANCE = new ParameterizedMessageFactory();
    @Override
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
