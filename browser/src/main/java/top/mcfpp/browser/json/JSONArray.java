package top.mcfpp.browser.json;

import java.util.ArrayList;

/** JSON array with the small typed access surface used by the compiler. */
public final class JSONArray extends ArrayList<Object> {
    public com.alibaba.fastjson2.JSONObject getJSONObject(int index) { return (com.alibaba.fastjson2.JSONObject) get(index); }
    public String getString(int index) { return (String) get(index); }
    @Override public String toString() { return com.alibaba.fastjson2.JSON.toJSONString(this); }
}
