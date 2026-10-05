package com.gencode.lowcode.datasource.dto;

import lombok.Data;

/**
 * 连接测试结果（ok=true 成功；ok=false 时 message 为异常摘要，不抛 500）
 */
@Data
public class DatasourceTestResult {

    /** 是否连接成功 */
    private boolean ok;
    /** 提示信息 / 异常摘要 */
    private String message;

    public static DatasourceTestResult ok(String message) {
        return build(true, message);
    }

    public static DatasourceTestResult fail(String message) {
        return build(false, message);
    }

    private static DatasourceTestResult build(boolean ok, String message) {
        DatasourceTestResult r = new DatasourceTestResult();
        r.setOk(ok);
        r.setMessage(message);
        return r;
    }
}
