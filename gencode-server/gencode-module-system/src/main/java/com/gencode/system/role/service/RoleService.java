package com.gencode.system.role.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.role.dto.RoleBody;
import com.gencode.system.role.dto.RoleQuery;
import com.gencode.system.role.dto.RoleVO;
import com.gencode.system.role.entity.SysRole;

import java.util.List;

/**
 * 角色服务
 */
public interface RoleService {

    PageResult<SysRole> page(RoleQuery query);

    List<SysRole> listAll();

    RoleVO detail(Long id);

    void create(RoleBody body);

    void update(RoleBody body);

    void delete(Long id);

    void assignMenus(Long id, List<Long> menuIds);

    void updateStatus(Long id, Integer status);
}
