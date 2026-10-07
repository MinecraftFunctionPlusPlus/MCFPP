package top.mcfpp.mni;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.backend.NativeTextOperations;

public class DataObjectData {

    @MNIFunction(caller = "DataObject", returnType = "text")
    public static void toText(NativeCallContext context) {
        NativeTextOperations.INSTANCE.template(context);
    }
}
