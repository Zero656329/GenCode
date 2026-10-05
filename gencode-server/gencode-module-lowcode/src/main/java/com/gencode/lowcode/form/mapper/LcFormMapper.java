package com.gencode.lowcode.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.form.entity.LcForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 表单定义 Mapper
 */
@Mapper
public interface LcFormMapper extends BaseMapper<LcForm> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_form_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"表单编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_form WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
