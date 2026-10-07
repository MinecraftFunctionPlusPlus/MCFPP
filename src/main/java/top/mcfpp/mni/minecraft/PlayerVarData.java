package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.entity.PlayerVar;
import top.mcfpp.core.lang.nbt.MCString;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.util.ValueWrapper;
public class PlayerVarData {
    //region clear
    @MNIFunction(normalParams = {"string", "ItemPredicate"}, caller = "Player", returnType = "CommandReturn")
    public static void clear(MCString id, DataTemplateObject predicate, PlayerVar caller, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("clear", caller, id, predicate);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //TODO check(Item item)->bool
    //endregion

    //region spawnpoint
    @MNIFunction(normalParams = {"pos3 = pos3.RELATIVE", "pos2 = pos2.RELATIVE"} ,caller = "Player", returnType = "CommandReturn")
    public static void setSpawnpoint(Pos3Var pos3, Pos2Var pos2, PlayerVar player, ValueWrapper<CommandReturn> returnValue){
        Command command = Command.Companion.buildAll("spawnpoint", player, pos3, pos2);
        Commands.processMacroCommandReturn(returnValue, command);
    }
    //endregion

}
