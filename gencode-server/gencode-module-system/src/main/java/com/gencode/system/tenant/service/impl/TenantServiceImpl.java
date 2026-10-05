package com.gencode.system.tenant.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.system.tenant.dto.TenantBody;
import com.gencode.system.tenant.dto.TenantQuery;
import com.gencode.system.tenant.entity.SysTenant;
import com.gencode.system.tenant.mapper.SysTenantMapper;
import com.gencode.system.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final SysTenantMapper tenantMapper;

    @Override
    public PageResult<SysTenant> page(TenantQuery query) {
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(SysTenant::getName, query.getKeyword())
                    .or().like(SysTenant::getTenantId, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysTenant::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysTenant::getId);
        Page<SysTenant> page = tenantMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public void create(TenantBody body) {
        Long count = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantId, body.getTenantId()));
        if (count > 0) {
            throw new BizException("租户编号已存在");
        }
        SysTenant tenant = new SysTenant();
        BeanUtil.copyProperties(body, tenant);
        if (tenant.getStatus() == null) {
            tenant.setStatus(Constants.STATUS_NORMAL);
        }
        tenantMapper.insert(tenant);
    }

    @Override
    public void update(TenantBody body) {
        if (body.getId() == null) {
            throw new BizException("租户ID不能为空");
        }
        if (tenantMapper.selectById(body.getId()) == null) {
            throw new BizException("租户不存在");
        }
        Long count = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantId, body.getTenantId())
                .ne(SysTenant::getId, body.getId()));
        if (count > 0) {
            throw new BizException("租户编号已存在");
        }
        SysTenant tenant = new SysTenant();
        BeanUtil.copyProperties(body, tenant);
        tenantMapper.updateById(tenant);
    }

    @Override
    public void delete(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("租户不存在");
        }
        if (Constants.DEFAULT_TENANT.equals(tenant.getTenantId())) {
            throw new BizException("内置租户不允许删除");
        }
        tenantMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysTenant tenant = new SysTenant();
        tenant.setId(id);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }
}
