package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.util.ValueWrapper;

@SuppressWarnings("DataFlowIssue")
public class ItemPredicateData {

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string id"}, returnType = "ItemPredicate")
    public static void hasComponent(MCString id, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "ContainPart");
        DataTemplate.assignField(obj, "predicate", id);
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string id", "nbt value"}, returnType = "ItemPredicate")
    public static void componentMatches(MCString id, NBTBasedData value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "MatchPart");
        DataTemplate.assignField(obj, "predicate", id);
        DataTemplate.assignField(obj, "value", value);
        list.getValue().add(obj);
    }


    @MNIFunction(caller = "ItemPredicate", normalParams = {"DamagePredicate value"}, returnType = "ItemPredicate")
    public static void subPredicate(MCString id, DataTemplateObject value , DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "SubPredicatePart");
        DataTemplate.assignField(obj, "predicate", id);
        DataTemplate.assignField(obj, "value", value);
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", returnType = "ItemPredicate")
    public static void hasCount(DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "CountPart");
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"int value"}, returnType = "ItemPredicate")
    public static void count(MCInt value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "CountMatchPart");
        DataTemplate.assignField(obj, "count", value);
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"range count"}, returnType = "ItemPredicate")
    public static void count(RangeVar count, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "CountRangePart");
        DataTemplate.assignField(obj, "count", count);
        list.getValue().add(obj);
    }
}
