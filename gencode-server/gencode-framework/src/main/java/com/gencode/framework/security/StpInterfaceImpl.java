package com.gencode.framework.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.gencode.common.constant.Constants;
import com.gencode.framework.security.mapper.AuthPermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源：角色 key / 菜单权限标识
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final AuthPermMapper authPermMapper;

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return authPermMapper.selectRoleKeysByUserId(Long.parseLong(loginId.toString()));
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        // 超级管理员返回通配权限
        if (roles != null && roles.contains(Constants.ROLE_SUPER_ADMIN)) {
            return List.of(Constants.PERM_ALL);
        }
        return authPermMapper.selectPermsByUserId(Long.parseLong(loginId.toString()))
                .stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }
}
