package com.gencode.lowcode.http.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 接口新建请求体（status 默认 0 启用）
 */
@Data
public class HttpApiCreateBody {

    /** 接口编码（租户内唯一） */
    @NotBlank(message = "接口编码不能为空")
    private String code;

    /** 接口名称 */
    @NotBlank(message = "接口名称不能为空")
    private String name;

    /** 请求方法：GET/POST */
    @NotBlank(message = "请求方法不能为空")
    private String method;

    /** 目标地址（支持 {param} 路径参数） */
    @NotBlank(message = "目标地址不能为空")
    private String url;

    /** 请求头 JSON */
    private String headersJson;

    /** POST 请求体模板（支持 {param} 占位） */
    private String bodyTemplate;

    /** 超时时间（毫秒），空默认 5000 */
    private Integer timeoutMs;

    private String remark;
}
