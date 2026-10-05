package com.gencode.lowcode.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 第三方 HTTP 调用日志（无审计列与逻辑删除，不继承 BaseEntity）
 */
@Data
@TableName("lc_http_log")
public class LcHttpLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编号 */
    private String tenantId;
    /** 接口编码 */
    private String apiCode;
    /** 请求方法 */
    private String method;
    /** 实际请求地址 */
    private String url;
    /** 请求体 */
    private String requestBody;
    /** 是否成功 */
    private Boolean success;
    /** 耗时（毫秒） */
    private Long costMs;
    /** 响应内容（截断 2000 字符） */
    private String respBody;
    /** 调用时间 */
    private LocalDateTime createTime;
}
