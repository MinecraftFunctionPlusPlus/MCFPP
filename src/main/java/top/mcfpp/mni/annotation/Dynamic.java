package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.annotation.FunctionAnnotation;
import top.mcfpp.model.function.Function;

public class Dynamic extends FunctionAnnotation {

    public Dynamic() {
        super("Dynamic", "mcfpp.lang");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        function.setAst(null);
    }
}
