package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.*;
import top.mcfpp.core.lang.bool.MCBool;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.ValueWrapper;

public class NBTDictionaryData {
    @MNIFunction(caller = "dict")
    public static void clear(NBTDictionary caller){
        Function.Companion.addCommand(new Command("data modify")
                .build(caller.getNbtPath().toCommandPart(), true)
                .build(" set value {}", false)
        );
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict", returnType = "bool")
    public static void containsKey(MCString key, NBTDictionary caller, ValueWrapper<MCBool> re){
        if(key instanceof MCStringConcrete keyC){
            Function.Companion.addCommand(new Command("execute " +
                    "store result score" + re.getValue().getIdentifier() + " " + re.getValue().getBoolObject() + " " +
                    "if data")
                    .build(caller.getNbtPath().toCommandPart(), true).build("." + keyC.getValue().getValue(), false)
            );
        }else {
            Function.Companion.addCommand(new Command("execute " +
                "store result score" + re.getValue().getIdentifier() + " " + re.getValue().getBoolObject() + " " +
                "if data")
                .build(caller.getNbtPath().toCommandPart(), true).build(".", false).buildMacro(key, false)
            );
        }
    }

    @MNIFunction(normalParams = {"dict source"}, caller = "dict")
    public static void merge(NBTDictionary source, NBTDictionary caller){
        if(source instanceof NBTDictionaryConcrete dictC){
            Function.Companion.addCommand(new Command("data modify")
                .build(caller.getNbtPath().toCommandPart(), true)
                .build("merge value", true)
                .build(dictC.getValue().valueToString(), true)
            );
        }else {
            Function.Companion.addCommand(new Command("data modify")
                .build(caller.getNbtPath().toCommandPart(), true)
                .build("merge from", true)
                .build(source.getNbtPath().toCommandPart(), true)
            );
        }
    }

    @MNIFunction(normalParams = {"string key"}, caller = "dict")
    public static void remove(MCString key, NBTDictionary caller){
        if(key instanceof MCStringConcrete keyC){
            Function.Companion.addCommand(new Command("data remove")
                .build(caller.getNbtPath().toCommandPart(), true)
                .build("." + keyC.getValue().getValue(), false)
            );
        }else {
            Function.Companion.addCommand(new Command("data remove")
                .build(caller.getNbtPath().toCommandPart(), true)
                .build(".", false)
                .buildMacro(key, false)
            );
        }
    }
}
