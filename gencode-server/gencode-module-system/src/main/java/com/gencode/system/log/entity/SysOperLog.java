package com.gencode.system.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（无审计/逻辑删除列，独立实体；tenant_id 插入时手动填操作者租户）
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String title;
    /** 业务类型（INSERT/UPDATE/DELETE/EXPORT等） */
    private String businessType;
    /** 方法名 */
    private String method;
    private String requestMethod;
    private String operUrl;
    private String operParam;
    private Long operId;
    private String operName;
    private String ip;
    /** （0成功 1失败） */
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime operTime;
}
