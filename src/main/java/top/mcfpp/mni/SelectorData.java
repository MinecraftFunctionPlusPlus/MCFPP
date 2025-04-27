package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.DataTemplateObject;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.entity.SelectorVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.nbt.NBTBasedData;
import top.mcfpp.lib.*;
import top.mcfpp.util.ValueWrapper;

public class SelectorData {

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void x(MCInt x, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new XPredicate(x.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void y(MCInt y, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new YPredicate(y.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void z(MCInt z, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new ZPredicate(z.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range"}, returnType = "selector")
    public static void distance(RangeVar distance, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DistancePredicate(distance.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void dx(MCInt dx, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DXPredicate(dx.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void dy(MCInt dy, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DYPredicate(dy.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void dz(MCInt dz, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DZPredicate(dz.getTempVar()));
        re.setValue(caller);
    }

    //TODO score

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void tag(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TagPredicate((MCString) tag.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void tagNot(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TagPredicate((MCString) tag.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void team(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TeamPredicate((MCString) team.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void teamNot(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TeamPredicate((MCString) team.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void name(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NamePredicate((MCString) name.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void nameNot(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NamePredicate((MCString) name.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"EntityType"}, returnType = "selector")
    public static void type(DataTemplateObject type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TypePredicate(type.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"EntityType"}, returnType = "selector")
    public static void typeNot(DataTemplateObject type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TypePredicate(type.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Predicate"}, returnType = "selector")
    public static void predicate(DataTemplateObject predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new PredicatePredicate(predicate.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Predicate"}, returnType = "selector")
    public static void predicateNot(DataTemplateObject predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new PredicatePredicate(predicate.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range"}, returnType = "selector")
    public static void xRotation(RangeVar xRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new XRotationPredicate(xRotation.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range"}, returnType = "selector")
    public static void yRotation(RangeVar yRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new YRotationPredicate(yRotation.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"nbt"}, returnType = "selector")
    public static void nbt(NBTBasedData n, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NBTPredicate(n.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range"}, returnType = "selector")
    public static void level(RangeVar level, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new LevelPredicate(level.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void gamemode(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new GamemodePredicate((MCString) gamemode.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void gamemodeNot(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new GamemodePredicate((MCString) gamemode.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Advancement"}, returnType = "selector")
    public static void advancements(DataTemplateObject advancements, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new AdvancementsPredicate(advancements.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Advancement"}, returnType = "selector")
    public static void advancementsNot(DataTemplateObject advancements, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new AdvancementsPredicate(advancements.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int"}, returnType = "selector")
    public static void limit(MCInt limit, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new LimitPredicate(limit.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string"}, returnType = "selector")
    public static void sort(MCString sort, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new SortPredicate((MCString) sort.clone()));
        re.setValue(caller);
    }
}
