package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class MCFPPEntity extends DataTemplateAnnotation {

    public MCFPPEntity(){
        super("MCFPPEntity", "mcfpp.annotation");
        extends_(new DataOnly());
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {}
}
