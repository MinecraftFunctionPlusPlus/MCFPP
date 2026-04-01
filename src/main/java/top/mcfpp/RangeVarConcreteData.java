package top.mcfpp;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.lang.JavaVar;
import top.mcfpp.core.lang.RangeVarConcrete;
import top.mcfpp.core.lang.iterator.ConcreteIterator;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.ValueWrapper;

public class RangeVarConcreteData {

    @MNIFunction(caller = "range", returnType = "JavaVar")
    public static void iterator(RangeVarConcrete caller, ValueWrapper<JavaVar> re) {
        //check if both sides exist and are integer
        if(caller.getValue().getFirst() == null || caller.getValue().getSecond() == null) {
            LogProcessor.error("Both sides of the range must exist");
            return;
        }
        var left = caller.getValue().getFirst();
        var right = caller.getValue().getSecond();
        if(left != (float) Math.floor(left) || right != (float) Math.floor(right)) {
            LogProcessor.error("Both sides of the range must be integers");
            return;
        }
        re.setValue(new JavaVar(ConcreteIterator.fromIntRange(caller)));
    }
}
