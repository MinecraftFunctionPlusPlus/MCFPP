package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.PropertyVar;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.util.ValueWrapper;

public class ResourceIDData {
    @MNIFunction(caller = "ResourceID", returnType = "JavaVar", override = true)
    public static void toCommandPart(DataTemplateObject caller, ValueWrapper<JavaVar> result) {
        var field = DataTemplate.getField(caller, "id");
        if (field instanceof PropertyVar property) field = property.get();
        result.setValue(new JavaVar(field.toCommandPart(), "command"));
    }
}
