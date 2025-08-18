package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIOperator;
import top.mcfpp.core.lang.obj.DataTemplateObject;
import top.mcfpp.model.Member;
import top.mcfpp.model.scope.GlobalScope;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

public class ItemPredicatePartData {

    @MNIOperator(paramType = "ItemPredicatePart", operator = "|", returnType = "ItemPredicatePart")
    public static void Or(DataTemplateObject b, DataTemplateObject caller, ValueWrapper<DataTemplateObject> re){
        var r = GlobalScope.getTemplate("mcfpp.minecraft.item","OrItemPredicatePart");
        assert r != null;
        var obj = (DataTemplateObject) r.getType().build(TempPool.getVarIdentify());
        var predicate1 = obj.getMemberVar("predicate1", Member.AccessModifier.PUBLIC).component1();
        var predicate2 = obj.getMemberVar("predicate2", Member.AccessModifier.PUBLIC).component1();
        predicate1.replacedBy(predicate1.assignedBy(caller));
        predicate2.replacedBy(predicate1.assignedBy(b));
        re.setValue(obj);
    }

}
