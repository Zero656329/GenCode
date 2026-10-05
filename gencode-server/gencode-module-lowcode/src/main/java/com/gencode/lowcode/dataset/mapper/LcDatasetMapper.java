package com.gencode.lowcode.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.dataset.entity.LcDataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据集定义 Mapper
 */
@Mapper
public interface LcDatasetMapper extends BaseMapper<LcDataset> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_dataset_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"数据集编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_dataset WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
