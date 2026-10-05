package com.gencode.lowcode.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码外部数据源（密码 AES 加密存储，任何出参一律置 null 不回显）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_datasource")
public class LcDatasource extends TenantBaseEntity {

    private static final long serialVersionUID = 1L;

    /** 数据源名称 */
    private String name;
    /** 驱动类全名 */
    private String driver;
    /** JDBC URL */
    private String jdbcUrl;
    /** 用户名 */
    private String username;
    /** 密码（AES 加密密文，查询出参一律置 null） */
    private String password;
    private String remark;
}
