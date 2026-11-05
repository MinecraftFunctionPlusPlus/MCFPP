package top.mcfpp.browser.logging;

import org.apache.logging.log4j.message.AbstractMessageFactory;
import org.apache.logging.log4j.message.Message;

public final class ParameterizedNoReferenceMessageFactory extends AbstractMessageFactory {
    public static final ParameterizedNoReferenceMessageFactory INSTANCE = new ParameterizedNoReferenceMessageFactory();
    @Override public Message newMessage(String text, Object... values) {
        return ParameterizedMessageFactory.INSTANCE.newMessage(text, values);
    }
}
