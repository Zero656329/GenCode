package com.gencode.system.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.dept.entity.SysDept;
import com.gencode.system.dept.mapper.SysDeptMapper;
import com.gencode.system.user.dto.UserBody;
import com.gencode.system.user.dto.UserQuery;
import com.gencode.system.user.dto.UserVO;
import com.gencode.system.user.entity.SysUser;
import com.gencode.system.user.entity.SysUserRole;
import com.gencode.system.user.mapper.SysUserMapper;
import com.gencode.system.user.mapper.SysUserRoleMapper;
import com.gencode.system.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysUser::getUsername, query.getKeyword())
                    .or().like(SysUser::getNickname, query.getKeyword())
                    .or().like(SysUser::getPhone, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.in(SysUser::getDeptId, deptAndChildren(query.getDeptId()));
        }
        wrapper.orderByAsc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(query.toPage(), wrapper);
        // 组装部门名
        List<Long> deptIds = page.getRecords().stream()
                .map(SysUser::getDeptId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = deptIds.isEmpty() ? Collections.emptyMap()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
        List<UserVO> vos = page.getRecords().stream()
                .map(u -> toVO(u, deptNames))
                .toList();
        return PageResult.of(vos, page.getTotal());
    }

    @Override
    public UserVO detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserVO vo = toVO(user, Collections.emptyMap());
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            vo.setDeptName(dept != null ? dept.getName() : null);
        }
        vo.setRoleIds(userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserBody body) {
        if (StrUtil.isBlank(body.getPassword())) {
            throw new BizException("密码不能为空");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, body.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        BeanUtil.copyProperties(body, user);
        user.setPassword(BCrypt.hashpw(body.getPassword(), BCrypt.gensalt()));
        if (user.getStatus() == null) {
            user.setStatus(Constants.STATUS_NORMAL);
        }
        if (user.getUserType() == null) {
            user.setUserType(1);
        }
        userMapper.insert(user);
        saveRoles(user.getId(), body.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserBody body) {
        if (body.getId() == null) {
            throw new BizException("用户ID不能为空");
        }
        if (userMapper.selectById(body.getId()) == null) {
            throw new BizException("用户不存在");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, body.getUsername())
                .ne(SysUser::getId, body.getId()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        BeanUtil.copyProperties(body, user);
        // 密码为空表示不修改
        user.setPassword(StrUtil.isBlank(body.getPassword())
                ? null : BCrypt.hashpw(body.getPassword(), BCrypt.gensalt()));
        userMapper.updateById(user);
        if (body.getRoleIds() != null) {
            saveRoles(body.getId(), body.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == Constants.ADMIN_USER_ID) {
            throw new BizException("内置管理员不允许删除");
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == Constants.ADMIN_USER_ID && Constants.STATUS_DISABLE.equals(status)) {
            throw new BizException("内置管理员不允许停用");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String password) {
        if (StrUtil.isBlank(password)) {
            throw new BizException("密码不能为空");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------ 私有方法

    private UserVO toVO(SysUser user, Map<Long, String> deptNames) {
        UserVO vo = BeanUtil.toBean(user, UserVO.class);
        if (user.getDeptId() != null) {
            vo.setDeptName(deptNames.get(user.getDeptId()));
        }
        return vo;
    }

    private void saveRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /** 部门及其子部门 ID 集合（依据 ancestors 祖级链） */
    private List<Long> deptAndChildren(Long deptId) {
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        List<SysDept> all = deptMapper.selectList(null);
        for (SysDept dept : all) {
            if (StrUtil.isNotBlank(dept.getAncestors())
                    && Arrays.asList(dept.getAncestors().split(",")).contains(String.valueOf(deptId))) {
                ids.add(dept.getId());
            }
        }
        return ids;
    }
}
