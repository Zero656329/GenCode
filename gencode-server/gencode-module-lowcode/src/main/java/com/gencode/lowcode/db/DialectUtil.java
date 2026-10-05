package com.gencode.lowcode.db;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Set;

/**
 * SQL 方言工具：常用保留字清单与标识符转义（可视化建表与代码生成共用）
 *
 * <p>转义规则（命中保留字时才包裹，普通标识符保持原样输出，保证与
 * MyBatis-Plus 驼峰转下划线生成的非引用 SQL 一致）：
 * MySQL 用反引号；Oracle / PostgreSQL / 达梦 用双引号（Oracle/达梦 存储为大写，
 * 与非引用标识符折叠行为一致）；SQLServer 用方括号。</p>
 */
public final class DialectUtil {

    /**
     * 常用保留字（小写）：建表/生成代码时命中即转义。
     * status、name 等各库均非严格保留字，不转义，避免 SQL 噪音。
     */
    public static final Set<String> RESERVED_WORDS = Set.of(
            // 任务内置清单
            "order", "user", "desc", "asc", "group", "size", "type", "comment", "value",
            "key", "data", "level", "mode", "sort",
            // 五方言通用高严格度补充
            "column", "table", "index", "default", "check", "unique", "select", "where",
            "from", "usage", "number", "date", "time", "timestamp", "action", "language",
            "domain", "position", "options");

    /** Java 关键字（列名撞关键字时字段名加 Field 后缀规避） */
    public static final Set<String> JAVA_KEYWORDS = Set.of(
            "class", "package", "import", "private", "public", "protected", "static", "final",
            "void", "new", "return", "this", "super", "try", "catch", "finally", "throw",
            "throws", "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "instanceof", "interface", "enum", "implements", "extends", "native",
            "transient", "volatile", "synchronized", "assert", "const", "goto", "strictfp",
            "boolean", "byte", "char", "short", "int", "long", "float", "double", "record",
            "yield", "sealed", "permits");

    private DialectUtil() {
    }

    /** 是否保留字（大小写不敏感） */
    public static boolean isReserved(String identifier) {
        return identifier != null && RESERVED_WORDS.contains(identifier.toLowerCase(Locale.ROOT));
    }

    /** 是否 Java 关键字（大小写不敏感） */
    public static boolean isJavaKeyword(String identifier) {
        return identifier != null && JAVA_KEYWORDS.contains(identifier.toLowerCase(Locale.ROOT));
    }

    /**
     * 按方言转义标识符：MySQL 反引号、Oracle/PG/达梦 双引号（Oracle/达梦 转大写）、
     * SQLServer 方括号；未知方言按 PG（双引号，保持原大小写）处理
     */
    public static String quote(String identifier, String dbType) {
        return switch (normalizeDbType(dbType)) {
            case "mysql", "mariadb" -> "`" + identifier + "`";
            case "sqlserver" -> "[" + identifier + "]";
            case "oracle", "oracle11g", "dm" -> "\"" + identifier.toUpperCase(Locale.ROOT) + "\"";
            default -> "\"" + identifier + "\"";
        };
    }

    /** 方言归一化：空按 mysql，其余转小写 */
    public static String normalizeDbType(String dbType) {
        return StrUtil.isBlank(dbType) ? "mysql" : dbType.trim().toLowerCase(Locale.ROOT);
    }
}
