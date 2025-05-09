package top.mcfpp.mni.annotation;

import org.jetbrains.annotations.NotNull;
import top.mcfpp.model.compound.Class;
import top.mcfpp.model.annotation.ClassAnnotation;

public class Base extends ClassAnnotation {

    String baseEntity;

    @SuppressWarnings("unused")
    private Base(String baseEntity) {
        super("Base","mcfpp.lang");
        this.baseEntity = baseEntity;
    }

    @Override
    public void forClass(@NotNull Class clazz) {
        clazz.setBaseEntity(baseEntity);
    }
}
