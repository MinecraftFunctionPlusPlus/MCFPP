package top.mcfpp.mni;

import kotlin.Pair;
import top.mcfpp.annotations.MNIRegister;
import top.mcfpp.lang.*;
import top.mcfpp.lib.OneIntConcreteRange;
import top.mcfpp.util.ValueWrapper;

import java.util.UUID;

public class SelectorConcreteData extends BaseMNIMethodContainer {

    @MNIRegister(normalParams = {"int x"}, returnType = "selector")
    public static void x(MCInt x, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setX((MCInt) x.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"int y"}, returnType = "selector")
    public static void y(MCInt y, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setY((MCInt) y.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"int z"}, returnType = "selector")
    public static void z(MCInt z, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setZ((MCInt) z.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"range distance"}, returnType = "selector")
    public static void distance(RangeVar distance, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setDistance((RangeVar) distance.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"int dx"}, returnType = "selector")
    public static void dx(MCInt dx, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setDx((MCInt) dx.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"int dy"}, returnType = "selector")
    public static void dy(MCInt dy, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setDy((MCInt) dy.getTempVar());
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"int dz"}, returnType = "selector")
    public static void dz(MCInt dz, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setDz((MCInt) dz.getTempVar());
        re.setValue(qwq);
    }

    //TODO score

    @MNIRegister(normalParams = {"string tag"}, returnType = "selector")
    public static void tag(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().getTag().put((MCString)tag.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString()));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string tag"}, returnType = "selector")
    public static void tagNot(MCString tag, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().getTag().put((MCString)tag.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString()));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string team"}, returnType = "selector")
    public static void team(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().getTeam().put((MCString)team.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString()));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string team"}, returnType = "selector")
    public static void teamNot(MCString team, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().getTeam().put((MCString)team.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString()));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string name"}, returnType = "selector")
    public static void name(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setName(new Pair<>((MCString)name.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString())));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string name"}, returnType = "selector")
    public static void nameNot(MCString name, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setName(new Pair<>((MCString)name.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString())));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string type"}, returnType = "selector")
    public static void type(MCString type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setType(new Pair<>((MCString)type.clone(), new MCBoolConcrete(false, UUID.randomUUID().toString())));
        re.setValue(qwq);
    }

    @MNIRegister(normalParams = {"string type"}, returnType = "selector")
    public static void typeNot(MCString type, SelectorVar caller, ValueWrapper<SelectorVar> re){
        SelectorVar qwq = caller.clone();
        qwq.getValue().setType(new Pair<>((MCString)type.clone(), new MCBoolConcrete(true, UUID.randomUUID().toString())));
        re.setValue(qwq);
    }
//
//    @MNIRegister(normalParams = {"Predicate predicate"}, returnType = "selector")
//
//
//    static {
//        //predicate
//        methods.put("predicate", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var predicate = (MCStringConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().getPredicate().put(predicate.getValue().getValue(), true);
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //tag!
//        methods.put("predicateNot", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var predicate = (MCStringConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().getPredicate().put(predicate.getValue().getValue(), false);
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //x_rotation
//        methods.put("xRotation", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var xRotation = (MCIntConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setX_rotation(new OneIntConcreteRange(xRotation.getValue()));
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //y_rotation
//        methods.put("yRotation", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var yRotation = (MCIntConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setY_rotation(new OneIntConcreteRange(yRotation.getValue()));
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //nbt
//        methods.put("nbt", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var nbt = (NBTBasedDataConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setNbt(nbt.getValue());
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //level
//        methods.put("level", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var level = (MCIntConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setLevel(new OneIntConcreteRange(level.getValue()));
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //gamemode
//        methods.put("gamemode", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var gamemode = (MCStringConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setGamemode(new Pair<>(gamemode.getValue().getValue(), true));
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //gamemode!
//        methods.put("gamemodeNot", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var gamemode = (MCStringConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setGamemode(new Pair<>(gamemode.getValue().getValue(), false));
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //advancements(string advancements, bool value)
//        methods.put("advancements", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var advancements = (MCStringConcrete)readOnlyVar[0];
//            var value = (MCBoolConcrete)readOnlyVar[1];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().getAdvancements().put(advancements.getValue().getValue(), value.getValue());
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //limit
//        methods.put("limit", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var limit = (MCIntConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setLimit(limit.getValue());
//            returnVar.setValue(qwq);
//            return null;
//        });
//        //sort
//        methods.put("sort", (readOnlyVar, normalVar, caller, returnVar) -> {
//            var sort = (MCStringConcrete)readOnlyVar[0];
//            SelectorVarConcrete qwq = (SelectorVarConcrete)((SelectorVarConcrete)caller).clone();
//            qwq.getValue().setSort(sort.getValue().getValue());
//            returnVar.setValue(qwq);
//            return null;
//        });
//    }

}
