package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class ConcreteOnly extends DataTemplateAnnotation {

    @SuppressWarnings("unused")
    private ConcreteOnly() {
        super("ConcreteOnly","mcfpp.annotation");
    }

    @Override
    public void forDataObject(@NotNull DataTemplate data){}
}
