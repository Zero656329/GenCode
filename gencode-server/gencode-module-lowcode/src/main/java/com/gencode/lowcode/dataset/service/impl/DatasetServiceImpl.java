package com.gencode.lowcode.dataset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.lowcode.dataset.dto.DatasetCreateBody;
import com.gencode.lowcode.dataset.dto.DatasetPublishedVO;
import com.gencode.lowcode.dataset.dto.DatasetQuery;
import com.gencode.lowcode.dataset.dto.DatasetSaveBody;
import com.gencode.lowcode.dataset.entity.LcDataset;
import com.gencode.lowcode.dataset.mapper.LcDatasetMapper;
import com.gencode.lowcode.dataset.service.DatasetService;
import com.gencode.lowcode.datasource.service.LcDatasourceService;
import com.gencode.lowcode.list.engine.DialectPageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据集定义服务实现：租户插件自动隔离，雪花 ID 由 MP ASSIGN_ID 生成。
 *
 * SQL 执行与 com.gencode.lowcode.list.engine.DynamicQueryService 同一套安全层
 * （sanitizeSql：注释剥离 / select 开头 / 禁分号 / 黑名单关键字；#{name}→? 参数绑定），
 * 数据集无发布概念，preview 与取数均按当前定义直接执行，行数上限 100。
 */
@Service
@RequiredArgsConstructor
public class DatasetServiceImpl implements DatasetService {

