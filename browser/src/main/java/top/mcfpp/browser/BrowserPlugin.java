package top.mcfpp.browser;

import java.util.Arrays;
import org.teavm.model.*;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/** Small, explicit host substitutions, applied only to the Wasm artifact. */
public final class BrowserPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add((ClassHolderTransformer) (cls, context) -> {
            addJdkMethods(cls, context);
            for (var method : cls.getMethods()) {
                if (cls.getName().equals("top.mcfpp.library.api.PlaygroundCompilerKernel$Companion") && method.getName().equals("failureDiagnostic")) {
                    delegate(method, context, "failureDiagnostic");
                } else if (cls.getName().equals("org.bouncycastle.util.Properties") && method.getName().equals("getPropertyValue") && method.parameterCount() == 1) {
                    delegateTo(method, context, BrowserJdk.class.getName(), "property");
                }
                improveReflectionDiagnostics(cls, method);
            }
        });
    }

    /** TeaVM's missing-method exception omits its owner/signature; keep that information. */
    private static void improveReflectionDiagnostics(ClassHolder cls, MethodHolder method) {
        if (!cls.getName().startsWith("top.mcfpp.") || cls.getName().equals(BrowserJdk.class.getName()) || method.getProgram() == null) return;
        for (int index = 0; index < method.getProgram().basicBlockCount(); index++) {
            for (var instruction : method.getProgram().basicBlockAt(index)) {
                if (!(instruction instanceof InvokeInstruction call) || !call.getMethod().getClassName().equals("java.lang.Class")) continue;
                String name = call.getMethod().getName();
                if (!Arrays.asList("getMethod", "getDeclaredMethod", "getConstructor", "getDeclaredConstructor").contains(name)) continue;
                var old = call.getMethod();
                ValueType[] types = new ValueType[old.parameterCount() + 2];
                types[0] = ValueType.object("java.lang.Class");
                for (int i = 0; i < old.parameterCount(); i++) types[i + 1] = old.parameterType(i);
                types[types.length - 1] = old.getReturnType();
                Variable[] args = new Variable[call.getArguments().size() + 1];
                args[0] = call.getInstance();
                for (int i = 1; i < args.length; i++) args[i] = call.getArguments().get(i - 1);
                call.setMethod(new MethodReference(BrowserJdk.class.getName(), name, types));
                call.setArguments(args); call.setInstance(null); call.setType(InvocationType.SPECIAL);
            }
        }
    }

    private static void delegate(MethodHolder method, ClassHolderTransformerContext context, String name) {
        delegateTo(method, context, BrowserPlatform.class.getName(), name);
    }
    private static void delegateTo(MethodHolder method, ClassHolderTransformerContext context, String owner, String name) {
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        var args = new ValueEmitter[method.parameterCount()];
        for (int i = 0; i < args.length; i++) args[i] = emitter.var(i + 1, method.parameterType(i));
        ValueType[] signature = method.getDescriptor().getSignature();
        var result = emitter.invoke(new MethodReference(owner, name, signature), args);
        if (method.getResultType() == ValueType.VOID) emitter.exit();
        else result.returnValue();
    }

    private static void addJdkMethods(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "java.util.UUID" -> {
                add(cls, context, "getMostSignificantBits", "uuidMost", false, ValueType.LONG);
                add(cls, context, "getLeastSignificantBits", "uuidLeast", false, ValueType.LONG);
            }
            case "java.lang.String" -> add(cls, context, "lines", "lines", false, ValueType.object("java.util.stream.Stream"));
            case "java.util.stream.StreamSupport" -> add(cls, context, "intStream", "intStream", true, ValueType.object("java.util.Spliterator$OfInt"), ValueType.BOOLEAN, ValueType.object("java.util.stream.IntStream"));
            case "java.io.File" -> add(cls, context, "toPath", "fileToPath", false, FileTypes.PATH);
            case "java.io.Reader" -> add(cls, context, "transferTo", "transferTo", false, ValueType.object("java.io.Writer"), ValueType.LONG);
            case "java.lang.reflect.AccessibleObject" -> add(cls, context, "trySetAccessible", "trySetAccessible", false, ValueType.BOOLEAN);
            case "java.lang.ClassLoader" -> add(cls, context, "loadClass", "loadClass", false, ValueType.object("java.lang.String"), ValueType.object("java.lang.Class"));
            case "java.lang.Class" -> add(cls, context, "isAnonymousClass", "isAnonymousClass", false, ValueType.BOOLEAN);
            case "java.lang.reflect.Method" -> add(cls, context, "toGenericString", "genericMethodText", false, ValueType.object("java.lang.String"));
            case "java.lang.Character" -> {
                add(cls, context, "toString", "characterString", true, ValueType.INTEGER, ValueType.object("java.lang.String"));
                add(cls, context, "codePointOf", "codePointOf", true, ValueType.object("java.lang.String"), ValueType.INTEGER);
            }
            case "java.lang.Integer" -> add(cls, context, "parseUnsignedInt", "parseUnsignedInt", true, ValueType.object("java.lang.String"), ValueType.INTEGER, ValueType.INTEGER);
            case "java.lang.Long" -> add(cls, context, "parseUnsignedLong", "parseUnsignedLong", true, ValueType.object("java.lang.String"), ValueType.INTEGER, ValueType.LONG);
        }
    }

    private static void add(ClassHolder cls, ClassHolderTransformerContext context, String methodName,
                            String targetName, boolean isStatic, ValueType... signature) {
        var descriptor = new MethodDescriptor(methodName, signature);
        if (cls.getMethod(descriptor) != null) return;
        var method = new MethodHolder(descriptor);
        method.setLevel(AccessLevel.PUBLIC);
        if (isStatic) method.getModifiers().add(ElementModifier.STATIC);
        cls.addMethod(method);
        var emitter = ProgramEmitter.create(method, context.getHierarchy());
        var args = new ValueEmitter[method.parameterCount() + (isStatic ? 0 : 1)];
        int start = 0;
        if (!isStatic) args[start++] = emitter.var(0, ValueType.object(cls.getName()));
        for (int i = 0; i < method.parameterCount(); i++) args[start + i] = emitter.var(i + 1, method.parameterType(i));
        var types = new ValueType[args.length + 1];
        for (int i = 0; i < args.length; i++) types[i] = args[i].getType();
        types[args.length] = method.getResultType();
        var result = emitter.invoke(new MethodReference(BrowserJdk.class.getName(), targetName, types), args);
        if (method.getResultType() == ValueType.VOID) emitter.exit(); else result.returnValue();
    }

    private static final class FileTypes {
        static final ValueType PATH = ValueType.object("java.nio.file.Path");
    }
}
