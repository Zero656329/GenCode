package com.gencode.system.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志（无审计/逻辑删除列，独立实体）
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;
    private String username;
    private String ip;
    private String browser;
    private String os;
    /** （0成功 1失败） */
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
