package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.core.lang.Var;
import top.mcfpp.model.annotation.FieldAnnotation;

public class Name extends FieldAnnotation {

    String name;

    String version = null;

    public Name(String name) {
        super("name", "mcfpp.lang");
        this.name = name;
    }

    public Name(String name, String version){
        this(name);
        this.version = version;
    }

    public String getName() { return name; }
    public String getVersion() { return version; }

    public void restorePortableState(String value, String targetVersion) { name = value; version = targetVersion; }

    @Override
    public void forField(@NotNull Var<?> field) {

    }
}
