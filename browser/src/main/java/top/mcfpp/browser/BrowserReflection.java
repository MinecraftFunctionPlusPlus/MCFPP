package top.mcfpp.browser;

import org.teavm.extension.Autoregistered;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

/** Closed reflection boundary for built-in providers and compiler rollback snapshots. */
@Autoregistered
public final class BrowserReflection extends SimpleReflectionPolicy {
    @Override
    protected void setup() {
        var compilerClass = inPackage("top.mcfpp", true)
                .and(cls -> !cls.name().contains("$$_"));
        selectClasses(compilerClass).foundByName();
        selectClass("top.mcfpp.model.function.NativeFunction$Companion")
                .reflectableMethods(namePattern("defaultNativeFunction*"));
        selectClasses(compilerClass.and(extending("top.mcfpp.model.annotation.Annotation")))
                .reflectableMethods(named("<init>"));
        selectClasses(compilerClass.and(inPackage("top.mcfpp.antlr", true)))
                .reflectableMethods(named("<init>"));
        for (String annotation : new String[] {
                "MNIFunction", "MNIMacro", "MNIMember", "MNIOperator", "MNIAccessor", "MNIMutator"
        }) {
            selectClasses(compilerClass)
                    .reflectableMethods(withAnnotation("top.mcfpp.annotations." + annotation));
        }
        // Journals restore fields on the original objects, including private backing fields.
        for (String pkg : new String[] {"model", "core", "type", "analysis", "mni"}) {
            // TeaVM creates annotation implementations inside the annotated package. Their
            // $$_nativeData fields are Wasm structs, not Java objects; never reflect these.
            selectClasses(inPackage("top.mcfpp." + pkg, true).and(compilerClass)).reflectableFields(INSTANCE);
        }
    }
}
