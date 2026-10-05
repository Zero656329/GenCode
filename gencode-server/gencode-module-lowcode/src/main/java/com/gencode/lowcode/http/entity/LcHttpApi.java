package com.gencode.lowcode.http.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方 HTTP 接口配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_http_api")
public class LcHttpApi extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 接口编码（租户内唯一，不可修改） */
    private String code;
    /** 接口名称 */
    private String name;
    /** 请求方法：GET/POST */
    private String method;
    /** 目标地址（支持 {param} 路径参数） */
    private String url;
    /** 请求头 JSON */
    private String headersJson;
    /** POST 请求体模板（支持 {param} 占位） */
    private String bodyTemplate;
    /** 超时时间（毫秒） */
    private Integer timeoutMs;
    /** 状态：0启用 1停用 */
    private Integer status;
    private String remark;
}
