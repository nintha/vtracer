package none.nintha.vtracer.util;

import com.google.common.collect.Maps;

import java.util.Map;

public class Result {
    private int code;
    private String msg = "".intern();
    private Map<String, Object> data = Maps.newLinkedHashMap();

    public Result() {
        super();
    }

    public Result(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static Result success() {
        return new Result(200, "OK");
    }

    public static Result failed(String errorMsg) {
        return new Result(400, errorMsg);
    }

    public static Result unlogin() {
        return new Result(401, "未登录，请登录后再试");
    }

    public Result put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Result putAll(Map<String, Object> map) {
        data.putAll(map);
        return this;
    }

    public Result remove(String key) {
        data.remove(key);
        return this;
    }


    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
