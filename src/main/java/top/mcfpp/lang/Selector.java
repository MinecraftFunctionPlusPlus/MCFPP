package top.mcfpp.lang;

import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mcfpp.exception.ArgumentNotMatchException;
import top.mcfpp.exception.TODOException;
import top.mcfpp.exception.VariableConverseException;
import top.mcfpp.lib.Function;

import java.util.HashMap;
import java.util.List;
import com.mojang.brigadier.*;

//TODO
//一个目标选择器
public class Selector extends Var {

    public String type = "selector";
    public String text = null;
    public String selectorType = null;
    public HashMap<String, Object> selectorArgs = new HashMap<>();

    public boolean singleEntity = false;

    public Selector(Var b){
        super(b);
    }

    public Selector(String string){
        super("");
        text = string;
        //@a[a=b,b=c]
        //解析参数
        String args = string.split("\\[")[1].split("]")[0];
        selectorType = string.substring(0,2);

    }

    @NotNull
    @Override
    public Pair<Var, Boolean> getMemberVar(@NotNull String key, @NotNull AccessModifier accessModifier) {
        throw new TODOException("");
    }

    @NotNull
    @Override
    public Pair<Function, Boolean> getMemberFunction(@NotNull String key, @NotNull List<String> params, @NotNull AccessModifier accessModifier) {
        throw new TODOException("");
    }

    @Override
    public void assign(@Nullable Var b) throws VariableConverseException {
        throw new TODOException("");
    }

    @Nullable
    @Override
    public Var cast(@NotNull String type) {
        throw new TODOException("");
    }

    @NotNull
    @Override
    public Object clone() {
        throw new TODOException("");
    }

    @NotNull
    @Override
    public Var getTempVar(@NotNull HashMap<Var, String> cache) {
        throw new TODOException("");
    }

    private static Object resolveSelectorParam(String key, String value){
        switch (key){
            case "x":
            case "y":
            case "z":
            case "dx":
            case "dy":
            case "dz":
                return Double.parseDouble(value);
            case "distance":
                String start = value.split("\\.\\.")[0];
                String end = value.split("\\.\\.")[1];
                Double startD,endD;
                if(start.equals(""))
                    start = null;
                else {
                    startD = Double.parseDouble(start);
                }
                if(end.equals("")) end = null;
                try {

                } catch (NumberFormatException e) {
                    throw new TODOException("未实现的特性：动态目标选择器");
                }
                break;
            case "scores":
                break;
            case "tag":
                break;
            case "team":
                break;
            case "limit":
                break;
            case "sort":
                break;
            case "gamemode":
                break;
            case "level":
                break;
            case "name":
                break;
            case "type":
                break;
            case "nbt":
                break;
            case "advancements":
                break;
            case "predicate":
                break;
            default:
                throw new ArgumentNotMatchException("");
        }
        return null;
    }
}