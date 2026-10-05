package com.gencode.lowcode.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源修改请求体（password 不传/空 = 不修改）
 */
@Data
public class DatasourceUpdateBody {

    /** 数据源 ID */
    @NotNull(message = "数据源ID不能为空")
    private Long id;

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

    /** 密码（明文入参；不传或空 = 沿用原密码） */
    private String password;

    private String remark;
}
