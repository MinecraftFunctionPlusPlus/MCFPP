package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.PropertyVar;
import top.mcfpp.core.lang.bool.CommandBoolPart;
import top.mcfpp.core.lang.bool.ExecuteBool;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.util.ValueWrapper;

public class LootTablePredicateData {

    @MNIFunction(caller = "Predicate", returnType = "bool")
    public static void pass(DataTemplateObject caller, ValueWrapper<ExecuteBool> result){
        var bool = new ExecuteBool();
        var predicate = DataTemplate.getField(caller, "id");
        assert predicate != null;
        if(predicate instanceof PropertyVar p) predicate = p.get();
        bool.getValue().add(new CommandBoolPart(
                false,
                new Command("if predicate").build(predicate.toCommandPart())
        ));
        result.set(bool);
    }

    @MNIFunction(caller = "Predicate", returnType = "bool")
    public static void fail(DataTemplateObject caller, ValueWrapper<ExecuteBool> result){
        var bool = new ExecuteBool();
        var predicate = DataTemplate.getField(caller, "id");
        assert predicate != null;
        bool.getValue().add(new CommandBoolPart(
                false,
                new Command("unless predicate").build(predicate.toCommandPart())
        ));
        result.set(bool);
    }
}        
