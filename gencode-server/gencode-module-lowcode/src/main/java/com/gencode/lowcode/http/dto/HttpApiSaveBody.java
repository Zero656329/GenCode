package com.gencode.lowcode.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 接口保存请求体（code 不可修改，故不含 code 字段）
 */
@Data
public class HttpApiSaveBody {

    /** 接口 ID */
    @NotNull(message = "接口ID不能为空")
    private Long id;

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

    /** 状态：0启用 1停用（空=不修改） */
    private Integer status;

    private String remark;
}
