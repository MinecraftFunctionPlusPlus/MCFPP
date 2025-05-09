package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;
import top.mcfpp.util.LogProcessor;

public class From extends DataTemplateAnnotation {

    String className;

    @SuppressWarnings("unused")
    public From(String className) {
        super("From", "mcfpp.lang");
        this.className = className;
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {
        try {
            data.injectedBy(Class.forName(className));
        } catch (ClassNotFoundException e) {
            LogProcessor.error("Class not found: " + className);
        }
    }
}
