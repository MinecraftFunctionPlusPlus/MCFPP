package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class NoInstance extends DataTemplateAnnotation {

    @SuppressWarnings("unused")
    private NoInstance() {
        super("NoInstance","mcfpp.annotation");
    }

    @Override
    public void forDataObject(@NotNull DataTemplate data){}
}
