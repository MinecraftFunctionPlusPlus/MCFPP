package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.MCStringConcrete;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.nbt.tags.primitive.StringTag;
import top.mcfpp.util.ValueWrapper;

public class LootTablePredicateObjectData {

    @MNIFunction(normalParams = {"string"}, returnType = "Predicate")
    public static void of(MCString id, ValueWrapper<DataTemplateObject> re) {
        var obj = DataTemplate.newInstance("mcfpp.minecraft.resource", "Predicate");
        if(id instanceof MCStringConcrete idC){
            DataTemplate.assignField(obj, "id", new MCStringConcrete(idC.getValue(), ""));
        }
        re.set(obj);
    }

}
