package com.gencode.lowcode.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源新建请求体（password AES 加密落库）
 */
@Data
public class DatasourceCreateBody {

    /** 数据源名称 */
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /** 驱动类全名 */
    @NotBlank(message = "驱动类不能为空")
    private String driver;

    /** JDBC URL */
    @NotBlank(message = "JDBC URL不能为空")
    private String jdbcUrl;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文入参，AES 加密存储） */
    @NotBlank(message = "密码不能为空")
    private String password;

    private String remark;
}
