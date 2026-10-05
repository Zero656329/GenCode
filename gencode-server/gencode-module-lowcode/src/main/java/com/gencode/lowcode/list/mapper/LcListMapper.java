package com.gencode.lowcode.list.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.list.entity.LcList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 列表定义 Mapper
 */
@Mapper
public interface LcListMapper extends BaseMapper<LcList> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_list_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"列表编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_list WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
