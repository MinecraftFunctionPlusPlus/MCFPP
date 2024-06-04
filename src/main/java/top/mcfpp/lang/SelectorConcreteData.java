package top.mcfpp.lang;

import kotlin.Pair;
import kotlin.jvm.functions.Function4;
import net.querz.nbt.tag.Tag;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.function.MNIMethodContainer;
import top.mcfpp.util.ValueWrapper;

import java.util.HashMap;

public class SelectorConcreteData extends BaseMNIMethodContainer {

    public static final NBTListConcreteData INSTANCE = new NBTListConcreteData();

    static {
        //selector.x<int x>
        methods.put("x", (readOnlyVar, normalVar, caller, returnVar) -> {
            var x = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setX(x.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //selector.y<int y>
        methods.put("y", (readOnlyVar, normalVar, caller, returnVar) -> {
            var y = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setY(y.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //selector.z<int z>
        methods.put("z", (readOnlyVar, normalVar, caller, returnVar) -> {
            var z = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setZ(z.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //selector.distance<int distance>
        methods.put("distance", (readOnlyVar, normalVar, caller, returnVar) -> {
            var distance = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setDistance(new OneIntConcreteRange(distance.getValue()));
            returnVar.setValue(qwq);
            return null;
        });
        //TODO selector.distance<range distance>
        //dx
        methods.put("dx", (readOnlyVar, normalVar, caller, returnVar) -> {
            var dx = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setDx(dx.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //dy
        methods.put("dy", (readOnlyVar, normalVar, caller, returnVar) -> {
            var dy = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setDy(dy.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //dz
        methods.put("dz", (readOnlyVar, normalVar, caller, returnVar) -> {
            var dz = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setDz(dz.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //TODO score
        //tag
        methods.put("tag", (readOnlyVar, normalVar, caller, returnVar) -> {
            var tag = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getTag().put(tag.getValue().getValue(), true);
            returnVar.setValue(qwq);
            return null;
        });
        //tag!
        methods.put("tagNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var tag = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getTag().put(tag.getValue().getValue(), false);
            returnVar.setValue(qwq);
            return null;
        });
        //team
        methods.put("team", (readOnlyVar, normalVar, caller, returnVar) -> {
            var team = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getTeam().put(team.getValue().getValue(), true);
            returnVar.setValue(qwq);
            return null;
        });
        //team!
        methods.put("teamNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var team = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getTeam().put(team.getValue().getValue(), false);
            returnVar.setValue(qwq);
            return null;
        });
        //name
        methods.put("name", (readOnlyVar, normalVar, caller, returnVar) -> {
            var name = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setName(new Pair<>(name.getValue().getValue(), true));
            returnVar.setValue(qwq);
            return null;
        });
        //name!
        methods.put("nameNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var name = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setName(new Pair<>(name.getValue().getValue(), false));
            returnVar.setValue(qwq);
            return null;
        });
        //type
        methods.put("type", (readOnlyVar, normalVar, caller, returnVar) -> {
            var type = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setType(new Pair<>(type.getValue().getValue(), false));
            returnVar.setValue(qwq);
            return null;
        });
        //type!
        methods.put("typeNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var type = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setType(new Pair<>(type.getValue().getValue(), true));
            returnVar.setValue(qwq);
            return null;
        });
        //predicate
        methods.put("predicate", (readOnlyVar, normalVar, caller, returnVar) -> {
            var predicate = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getPredicate().put(predicate.getValue().getValue(), true);
            returnVar.setValue(qwq);
            return null;
        });
        //tag!
        methods.put("predicateNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var predicate = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getPredicate().put(predicate.getValue().getValue(), false);
            returnVar.setValue(qwq);
            return null;
        });
        //x_rotation
        methods.put("xRotation", (readOnlyVar, normalVar, caller, returnVar) -> {
            var xRotation = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setX_rotation(new OneIntConcreteRange(xRotation.getValue()));
            returnVar.setValue(qwq);
            return null;
        });
        //y_rotation
        methods.put("yRotation", (readOnlyVar, normalVar, caller, returnVar) -> {
            var yRotation = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setY_rotation(new OneIntConcreteRange(yRotation.getValue()));
            returnVar.setValue(qwq);
            return null;
        });
        //nbt
        methods.put("nbt", (readOnlyVar, normalVar, caller, returnVar) -> {
            var nbt = (NBTBasedDataConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setNbt(nbt.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //level
        methods.put("level", (readOnlyVar, normalVar, caller, returnVar) -> {
            var level = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setLevel(new OneIntConcreteRange(level.getValue()));
            returnVar.setValue(qwq);
            return null;
        });
        //gamemode
        methods.put("gamemode", (readOnlyVar, normalVar, caller, returnVar) -> {
            var gamemode = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setGamemode(new Pair<>(gamemode.getValue().getValue(), true));
            returnVar.setValue(qwq);
            return null;
        });
        //gamemode!
        methods.put("gamemodeNot", (readOnlyVar, normalVar, caller, returnVar) -> {
            var gamemode = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setGamemode(new Pair<>(gamemode.getValue().getValue(), false));
            returnVar.setValue(qwq);
            return null;
        });
        //advancements(string advancements, bool value)
        methods.put("advancements", (readOnlyVar, normalVar, caller, returnVar) -> {
            var advancements = (MCStringConcrete)readOnlyVar[0];
            var value = (MCBoolConcrete)readOnlyVar[1];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().getAdvancements().put(advancements.getValue().getValue(), value.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //limit
        methods.put("limit", (readOnlyVar, normalVar, caller, returnVar) -> {
            var limit = (MCIntConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setLimit(limit.getValue());
            returnVar.setValue(qwq);
            return null;
        });
        //sort
        methods.put("sort", (readOnlyVar, normalVar, caller, returnVar) -> {
            var sort = (MCStringConcrete)readOnlyVar[0];
            SelectorConcrete qwq = (SelectorConcrete)((SelectorConcrete)caller).clone();
            qwq.getValue().setSort(sort.getValue().getValue());
            returnVar.setValue(qwq);
            return null;
        });
    }

}
