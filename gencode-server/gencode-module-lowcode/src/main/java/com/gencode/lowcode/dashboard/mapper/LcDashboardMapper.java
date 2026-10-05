package com.gencode.lowcode.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.dashboard.entity.LcDashboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 大屏定义 Mapper
 */
@Mapper
public interface LcDashboardMapper extends BaseMapper<LcDashboard> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_dashboard_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"大屏编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_dashboard WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
