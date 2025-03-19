package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.ClassPointer;

public class ObjectData {

    @MNIFunction(caller = "Object")
    public static void tick(ClassPointer caller){}

    @MNIFunction(caller = "Object")
    public static void load(ClassPointer caller){}

}
