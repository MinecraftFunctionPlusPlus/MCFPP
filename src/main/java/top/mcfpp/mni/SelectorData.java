package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.resource.Advancement;
import top.mcfpp.core.lang.resource.EntityType;
import top.mcfpp.core.lang.resource.LootTablePredicate;
import top.mcfpp.lib.*;
import top.mcfpp.util.ValueWrapper;

public class SelectorData {

    @MNIFunction(normalParams = {"int x"}, returnType = "selector")
    public static void x(MCInt x, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new XPredicate(x.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int y"}, returnType = "selector")
    public static void y(MCInt y, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new YPredicate(y.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int z"}, returnType = "selector")
    public static void z(MCInt z, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new ZPredicate(z.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range distance"}, returnType = "selector")
    public static void distance(RangeVar distance, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DistancePredicate(distance.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int dx"}, returnType = "selector")
    public static void dx(MCInt dx, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DXPredicate(dx.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int dy"}, returnType = "selector")
    public static void dy(MCInt dy, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DYPredicate(dy.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int dz"}, returnType = "selector")
    public static void dz(MCInt dz, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new DZPredicate(dz.getTempVar()));
        re.setValue(caller);
    }

    //TODO score

    @MNIFunction(normalParams = {"string tag"}, returnType = "selector")
    public static void tag(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TagPredicate((MCString) tag.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string tag"}, returnType = "selector")
    public static void tagNot(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TagPredicate((MCString) tag.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string team"}, returnType = "selector")
    public static void team(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TeamPredicate((MCString) team.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string team"}, returnType = "selector")
    public static void teamNot(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TeamPredicate((MCString) team.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string name"}, returnType = "selector")
    public static void name(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NamePredicate((MCString) name.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string name"}, returnType = "selector")
    public static void nameNot(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NamePredicate((MCString) name.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"EntityType type"}, returnType = "selector")
    public static void type(EntityType type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TypePredicate((EntityType) type.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"EntityType type"}, returnType = "selector")
    public static void typeNot(EntityType type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new TypePredicate((EntityType) type.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Predicate predicate"}, returnType = "selector")
    public static void predicate(LootTablePredicate predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new PredicatePredicate((LootTablePredicate) predicate.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Predicate predicate"}, returnType = "selector")
    public static void predicateNot(MCString predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new PredicatePredicate((LootTablePredicate) predicate.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range xRotation"}, returnType = "selector")
    public static void xRotation(RangeVar xRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new XRotationPredicate(xRotation.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range yRotation"}, returnType = "selector")
    public static void yRotation(RangeVar yRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new YRotationPredicate(yRotation.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"nbt n"}, returnType = "selector")
    public static void nbt(NBTBasedData n, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new NBTPredicate(n.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"range level"}, returnType = "selector")
    public static void level(RangeVar level, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new LevelPredicate(level.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string gamemode"}, returnType = "selector")
    public static void gamemode(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new GamemodePredicate((MCString) gamemode.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string gamemode"}, returnType = "selector")
    public static void gamemodeNot(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new GamemodePredicate((MCString) gamemode.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Advancement advancements"}, returnType = "selector")
    public static void advancements(Advancement advancements, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new AdvancementsPredicate((Advancement) advancements.clone(), false));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"Advancement advancements"}, returnType = "selector")
    public static void advancementsNot(Advancement advancements, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new AdvancementsPredicate((Advancement) advancements.clone(), true));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"int limit"}, returnType = "selector")
    public static void limit(MCInt limit, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new LimitPredicate(limit.getTempVar()));
        re.setValue(caller);
    }

    @MNIFunction(normalParams = {"string sort"}, returnType = "selector")
    public static void sort(MCString sort, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().addPredicate(new SortPredicate((MCString) sort.clone()));
        re.setValue(caller);
    }
}
