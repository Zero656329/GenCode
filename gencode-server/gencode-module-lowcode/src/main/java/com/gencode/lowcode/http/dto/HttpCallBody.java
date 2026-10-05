package com.gencode.lowcode.http.dto;

import lombok.Data;

import java.util.Map;

/**
 * 接口调用请求体：params 用于替换 url/bodyTemplate 中 {param} 占位符
 */
@Data
public class HttpCallBody {

    /** 占位参数（可空） */
    private Map<String, Object> params;
}
