package com.gencode.system.role.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.role.dto.RoleBody;
import com.gencode.system.role.dto.RoleQuery;
import com.gencode.system.role.dto.RoleVO;
import com.gencode.system.role.entity.SysRole;
import com.gencode.system.role.entity.SysRoleMenu;
import com.gencode.system.role.mapper.SysRoleMapper;
import com.gencode.system.role.mapper.SysRoleMenuMapper;
import com.gencode.system.role.service.RoleService;
import com.gencode.system.user.entity.SysUserRole;
import com.gencode.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<SysRole> page(RoleQuery query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysRole::getName, query.getKeyword())
                    .or().like(SysRole::getRoleKey, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysRole::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId);
        Page<SysRole> page = roleMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<SysRole> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId));
    }

    @Override
    public RoleVO detail(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        RoleVO vo = BeanUtil.toBean(role, RoleVO.class);
        vo.setMenuIds(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, id))
                .stream().map(SysRoleMenu::getMenuId).toList());
        return vo;
    }

    @Override
    public void create(RoleBody body) {
        checkRoleKeyUnique(body.getRoleKey(), null);
        SysRole role = new SysRole();
        BeanUtil.copyProperties(body, role);
        if (role.getStatus() == null) {
            role.setStatus(Constants.STATUS_NORMAL);
        }
        if (role.getDataScope() == null) {
            role.setDataScope(1);
        }
        roleMapper.insert(role);
    }

    @Override
    public void update(RoleBody body) {
        if (body.getId() == null) {
            throw new BizException("角色ID不能为空");
        }
        SysRole old = roleMapper.selectById(body.getId());
        if (old == null) {
            throw new BizException("角色不存在");
        }
        // 内置角色 id=1/2 不允许修改 roleKey
        boolean builtin = body.getId() == 1L || body.getId() == 2L;
        if (builtin && !StrUtil.equals(old.getRoleKey(), body.getRoleKey())) {
            throw new BizException("内置角色不允许修改角色权限字符");
        }
        checkRoleKeyUnique(body.getRoleKey(), body.getId());
        SysRole role = new SysRole();
        BeanUtil.copyProperties(body, role);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == 1L || id == 2L) {
            throw new BizException("内置角色不允许删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long id, List<Long> menuIds) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException("角色不存在");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(id);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setStatus(status);
        roleMapper.updateById(role);
    }

    // ------------------------------------------------------------------ 私有方法

    private void checkRoleKeyUnique(String roleKey, Long excludeId) {
        if (StrUtil.isBlank(roleKey)) {
            return;
        }
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeId != null, SysRole::getId, excludeId);
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BizException("角色权限字符已存在");
        }
    }
}
