package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.annotation.FunctionAnnotation;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.function.FunctionTag;

public class Tick extends FunctionAnnotation {

    public Tick(){
        super("Tick", "mcfpp.lang");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        function.addTag(FunctionTag.Companion.getTICK());
    }
}
