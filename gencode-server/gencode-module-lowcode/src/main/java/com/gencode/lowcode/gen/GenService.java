package com.gencode.lowcode.gen;

import cn.hutool.core.util.StrUtil;
import com.gencode.common.exception.BizException;
import com.gencode.lowcode.db.DialectUtil;
import com.gencode.lowcode.gen.dto.GenFileVO;
import com.gencode.lowcode.gen.dto.GenPreviewBody;
import com.gencode.lowcode.gen.dto.GenTableVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成服务：读平台主库 information_schema/元数据 → 生成五方言适配的
 * Java（Entity/Mapper/Service/Controller/MapperXML）+ Vue 列表页/api.ts + 菜单 SQL。
 *
 * <p>约定：主键统一雪花 Long（继承 BaseEntity 的 @TableId(ASSIGN_ID)）；实体按表是否含
 * tenant_id 决定继承 TenantBaseEntity/BaseEntity；租户与审计字段由平台插件自动填充；
 * R/PageResult 分页与 @SaCheckPermission 权限标识遵循平台约定；保留字列 Java 字段名加
 * Field 后缀并 @TableField(方言引号) 映射真实列。模板为 String.format 级文本骨架，
 * 不引入模板引擎。</p>
 */
@Service
public class GenService {

    private static final Logger log = LoggerFactory.getLogger(GenService.class);

    /** 标识符白名单（表名/模块名/业务名） */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    /** 包名白名单：a.b.c 形式 */
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");
    /** 系统表前缀过滤（Flowable） */
    private static final Set<String> SKIP_TABLE_PREFIXES = Set.of("act_", "flw_");
    /** 约定列（继承自基类，不生成业务字段） */
    private static final Set<String> BASE_COLUMNS =
            Set.of("id", "tenant_id", "create_by", "create_time", "update_by", "update_time", "deleted");

    private final JdbcTemplate jdbcTemplate;

    /** 数据库方言（gencode.db-type）：决定保留字转义引号 */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    public GenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 表清单 ====================

