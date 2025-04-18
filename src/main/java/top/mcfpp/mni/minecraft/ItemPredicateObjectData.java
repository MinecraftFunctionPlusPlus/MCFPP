package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.ObjectVar;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.util.ValueWrapper;

public class ItemPredicateObjectData {

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string id"}, returnType = "ItemPredicatePart")
    public static void hasComponent(MCString id, ObjectVar caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "ContainPart"));
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string id", "nbt value"}, returnType = "ItemPredicatePart")
    public static void componentMatches(MCString id, NBTBasedData value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "MatchPart"));
    }


    @MNIFunction(caller = "ItemPredicate", normalParams = {"string id","ItemSubPredicate value"}, returnType = "ItemPredicate")
    public static void subPredicate(MCString id, DataTemplateObject value , DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "SubPredicatePart"));
    }

    @MNIFunction(caller = "ItemPredicate", returnType = "ItemPredicatePart")
    public static void hasCount(DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "CountPart"));
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"int value"}, returnType = "ItemPredicatePart")
    public static void count(MCInt value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "CountMatchPart"));
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"range count"}, returnType = "ItemPredicate")
    public static void count(RangeVar count, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        re.set(DataTemplate.newInstance("mcfpp.minecraft.item", "CountRangePart"));
    }
}
