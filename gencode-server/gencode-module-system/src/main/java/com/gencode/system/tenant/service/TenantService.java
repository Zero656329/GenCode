package com.gencode.system.tenant.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.tenant.dto.TenantBody;
import com.gencode.system.tenant.dto.TenantQuery;
import com.gencode.system.tenant.entity.SysTenant;

/**
 * 租户服务
 */
public interface TenantService {

    PageResult<SysTenant> page(TenantQuery query);

    void create(TenantBody body);

    void update(TenantBody body);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}
