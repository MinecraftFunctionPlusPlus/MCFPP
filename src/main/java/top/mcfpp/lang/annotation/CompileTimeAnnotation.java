package top.mcfpp.lang.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.lang.Var;
import top.mcfpp.lib.Annotation;
import top.mcfpp.lib.Class;
import top.mcfpp.lib.Function;
import top.mcfpp.lib.FunctionAnnotation;

public class CompileTimeAnnotation extends FunctionAnnotation {

    public CompileTimeAnnotation(){
        super("CompileTimeAnnotation","mcfpp");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        System.out.println("CompileTimeAnnotation.forFunction");
    }
}
