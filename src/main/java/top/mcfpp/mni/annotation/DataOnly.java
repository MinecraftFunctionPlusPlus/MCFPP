package top.mcfpp.mni.annotation;

import kotlin.Unit;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.OnScoreboard;
import top.mcfpp.core.lang.Var;
import top.mcfpp.model.Class;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.Annotation;
import top.mcfpp.model.annotation.FieldAnnotation;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.LogProcessor;

import java.util.ArrayList;

public class DataOnly extends Annotation {
    public DataOnly(){
        super("DataOnly", "mcfpp.annotation", new ArrayList<>());
    }

    @Override
    public void forField(@NotNull Var<?> field) {
        if(field instanceof OnScoreboard scoreboard){
            scoreboard.setDataOnly(true);
        }else {
            LogProcessor.warn("@DataOnly can only be used on int");
        }
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data) {
        data.getField().forEachVar((v) -> {
            if(v instanceof OnScoreboard scoreboard){
                scoreboard.setDataOnly(true);
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    public void forClass(@NotNull Class clazz) {
        LogProcessor.error("@" + getIdentifier() + "cannot be used on class");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        LogProcessor.error("@" + getIdentifier() + "cannot be used on function");
    }
}
