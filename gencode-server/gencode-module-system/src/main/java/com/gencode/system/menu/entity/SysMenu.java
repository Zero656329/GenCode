package com.gencode.system.menu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gencode.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单权限（sys_menu 无 tenant_id 列，且为租户忽略表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long parentId;
    private String name;
    /** 路由地址（目录/菜单） */
    private String path;
    /** 前端组件路径（如 system/user/index） */
    private String component;
    /** 类型（M目录 C菜单 F按钮） */
    private String menuType;
    /** 权限标识（如 system:user:list） */
    private String perms;
    /** 图标（AntD 图标名） */
    private String icon;
    private Integer sort;
    /** 显示（0显示 1隐藏） */
    private Integer visible;
    private Integer status;
}
