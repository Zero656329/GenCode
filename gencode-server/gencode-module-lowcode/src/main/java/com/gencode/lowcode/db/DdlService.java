package com.gencode.lowcode.db;

import cn.hutool.core.util.StrUtil;
import com.gencode.common.exception.BizException;
import com.gencode.lowcode.db.dto.ColumnInfo;
import com.gencode.lowcode.db.dto.ColumnSpec;
import com.gencode.lowcode.db.dto.DdlPreviewVO;
import com.gencode.lowcode.db.dto.TableSpec;
import com.gencode.lowcode.db.dto.TypeMapVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 可视化建表 DDL 服务：类型映射 / 保留字转义 / 统一约定列 / DDL 生成与执行 / 元数据逆向
 *
 * <p>安全层（先于一切生成与执行）：
 * 1. 表名/列名一律 ^[A-Za-z_][A-Za-z0-9_]*$ 白名单校验；
 * 2. 列类型仅允许 varchar/int/bigint/datetime/text/decimal 逻辑类型（tinyint 供约定列内部使用），
 *    不接收任何裸 DDL 文本，CREATE TABLE 语句全部由本服务内部拼装；
 * 3. 执行前 DatabaseMetaData 查重，表已存在直接报错。</p>
 *
 * <p>统一约定列（用户列之外自动追加）：
 * id BIGINT 主键（雪花）、tenant_id varchar(12) default '000000'、
 * create_by varchar(64)、create_time、update_by varchar(64)、update_time、
 * deleted 逻辑删除 default 0——类型按方言映射。
 * 用户列勾选 pk=true 时生成 UNIQUE 约束（业务唯一键），不替换 id 主键。</p>
 */
@Service
public class DdlService {

    private static final Logger log = LoggerFactory.getLogger(DdlService.class);

    /** 标识符（表名/列名）白名单正则 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    /** 用户列支持的逻辑类型（tinyint 为约定列 deleted 内部类型） */
    private static final Set<String> LOGICAL_TYPES = Set.of(
            "varchar", "int", "bigint", "datetime", "text", "decimal", "tinyint");
    /** 约定列清单（小写）：逆向/代码生成时按约定列过滤 */
    public static final Set<String> CONVENTION_COLUMNS = Set.of(
            "id", "tenant_id", "create_by", "create_time", "update_by", "update_time", "deleted");
    /** varchar 默认长度 / decimal 默认精度、小数位 */
    private static final int DEFAULT_VARCHAR_LEN = 255;
    private static final int DEFAULT_DECIMAL_P = 10;
    private static final int DEFAULT_DECIMAL_S = 2;
    /** MySQL 标识符/约束名长度上限 */
    private static final int MYSQL_NAME_MAX = 64;

    private final JdbcTemplate jdbcTemplate;

    /** 数据库方言（gencode.db-type：mysql/postgresql/oracle/sqlserver/dm） */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    public DdlService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 对外接口 ====================

    /** DDL 预览：返回 CREATE TABLE（含注释）语句文本 */
    public DdlPreviewVO preview(TableSpec spec) {
        return new DdlPreviewVO(String.join("\n", buildStatements(spec)));
    }

    /** 执行建表：单表 CREATE TABLE 一条 execute；Oracle/PG/SQLServer 注释语句逐条执行 */
    public void execute(TableSpec spec) {
        String table = requireIdentifier(specOf(spec).getTableName(), "表名");
        if (tableExists(table)) {
            throw new BizException("表已存在：" + table);
        }
        List<String> statements = buildStatements(spec);
        for (String stmt : statements) {
            jdbcTemplate.execute(stmt);
        }
        log.info("可视化建表完成 [{}]，共执行 {} 条 DDL", table, statements.size());
    }

