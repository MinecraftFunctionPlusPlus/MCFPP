package top.mcfpp.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Stream;
import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.resources.DefaultResourcesPolicy;

/** Build-time resource inclusion. The inventory is shared with the portable runtime reader. */
@Autoregistered
public final class BrowserResources extends DefaultResourcesPolicy {
    @Override
    public String[] supplyResources(Collection<? extends String> availableClasses) {
        try (var stream = environment().classLoader().getResourceAsStream("browser-resources.txt")) {
            if (stream == null) throw new IllegalStateException("Missing browser resource inventory");
            return Stream.concat(Stream.of("browser-resources.txt", "unicode-names.txt"),
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()).toArray(String[]::new);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read browser resource inventory", failure);
        }
    }
}
