package com.gencode.lowcode.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口调用结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpCallResultVO {

    /** 是否成功（HTTP 2xx） */
    private Boolean success;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 响应内容（截断 2000 字符） */
    private String respBody;
}
