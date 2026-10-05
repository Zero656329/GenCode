package com.gencode.system.menu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 菜单新增/修改请求体
 */
@Data
public class MenuBody {

    /** 修改时必填 */
    private Long id;

    /** 父菜单 ID，0=根 */
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String name;

    private String path;
    private String component;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    private String perms;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
}
