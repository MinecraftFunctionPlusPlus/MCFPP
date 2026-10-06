package top.mcfpp.mni;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.MCFPPTypeVar;
import top.mcfpp.mni.annotation.NoExternalWrites;
import top.mcfpp.type.MCFPPUnionType;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

@NoExternalWrites
public class MCFPPTypeData {

    @MNIOperator(operator = "|", paramType = "type", returnType = "type", returnsConstWhenArgsConst = true)
    public static void union(MCFPPTypeVar other, MCFPPTypeVar caller, ValueWrapper<MCFPPTypeVar> result) {
        result.setValue(new MCFPPTypeVar(
                new MCFPPUnionType(caller.getValue(), other.getValue()), TempPool.getVarIdentify()));
    }
}