    /** #{name} 占位符（与 DynamicQueryService 保持一致） */
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{\\s*([A-Za-z0-9_]+)\\s*}");
    /** SQL 黑名单关键字（独立单词，命中即拒绝；照抄 DynamicQueryService） */
    private static final Pattern SQL_BLACKLIST = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|exec|merge)\\b",
            Pattern.CASE_INSENSITIVE);
    /** 预览/取数行数上限 */
    private static final int MAX_ROWS = 100;

    private final LcDatasetMapper datasetMapper;
    private final LcDatasourceService datasourceService;

    /** 数据库方言（方言分页包装用，与 DynamicQueryService 同源配置） */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    @Override
    public PageResult<LcDataset> page(DatasetQuery query) {
        LambdaQueryWrapper<LcDataset> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(LcDataset::getCode, query.getKeyword())
                    .or().like(LcDataset::getName, query.getKeyword()));
        }
        wrapper.orderByDesc(LcDataset::getCreateTime);
        IPage<LcDataset> page = datasetMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public LcDataset detail(Long id) {
        LcDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException("数据集不存在");
        }
        return dataset;
    }

    @Override
    public void create(DatasetCreateBody body) {
        checkCodeUnique(body.getCode());
        sanitizeSql(body.getSqlText());
        LcDataset dataset = new LcDataset();
        dataset.setCode(body.getCode());
        dataset.setName(body.getName());
        dataset.setSqlText(body.getSqlText());
        dataset.setParamsJson(body.getParamsJson());
        dataset.setRemark(body.getRemark());
        datasetMapper.insert(dataset);
    }

    @Override
    public void saveDesign(DatasetSaveBody body) {
        if (datasetMapper.selectById(body.getId()) == null) {
            throw new BizException("数据集不存在");
        }
        sanitizeSql(body.getSqlText());
        LcDataset dataset = new LcDataset();
        BeanUtil.copyProperties(body, dataset);
        // DatasetSaveBody 无 code 字段，code 天然不会被修改
        datasetMapper.updateById(dataset);
    }

    @Override
    public void delete(Long id) {
        if (datasetMapper.selectById(id) == null) {
            throw new BizException("数据集不存在");
        }
        datasetMapper.deleteById(id);
    }

    @Override
    public DatasetPublishedVO preview(Long id, Map<String, Object> params) {
        return execute(detail(id), params);
    }

    @Override
    public DatasetPublishedVO publishedData(String code, Map<String, Object> params) {
        LcDataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<LcDataset>()
                .eq(LcDataset::getCode, code));
        if (dataset == null) {
            throw new BizException("数据集不存在");
        }
        return execute(dataset, params);
    }

    // ==================== SQL 执行（安全层 + 参数绑定 + 限行） ====================

    /** 统一执行入口：安全层校验 → #{name} 转 ? 并按出现顺序绑定参数值 → 方言分页限 100 行 */
    private DatasetPublishedVO execute(LcDataset dataset, Map<String, Object> params) {
        String safeSql = sanitizeSql(dataset.getSqlText());
        Map<String, Object> p = params == null ? new java.util.HashMap<>() : new java.util.HashMap<>(params);
        applyParamDefaults(dataset.getParamsJson(), p);

        // #{name} 占位符逐个转成 ?，按 SQL 出现顺序收集参数名
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(safeSql);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            names.add(matcher.group(1));
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);
        String prepared = sb.toString();
        if (prepared.contains("#{")) {
            throw new BizException("存在非法占位符，仅支持 #{field} 形式");
        }

        // 数据集无搜索追加逻辑：缺参绑 null（与 DynamicQueryService.bindValue 缺省一致，无 like 包裹）
        List<Object> args = new ArrayList<>();
        for (String name : names) {
            args.add(p.get(name));
        }
        // 行数上限 100：复用方言分页工具从第 0 行取 MAX_ROWS 行，分页参数置后追加
        List<Object> pageArgs = new ArrayList<>(args);
        String pageSql = DialectPageUtil.wrap(prepared, dbType, 0, MAX_ROWS, pageArgs);
        return queryRows(pageSql, pageArgs);
    }

    /** 应用参数默认值：paramsJson 定义了 defaultValue 且请求未传/为空的参数补默认值（大屏空参取数场景） */
    private void applyParamDefaults(String paramsJson, Map<String, Object> p) {
        if (StrUtil.isBlank(paramsJson)) {
            return;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(paramsJson);
            for (Object o : arr) {
                JSONObject def = (JSONObject) o;
                String name = def.getStr("name");
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                Object current = p.get(name);
                boolean missing = current == null || (current instanceof String s && StrUtil.isBlank(s));
                if (missing && def.containsKey("defaultValue") && def.get("defaultValue") != null) {
                    p.put(name, def.get("defaultValue"));
                }
            }
        } catch (Exception e) {
            // paramsJson 非法时忽略默认值，按原参数执行（合法性由保存接口校验）
        }
    }

    /** 执行查询：列名取自结果集元数据（空结果也返回 columns），行对象按字段名组装 */
    private DatasetPublishedVO queryRows(String pageSql, List<Object> args) {
        JdbcTemplate jdbc = datasourceService.getJdbcTemplate(null);
        DatasetPublishedVO vo = new DatasetPublishedVO();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query(pageSql, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            int count = md.getColumnCount();
            for (int i = 1; i <= count; i++) {
                columns.add(md.getColumnLabel(i));
            }
            while (rs.next() && rows.size() < MAX_ROWS) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= count; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return null;
        }, args.toArray());
        vo.setColumns(columns);
        vo.setRows(rows);
        return vo;
    }

    // ==================== 安全层（照抄 DynamicQueryService.sanitizeSql） ====================

    /**
     * SQL 型安全层（先于一切执行）：去除 /*星 与 -- 注释后必须以 select 开头（忽略大小写）、
     * 不含分号、黑名单关键字（独立单词）命中即拒绝
     */
    private String sanitizeSql(String sql) {
        if (StrUtil.isBlank(sql)) {
            throw new BizException("SQL 不能为空");
        }
        String noComments = sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*?$", " ");
        String trimmed = noComments.trim();
        if (!trimmed.toLowerCase().startsWith("select")) {
            throw new BizException("仅允许单条 SELECT 查询");
        }
        if (trimmed.contains(";")) {
            throw new BizException("SQL 不允许包含分号");
        }
        Matcher blacklist = SQL_BLACKLIST.matcher(trimmed);
        if (blacklist.find()) {
            throw new BizException("SQL 包含禁止的关键字：" + blacklist.group().toLowerCase());
        }
        return trimmed;
    }

    /**
     * 编码查重：含已逻辑删除的行（数据库唯一键不区分 deleted，已删除记录仍占用编码）
     */
    private void checkCodeUnique(String code) {
        if (datasetMapper.countByCodeIncludeDeleted(code) > 0) {
            throw new BizException("数据集编码已存在");
        }
    }
}
