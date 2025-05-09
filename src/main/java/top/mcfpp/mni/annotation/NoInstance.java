package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class NoInstance extends DataTemplateAnnotation {

    @SuppressWarnings("unused")
    private NoInstance() {
        super("NoInstance","mcfpp.lang");
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data){}
}
