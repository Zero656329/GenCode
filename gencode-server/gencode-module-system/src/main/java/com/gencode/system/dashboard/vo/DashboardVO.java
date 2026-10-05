package com.gencode.system.dashboard.vo;

import com.gencode.system.log.entity.SysLoginLog;
import lombok.Data;

import java.util.List;

/**
 * 仪表盘统计
 */
@Data
public class DashboardVO {

    private Integer userCount;
    private Integer roleCount;
    private Integer deptCount;
    private Integer tenantCount;
    private Integer menuCount;
    /** 最近 7 天登录趋势（按日聚合，含空日，date 升序） */
    private List<TrendItem> loginTrend;
    /** 最近 7 天登录数量数组 */
    private List<Integer> loginTrend7d;
    /** 最近 10 条登录日志 */
    private List<SysLoginLog> latestLogins;

    @Data
    public static class TrendItem {

        /** yyyy-MM-dd */
        private String date;
        private Integer count;
    }
}
