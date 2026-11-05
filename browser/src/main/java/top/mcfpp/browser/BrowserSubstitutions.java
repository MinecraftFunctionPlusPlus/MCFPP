package top.mcfpp.browser;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/** Runtime-only adapters. The ordinary JVM artifact keeps its original dependencies. */
@Autoregistered
public final class BrowserSubstitutions extends SimpleSubstitutionPolicy {
    @Override
    public void contribute(SubstitutionSink sink) {
        for (String name : new String[] {"JSON", "JSONObject", "JSONArray"}) {
            sink.selectClasses(named("com.alibaba.fastjson2." + name))
                    .replacePackage("com.alibaba.fastjson2", "top.mcfpp.browser.json");
        }
        sink.selectClasses(named("org.apache.logging.log4j.LogManager"))
                .replacePackage("org.apache.logging.log4j", "top.mcfpp.browser.logging");
        sink.selectClasses(named("org.apache.logging.log4j.message.ParameterizedMessageFactory"))
                .replacePackage("org.apache.logging.log4j.message", "top.mcfpp.browser.logging");
        sink.selectClasses(named("org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory"))
                .replacePackage("org.apache.logging.log4j.message", "top.mcfpp.browser.logging");
        sink.selectClasses(named("org.apache.logging.log4j.status.StatusLogger"))
                .replacePackage("org.apache.logging.log4j.status", "top.mcfpp.browser.logging");
        sink.selectClasses(named("java.security.MessageDigest"))
                .replacePackage("java.security", "top.mcfpp.browser.jdk");
        sink.selectClasses(named("java.util.HexFormat")).replacePackage("java.util", "top.mcfpp.browser.jdk");
    }
}
