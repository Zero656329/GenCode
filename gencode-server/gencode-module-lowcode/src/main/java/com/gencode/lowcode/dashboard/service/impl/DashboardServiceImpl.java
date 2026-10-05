package com.gencode.lowcode.dashboard.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.dashboard.dto.DashboardCreateBody;
import com.gencode.lowcode.dashboard.dto.DashboardPublishedVO;
import com.gencode.lowcode.dashboard.dto.DashboardQuery;
import com.gencode.lowcode.dashboard.dto.DashboardSaveBody;
import com.gencode.lowcode.dashboard.entity.LcDashboard;
import com.gencode.lowcode.dashboard.mapper.LcDashboardMapper;
import com.gencode.lowcode.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 大屏定义服务实现：租户插件自动隔离，雪花 ID 由 MP ASSIGN_ID 生成（发布模式同表单）
 */
@Service("lcDashboardServiceImpl")
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 状态：停用 */
    private static final int STATUS_DISABLED = 2;

    private final LcDashboardMapper dashboardMapper;

    @Override
    public PageResult<LcDashboard> page(DashboardQuery query) {
        LambdaQueryWrapper<LcDashboard> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcDashboard::getCode, query.getKeyword())
                    .or().like(LcDashboard::getName, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(LcDashboard::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LcDashboard::getCreateTime);
        IPage<LcDashboard> page = dashboardMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcDashboard detail(Long id) {
        LcDashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new BizException("大屏不存在");
        }
        return dashboard;
    }

    @Override
    public void create(DashboardCreateBody body) {
        checkCodeUnique(body.getCode());
        LcDashboard dashboard = new LcDashboard();
        dashboard.setCode(body.getCode());
        dashboard.setName(body.getName());
        dashboard.setLayoutJson(body.getLayoutJson());
        dashboard.setRemark(body.getRemark());
        dashboard.setStatus(STATUS_DRAFT);
        dashboard.setVersion(0);
        dashboardMapper.insert(dashboard);
    }

    @Override
    public void saveDesign(DashboardSaveBody body) {
        if (dashboardMapper.selectById(body.getId()) == null) {
            throw new BizException("大屏不存在");
        }
        LcDashboard dashboard = new LcDashboard();
        BeanUtil.copyProperties(body, dashboard);
        // DashboardSaveBody 无 code 字段，code 天然不会被修改
        dashboardMapper.updateById(dashboard);
    }

    @Override
    public void delete(Long id) {
        if (dashboardMapper.selectById(id) == null) {
            throw new BizException("大屏不存在");
        }
        dashboardMapper.deleteById(id);
    }

    @Override
    public void publish(Long id) {
        LcDashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new BizException("大屏不存在");
        }
        if (StrUtil.isBlank(dashboard.getLayoutJson())) {
            throw new BizException("请先设计大屏布局");
        }
        dashboard.setVersion(dashboard.getVersion() == null ? 1 : dashboard.getVersion() + 1);
        dashboard.setPublishedSchema(dashboard.getLayoutJson());
        dashboard.setStatus(STATUS_PUBLISHED);
        dashboard.setPublishTime(LocalDateTime.now());
        dashboardMapper.updateById(dashboard);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || status < STATUS_DRAFT || status > STATUS_DISABLED) {
            throw new BizException("非法的状态值");
        }
        dashboardMapper.update(null, new LambdaUpdateWrapper<LcDashboard>()
                .eq(LcDashboard::getId, id)
                .set(LcDashboard::getStatus, status));
    }

    @Override
    public DashboardPublishedVO getPublished(String code) {
        LcDashboard dashboard = dashboardMapper.selectOne(new LambdaQueryWrapper<LcDashboard>()
                .eq(LcDashboard::getCode, code)
                .eq(LcDashboard::getStatus, STATUS_PUBLISHED));
        if (dashboard == null) {
            throw new BizException("大屏不存在或未发布");
        }
        DashboardPublishedVO vo = new DashboardPublishedVO();
        vo.setCode(dashboard.getCode());
        vo.setName(dashboard.getName());
        vo.setVersion(dashboard.getVersion());
        vo.setLayoutJson(dashboard.getPublishedSchema());
        return vo;
    }

    /**
     * 编码查重：含已逻辑删除的行（数据库唯一键不区分 deleted，已删除记录仍占用编码）
     */
    private void checkCodeUnique(String code) {
        if (dashboardMapper.countByCodeIncludeDeleted(code) > 0) {
            throw new BizException("大屏编码已存在");
        }
    }
}
