package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.model.annotation.DataTemplateAnnotation;

public class MCFPPEntity extends DataTemplateAnnotation {

    public MCFPPEntity(){ this(false); }

    private MCFPPEntity(boolean restoring){
        super("MCFPPEntity", "mcfpp.lang");
        if (!restoring) extends_(new DataOnly());
    }

    public static MCFPPEntity restorePortable() { return new MCFPPEntity(true); }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {}
}
