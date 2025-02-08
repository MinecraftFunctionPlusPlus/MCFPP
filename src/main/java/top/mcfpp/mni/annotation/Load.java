package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.annotation.FunctionAnnotation;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.FunctionTag;

public class Load extends FunctionAnnotation {

    public Load(){
        super("Load","mcfpp.annotation");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        function.addTag(FunctionTag.Companion.getLOAD());
    }
}
