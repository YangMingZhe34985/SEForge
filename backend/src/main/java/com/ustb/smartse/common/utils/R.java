package com.ustb.smartse.common.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一API响应结果封装
 */
public class R extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    public R() {
        put("code", 200);
        put("message", "操作成功");
    }

    public static R ok() {
        return new R();
    }

    public static R ok(String message) {
        R r = new R();
        r.put("message", message);
        return r;
    }

    public static R error(int code, String message) {
        R r = new R();
        r.put("code", code);
        r.put("message", message);
        return r;
    }

    public static R error() {
        return error(500, "未知异常，请联系管理员");
    }

    public static R error(String message) {
        return error(500, message);
    }

    @Override
    public R put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public R putMap(Map<String, Object> map) {
        super.putAll(map);
        return this;
    }
} 