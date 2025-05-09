package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class MCFPPEntity extends DataTemplateAnnotation {

    public MCFPPEntity(){
        super("MCFPPEntity", "mcfpp.lang");
        extends_(new DataOnly());
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {}
}
