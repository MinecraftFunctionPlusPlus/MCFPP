package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;
import top.mcfpp.util.LogProcessor;

public class To extends DataTemplateAnnotation {
    String className;

    @SuppressWarnings("unused")
    public To(String className) {
        super("To", "mcfpp.lang");
        this.className = className;
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
