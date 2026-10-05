package com.gencode.lowcode.list.engine;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 方言分页工具：根据 gencode.db-type 生成翻页 SQL，分页参数一律置后追加
 */
public final class DialectPageUtil {

    private static final Logger log = LoggerFactory.getLogger(DialectPageUtil.class);

    private DialectPageUtil() {
    }

    /**
     * 分页包装：
     * mysql/mariadb 用 LIMIT ? OFFSET ?（分页参数置后：size, start）；
     * oracle 用 ROWNUM 三层包装（内层 ROWNUM &lt;= end，外层 rn &gt; start，参数：end, start）；
     * 其他方言按 mysql 处理并 log.warn
     *
     * @param sql     已含业务条件的查询 SQL
     * @param dbType  数据库方言（gencode.db-type）
     * @param start   起始行号（0 开始）
     * @param size    每页条数
     * @param params  绑定参数列表（分页参数追加到尾部）
     */
    public static String wrap(String sql, String dbType, long start, int size, List<Object> params) {
        String type = StrUtil.isBlank(dbType) ? "mysql" : dbType.trim().toLowerCase();
        switch (type) {
            case "oracle", "oracle11g" -> {
                String wrapped = "SELECT * FROM ( SELECT tmp_page_.*, ROWNUM rn_ FROM ( " + sql
                        + " ) tmp_page_ WHERE ROWNUM <= ? ) WHERE rn_ > ?";
                params.add(start + size);
                params.add(start);
                return wrapped;
            }
            case "mysql", "mariadb" -> {
                params.add(size);
                params.add(start);
                return sql + " LIMIT ? OFFSET ?";
            }
            default -> {
                log.warn("未知的数据库方言 [{}]，动态列表分页按 mysql（LIMIT/OFFSET）处理", dbType);
                params.add(size);
                params.add(start);
                return sql + " LIMIT ? OFFSET ?";
            }
        }
    }

    /** 总数包装：SELECT COUNT(*) FROM (原始SQL) t */
    public static String countSql(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") t";
    }
}
