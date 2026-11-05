package top.mcfpp.browser.json;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered JSON object used by library metadata and generated datapack resources. */
public final class JSONObject extends LinkedHashMap<String, Object> {
    public JSONObject() { }
    public JSONObject(Map<String, Object> values) { super(values); }
    public static Object parse(String text) { return com.alibaba.fastjson2.JSON.parse(text); }
    public static com.alibaba.fastjson2.JSONObject parse(String text, com.alibaba.fastjson2.JSONReader.Feature... features) { return com.alibaba.fastjson2.JSON.parseObject(text); }
    public static com.alibaba.fastjson2.JSONObject parseObject(String text) { return com.alibaba.fastjson2.JSON.parseObject(text); }
    public com.alibaba.fastjson2.JSONObject getJSONObject(String key) { return (com.alibaba.fastjson2.JSONObject) get(key); }
    public com.alibaba.fastjson2.JSONArray getJSONArray(String key) { return (com.alibaba.fastjson2.JSONArray) get(key); }
    public String getString(String key) { return get(key) == null ? null : get(key).toString(); }
    public int getIntValue(String key) { return get(key) == null ? 0 : ((Number) get(key)).intValue(); }
    public boolean getBooleanValue(String key) { return Boolean.TRUE.equals(get(key)); }
    @Override public String toString() { return com.alibaba.fastjson2.JSON.toJSONString(this); }
    public String toString(com.alibaba.fastjson2.JSONWriter.Feature... features) { return com.alibaba.fastjson2.JSON.toJSONString(this, features); }
}
