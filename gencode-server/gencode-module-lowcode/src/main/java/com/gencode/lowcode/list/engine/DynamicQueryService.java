package com.gencode.lowcode.list.engine;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.lowcode.datasource.service.LcDatasourceService;
import com.gencode.lowcode.list.dto.ListDataDeleteBody;
import com.gencode.lowcode.list.dto.ListDataQuery;
import com.gencode.lowcode.list.dto.ListDataSaveBody;
import com.gencode.lowcode.list.entity.LcList;
import com.gencode.lowcode.list.mapper.LcListMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态 SQL 查询引擎：入参 code + 分页/排序/搜索参数，从已发布快照（published_schema）驱动查询与行保存。
 *
 * 安全层（先于一切执行）：
 * 1. 仅已发布列表可查，配置取自发布快照；
 * 2. SQL 型：去除 /*-star 与 -- 注释后必须以 select 开头（忽略大小写）、不含分号、
 *    黑名单关键字（独立单词）insert|update|delete|drop|alter|truncate|create|grant|exec|merge 命中即拒绝；
 *    #{name} 占位符逐个转成 ? 并按 search 配置绑定参数值；
 * 3. 标识符（表名/列名/orderBy）必须匹配 ^[A-Za-z_][A-Za-z0-9_]*$，orderBy 还必须出现在
 *    list_schema.columns 或 pkField 中；
 * 4. TABLE 型查询列取 columns 白名单；内部表（information_schema 确认含 tenant_id 列）自动追加租户条件；
 * 5. 外部库统一走数据源连接缓存（Hikari maximumPoolSize=4），平台库走主 DataSource。
 */
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryService.class);

    /** 标识符（表名/列名/排序字段）白名单正则 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    /** #{name} 占位符 */
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{\\s*([A-Za-z0-9_]+)\\s*}");
    /** SQL 黑名单关键字（独立单词，命中即拒绝） */
    private static final Pattern SQL_BLACKLIST = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|exec|merge)\\b",
            Pattern.CASE_INSENSITIVE);
    /** 搜索操作符白名单 */
    private static final Set<String> OPS = Set.of("eq", "like", "gt", "ge", "lt", "le", "between");

    private static final int STATUS_PUBLISHED = 1;
    /** 单页上限，防御 pageSize 过大拖垮外部库 */
    private static final int MAX_PAGE_SIZE = 500;

    private final LcListMapper listMapper;
    private final LcDatasourceService datasourceService;

    /** 数据库方言（动态列表分页包装用） */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    // ==================== 查询 ====================

    /** 列表数据分页查询（仅已发布列表） */
    public PageResult<Map<String, Object>> query(String code, ListDataQuery body) {
        ListDataQuery q = body == null ? new ListDataQuery() : body;
        ParsedDefinition def = parseDefinition(loadPublished(code));
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), MAX_PAGE_SIZE);
        long start = (long) (pageNum - 1) * pageSize;
        Map<String, Object> params = q.getParams() == null ? Map.of() : q.getParams();
        return switch (def.sourceType()) {
            case "TABLE" -> queryTable(def, q, params, start, pageSize);
            case "SQL" -> querySql(def, q, params, start, pageSize);
            default -> throw new BizException("API 数据源暂未支持，请使用 TABLE 或 SQL");
        };
    }

    /** TABLE 型查询：SELECT 列(白名单) FROM 表 WHERE [租户条件]+搜索条件 ORDER BY，方言分页 */
    private PageResult<Map<String, Object>> queryTable(ParsedDefinition def, ListDataQuery q,
                                                       Map<String, Object> params, long start, int pageSize) {
        String table = requireIdentifier(def.tableName(), "表名");
        List<String> cols = def.columns().stream().map(ColumnDef::field).toList();
        if (cols.isEmpty()) {
            throw new BizException("列表未配置列");
        }
        JdbcTemplate jdbc = getJdbc(def.datasourceId());

        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        appendTenantCondition(jdbc, table, conditions, args);
        appendSearchConditions(def.search(), params, conditions, args);
        String whereSql = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        String baseSql = "SELECT " + String.join(", ", cols) + " FROM " + table
                + whereSql + buildOrderClause(def, q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + whereSql, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        String pageSql = DialectPageUtil.wrap(baseSql, dbType, start, pageSize, pageArgs);
        List<Map<String, Object>> rows = jdbc.queryForList(pageSql, pageArgs.toArray());
        return PageResult.of(rows, total == null ? 0 : total);
    }

    /** SQL 型查询：安全层校验 + #{name} 转参数绑定 + AND 追加搜索条件 + 分页包装 */
    private PageResult<Map<String, Object>> querySql(ParsedDefinition def, ListDataQuery q,
                                                     Map<String, Object> params, long start, int pageSize) {
        String safeSql = sanitizeSql(def.sql());
        JdbcTemplate jdbc = getJdbc(def.datasourceId());

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

        List<Object> args = new ArrayList<>();
        for (String name : names) {
            args.add(bindValue(def, params, name));
        }
        // 搜索条件：SQL 中已有 #{field} 占位符的字段只经占位符绑值，其余统一以 AND 追加
        List<String> conditions = new ArrayList<>();
        for (SearchDef s : def.search()) {
            if (safeSql.contains("#{" + s.field() + "}")) {
                continue;
            }
            appendSearchCondition(s, params, conditions, args);
        }
        String finalSql = prepared;
        for (String cond : conditions) {
            finalSql += " AND (" + cond + ")";
        }

        Long total = jdbc.queryForObject(DialectPageUtil.countSql(finalSql), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        String pageSql = DialectPageUtil.wrap(finalSql, dbType, start, pageSize, pageArgs);
        List<Map<String, Object>> rows = jdbc.queryForList(pageSql, pageArgs.toArray());
        return PageResult.of(rows, total == null ? 0 : total);
    }

    // ==================== 行保存 / 行删除（仅 TABLE 型） ====================

    /** 行保存：mode=add|edit，列白名单 = columns(editable=true 的 field) ∪ pkField */
    public void save(String code, ListDataSaveBody body) {
        ParsedDefinition def = parseDefinition(loadPublished(code));
        if (!"TABLE".equals(def.sourceType())) {
            throw new BizException("仅 TABLE 型列表支持行保存");
        }
        String table = requireIdentifier(def.tableName(), "表名");
        String pk = requireIdentifier(def.pkField(), "主键字段");
        String mode = StrUtil.blankToDefault(body.getMode(), "").trim().toLowerCase();
        if (!"add".equals(mode) && !"edit".equals(mode)) {
            throw new BizException("非法的保存模式，仅支持 add/edit");
        }
        Map<String, Object> row = body.getRow() == null ? Map.of() : body.getRow();
        // 列白名单（小写 -> 原名）：editable 列 + 主键
        Map<String, String> whitelist = new LinkedHashMap<>();
        def.columns().stream()
                .filter(ColumnDef::editable)
                .map(ColumnDef::field)
                .forEach(f -> whitelist.put(f.toLowerCase(), f));
        whitelist.put(pk.toLowerCase(), pk);
        JdbcTemplate jdbc = getJdbc(def.datasourceId());

        if ("add".equals(mode)) {
            List<String> cols = new ArrayList<>();
            List<String> valueFragments = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            for (Map.Entry<String, Object> en : row.entrySet()) {
                String actual = whitelist.get(en.getKey() == null ? "" : en.getKey().toLowerCase());
                if (actual == null || en.getValue() == null) {
                    continue; // 白名单外/null 值跳过（null 交由数据库默认值）
                }
                cols.add(actual);
                valueFragments.add("?");
                args.add(en.getValue());
            }
            if (cols.isEmpty()) {
                throw new BizException("没有可保存的列");
            }
            // 主键缺省：目标表含主键列且用户未提供值时，自动生成雪花 ID
            if (cols.stream().noneMatch(c -> c.equalsIgnoreCase(pk)) && hasColumn(jdbc, table, pk)) {
                cols.add(pk);
                valueFragments.add("?");
                args.add(cn.hutool.core.util.IdUtil.getSnowflakeNextId());
            }
            // 目标表含 tenant_id 列时自动补当前租户
            if (cols.stream().noneMatch(c -> c.equalsIgnoreCase("tenant_id")) && hasColumn(jdbc, table, "tenant_id")) {
                cols.add("tenant_id");
                valueFragments.add("?");
                args.add(StrUtil.blankToDefault(TenantContext.getTenantId(), Constants.DEFAULT_TENANT));
            }
            // 内部表 INSERT 时若有 create_time 列填 NOW()
            if (cols.stream().noneMatch(c -> c.equalsIgnoreCase("create_time")) && hasColumn(jdbc, table, "create_time")) {
                cols.add("create_time");
                valueFragments.add("NOW()");
            }
            String sql = "INSERT INTO " + table + " (" + String.join(", ", cols)
                    + ") VALUES (" + String.join(", ", valueFragments) + ")";
            jdbc.update(sql, args.toArray());
            return;
        }

        // edit：主键值必填，主键列不参与 SET
        if (StrUtil.isBlank(body.getPk())) {
            throw new BizException("编辑模式必须提供主键值 pk");
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> en : row.entrySet()) {
            String actual = whitelist.get(en.getKey() == null ? "" : en.getKey().toLowerCase());
            if (actual == null || actual.equalsIgnoreCase(pk)) {
                continue;
            }
            sets.add(actual + " = ?");
            args.add(en.getValue());
        }
        if (sets.isEmpty()) {
            throw new BizException("没有可更新的列");
        }
        String sql = "UPDATE " + table + " SET " + String.join(", ", sets) + " WHERE " + pk + " = ?";
        args.add(body.getPk());
        jdbc.update(sql, args.toArray());
    }

    /** 行删除：按 pk 删（pk 白名单） */
    public void delete(String code, ListDataDeleteBody body) {
        ParsedDefinition def = parseDefinition(loadPublished(code));
        if (!"TABLE".equals(def.sourceType())) {
            throw new BizException("仅 TABLE 型列表支持行删除");
        }
        String table = requireIdentifier(def.tableName(), "表名");
        String pk = requireIdentifier(def.pkField(), "主键字段");
        getJdbc(def.datasourceId()).update("DELETE FROM " + table + " WHERE " + pk + " = ?", body.getPk());
    }

    // ==================== 安全层 ====================

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

    /** 标识符校验：必须匹配 ^[A-Za-z_][A-Za-z0-9_]*$ */
    private String requireIdentifier(String identifier, String what) {
        if (StrUtil.isBlank(identifier) || !IDENTIFIER.matcher(identifier.trim()).matches()) {
            throw new BizException("非法的" + what + "：" + identifier);
        }
        return identifier.trim();
    }

    /** ORDER BY：标识符校验 + 必须出现在 list_schema.columns 或 pkField 中 */
    private String buildOrderClause(ParsedDefinition def, ListDataQuery q) {
        if (StrUtil.isBlank(q.getOrderBy())) {
            return "";
        }
        String orderBy = q.getOrderBy().trim();
        if (!IDENTIFIER.matcher(orderBy).matches()) {
            throw new BizException("非法的排序字段");
        }
        boolean inColumns = def.columns().stream().anyMatch(c -> c.field().equalsIgnoreCase(orderBy));
        boolean isPk = orderBy.equalsIgnoreCase(def.pkField());
        if (!inColumns && !isPk) {
            throw new BizException("排序字段不在列表列中");
        }
        String dir = "desc".equalsIgnoreCase(q.getOrderDir()) ? "DESC" : "ASC";
        return " ORDER BY " + orderBy + " " + dir;
    }

    // ==================== 条件拼装 ====================

    /**
     * 自动租户条件：information_schema.columns 确认目标表含 tenant_id 列时追加
     * "tenant_id = ?"（值取 TenantContext，空则 '000000'），无该列则不加
     */
    private void appendTenantCondition(JdbcTemplate jdbc, String table, List<String> conditions, List<Object> args) {
        if (!hasColumn(jdbc, table, "tenant_id")) {
            return;
        }
        String tenantId = TenantContext.getTenantId();
        conditions.add("tenant_id = ?");
        args.add(StrUtil.isBlank(tenantId) ? Constants.DEFAULT_TENANT : tenantId);
    }

    /** 搜索条件：search 配置驱动，op 白名单 eq/like/gt/ge/lt/le/between，参数全绑定 */
    private void appendSearchConditions(List<SearchDef> search, Map<String, Object> params,
                                        List<String> conditions, List<Object> args) {
        for (SearchDef s : search) {
            appendSearchCondition(s, params, conditions, args);
        }
    }

    /** 追加单个搜索条件（空值跳过） */
    private void appendSearchCondition(SearchDef s, Map<String, Object> params,
                                       List<String> conditions, List<Object> args) {
        Object value = params.get(s.field());
        if (isEmptyValue(value)) {
            return;
        }
        String op = StrUtil.blankToDefault(s.op(), "eq").trim().toLowerCase();
        if (!OPS.contains(op)) {
            throw new BizException("不支持的查询操作符：" + s.op());
        }
        String field = requireIdentifier(s.field(), "搜索字段");
        switch (op) {
            case "like" -> {
                conditions.add(field + " LIKE ?");
                args.add("%" + value + "%");
            }
            case "gt" -> {
                conditions.add(field + " > ?");
                args.add(value);
            }
            case "ge" -> {
                conditions.add(field + " >= ?");
                args.add(value);
            }
            case "lt" -> {
                conditions.add(field + " < ?");
                args.add(value);
            }
            case "le" -> {
                conditions.add(field + " <= ?");
                args.add(value);
            }
            case "between" -> {
                List<Object> pair = betweenPair(value);
                conditions.add(field + " BETWEEN ? AND ?");
                args.add(pair.get(0));
                args.add(pair.get(1));
            }
            default -> {
                conditions.add(field + " = ?");
                args.add(value);
            }
        }
    }

    /** #{name} 占位符的绑定值：搜索配置 op=like 时包裹 %%，缺参绑 null */
    private Object bindValue(ParsedDefinition def, Map<String, Object> params, String name) {
        Object value = params.get(name);
        String op = def.search().stream()
                .filter(s -> s.field().equals(name))
                .map(SearchDef::op)
                .findFirst()
                .orElse("eq");
        if ("like".equalsIgnoreCase(StrUtil.blankToDefault(op, "eq")) && value != null) {
            return "%" + value + "%";
        }
        return value;
    }

    private boolean isEmptyValue(Object value) {
        return value == null || (value instanceof String s && s.isEmpty());
    }

    /** between 取值：Collection/Object[] 两元素，或逗号分隔字符串 */
    private List<Object> betweenPair(Object value) {
        if (value instanceof Collection<?> c) {
            if (c.size() == 2) {
                return new ArrayList<>(c);
            }
        } else if (value instanceof Object[] arr) {
            if (arr.length == 2) {
                return Arrays.asList(arr[0], arr[1]);
            }
        } else if (value instanceof String s && s.contains(",")) {
            int idx = s.indexOf(',');
            return List.of(s.substring(0, idx).trim(), s.substring(idx + 1).trim());
        }
        throw new BizException("between 条件需要两个值（数组或逗号分隔）");
    }

    // ==================== 元数据 / 基础设施 ====================

    /** 目标表是否含指定列（查 information_schema.columns；元数据不可用时按无该列处理） */
    private boolean hasColumn(JdbcTemplate jdbc, String table, String column) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("查询表 [{}] 元数据失败，跳过 {} 列判定：{}", table, column, e.getMessage());
            return false;
        }
    }

    /** 仅已发布列表可用（查询/保存/删除统一入口） */
    private LcList loadPublished(String code) {
        LcList def = listMapper.selectOne(new LambdaQueryWrapper<LcList>()
                .eq(LcList::getCode, code)
                .eq(LcList::getStatus, STATUS_PUBLISHED));
        if (def == null || StrUtil.isBlank(def.getPublishedSchema())) {
            throw new BizException("列表不存在或未发布");
        }
        return def;
    }

    /** 解析发布快照：sourceConfig + listSchema */
    private ParsedDefinition parseDefinition(LcList entity) {
        String sourceType = StrUtil.blankToDefault(entity.getSourceType(), "TABLE").trim().toUpperCase();
        if ("API".equals(sourceType)) {
            throw new BizException("API 数据源暂未支持，请使用 TABLE 或 SQL");
        }
        JSONObject snapshot = parseJson(entity.getPublishedSchema(), "发布快照");
        JSONObject sourceCfg = parseJson(snapshot.getStr("sourceConfig"), "数据源配置");
        JSONObject schemaCfg = parseJson(snapshot.getStr("listSchema"), "列表 Schema");

        List<ColumnDef> columns = new ArrayList<>();
        JSONArray colArr = schemaCfg.getJSONArray("columns");
        if (colArr != null) {
            for (Object o : colArr) {
                if (!(o instanceof JSONObject c)) {
                    continue;
                }
                String field = c.getStr("field");
                if (StrUtil.isBlank(field)) {
                    continue;
                }
                columns.add(new ColumnDef(field.trim(), Boolean.TRUE.equals(c.getBool("editable"))));
            }
        }
        List<SearchDef> search = new ArrayList<>();
        JSONArray searchArr = schemaCfg.getJSONArray("search");
        if (searchArr != null) {
            for (Object o : searchArr) {
                if (!(o instanceof JSONObject s)) {
                    continue;
                }
                String field = s.getStr("field");
                if (StrUtil.isBlank(field)) {
                    continue;
                }
                search.add(new SearchDef(field.trim(), s.getStr("op")));
            }
        }
        return new ParsedDefinition(sourceType,
                sourceCfg.getLong("datasourceId"),
                sourceCfg.getStr("tableName"),
                sourceCfg.getStr("pkField"),
                sourceCfg.getStr("sql"),
                columns, search);
    }

    private JSONObject parseJson(String json, String what) {
        if (StrUtil.isBlank(json)) {
            throw new BizException(what + "未配置，请重新发布列表");
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            throw new BizException(what + "不是合法的 JSON，请重新发布列表");
        }
    }

    /** 外部库统一走数据源连接缓存，datasourceId 空=平台主库 */
    private JdbcTemplate getJdbc(Long datasourceId) {
        return datasourceService.getJdbcTemplate(datasourceId);
    }

    // ==================== 解析模型 ====================

    /** 已发布列表定义解析结果 */
    private record ParsedDefinition(String sourceType, Long datasourceId, String tableName,
                                    String pkField, String sql, List<ColumnDef> columns, List<SearchDef> search) {
    }

    /** 列定义：field + editable */
    private record ColumnDef(String field, boolean editable) {
    }

    /** 搜索定义：field + op（eq/like/gt/ge/lt/le/between） */
    private record SearchDef(String field, String op) {
    }
}
