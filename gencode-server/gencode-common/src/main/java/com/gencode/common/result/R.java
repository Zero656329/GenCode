package com.gencode.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装：code=0 成功，非 0 失败
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return build(0, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return build(0, "success", data);
    }

    public static <T> R<T> fail(String msg) {
        return build(500, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return build(code, msg, null);
    }

    private static <T> R<T> build(int code, String msg, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
