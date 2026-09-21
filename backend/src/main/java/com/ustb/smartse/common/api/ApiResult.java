package com.ustb.smartse.common.api;

import lombok.Data;

/**
 * API响应结果封装类
 * @param <T> 数据类型
 */
@Data
public class ApiResult<T> {
    private Integer code;      // 状态码
    private String message;    // 返回信息
    private T data;            // 返回数据
    private boolean success;   // 是否成功

    public ApiResult() {
    }

    public ApiResult(Integer code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    /**
     * 成功返回结果
     * @param data 返回数据
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "操作成功", data, true);
    }

    /**
     * 成功返回结果
     * @param data 返回数据
     * @param message 提示信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> success(T data, String message) {
        return new ApiResult<>(200, message, data, true);
    }

    /**
     * 失败返回结果
     * @param errorCode 错误码
     * @param message 错误信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> failed(Integer errorCode, String message) {
        return new ApiResult<>(errorCode, message, null, false);
    }

    /**
     * 失败返回结果
     * @param message 错误信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> error(String message) {
        return new ApiResult<>(500, message, null, false);
    }

    /**
     * 参数验证失败返回结果
     * @param message 错误信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> validateFailed(String message) {
        return new ApiResult<>(400, message, null, false);
    }

    /**
     * 未授权返回结果
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> unauthorized() {
        return new ApiResult<>(401, "暂未登录或token已经过期", null, false);
    }

    /**
     * 未授权返回结果
     * @param message 错误信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> unauthorized(String message) {
        return new ApiResult<>(401, message, null, false);
    }

    /**
     * 禁止访问返回结果
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> forbidden() {
        return new ApiResult<>(403, "没有相关权限", null, false);
    }

    /**
     * 禁止访问返回结果
     * @param message 错误信息
     * @param <T> 数据类型
     * @return API响应结果
     */
    public static <T> ApiResult<T> forbidden(String message) {
        return new ApiResult<>(403, message, null, false);
    }
} 