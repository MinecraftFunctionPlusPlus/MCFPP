package top.mcfpp.mni;

import top.mcfpp.analysis.StorageAccess;
import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.RangeVar;
import top.mcfpp.core.lang.RangeVarConcrete;
import top.mcfpp.core.lang.iterator.ConcreteIterator;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.ValueWrapper;
import top.mcfpp.mni.annotation.NoExternalWrites;

public class RangeVarData {
    @NoExternalWrites
    @MNIFunction(caller = "range", returnType = "JavaVar")
    public static void iterator(RangeVar caller, ValueWrapper<JavaVar> result) {
        var loaded = StorageAccess.INSTANCE.read(caller);
        if (!(loaded instanceof RangeVarConcrete range)) {
            LogProcessor.error("Runtime range iteration requires typed IR with proven integer endpoints");
            return;
        }
        var left = range.getValue().getFirst();
        var right = range.getValue().getSecond();
        if (left == null || right == null) {
            LogProcessor.error("Both sides of the range must exist");
            return;
        }
        if (left.doubleValue() != Math.floor(left.doubleValue()) || right.doubleValue() != Math.floor(right.doubleValue())
                || left.doubleValue() < Integer.MIN_VALUE || right.doubleValue() > Integer.MAX_VALUE) {
            LogProcessor.error("Both sides of the range must be 32-bit integers");
            return;
        }
        result.setValue(new JavaVar(ConcreteIterator.fromIntRange(range)));
    }
}
