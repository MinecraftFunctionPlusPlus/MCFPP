package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.annotations.MNIMutator;
import top.mcfpp.command.Command;
import top.mcfpp.command.Commands;
import top.mcfpp.core.lang.CommandReturn;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.core.lang.obj.EnumVar;
import top.mcfpp.core.lang.JsonText;
import top.mcfpp.core.lang.bool.BaseBool;
import top.mcfpp.model.compound.DataTemplate;
import top.mcfpp.util.ValueWrapper;

public class TeamData {
    @MNIFunction(caller = "Team", returnType = "CommandReturn")
    public static void unregister(DataTemplateObject caller, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("team remove", caller);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "Team", returnType = "CommandReturn")
    public static void register(DataTemplateObject caller, ValueWrapper<CommandReturn> re) {
        var displayName = DataTemplate.getField(caller, "displayName");
        var command = Command.Companion.buildAll("team add", caller, displayName);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIFunction(caller = "Team", returnType = "CommandReturn")
    public static void clear(DataTemplateObject caller, ValueWrapper<CommandReturn> re) {
        var command = Command.Companion.buildAll("team empty", caller);
        Commands.processMacroCommandReturn(re, command);
    }

    @MNIMutator("displayName")
    public static void setDisplayName(DataTemplateObject caller, JsonText displayName) {
        var command = Command.Companion.buildAll("team modify", caller, "displayName", displayName);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("color")
    public static void setColor(DataTemplateObject caller, EnumVar color) {
        var command = Command.Companion.buildAll("team modify", caller, "color", color);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("friendlyFire")
    public static void setFriendlyFire(DataTemplateObject caller, BaseBool friendlyFire) {
        var command = Command.Companion.buildAll("team modify", caller, "friendlyFire", friendlyFire);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("seeFriendlyInvisibles")
    public static void setSeeFriendlyInvisibles(DataTemplateObject caller, BaseBool seeFriendlyInvisibles) {
        var command = Command.Companion.buildAll("team modify", caller, "seeFriendlyInvisibles", seeFriendlyInvisibles);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("nametagVisibility")
    public static void setNametagVisibility(DataTemplateObject caller, EnumVar nametagVisibility) {
        var command = Command.Companion.buildAll("team modify", caller, "nametagVisibility", nametagVisibility);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("deathMessageVisibility")
    public static void setDeathMessageVisibility(DataTemplateObject caller, EnumVar deathMessageVisibility) {
        var command = Command.Companion.buildAll("team modify", caller, "deathMessageVisibility", deathMessageVisibility);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("collisionRule")
    public static void setCollisionRule(DataTemplateObject caller, EnumVar collisionRule) {
        var command = Command.Companion.buildAll("team modify", caller, "collisionRule", collisionRule);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("prefix")
    public static void setPrefix(DataTemplateObject caller, JsonText prefix) {
        var command = Command.Companion.buildAll("team modify", caller, "prefix", prefix);
        Commands.processMacroCommand(command);
    }

    @MNIMutator("suffix")
    public static void setSuffix(DataTemplateObject caller, JsonText suffix) {
        var command = Command.Companion.buildAll("team modify", caller, "suffix", suffix);
        Commands.processMacroCommand(command);
    }


}
