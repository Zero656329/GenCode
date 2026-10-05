package com.gencode.system.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gencode.system.dashboard.service.DashboardService;
import com.gencode.system.dashboard.vo.DashboardVO;
import com.gencode.system.dept.mapper.SysDeptMapper;
import com.gencode.system.log.entity.SysLoginLog;
import com.gencode.system.log.mapper.SysLoginLogMapper;
import com.gencode.system.menu.mapper.SysMenuMapper;
import com.gencode.system.role.mapper.SysRoleMapper;
import com.gencode.system.tenant.mapper.SysTenantMapper;
import com.gencode.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘服务实现
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysTenantMapper tenantMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;

    @Override
    public DashboardVO stats() {
        DashboardVO vo = new DashboardVO();
        vo.setUserCount(intValue(userMapper.selectCount(null)));
        vo.setRoleCount(intValue(roleMapper.selectCount(null)));
        vo.setDeptCount(intValue(deptMapper.selectCount(null)));
        vo.setTenantCount(intValue(tenantMapper.selectCount(null)));
        vo.setMenuCount(intValue(menuMapper.selectCount(null)));

        // 最近 7 天登录趋势（含无数据日期，date 升序）
        LocalDate today = LocalDate.now();
        List<DashboardVO.TrendItem> trend = new ArrayList<>();
        List<Integer> trend7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Long count = loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                    .ge(SysLoginLog::getLoginTime, day.atStartOfDay())
                    .lt(SysLoginLog::getLoginTime, day.plusDays(1).atStartOfDay()));
            DashboardVO.TrendItem item = new DashboardVO.TrendItem();
            item.setDate(day.toString());
            item.setCount(intValue(count));
            trend.add(item);
            trend7d.add(item.getCount());
        }
        vo.setLoginTrend(trend);
        vo.setLoginTrend7d(trend7d);

        // 最近 10 条登录日志
        vo.setLatestLogins(loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>()
                .orderByDesc(SysLoginLog::getLoginTime)
                .orderByDesc(SysLoginLog::getId)
                .last("LIMIT 10")));
        return vo;
    }

    private Integer intValue(Long count) {
        return count == null ? 0 : count.intValue();
    }
}
