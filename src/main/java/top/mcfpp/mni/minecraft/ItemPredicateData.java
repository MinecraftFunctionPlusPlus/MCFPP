package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.core.lang.nbt.NBTListConcrete;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.util.ValueWrapper;

@SuppressWarnings("DataFlowIssue")
public class ItemPredicateData {

    private static Command partCommand(DataTemplateObject obj){
        if(obj.isInstance("mcfpp.minecraft.item", "ContainPart")){
            MCString predicate = DataTemplate.getField(obj, "predicate");
            return predicate.toCommandPart();
        }else if(obj.isInstance("mcfpp.minecraft.item", "MatchPart")){
            MCString predicate = DataTemplate.getField(obj, "predicate");
            NBTBasedData value = DataTemplate.getField(obj, "value");
            return Command.Companion.buildAll(predicate, "=", value);
        }else if(obj.isInstance("mcfpp.minecraft.item", "SubPredicatePart")){
            MCString predicate = DataTemplate.getField(obj, "predicate");
            NBTBasedData value = DataTemplate.getField(obj, "value");
            return Command.Companion.buildAll(predicate, "~", value);
        }else if(obj.isInstance("mcfpp.minecraft.item", "CountPart")){
            return new Command("count");
        }else if(obj.isInstance("mcfpp.minecraft.item", "CountMatchPart")){
            MCInt count = DataTemplate.getField(obj, "count");
            return Command.Companion.buildAll("count", "=", count);
        }else if(obj.isInstance("mcfpp.minecraft.item", "CountRangePart")){
            RangeVar count = DataTemplate.getField(obj, "count");
            return Command.Companion.buildAll("count", "~", count);
        }else if(obj.isInstance("mcfpp.minecraft.item", "NotItemPredicate")){
            DataTemplateObject predicate = DataTemplate.getField(obj, "predicate");
            return Command.Companion.buildAll("!", predicate);
        }else if(obj.isInstance("mcfpp.minecraft.item", "OrItemPredicatePart")){
            DataTemplateObject predicate = DataTemplate.getField(obj, "predicate1");
            DataTemplateObject predicate2 = DataTemplate.getField(obj, "predicate2");
            return Command.Companion.buildAll(partCommand(predicate), "|", partCommand(predicate2));
        }
        throw new RuntimeException("Unknown ItemPredicatePart");
    }

    @MNIFunction(override = true, caller = "ItemPredicate", returnType = "JavaVar")
    public static void toCommandPart(DataTemplateObject caller, ValueWrapper<JavaVar> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        Command finalCommand = new Command("[");
        for(var element : list.getValue()){
            var obj = (DataTemplateObject) element;
            Command command = partCommand(obj);
            finalCommand.build(command).build(",");
        }
        finalCommand.removeLast();
        finalCommand.build("]");
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string"}, returnType = "ItemPredicate")
    public static void hasComponent(MCString id, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "ContainPart");
        DataTemplate.assignField(obj, "predicate", id);
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"string", "nbt"}, returnType = "ItemPredicate")
    public static void componentMatches(MCString id, NBTBasedData value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "MatchPart");
        DataTemplate.assignField(obj, "predicate", id);
        DataTemplate.assignField(obj, "value", value);
        list.getValue().add(obj);
    }


    @MNIFunction(caller = "ItemPredicate", normalParams = {"string","ItemSubPredicate"}, returnType = "ItemPredicate")
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

    @MNIFunction(caller = "ItemPredicate", normalParams = {"int"}, returnType = "ItemPredicate")
    public static void count(MCInt value, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "CountMatchPart");
        DataTemplate.assignField(obj, "count", value);
        list.getValue().add(obj);
    }

    @MNIFunction(caller = "ItemPredicate", normalParams = {"range"}, returnType = "ItemPredicate")
    public static void count(RangeVar count, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        NBTListConcrete list = DataTemplate.getField(caller, "parts");
        var obj = DataTemplate.newInstance("mcfpp.minecraft.item", "CountRangePart");
        DataTemplate.assignField(obj, "count", count);
        list.getValue().add(obj);
    }
}
