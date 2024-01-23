package top.mcfpp.lang.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.lib.Function;
import top.mcfpp.lib.FunctionAnnotation;

public class CompileTimeAnnotation extends FunctionAnnotation {

    public CompileTimeAnnotation(){
        super("CompileTime","mcfpp");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        System.out.println("CompileTime.forFunction");
    }
}