    /** 可选表清单（平台主库），keyword 模糊匹配表名/注释，过滤 Flowable 系统表 */
    public List<GenTableVO> tables(String keyword) {
        String dialect = DialectUtil.normalizeDbType(dbType);
        String sql = switch (dialect) {
            case "oracle", "oracle11g", "dm" -> """
                    SELECT t.table_name AS table_name, c.comments AS table_comment
                    FROM user_tables t LEFT JOIN user_tab_comments c ON c.table_name = t.table_name""";
            case "postgresql" -> """
                    SELECT c.relname AS table_name, COALESCE(obj_description(c.oid), '') AS table_comment
                    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE c.relkind = 'r' AND n.nspname = CURRENT_SCHEMA()""";
            case "sqlserver" -> """
                    SELECT t.name AS table_name, CAST(ISNULL(ep.value, '') AS NVARCHAR(1000)) AS table_comment
                    FROM sys.tables t
                    LEFT JOIN sys.extended_properties ep
                      ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.class = 1
                     AND ep.name = N'MS_Description'""";
            default -> """
                    SELECT table_name, table_comment FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'""";
        };
        List<GenTableVO> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            Map<String, Object> r = lowerKeyMap(row);
            String name = str(r.get("table_name"));
            if (StrUtil.isBlank(name) || startsWithAny(name)) {
                continue;
            }
            String comment = str(r.get("table_comment"));
            if (StrUtil.isNotBlank(keyword)
                    && !name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
                    && !comment.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                continue;
            }
            GenTableVO vo = new GenTableVO();
            vo.setTableName(name);
            vo.setTableComment(comment);
            list.add(vo);
        }
        list.sort((a, b) -> a.getTableName().compareToIgnoreCase(b.getTableName()));
        return list;
    }

    // ==================== 预览 / 下载 ====================

    /** 生成文件集预览 */
    public List<GenFileVO> preview(GenPreviewBody body) {
        return buildFiles(body);
    }

    /** 打包下载：全部生成文件写入 zip（UTF-8 文件名） */
    public byte[] download(GenPreviewBody body) {
        List<GenFileVO> files = buildFiles(body);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (GenFileVO file : files) {
                zip.putNextEntry(new ZipEntry(file.getPath()));
                zip.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new BizException("打包下载失败：" + e.getMessage());
        }
        return bos.toByteArray();
    }

    // ==================== 文件集生成 ====================

    private List<GenFileVO> buildFiles(GenPreviewBody body) {
        if (body == null) {
            throw new BizException("请求体不能为空");
        }
        GenModel model = loadModel(body);
        String pkgPath = model.pkg().replace('.', '/');
        String entity = model.entity();

        List<GenFileVO> files = new ArrayList<>();
        files.add(new GenFileVO(pkgPath + "/entity/" + entity + ".java", renderEntity(model)));
        files.add(new GenFileVO(pkgPath + "/mapper/" + entity + "Mapper.java", renderMapper(model)));
        files.add(new GenFileVO(pkgPath + "/service/I" + entity + "Service.java", renderIService(model)));
        files.add(new GenFileVO(pkgPath + "/service/impl/" + entity + "ServiceImpl.java", renderServiceImpl(model)));
        files.add(new GenFileVO(pkgPath + "/controller/" + entity + "Controller.java", renderController(model)));
        files.add(new GenFileVO(pkgPath + "/mapper/xml/" + entity + "Mapper.xml", renderMapperXml(model)));
        files.add(new GenFileVO("vue/" + model.business() + "/index.vue", renderVueIndex(model)));
        files.add(new GenFileVO("vue/" + model.business() + "/api.ts", renderVueApi(model)));
        files.add(new GenFileVO("sql/menu.sql", renderMenuSql(model)));
        log.info("代码生成 [{}]：{} 个文件（方言 {}）", model.table(), files.size(), DialectUtil.normalizeDbType(dbType));
        return files;
    }

    /** 读取目标表元数据并解析为生成模型（字段驼峰、保留字加 Field 后缀、类型映射） */
    private GenModel loadModel(GenPreviewBody body) {
        String table = requireIdentifier(body.getTableName(), "表名");
        GenPreviewBody.GenOptions opts = body.getOptions() == null ? new GenPreviewBody.GenOptions() : body.getOptions();
        String pkg = StrUtil.blankToDefault(opts.getPackageName(), "com.gencode.business").trim();
        if (!PACKAGE_PATTERN.matcher(pkg).matches()) {
            throw new BizException("非法的包名：" + pkg);
        }
        String module = StrUtil.blankToDefault(opts.getModuleName(), "business").trim();
        requireIdentifier(module, "模块名");
        String author = StrUtil.blankToDefault(opts.getAuthor(), "gencode").trim();
        String business = StrUtil.blankToDefault(opts.getBusinessName(), deriveBusinessName(table)).trim();
        requireIdentifier(business, "业务名");

        List<GenColumn> all = loadColumns(table);
        boolean hasTenant = all.stream().anyMatch(c -> "tenant_id".equalsIgnoreCase(c.column()));
        List<GenColumn> bizColumns = all.stream()
                .filter(c -> !BASE_COLUMNS.contains(c.column().toLowerCase(Locale.ROOT)))
                .toList();

        String tableComment = StrUtil.blankToDefault(tableComment(table), table);
        return new GenModel(table, tableComment, pkg, module, author, business,
                toPascal(table), hasTenant, bizColumns);
    }

    /** 表注释：按方言查主库元数据，查不到返回 null（调用方兜底为表名） */
    private String tableComment(String tableName) {
        String dialect = DialectUtil.normalizeDbType(dbType);
        try {
            String comment = switch (dialect) {
                case "oracle", "oracle11g", "dm" -> queryComment(
                        "SELECT comments FROM user_tab_comments WHERE table_name = ?", tableName,
                        tableName.toUpperCase(Locale.ROOT));
                case "postgresql" -> queryComment(
                        "SELECT obj_description(c.oid) FROM pg_class c WHERE c.relkind = 'r' AND c.relname = ?",
                        tableName);
                case "sqlserver" -> queryComment(
                        "SELECT CAST(ISNULL(ep.value, '') AS NVARCHAR(1000)) FROM sys.tables t"
                                + " LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id"
                                + " AND ep.minor_id = 0 AND ep.class = 1 AND ep.name = N'MS_Description'"
                                + " WHERE t.name = ?", tableName);
                default -> queryComment(
                        "SELECT table_comment FROM information_schema.tables"
                                + " WHERE table_schema = DATABASE() AND table_name = ?", tableName);
            };
            return StrUtil.isBlank(comment) ? null : comment;
        } catch (Exception e) {
            log.warn("查询表 [{}] 注释失败，按表名兜底：{}", tableName, e.getMessage());
            return null;
        }
    }

    /** 依次尝试多个候选名查单值（兼容 Oracle/达梦 大写存储） */
    private String queryComment(String sql, String... candidates) {
        for (String candidate : candidates) {
            List<String> values = jdbcTemplate.queryForList(sql, String.class, candidate);
            if (!values.isEmpty() && StrUtil.isNotBlank(values.get(0))) {
                return values.get(0);
            }
        }
        return null;
    }

    /** DatabaseMetaData 逆向读列（类型映射：string/int/long/bigDecimal/localDateTime） */
    private List<GenColumn> loadColumns(String tableName) {
        List<GenColumn> list = jdbcTemplate.execute((ConnectionCallback<List<GenColumn>>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            List<GenColumn> result = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), conn.getSchema(), tableName, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    int digits = rs.getInt("DECIMAL_DIGITS");
                    String remarks = rs.getString("REMARKS");
                    result.add(new GenColumn(name, toJavaField(name), toJavaType(typeName, size, digits),
                            StrUtil.blankToDefault(remarks, "").replaceAll("\\s+", " ")));
                }
            }
            return result;
        });
        if (list == null || list.isEmpty()) {
            throw new BizException("表不存在或没有列：" + tableName);
        }
        return list;
    }

    // ==================== 模板渲染 ====================

    private String renderEntity(GenModel m) {
        boolean anyQuoted = m.columns().stream().anyMatch(GenColumn::quoted);
        boolean anyBigDecimal = m.columns().stream().anyMatch(c -> "BigDecimal".equals(c.javaType()));
        boolean anyDateTime = m.columns().stream().anyMatch(c -> "LocalDateTime".equals(c.javaType()));
        String baseClass = m.hasTenant() ? "TenantBaseEntity" : "BaseEntity";

        List<String> imports = new ArrayList<>();
        imports.add("com.baomidou.mybatisplus.annotation.TableName");
        if (anyQuoted) {
            imports.add("com.baomidou.mybatisplus.annotation.TableField");
        }
        imports.add("com.gencode.common.entity." + baseClass);
        imports.add("lombok.Data");
        imports.add("lombok.EqualsAndHashCode");
        if (anyBigDecimal) {
            imports.add("java.math.BigDecimal");
        }
        if (anyDateTime) {
            imports.add("java.time.LocalDateTime");
        }

        StringBuilder fields = new StringBuilder();
        for (GenColumn c : m.columns()) {
            fields.append("    /** ").append(StrUtil.blankToDefault(c.comment(), c.column())).append(" */\n");
            if (c.quoted()) {
                fields.append("    @TableField(\"").append(escapeJava(DialectUtil.quote(c.column(), dbType)))
                        .append("\")\n");
            }
            fields.append("    private ").append(c.javaType()).append(' ').append(c.field()).append(";\n\n");
        }
        if (fields.isEmpty()) {
            fields.append("    // 该表没有业务列（仅约定列）\n\n");
        }

        return TPL_ENTITY
                .replace("__PACKAGE__", m.pkg())
                .replace("__IMPORTS__", imports.stream().map(i -> "import " + i + ";").collect(java.util.stream.Collectors.joining("\n")))
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__AUTHOR__", m.author())
                .replace("__DATE__", LocalDate.now().toString())
                .replace("__ENTITY__", m.entity())
                .replace("__TABLE__", m.table())
                .replace("__BASE_CLASS__", baseClass)
                .replace("__FIELDS__", fields.toString().stripTrailing());
    }

    private String renderMapper(GenModel m) {
        return TPL_MAPPER
                .replace("__PACKAGE__", m.pkg())
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__AUTHOR__", m.author())
                .replace("__DATE__", LocalDate.now().toString())
                .replace("__ENTITY__", m.entity());
    }

    private String renderIService(GenModel m) {
        return TPL_SERVICE
                .replace("__PACKAGE__", m.pkg())
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__AUTHOR__", m.author())
                .replace("__DATE__", LocalDate.now().toString())
                .replace("__ENTITY__", m.entity());
    }

    private String renderServiceImpl(GenModel m) {
        return TPL_SERVICE_IMPL
                .replace("__PACKAGE__", m.pkg())
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__AUTHOR__", m.author())
                .replace("__DATE__", LocalDate.now().toString())
                .replace("__ENTITY__", m.entity());
    }

    private String renderController(GenModel m) {
        List<String> stringFields = m.columns().stream()
                .filter(c -> "String".equals(c.javaType()))
                .map(GenColumn::field)
                .toList();

        String strUtilImport = stringFields.isEmpty() ? "" : "import cn.hutool.core.util.StrUtil;\n";
        String keywordBlock;
        if (stringFields.isEmpty()) {
            keywordBlock = "        // 无字符串业务列，keyword 参数预留\n";
        } else if (stringFields.size() == 1) {
            keywordBlock = "        if (StrUtil.isNotBlank(keyword)) {\n            wrapper.like("
                    + m.entity() + "::" + getter(stringFields.get(0)) + ", keyword);\n        }\n";
        } else {
            StringBuilder sb = new StringBuilder("        if (StrUtil.isNotBlank(keyword)) {\n")
                    .append("            wrapper.and(w -> w.like(").append(m.entity()).append("::")
                    .append(getter(stringFields.get(0))).append(", keyword)");
            for (int i = 1; i < stringFields.size(); i++) {
                sb.append("\n                    .or().like(").append(m.entity()).append("::")
                        .append(getter(stringFields.get(i))).append(", keyword)");
            }
            sb.append(");\n        }\n");
            keywordBlock = sb.toString();
        }

        List<String> imports = new ArrayList<>();
        imports.add("cn.dev33.satoken.annotation.SaCheckPermission");
        if (!stringFields.isEmpty()) {
            imports.add("cn.hutool.core.util.StrUtil");
        }
        imports.add("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");
        imports.add("com.baomidou.mybatisplus.extension.plugins.pagination.Page");
        imports.add("com.gencode.common.result.PageResult");
        imports.add("com.gencode.common.result.R");
        imports.add("com.gencode.framework.operlog.OperLog");
        imports.add(m.pkg() + ".entity." + m.entity());
        imports.add(m.pkg() + ".service.I" + m.entity() + "Service");
        imports.add("lombok.RequiredArgsConstructor");
        imports.add("org.springframework.web.bind.annotation.DeleteMapping");
        imports.add("org.springframework.web.bind.annotation.GetMapping");
        imports.add("org.springframework.web.bind.annotation.PathVariable");
        imports.add("org.springframework.web.bind.annotation.PostMapping");
        imports.add("org.springframework.web.bind.annotation.PutMapping");
        imports.add("org.springframework.web.bind.annotation.RequestBody");
        imports.add("org.springframework.web.bind.annotation.RequestMapping");
        imports.add("org.springframework.web.bind.annotation.RequestParam");
        imports.add("org.springframework.web.bind.annotation.RestController");

        return TPL_CONTROLLER
                .replace("__PACKAGE__", m.pkg())
                .replace("__IMPORTS__", imports.stream().map(i -> "import " + i + ";").collect(java.util.stream.Collectors.joining("\n")))
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__AUTHOR__", m.author())
                .replace("__DATE__", LocalDate.now().toString())
                .replace("__ENTITY__", m.entity())
                .replace("__ENTITY_VAR__", m.entityVar())
                .replace("__BASE_URL__", "/" + m.module() + "/" + m.business())
                .replace("__PERM__", m.module() + ":" + m.business())
                .replace("__KEYWORD_BLOCK__", keywordBlock);
    }

    private String renderMapperXml(GenModel m) {
        return TPL_MAPPER_XML
                .replace("__PACKAGE__", m.pkg())
                .replace("__ENTITY__", m.entity());
    }

    private String renderVueIndex(GenModel m) {
        StringBuilder cols = new StringBuilder();
        for (GenColumn c : m.columns()) {
            String editor;
            if ("LocalDateTime".equals(c.javaType())) {
                editor = "datetime";
            } else if ("number".equals(tsType(c.javaType()))) {
                editor = "number";
            } else {
                editor = "input";
            }
            cols.append("  { title: '").append(StrUtil.blankToDefault(c.comment(), c.column()))
                    .append("', dataIndex: '").append(c.field()).append("', editor: '").append(editor).append("' },\n");
        }
        cols.append("  { title: '创建时间', dataIndex: 'createTime', width: 170 },\n");
        cols.append("  { title: '操作', key: 'action', width: 150, fixed: 'right' }");

        return TPL_VUE_INDEX
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__PERM__", m.module() + ":" + m.business())
                .replace("__BIZ__", toPascal(m.business()))
                .replace("__VUE_COLUMNS__", cols.toString());
    }

    private String renderVueApi(GenModel m) {
        StringBuilder fields = new StringBuilder();
        for (GenColumn c : m.columns()) {
            fields.append("  ").append(c.field()).append("?: ").append(tsType(c.javaType())).append("\n");
        }
        return TPL_API_TS
                .replace("__TABLE__", m.table())
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__BIZ__", toPascal(m.business()))
                .replace("__BASE_URL__", "/" + m.module() + "/" + m.business())
                .replace("__API_FIELDS__", fields.toString());
    }

    private String renderMenuSql(GenModel m) {
        return TPL_MENU_SQL
                .replace("__TABLE__", m.table())
                .replace("__TABLE_COMMENT__", m.tableComment())
                .replace("__MODULE__", m.module())
                .replace("__BUSINESS__", m.business())
                .replace("__PERM__", m.module() + ":" + m.business());
    }

    // ==================== 模板（__TOKEN__ 占位替换，不引入模板引擎） ====================

    private static final String TPL_ENTITY = """
            package __PACKAGE__.entity;

            __IMPORTS__

            /**
             * __TABLE_COMMENT__
             *
             * @author __AUTHOR__
             * @since __DATE__
             */
            @Data
            @EqualsAndHashCode(callSuper = true)
            @TableName("__TABLE__")
            public class __ENTITY__ extends __BASE_CLASS__ {

                private static final long serialVersionUID = 1L;

                // 主键 id 与（含 tenant_id 时的）租户、审计、逻辑删除列均继承自 __BASE_CLASS__

            __FIELDS__
            }
            """;

    private static final String TPL_MAPPER = """
            package __PACKAGE__.mapper;

            import com.baomidou.mybatisplus.core.mapper.BaseMapper;
            import __PACKAGE__.entity.__ENTITY__;
            import org.apache.ibatis.annotations.Mapper;

            /**
             * __TABLE_COMMENT__ Mapper
             *
             * @author __AUTHOR__
             * @since __DATE__
             */
            @Mapper
            public interface __ENTITY__Mapper extends BaseMapper<__ENTITY__> {
            }
            """;

    private static final String TPL_SERVICE = """
            package __PACKAGE__.service;

            import com.baomidou.mybatisplus.extension.service.IService;
            import __PACKAGE__.entity.__ENTITY__;

            /**
             * __TABLE_COMMENT__ Service
             *
             * @author __AUTHOR__
             * @since __DATE__
             */
            public interface I__ENTITY__Service extends IService<__ENTITY__> {
            }
            """;

    private static final String TPL_SERVICE_IMPL = """
            package __PACKAGE__.service.impl;

            import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
            import __PACKAGE__.entity.__ENTITY__;
            import __PACKAGE__.mapper.__ENTITY__Mapper;
            import __PACKAGE__.service.I__ENTITY__Service;
            import org.springframework.stereotype.Service;

            /**
             * __TABLE_COMMENT__ Service 实现：ServiceImpl 提供通用 CRUD
             *
             * @author __AUTHOR__
             * @since __DATE__
             */
            @Service
            public class __ENTITY__ServiceImpl extends ServiceImpl<__ENTITY__Mapper, __ENTITY__>
                    implements I__ENTITY__Service {
            }
            """;

    private static final String TPL_CONTROLLER = """
            package __PACKAGE__.controller;

            __IMPORTS__

            /**
             * __TABLE_COMMENT__ 管理
             *
             * @author __AUTHOR__
             * @since __DATE__
             */
            @RestController
            @RequestMapping("__BASE_URL__")
            @RequiredArgsConstructor
            public class __ENTITY__Controller {

                private final I__ENTITY__Service __ENTITY_VAR__Service;

                /** 分页列表（keyword 模糊匹配全部字符串业务列） */
                @SaCheckPermission("__PERM__:list")
                @GetMapping("/page")
                public R<PageResult<__ENTITY__>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                      @RequestParam(defaultValue = "10") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
                    LambdaQueryWrapper<__ENTITY__> wrapper = new LambdaQueryWrapper<>();
            __KEYWORD_BLOCK__
                    wrapper.orderByDesc(__ENTITY__::getId);
                    Page<__ENTITY__> page = __ENTITY_VAR__Service.page(new Page<>(pageNum, pageSize), wrapper);
                    return R.ok(PageResult.of(page));
                }

                /** 详情 */
                @SaCheckPermission("__PERM__:list")
                @GetMapping("/{id}")
                public R<__ENTITY__> detail(@PathVariable Long id) {
                    return R.ok(__ENTITY_VAR__Service.getById(id));
                }

                /** 新增（雪花 ID、租户、审计字段由平台自动填充） */
                @OperLog(module = "__TABLE_COMMENT__", businessType = "INSERT")
                @SaCheckPermission("__PERM__:add")
                @PostMapping
                public R<Void> save(@RequestBody __ENTITY__ entity) {
                    __ENTITY_VAR__Service.save(entity);
                    return R.ok();
                }

                /** 更新（按主键 id） */
                @OperLog(module = "__TABLE_COMMENT__", businessType = "UPDATE")
                @SaCheckPermission("__PERM__:edit")
                @PutMapping
                public R<Void> update(@RequestBody __ENTITY__ entity) {
                    __ENTITY_VAR__Service.updateById(entity);
                    return R.ok();
                }

                /** 删除（逻辑删除） */
                @OperLog(module = "__TABLE_COMMENT__", businessType = "DELETE")
                @SaCheckPermission("__PERM__:delete")
                @DeleteMapping("/{id}")
                public R<Void> delete(@PathVariable Long id) {
                    __ENTITY_VAR__Service.removeById(id);
                    return R.ok();
                }
            }
            """;

    private static final String TPL_MAPPER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="__PACKAGE__.mapper.__ENTITY__Mapper">

                <!-- 空映射骨架：BaseMapper 已内置通用 CRUD，自定义 SQL 在此扩展 -->

            </mapper>
            """;

    private static final String TPL_VUE_INDEX = """
            <template>
              <div>
                <!-- 搜索区 -->
                <a-card :bordered="false" class="search-card">
                  <a-form layout="inline" @submit.prevent>
                    <a-form-item label="关键词">
                      <a-input
                        v-model:value="query.keyword"
                        placeholder="请输入关键词"
                        allow-clear
                        style="width: 220px"
                        @press-enter="handleSearch"
                      />
                    </a-form-item>
                    <a-form-item>
                      <a-space>
                        <a-button type="primary" @click="handleSearch">查询</a-button>
                        <a-button @click="handleReset">重置</a-button>
                      </a-space>
                    </a-form-item>
                  </a-form>
                </a-card>

                <!-- 列表区 -->
                <a-card :bordered="false">
                  <template #title>__TABLE_COMMENT__</template>
                  <template #extra>
                    <a-button v-perm="'__PERM__:add'" type="primary" @click="openAdd">新增</a-button>
                  </template>
                  <a-table
                    :columns="columns"
                    :data-source="list"
                    :loading="loading"
                    row-key="id"
                    :pagination="pagination"
                    :scroll="{ x: 900 }"
                    @change="handleTableChange"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'action'">
                        <a-space>
                          <a-button v-perm="'__PERM__:edit'" type="link" size="small" @click="openEdit(record)">编辑</a-button>
                          <a-button v-perm="'__PERM__:delete'" type="link" danger size="small" @click="handleDelete(record)">删除</a-button>
                        </a-space>
                      </template>
                    </template>
                  </a-table>
                </a-card>

                <!-- 新增/编辑弹窗 -->
                <a-modal
                  v-model:open="modalOpen"
                  :title="form.id ? '编辑' : '新增'"
                  :confirm-loading="saving"
                  @ok="handleSave"
                >
                  <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
                    <a-form-item v-for="f in formItems" :key="f.dataIndex" :label="f.title">
                      <a-input-number
                        v-if="f.editor === 'number'"
                        v-model:value="form[f.dataIndex]"
                        style="width: 100%"
                      />
                      <a-date-picker
                        v-else-if="f.editor === 'datetime'"
                        v-model:value="form[f.dataIndex]"
                        show-time
                        value-format="YYYY-MM-DD HH:mm:ss"
                        style="width: 100%"
                      />
                      <a-input v-else v-model:value="form[f.dataIndex]" />
                    </a-form-item>
                  </a-form>
                </a-modal>
              </div>
            </template>

            <script setup lang="ts">
            import { onMounted, reactive, ref } from 'vue'
            import { Modal, message } from 'ant-design-vue'
            import { add__BIZ__, get__BIZ__Page, remove__BIZ__, update__BIZ__ } from './api'
            import type { __BIZ__ } from './api'

            /** __TABLE_COMMENT__ */
            defineOptions({ name: '__BIZ__' })

            /** 列定义：editor 标记表单控件类型 */
            interface ColumnItem {
              title: string
              dataIndex?: string
              key?: string
              width?: number
              editor?: 'input' | 'number' | 'datetime'
            }

            const columns: ColumnItem[] = [
            __VUE_COLUMNS__
            ]

            /** 表单项 = 业务列（排除操作列与展示列） */
            const formItems = columns.filter((c) => c.dataIndex && c.dataIndex !== 'createTime')

            const query = reactive<{ keyword?: string }>({})
            const list = ref<__BIZ__[]>([])
            const loading = ref(false)
            const pagination = reactive({
              current: 1,
              pageSize: 10,
              total: 0,
              showSizeChanger: true,
              showTotal: (total: number) => `共 ${total} 条`
            })

            const modalOpen = ref(false)
            const saving = ref(false)
            const form = ref<Record<string, any>>({})

            async function loadPage(): Promise<void> {
              loading.value = true
              try {
                const data = await get__BIZ__Page({
                  keyword: query.keyword,
                  pageNum: pagination.current,
                  pageSize: pagination.pageSize
                })
                list.value = data.list
                pagination.total = data.total
              } finally {
                loading.value = false
              }
            }

            function handleSearch(): void {
              pagination.current = 1
              loadPage()
            }

            function handleReset(): void {
              query.keyword = undefined
              pagination.current = 1
              loadPage()
            }

            function handleTableChange(p: { current?: number; pageSize?: number }): void {
              pagination.current = p.current || 1
              pagination.pageSize = p.pageSize || 10
              loadPage()
            }

            function openAdd(): void {
              form.value = {}
              modalOpen.value = true
            }

            function openEdit(record: __BIZ__): void {
              form.value = { ...record }
              modalOpen.value = true
            }

            async function handleSave(): Promise<void> {
              saving.value = true
              try {
                if (form.value.id) {
                  await update__BIZ__(form.value as __BIZ__)
                } else {
                  await add__BIZ__(form.value as __BIZ__)
                }
                message.success('保存成功')
                modalOpen.value = false
                loadPage()
              } finally {
                saving.value = false
              }
            }

            function handleDelete(record: __BIZ__): void {
              Modal.confirm({
                title: '确认删除该条记录？',
                onOk: async () => {
                  await remove__BIZ__(record.id)
                  message.success('删除成功')
                  loadPage()
                }
              })
            }

            onMounted(loadPage)
            </script>

            <style scoped>
            .search-card {
              margin-bottom: 12px;
            }
            </style>
            """;

    private static final String TPL_API_TS = """
            import { del, get, post, put } from '@/utils/request'

            /**
             * __TABLE_COMMENT__（__TABLE__）
             * 契约见 docs/api-contract.md；ID 一律 string（后端雪花 ID 序列化为字符串）。
             */

            /** __TABLE_COMMENT__ */
            export interface __BIZ__ {
              id: string
            __API_FIELDS__
              createTime?: string
            }

            export interface __BIZ__PageParams {
              keyword?: string
              pageNum?: number
              pageSize?: number
            }

            /** 分页列表 */
            export function get__BIZ__Page(params: __BIZ__PageParams): Promise<PageResult<__BIZ__>> {
              return get<PageResult<__BIZ__>>('__BASE_URL__/page', params)
            }

            /** 详情 */
            export function get__BIZ__(id: string): Promise<__BIZ__> {
              return get<__BIZ__>(`__BASE_URL__/${id}`)
            }

            /** 新增 */
            export function add__BIZ__(data: Partial<__BIZ__>): Promise<null> {
              return post<null>('__BASE_URL__', data)
            }

            /** 更新 */
            export function update__BIZ__(data: Partial<__BIZ__>): Promise<null> {
              return put<null>('__BASE_URL__', data)
            }

            /** 删除（逻辑删除） */
            export function remove__BIZ__(id: string): Promise<null> {
              return del<null>(`__BASE_URL__/${id}`)
            }
            """;

    private static final String TPL_MENU_SQL = """
            -- ============================================================
            -- __TABLE_COMMENT__（__TABLE__）菜单脚本（MySQL 方言）
            -- 1. 起始菜单 ID 请按实际环境调整，避免与现有菜单冲突；
            -- 2. parent_id 默认挂在 200（低代码目录）下，请按需调整；
            -- 3. 执行后可按"角色管理"页面为角色分配菜单，脚本最后一条已授权超管角色。
            -- ============================================================
            SET @menu_id := 910001;

            -- 主菜单（C：path 即前端路由，component 即 views 组件路径）
            INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time)
            VALUES (@menu_id, 200, '__TABLE_COMMENT__', '/__MODULE__/__BUSINESS__', '__MODULE__/__BUSINESS__/index', 'C', '__PERM__:list', 'TableOutlined', 10, 0, 0, 'gen', NOW());

            -- 操作按钮（F）
            INSERT INTO sys_menu (id, parent_id, name, path, component, menu_type, perms, icon, sort, visible, status, create_by, create_time) VALUES
            (@menu_id + 1, @menu_id, '新增', NULL, NULL, 'F', '__PERM__:add', NULL, 1, 0, 0, 'gen', NOW()),
            (@menu_id + 2, @menu_id, '编辑', NULL, NULL, 'F', '__PERM__:edit', NULL, 2, 0, 0, 'gen', NOW()),
            (@menu_id + 3, @menu_id, '删除', NULL, NULL, 'F', '__PERM__:delete', NULL, 3, 0, 0, 'gen', NOW());

            -- 授权超管角色（角色ID=1，幂等）
            INSERT INTO sys_role_menu (role_id, menu_id)
            SELECT 1, id FROM sys_menu
            WHERE id IN (@menu_id, @menu_id + 1, @menu_id + 2, @menu_id + 3)
              AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.id);
            """;

    // ==================== 解析模型与映射 ====================

    /** 业务列：column 原列名 / field Java 字段 / javaType / comment */
    private record GenColumn(String column, String field, String javaType, String comment) {

        /** 保留字列需要 @TableField(方言引号) 映射真实列名 */
        boolean quoted() {
            return DialectUtil.isReserved(column);
        }
    }

    /** 生成模型：表信息 + 归一化选项 + 业务列 */
    private record GenModel(String table, String tableComment, String pkg, String module, String author,
                            String business, String entity, boolean hasTenant, List<GenColumn> columns) {

        /** 控制器服务变量名（实体首字母小写） */
        String entityVar() {
            return Character.toLowerCase(entity.charAt(0)) + entity.substring(1);
        }
    }

    /** 列名 → 驼峰字段；命中保留字/Java 关键字时加 Field 后缀 */
    private String toJavaField(String column) {
        String camel = camelize(column);
        if (DialectUtil.isReserved(camel) || DialectUtil.isJavaKeyword(camel)) {
            return camel + "Field";
        }
        return camel;
    }

    /** 表名 → 帕斯卡实体名：biz_order_item → BizOrderItem */
    private String toPascal(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** 业务名推导：取首个下划线之后的部分转驼峰（biz_order → order；无下划线取全名） */
    private String deriveBusinessName(String table) {
        String lower = table.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf('_');
        return camelize(idx >= 0 && idx < lower.length() - 1 ? lower.substring(idx + 1) : lower);
    }

    /** 下划线命名 → 驼峰（首字母小写） */
    private String camelize(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.isEmpty()) {
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        String camel = sb.toString();
        if (camel.isEmpty()) {
            throw new BizException("无法从列名/表名推导字段名：" + name);
        }
        return camel;
    }

    /**
     * JDBC 类型 → Java 类型：string/int/long/bigDecimal/localDateTime。
     * NUMBER（Oracle/达梦）按精度/小数位细分：带小数位→BigDecimal、≥19 位→Long、其余→Integer
     */
    private String toJavaType(String typeName, int size, int digits) {
        String t = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        if (t.contains("bigint")) {
            return "Long";
        }
        if (t.contains("number") || t.contains("numeric") || t.contains("decimal")) {
            if (digits > 0) {
                return "BigDecimal";
            }
            if (t.contains("number")) {
                return size >= 19 ? "Long" : "Integer";
            }
            return "BigDecimal";
        }
        if (t.contains("int")) {
            return "Integer";
        }
        if (t.contains("datetime") || t.contains("timestamp") || t.contains("date") || t.contains("time")) {
            return "LocalDateTime";
        }
        if (t.contains("float") || t.contains("double") || t.contains("real") || t.contains("money")) {
            return "BigDecimal";
        }
        return "String";
    }

    /** Java 类型 → TypeScript 类型 */
    private String tsType(String javaType) {
        return switch (javaType) {
            case "Integer", "Long", "BigDecimal" -> "number";
            case "LocalDateTime" -> "string";
            default -> "string";
        };
    }

    private String getter(String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    /** Java 字符串字面量转义（@TableField 值中的引号） */
    private String escapeJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== 工具 ====================

    private String requireIdentifier(String identifier, String what) {
        if (StrUtil.isBlank(identifier) || !IDENTIFIER.matcher(identifier.trim()).matches()) {
            throw new BizException("非法的" + what + "：" + identifier);
        }
        return identifier.trim();
    }

    private Map<String, Object> lowerKeyMap(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((k, v) -> result.put(k == null ? "" : k.toLowerCase(Locale.ROOT), v));
        return result;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean startsWithAny(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SKIP_TABLE_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
