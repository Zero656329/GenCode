package com.gencode.system.menu.service;

import com.gencode.system.menu.dto.MenuBody;
import com.gencode.system.menu.dto.MenuTreeNode;
import com.gencode.system.menu.entity.SysMenu;

import java.util.List;

/**
 * 菜单服务
 */
public interface MenuService {

    /** 完整菜单树（name 模糊、status 过滤） */
    List<MenuTreeNode> tree(String name, Integer status);

    SysMenu detail(Long id);

    void create(MenuBody body);

    void update(MenuBody body);

    void delete(Long id);
}
