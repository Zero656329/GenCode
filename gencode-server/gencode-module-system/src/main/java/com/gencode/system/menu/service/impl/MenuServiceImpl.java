package com.gencode.system.menu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gencode.common.exception.BizException;
import com.gencode.system.menu.dto.MenuBody;
import com.gencode.system.menu.dto.MenuTreeNode;
import com.gencode.system.menu.entity.SysMenu;
import com.gencode.system.menu.mapper.SysMenuMapper;
import com.gencode.system.menu.service.MenuService;
import com.gencode.system.role.entity.SysRoleMenu;
import com.gencode.system.role.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单服务实现
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<MenuTreeNode> tree(String name, Integer status) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId);
        List<SysMenu> all = menuMapper.selectList(wrapper);
        // 过滤后再组树，父节点不在集合中的节点提升为根节点
        List<SysMenu> filtered = all.stream()
                .filter(m -> StrUtil.isBlank(name) || StrUtil.contains(m.getName(), name))
                .filter(m -> status == null || status.equals(m.getStatus()))
                .toList();
        Map<Long, MenuTreeNode> nodeMap = new HashMap<>();
        for (SysMenu menu : filtered) {
            nodeMap.put(menu.getId(), BeanUtil.toBean(menu, MenuTreeNode.class));
        }
        List<MenuTreeNode> roots = new ArrayList<>();
        for (MenuTreeNode node : nodeMap.values()) {
            MenuTreeNode parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent != null && parent != node) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public SysMenu detail(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException("菜单不存在");
        }
        return menu;
    }

    @Override
    public void create(MenuBody body) {
        checkParentExists(body.getParentId());
        SysMenu menu = new SysMenu();
        BeanUtil.copyProperties(body, menu);
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSort() == null) {
            menu.setSort(0);
        }
        menuMapper.insert(menu);
    }

    @Override
    public void update(MenuBody body) {
        if (body.getId() == null) {
            throw new BizException("菜单ID不能为空");
        }
        if (menuMapper.selectById(body.getId()) == null) {
            throw new BizException("菜单不存在");
        }
        // 父级不能是自身或自身的子孙
        checkParentValid(body.getId(), body.getParentId());
        SysMenu menu = new SysMenu();
        BeanUtil.copyProperties(body, menu);
        menuMapper.updateById(menu);
    }

    @Override
    public void delete(Long id) {
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BizException("存在子菜单，不允许删除");
        }
        Long refCount = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getMenuId, id));
        if (refCount > 0) {
            throw new BizException("菜单已分配角色，不允许删除");
        }
        menuMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 私有方法

    private void checkParentExists(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BizException("父菜单不存在");
        }
    }

    /** 父级不能是自身或自身的子孙：从新父级沿父链上溯，若命中自身则非法 */
    private void checkParentValid(Long id, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BizException("父菜单不存在");
        }
        Long cur = parentId;
        int guard = 0;
        while (cur != null && cur != 0L && guard++ < 100) {
            if (cur.equals(id)) {
                throw new BizException("父菜单不能选择自身或其子菜单");
            }
            SysMenu menu = menuMapper.selectById(cur);
            if (menu == null) {
                break;
            }
            cur = menu.getParentId();
        }
    }
}
