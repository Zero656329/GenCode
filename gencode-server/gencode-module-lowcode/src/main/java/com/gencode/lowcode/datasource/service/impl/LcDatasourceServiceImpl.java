package com.gencode.lowcode.datasource.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.datasource.dto.DatasourceCreateBody;
import com.gencode.lowcode.datasource.dto.DatasourceOptionVO;
import com.gencode.lowcode.datasource.dto.DatasourceQuery;
import com.gencode.lowcode.datasource.dto.DatasourceTestResult;
import com.gencode.lowcode.datasource.dto.DatasourceUpdateBody;
import com.gencode.lowcode.datasource.entity.LcDatasource;
import com.gencode.lowcode.datasource.mapper.LcDatasourceMapper;
import com.gencode.lowcode.datasource.service.LcDatasourceService;
import com.gencode.lowcode.datasource.util.AesUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部数据源服务实现：
 * 密码 AES 加密存库、使用时解密、任何出参一律置 null；
 * 外部库连接缓存 ConcurrentHashMap&lt;Long, DataSource&gt;（HikariDataSource maximumPoolSize=4），修改/删除时 evict
 */
@Service
@RequiredArgsConstructor
public class LcDatasourceServiceImpl implements LcDatasourceService {

    /** 连接测试登录超时（秒） */
    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    /** 外部数据源连接池上限 */
    private static final int MAX_POOL_SIZE = 4;

    private final LcDatasourceMapper datasourceMapper;
    private final AesUtil aesUtil;
    /** 平台主库（datasourceId 为空时使用） */
    private final DataSource platformDataSource;

    /** 外部数据源连接缓存：id -> DataSource */
    private final ConcurrentHashMap<Long, DataSource> connectionCache = new ConcurrentHashMap<>();

    @Override
    public PageResult<LcDatasource> page(DatasourceQuery query) {
        LambdaQueryWrapper<LcDatasource> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(LcDatasource::getName, query.getKeyword());
        }
        wrapper.orderByDesc(LcDatasource::getCreateTime);
        IPage<LcDatasource> page = datasourceMapper.selectPage(query.toPage(), wrapper);
        // 密码任何出参一律置 null
        page.getRecords().forEach(ds -> ds.setPassword(null));
        return PageResult.of(page);
    }

    @Override
    public List<DatasourceOptionVO> listAll() {
        return datasourceMapper.selectList(new LambdaQueryWrapper<LcDatasource>()
                        .orderByDesc(LcDatasource::getCreateTime)).stream()
                .map(ds -> {
                    DatasourceOptionVO vo = new DatasourceOptionVO();
                    vo.setId(ds.getId());
                    vo.setName(ds.getName());
                    return vo;
                })
                .toList();
    }

    @Override
    public void create(DatasourceCreateBody body) {
        LcDatasource ds = new LcDatasource();
        ds.setName(body.getName());
        ds.setDriver(body.getDriver());
        ds.setJdbcUrl(body.getJdbcUrl());
        ds.setUsername(body.getUsername());
        ds.setPassword(aesUtil.encrypt(body.getPassword()));
        ds.setRemark(body.getRemark());
        datasourceMapper.insert(ds);
    }

    @Override
    public void update(DatasourceUpdateBody body) {
        if (datasourceMapper.selectById(body.getId()) == null) {
            throw new BizException("数据源不存在");
        }
        LcDatasource ds = new LcDatasource();
        ds.setId(body.getId());
        ds.setName(body.getName());
        ds.setDriver(body.getDriver());
        ds.setJdbcUrl(body.getJdbcUrl());
        ds.setUsername(body.getUsername());
        // password 空 = 不修改（updateById 跳过 null 字段）
        if (StrUtil.isNotBlank(body.getPassword())) {
            ds.setPassword(aesUtil.encrypt(body.getPassword()));
        }
        ds.setRemark(body.getRemark());
        datasourceMapper.updateById(ds);
        evictConnection(body.getId());
    }

    @Override
    public void delete(Long id) {
        if (datasourceMapper.selectById(id) == null) {
            throw new BizException("数据源不存在");
        }
        datasourceMapper.deleteById(id);
        evictConnection(id);
    }

    @Override
    public DatasourceTestResult test(Long id) {
        LcDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据源不存在");
        }
        String password = aesUtil.decrypt(ds.getPassword());
        try {
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            if (StrUtil.isNotBlank(ds.getDriver())) {
                Class.forName(ds.getDriver());
            }
            try (Connection ignored = DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), password)) {
                return DatasourceTestResult.ok("连接成功");
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            String summary = e.getClass().getSimpleName() + (StrUtil.isBlank(msg) ? "" : ": " + StrUtil.sub(msg, 0, 200));
            return DatasourceTestResult.fail("连接失败：" + summary);
        }
    }

    @Override
    public JdbcTemplate getJdbcTemplate(Long datasourceId) {
        if (datasourceId == null) {
            return new JdbcTemplate(platformDataSource);
        }
        DataSource ds = connectionCache.computeIfAbsent(datasourceId, this::buildHikariDataSource);
        return new JdbcTemplate(ds);
    }

    /** 构建外部数据源连接池（maximumPoolSize=4，连接超时 5 秒） */
    private DataSource buildHikariDataSource(Long id) {
        LcDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据源不存在");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ds.getJdbcUrl());
        config.setUsername(ds.getUsername());
        config.setPassword(aesUtil.decrypt(ds.getPassword()));
        if (StrUtil.isNotBlank(ds.getDriver())) {
            config.setDriverClassName(ds.getDriver());
        }
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(LOGIN_TIMEOUT_SECONDS * 1000L);
        config.setPoolName("lc-ds-" + id);
        return new HikariDataSource(config);
    }

    /** 失效并关闭数据源连接缓存 */
    private void evictConnection(Long id) {
        DataSource old = connectionCache.remove(id);
        if (old instanceof HikariDataSource hikari && !hikari.isClosed()) {
            hikari.close();
        }
    }
}
