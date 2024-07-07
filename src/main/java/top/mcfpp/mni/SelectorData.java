package top.mcfpp.mni;

import kotlin.Pair;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.*;
import top.mcfpp.lang.resource.Advancement;
import top.mcfpp.lang.resource.LootTablePredicate;
import top.mcfpp.util.ValueWrapper;

import java.util.UUID;

public class SelectorData extends BaseMNIMethodContainer {

    @MNIRegister(normalParams = {"int x"}, returnType = "selector")
    public static void x(MCInt x, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setX((MCInt) x.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int y"}, returnType = "selector")
    public static void y(MCInt y, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setY((MCInt) y.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int z"}, returnType = "selector")
    public static void z(MCInt z, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setZ((MCInt) z.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"range distance"}, returnType = "selector")
    public static void distance(RangeVar distance, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDistance((RangeVar) distance.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int dx"}, returnType = "selector")
    public static void dx(MCInt dx, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDx((MCInt) dx.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int dy"}, returnType = "selector")
    public static void dy(MCInt dy, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDy((MCInt) dy.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int dz"}, returnType = "selector")
    public static void dz(MCInt dz, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDz((MCInt) dz.getTempVar());
        re.setValue(caller);
    }

    //TODO score

    @MNIRegister(normalParams = {"string tag"}, returnType = "selector")
    public static void tag(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getTag().put((MCString)tag.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string tag"}, returnType = "selector")
    public static void tagNot(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getTag().put((MCString)tag.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string team"}, returnType = "selector")
    public static void team(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getTeam().put((MCString)team.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string team"}, returnType = "selector")
    public static void teamNot(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getTeam().put((MCString)team.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string name"}, returnType = "selector")
    public static void name(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setName(new Pair<>((MCString)name.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string name"}, returnType = "selector")
    public static void nameNot(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setName(new Pair<>((MCString)name.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string type"}, returnType = "selector")
    public static void type(MCString type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setType(new Pair<>((MCString)type.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string type"}, returnType = "selector")
    public static void typeNot(MCString type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setType(new Pair<>((MCString)type.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"Predicate predicate"}, returnType = "selector")
    public static void predicate(MCString predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getPredicate().put((LootTablePredicate)predicate.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"Predicate predicate"}, returnType = "selector")
    public static void predicateNot(MCString predicate, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getPredicate().put((LootTablePredicate)predicate.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString()));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"range xRotation"}, returnType = "selector")
    public static void xRotation(RangeVar xRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDistance((RangeVar) xRotation.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"range yRotation"}, returnType = "selector")
    public static void yRotation(RangeVar yRotation, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDistance((RangeVar) yRotation.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"nbt n"}, returnType = "selector")
    public static void nbt(NBTBasedData<?> n, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setNbt((NBTBasedData<?>) n.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"range level"}, returnType = "selector")
    public static void level(RangeVar level, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setDistance((RangeVar) level.getTempVar());
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string gamemode"}, returnType = "selector")
    public static void gamemode(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setGamemode(new Pair<>((MCString)gamemode.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string gamemode"}, returnType = "selector")
    public static void gamemodeNot(MCString gamemode, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setGamemode(new Pair<>((MCString)gamemode.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString())));
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"top.mcfpp.lang.resource.Advancement advancements", "bool value"}, returnType = "selector")
    public static void advancements(Advancement advancements, MCBool value, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().getAdvancements().put((Advancement) advancements.clone(), value);
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"int limit"}, returnType = "selector")
    public static void limit(MCInt limit, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setLimit(limit);
        re.setValue(caller);
    }

    @MNIRegister(normalParams = {"string sort"}, returnType = "selector")
    public static void sort(MCString sort, SelectorVar caller, ValueWrapper<SelectorVar> re){
        caller.getValue().setSort((MCString) sort.clone());
        re.setValue(caller);
    }
}
