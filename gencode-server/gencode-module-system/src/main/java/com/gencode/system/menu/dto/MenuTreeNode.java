package com.gencode.system.menu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点
 */
@Data
public class MenuTreeNode {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String menuType;
    private String perms;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private LocalDateTime createTime;
    private List<MenuTreeNode> children = new ArrayList<>();
}
