package top.mcfpp.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import top.mcfpp.library.compiler.BuiltinImportPlan;
import top.mcfpp.library.compiler.PortableAnnotationCodecs;
import top.mcfpp.library.format.CompoundCodec;
import top.mcfpp.library.format.LibraryModelCodec;
import top.mcfpp.library.format.NativeCodec;
import top.mcfpp.library.model.AnnotationProviderDeclaration;
import top.mcfpp.library.model.LibraryCompatibility;

/** Strong AOT roots from the exact bundled protocol, including otherwise unused native providers. */
public final class NativeRootsGenerator {
    public static void main(String[] args) throws Exception {
        byte[] binary = Files.readAllBytes(Path.of(args[0]));
        var codec = new LibraryModelCodec(List.of(CompoundCodec.INSTANCE, NativeCodec.INSTANCE));
        var model = codec.decode(binary, new LibraryCompatibility());
        var plan = BuiltinImportPlan.Companion.decode(Files.readAllBytes(Path.of(args[1])), binary, model);
        var roots = new TreeSet<String>();
        for (var binding : plan.getNativeBindings()) {
            roots.add(binding.getOwnerClass());
            for (String type : binding.getJvmParameters()) addReferenceType(roots, type);
            addReferenceType(roots, binding.getJvmResult());
        }
        for (var declaration : model.getDeclarations().getDeclarations()) {
            if (declaration instanceof AnnotationProviderDeclaration provider) roots.add(provider.getBinding().getOwnerClass());
        }
        for (var type : PortableAnnotationCodecs.builtinClasses()) roots.add(type.getName());
        roots.add("java.lang.String"); // Built-in annotation constructor parameters.
        Path output = Path.of(args[2]);
        Files.createDirectories(output.getParent()); Files.write(output, roots);
        Path source = Path.of(args[3]);
        Files.createDirectories(source.getParent());
        StringBuilder java = new StringBuilder("package top.mcfpp.browser;\n\n// Generated from the bundled library. Do not edit.\npublic final class GeneratedNativeRoots {\n public static Class<?>[] classes() { return new Class<?>[] {\n");
        for (String name : roots) java.append("  ").append(Class.forName(name).getCanonicalName()).append(".class,\n");
        java.append(" }; }\n}\n");
        Files.writeString(source, java);
        System.out.println("Prepared " + roots.size() + " native/annotation AOT roots from " + plan.getNativeBindings().size() + " bindings");
    }

    private static void addReferenceType(TreeSet<String> roots, String name) {
        if (!List.of("boolean", "byte", "short", "int", "long", "float", "double", "char", "void").contains(name)) roots.add(name);
    }
}
