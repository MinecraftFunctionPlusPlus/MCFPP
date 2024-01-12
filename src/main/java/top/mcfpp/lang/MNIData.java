package top.mcfpp.lang;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class MNIData {

    {
        init();
    }

    protected HashMap<String, MNIFunction> functions = new HashMap<>();

    public void addFunction(String name, MNIFunction function){
        functions.put(name, function);
    }

    public MNIFunction getFunction(String name){
        return functions.get(name);
    }

    public void invoke(String name, Var[] vars, CanSelectMember caller){
        MNIFunction function = getFunction(name);
        if (function != null) function.function(vars, caller);
    }

    /**
     * 在这个方法中注册你需要添加的函数
     */
    abstract void init();
}
