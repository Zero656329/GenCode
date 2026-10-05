package com.gencode.lowcode.http.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gencode.lowcode.http.entity.LcHttpApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 第三方 HTTP 接口 Mapper
 */
@Mapper
public interface LcHttpApiMapper extends BaseMapper<LcHttpApi> {

    /**
     * 按 code 统计（含已逻辑删除的行：数据库唯一键 uk_lc_http_code 不区分 deleted，
     * 已删除记录仍占用编码，需据此提示"接口编码已存在"）
     */
    @Select("SELECT COUNT(*) FROM lc_http_api WHERE code = #{code}")
    long countByCodeIncludeDeleted(@Param("code") String code);
}
