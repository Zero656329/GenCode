package com.gencode.framework.operlog;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志事件对象（切面在请求线程内组装，异步线程落库）
 */
@Data
public class OperLogEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String businessType;
    private String method;
    private String requestMethod;
    private String operUrl;
    private String operParam;
    private Long operId;
    private String operName;
    private String ip;
    private String tenantId;
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime operTime;
}
