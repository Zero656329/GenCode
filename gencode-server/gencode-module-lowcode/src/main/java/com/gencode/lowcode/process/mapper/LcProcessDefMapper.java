package com.gencode.lowcode.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.process.entity.LcProcessDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流程定义 Mapper
 */
@Mapper
public interface LcProcessDefMapper extends BaseMapper<LcProcessDef> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_process_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"流程编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_process_def WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
