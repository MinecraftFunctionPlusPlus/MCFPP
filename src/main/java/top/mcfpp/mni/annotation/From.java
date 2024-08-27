package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;
import top.mcfpp.util.LogProcessor;

public class From extends DataTemplateAnnotation {

    String className;

    @SuppressWarnings("unused")
    public From(String className) {
        super("From", "mcfpp.annotation");
        this.className = className;
    }

    @Override
    public void forDataObject(@NotNull DataTemplate data) {
        try {
            data.getNativeFromClass(Class.forName(className));
        } catch (ClassNotFoundException e) {
            LogProcessor.INSTANCE.error("Class not found: " + className, e);
        }
    }
}
