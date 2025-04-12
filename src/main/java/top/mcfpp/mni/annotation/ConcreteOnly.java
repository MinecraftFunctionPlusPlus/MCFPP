package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.core.lang.Var;
import top.mcfpp.model.Class;
import top.mcfpp.model.DataTemplate;
import top.mcfpp.model.annotation.Annotation;
import top.mcfpp.model.annotation.DataTemplateAnnotation;
import top.mcfpp.model.function.Function;
import top.mcfpp.util.LogProcessor;

import java.util.ArrayList;

public class ConcreteOnly extends Annotation {

    @SuppressWarnings("unused")
    private ConcreteOnly() {
        super("ConcreteOnly","mcfpp.annotation",new ArrayList<>());
    }

    @Override
    public void forDataTemplate(@NotNull DataTemplate data){}

    @Override
    public void forField(@NotNull Var<?> field){}


    @Override
    public void forClass(@NotNull Class clazz) {
        LogProcessor.error("@" + getIdentifier() + "cannot be used on class");
    }

    @Override
    public void forFunction(@NotNull Function function) {
        LogProcessor.error("@" + getIdentifier() + "cannot be used on function");
    }
}