    /** 逆向读表结构：DatabaseMetaData（含主键标记） */
    public List<ColumnInfo> columns(String tableName) {
        String table = requireIdentifier(tableName, "表名");
        List<ColumnInfo> list = jdbcTemplate.execute((ConnectionCallback<List<ColumnInfo>>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            Set<String> pks = new HashSet<>();
            try (ResultSet rs = meta.getPrimaryKeys(conn.getCatalog(), conn.getSchema(), table)) {
                while (rs.next()) {
                    pks.add(rs.getString("COLUMN_NAME"));
                }
            }
            List<ColumnInfo> result = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), conn.getSchema(), table, "%")) {
                while (rs.next()) {
                    ColumnInfo info = new ColumnInfo();
                    info.setColumnName(rs.getString("COLUMN_NAME"));
                    info.setTypeName(rs.getString("TYPE_NAME"));
                    info.setComment(rs.getString("REMARKS"));
                    info.setNullable(!"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    info.setPk(rs.getString("COLUMN_NAME") != null && pks.contains(rs.getString("COLUMN_NAME")));
                    result.add(info);
                }
            }
            return result;
        });
        if (list == null || list.isEmpty()) {
            throw new BizException("表不存在或没有列：" + table);
        }
        return list;
    }

    /** 方言类型映射表（供前端下拉；键为方言无关的逻辑类型） */
    public List<TypeMapVO> typemap() {
        return List.of(
                new TypeMapVO("varchar", "文本"),
                new TypeMapVO("int", "整数"),
                new TypeMapVO("bigint", "长整数"),
                new TypeMapVO("decimal", "小数"),
                new TypeMapVO("datetime", "日期时间"),
                new TypeMapVO("text", "长文本"));
    }

    // ==================== DDL 生成 ====================

    /** 生成全部语句：CREATE TABLE + （按方言）注释语句 */
    private List<String> buildStatements(TableSpec spec) {
        String dialect = DialectUtil.normalizeDbType(dbType);
        boolean mysqlLike = "mysql".equals(dialect) || "mariadb".equals(dialect);
        String table = requireIdentifier(specOf(spec).getTableName(), "表名");
        String qTable = quoteIfNeeded(table, dialect);

        // 用户列校验 + 列定义
        List<ColumnSpec> userColumns = validColumns(spec);
        Set<String> used = new HashSet<>();
        List<String> defs = new ArrayList<>();
        List<String> uniqueDefs = new ArrayList<>();
        // 注释对：[0]=列名, [1]=注释（MySQL 内联，其余方言后置语句）
        List<String[]> columnComments = new ArrayList<>();
        for (ColumnSpec col : userColumns) {
            String name = col.getName().trim();
            used.add(name.toLowerCase(Locale.ROOT));
            defs.add(columnDef(col, dialect));
            if (StrUtil.isNotBlank(col.getComment())) {
                columnComments.add(new String[]{name, col.getComment().trim()});
            }
            if (col.isPk() && !"id".equalsIgnoreCase(name)) {
                // 业务主键标记 → 唯一约束（平台主键恒为 id）
                uniqueDefs.add(uniqueConstraint(table, name, mysqlLike));
            }
        }
        // 统一约定列（用户已定义同名列时不重复追加）
        for (ColumnSpec col : conventionColumns()) {
            if (!used.contains(col.getName().toLowerCase(Locale.ROOT))) {
                defs.add(columnDef(col, dialect));
                columnComments.add(new String[]{col.getName(), col.getComment()});
            }
        }

        List<String> body = new ArrayList<>(defs);
        // 主键：恒为约定 id 列
        String pkName = truncateName("pk_" + table, mysqlLike);
        body.add(mysqlLike
                ? "  PRIMARY KEY (id)"
                : "  CONSTRAINT " + pkName + " PRIMARY KEY (id)");
        body.addAll(uniqueDefs);

        StringBuilder create = new StringBuilder("CREATE TABLE ").append(qTable).append(" (\n");
        create.append(String.join(",\n", body));
        if (mysqlLike) {
            create.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            if (StrUtil.isNotBlank(spec.getTableComment())) {
                create.append(" COMMENT='").append(escape(spec.getTableComment())).append("'");
            }
            create.append(";");
        } else {
            create.append("\n);");
        }

        List<String> statements = new ArrayList<>();
        statements.add(create.toString());
        statements.addAll(commentStatements(dialect, qTable, table, spec.getTableComment(), columnComments));
        return statements;
    }

    /** 用户列校验：列名标识符白名单 + 类型白名单 + 列名查重 */
    private List<ColumnSpec> validColumns(TableSpec spec) {
        List<ColumnSpec> columns = spec.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new BizException("请至少定义一列");
        }
        Set<String> seen = new HashSet<>();
        for (ColumnSpec col : columns) {
            requireIdentifier(col.getName(), "列名");
            String type = StrUtil.blankToDefault(col.getTypeName(), "").trim().toLowerCase(Locale.ROOT);
            if (!LOGICAL_TYPES.contains(type) || "tinyint".equals(type)) {
                throw new BizException("不支持的列类型：" + col.getTypeName()
                        + "（仅支持 varchar/int/bigint/datetime/text/decimal）");
            }
            if (!seen.add(col.getName().trim().toLowerCase(Locale.ROOT))) {
                throw new BizException("列名重复：" + col.getName());
            }
        }
        return columns;
    }

    /** 单列定义：名字（保留字转义）+ 方言类型 + [NOT NULL] + [DEFAULT v]（MySQL 追加 COMMENT） */
    private String columnDef(ColumnSpec col, String dialect) {
        boolean mysqlLike = "mysql".equals(dialect) || "mariadb".equals(dialect);
        StringBuilder sb = new StringBuilder("  ");
        sb.append(quoteIfNeeded(col.getName().trim(), dialect)).append(' ').append(columnType(col, dialect));
        String dft = defaultSql(col, mysqlLike);
        if (mysqlLike) {
            if (col.isNotNull()) {
                sb.append(" NOT NULL");
            }
            if (dft != null) {
                sb.append(" DEFAULT ").append(dft);
            }
            if (StrUtil.isNotBlank(col.getComment())) {
                sb.append(" COMMENT '").append(escape(col.getComment())).append("'");
            }
        } else {
            if (dft != null) {
                sb.append(" DEFAULT ").append(dft);
            }
            if (col.isNotNull()) {
                sb.append(" NOT NULL");
            }
        }
        return sb.toString();
    }

    /** 类型映射（用户列 + 约定列按同一套规则）：varchar/int/bigint/datetime/text/decimal/tinyint */
    private String columnType(ColumnSpec col, String dialect) {
        String type = col.getTypeName().trim().toLowerCase(Locale.ROOT);
        int len = positiveOr(col.getLength(), DEFAULT_VARCHAR_LEN);
        int p = positiveOr(col.getLength(), DEFAULT_DECIMAL_P);
        int s = nonNegativeOr(col.getScale(), DEFAULT_DECIMAL_S);
        return switch (type) {
            case "varchar" -> switch (dialect) {
                case "oracle" -> "VARCHAR2(" + len + " CHAR)";
                case "sqlserver" -> "NVARCHAR(" + len + ")";
                case "mysql", "mariadb", "postgresql", "dm" -> "VARCHAR(" + len + ")";
                default -> "VARCHAR(" + len + ")";
            };
            case "int" -> "oracle".equals(dialect) ? "NUMBER(10)" : "INT";
            case "bigint" -> "oracle".equals(dialect) ? "NUMBER(20)" : "BIGINT";
            case "datetime" -> switch (dialect) {
                case "mysql", "mariadb", "sqlserver" -> "DATETIME";
                default -> "TIMESTAMP"; // oracle / postgresql / dm
            };
            case "text" -> switch (dialect) {
                case "oracle" -> "CLOB";
                case "sqlserver" -> "NVARCHAR(MAX)";
                default -> "TEXT"; // mysql / postgresql / dm
            };
            case "decimal" -> "oracle".equals(dialect) ? "NUMBER(" + p + "," + s + ")" : "DECIMAL(" + p + "," + s + ")";
            // tinyint：约定列 deleted 专用
            case "tinyint" -> switch (dialect) {
                case "oracle" -> "NUMBER(3)";
                case "postgresql" -> "SMALLINT";
                default -> "TINYINT"; // mysql / sqlserver / dm
            };
            default -> throw new BizException("不支持的列类型：" + type);
        };
    }

    /** 默认值：数值类型不加引号，其余按字符串字面量（单引号转义） */
    private String defaultSql(ColumnSpec col, boolean mysqlLike) {
        String value = col.getDefaultValue();
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String type = col.getTypeName().trim().toLowerCase(Locale.ROOT);
        if ("int".equals(type) || "bigint".equals(type) || "decimal".equals(type) || "tinyint".equals(type)) {
            return value.trim();
        }
        return "'" + escape(value) + "'";
    }

    /** 统一约定列（用户列之外自动追加，按方言映射） */
    private List<ColumnSpec> conventionColumns() {
        List<ColumnSpec> list = new ArrayList<>();
        list.add(conventionCol("id", "bigint", true, "主键ID"));
        list.add(conventionCol("tenant_id", "varchar", false, "租户编号", 12, "000000"));
        list.add(conventionCol("create_by", "varchar", false, "创建人", 64, null));
        list.add(conventionCol("create_time", "datetime", false, "创建时间", null, null));
        list.add(conventionCol("update_by", "varchar", false, "更新人", 64, null));
        list.add(conventionCol("update_time", "datetime", false, "更新时间", null, null));
        list.add(conventionCol("deleted", "tinyint", true, "逻辑删除：0未删除 1已删除", null, "0"));
        return list;
    }

    private ColumnSpec conventionCol(String name, String type, boolean notNull, String comment) {
        return conventionCol(name, type, notNull, comment, null, null);
    }

    private ColumnSpec conventionCol(String name, String type, boolean notNull, String comment,
                                     Integer length, String defaultValue) {
        ColumnSpec col = new ColumnSpec();
        col.setName(name);
        col.setTypeName(type);
        col.setLength(length);
        col.setNotNull(notNull);
        col.setComment(comment);
        col.setDefaultValue(defaultValue);
        return col;
    }

    /** 用户勾选 pk 的列生成唯一约束（业务唯一键，不替换 id 主键） */
    private String uniqueConstraint(String table, String column, boolean mysqlLike) {
        String name = truncateName("uk_" + table + "_" + column, mysqlLike);
        return mysqlLike
                ? "  UNIQUE KEY " + name + " (" + column + ")"
                : "  CONSTRAINT " + name + " UNIQUE (" + column + ")";
    }

    /** 注释语句：MySQL 内联返回空；Oracle/PG/达梦 COMMENT ON；SQLServer sp_addextendedproperty */
    private List<String> commentStatements(String dialect, String qTable, String table,
                                           String tableComment, List<String[]> columnComments) {
        List<String> stmts = new ArrayList<>();
        boolean mysqlLike = "mysql".equals(dialect) || "mariadb".equals(dialect);
        if (mysqlLike) {
            return stmts;
        }
        if ("sqlserver".equals(dialect)) {
            if (StrUtil.isNotBlank(tableComment)) {
                stmts.add("EXEC sp_addextendedproperty N'MS_Description', N'" + escape(tableComment)
                        + "', N'SCHEMA', N'dbo', N'TABLE', N'" + table + "';");
            }
            for (String[] c : columnComments) {
                stmts.add("EXEC sp_addextendedproperty N'MS_Description', N'" + escape(c[1])
                        + "', N'SCHEMA', N'dbo', N'TABLE', N'" + table + "', N'COLUMN', N'" + c[0] + "';");
            }
            return stmts;
        }
        // oracle / postgresql / dm：COMMENT ON
        if (StrUtil.isNotBlank(tableComment)) {
            stmts.add("COMMENT ON TABLE " + qTable + " IS '" + escape(tableComment) + "';");
        }
        for (String[] c : columnComments) {
            stmts.add("COMMENT ON COLUMN " + qTable + "." + quoteIfNeeded(c[0], dialect)
                    + " IS '" + escape(c[1]) + "';");
        }
        return stmts;
    }

    // ==================== 工具 ====================

    /** 入参判空 */
    private TableSpec specOf(TableSpec spec) {
        if (spec == null) {
            throw new BizException("建表规格不能为空");
        }
        return spec;
    }

    /** 保留字按方言转义，普通标识符原样返回 */
    private String quoteIfNeeded(String identifier, String dialect) {
        return DialectUtil.isReserved(identifier) ? DialectUtil.quote(identifier, dialect) : identifier;
    }

    /** 标识符白名单校验（表名/列名）：^[A-Za-z_][A-Za-z0-9_]*$ */
    private String requireIdentifier(String identifier, String what) {
        if (StrUtil.isBlank(identifier) || !IDENTIFIER.matcher(identifier.trim()).matches()) {
            throw new BizException("非法的" + what + "：" + identifier);
        }
        return identifier.trim();
    }

    /** SQL 字符串字面量转义：单引号翻倍 */
    private String escape(String text) {
        return text == null ? "" : text.replace("'", "''");
    }

    private int positiveOr(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int nonNegativeOr(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private String truncateName(String name, boolean need) {
        return need && name.length() > MYSQL_NAME_MAX ? name.substring(0, MYSQL_NAME_MAX) : name;
    }

    /** 表是否存在（DatabaseMetaData 查重；Oracle/达梦 补大写、PG 补小写再查） */
    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            if (existsTable(conn, meta, table)) {
                return true;
            }
            String dialect = DialectUtil.normalizeDbType(dbType);
            if ("oracle".equals(dialect) || "oracle11g".equals(dialect) || "dm".equals(dialect)) {
                return existsTable(conn, meta, table.toUpperCase(Locale.ROOT));
            }
            if ("postgresql".equals(dialect)) {
                return existsTable(conn, meta, table.toLowerCase(Locale.ROOT));
            }
            return false;
        }));
    }

    private boolean existsTable(Connection conn, DatabaseMetaData meta, String table) throws SQLException {
        try (ResultSet rs = meta.getTables(conn.getCatalog(), conn.getSchema(), table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
