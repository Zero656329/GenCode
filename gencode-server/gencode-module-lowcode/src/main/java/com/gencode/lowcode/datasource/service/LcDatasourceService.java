package com.gencode.lowcode.datasource.service;

import com.gencode.common.result.PageResult;
import com.gencode.lowcode.datasource.dto.DatasourceCreateBody;
import com.gencode.lowcode.datasource.dto.DatasourceOptionVO;
import com.gencode.lowcode.datasource.dto.DatasourceQuery;
import com.gencode.lowcode.datasource.dto.DatasourceTestResult;
import com.gencode.lowcode.datasource.dto.DatasourceUpdateBody;
import com.gencode.lowcode.datasource.entity.LcDatasource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 外部数据源服务
 */
public interface LcDatasourceService {

    /** 分页查询（keyword 匹配 name；password 一律置 null 不回显） */
    PageResult<LcDatasource> page(DatasourceQuery query);

    /** 下拉选项（仅 id/name） */
    List<DatasourceOptionVO> listAll();

    /** 新建（password AES 加密落库） */
    void create(DatasourceCreateBody body);

    /** 修改（password 空 = 不修改；修改后失效连接缓存） */
    void update(DatasourceUpdateBody body);

    /** 逻辑删除（删除后失效连接缓存） */
    void delete(Long id);

    /** 连接测试（超时 5 秒；失败返回 ok=false + 异常摘要，不抛 500） */
    DatasourceTestResult test(Long id);

    /**
     * 按数据源 ID 取 JdbcTemplate（外部库连接走缓存，HikariDataSource maximumPoolSize=4）；
     * datasourceId 为空时返回平台主库 JdbcTemplate
     */
    JdbcTemplate getJdbcTemplate(Long datasourceId);
}
