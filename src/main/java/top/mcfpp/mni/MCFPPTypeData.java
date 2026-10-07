package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.backend.NativeOperatorOperations;
import top.mcfpp.mni.annotation.NoExternalWrites;

@NoExternalWrites
public class MCFPPTypeData {

    @MNIOperator(operator = "|", paramType = "type", returnType = "type", returnsConstWhenArgsConst = true)
    public static void union(NativeCallContext context) {
        NativeOperatorOperations.INSTANCE.unionType(context);
    }
}
