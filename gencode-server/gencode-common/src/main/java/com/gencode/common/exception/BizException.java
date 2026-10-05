package com.gencode.common.exception;

import lombok.Getter;

/**
 * 业务异常：msg + 可选 code（默认 500）
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String msg) {
        super(msg);
        this.code = 500;
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
