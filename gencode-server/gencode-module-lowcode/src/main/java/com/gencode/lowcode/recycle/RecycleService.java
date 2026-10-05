package com.gencode.lowcode.recycle;

import cn.hutool.core.util.StrUtil;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.common.result.PageResult;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.lowcode.list.engine.DialectPageUtil;
import com.gencode.lowcode.recycle.dto.RecycleItemVO;
import com.gencode.lowcode.recycle.dto.RecycleTypeVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据回收站服务。
 *
 * 实现要点：lc_form/lc_list/lc_dashboard/lc_dataset 均在租户插件过滤范围内，
 * MyBatis-Plus 的 BaseMapper/Wrapper 会自动拼 deleted 逻辑删除条件与 tenant_id 条件
 * （手写 SQL 再含 tenant_id 会被插件二次追加导致参数重复），因此本服务全程走
 * JdbcTemplate 直连执行——不经 MP 插件：
 * 1. 表名/列名全部来自静态白名单注册表（type→table），杜绝 SQL 注入；
 * 2. 查询/恢复/物理删除的 SQL 显式携带 tenant_id = ?（TenantContext）与 deleted 条件；
 * 3. 表名额外经 information_schema 校验存在性（非 MySQL 系方言校验失败时按白名单继续）。
 */
@Service
@RequiredArgsConstructor
public class RecycleService {

    private static final Logger log = LoggerFactory.getLogger(RecycleService.class);

    /** 回收站类型注册表（type → 表/标签/可检索列），静态白名单，顺序即返回顺序 */
    private static final Map<String, RecycleType> TYPES = new LinkedHashMap<>();

    static {
        register("lc_form", "表单", "lc_form");
        register("lc_list", "列表", "lc_list");
        register("lc_dashboard", "大屏", "lc_dashboard");
        register("lc_dataset", "数据集", "lc_dataset");
    }

    private static void register(String type, String label, String table) {
        TYPES.put(type, new RecycleType(type, label, table, List.of("name", "code")));
    }

    private final JdbcTemplate jdbcTemplate;

    /** 数据库方言（分页包装用，与列表引擎一致） */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    /** 支持的回收站类型列表 */
    public List<RecycleTypeVO> types() {
        return TYPES.values().stream()
                .map(meta -> new RecycleTypeVO(meta.type(), meta.label()))
                .toList();
    }

    /** 已删除行分页（deleted=1，keyword 对 name/code 模糊，createTime 倒序） */
    public PageResult<RecycleItemVO> page(String type, String keyword, Integer pageNum, Integer pageSize) {
        RecycleType meta = requireType(type);
        checkTableExists(meta.table());
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 10 : pageSize;
        long start = (long) (pn - 1) * ps;

        List<String> conditions = new ArrayList<>(List.of("deleted = 1", "tenant_id = ?"));
        List<Object> args = new ArrayList<>(List.of(currentTenant()));
        if (StrUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(" + String.join(" OR ",
                    meta.nameColumns().stream().map(col -> col + " LIKE ?").toList()) + ")");
            for (int i = 0; i < meta.nameColumns().size(); i++) {
                args.add(like);
            }
        }
        String whereSql = " WHERE " + String.join(" AND ", conditions);
        String baseSql = "SELECT id, " + String.join(", ", meta.nameColumns()) + ", create_time FROM "
                + meta.table() + whereSql + " ORDER BY create_time DESC";

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + meta.table() + whereSql, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        String pageSql = DialectPageUtil.wrap(baseSql, dbType, start, ps, pageArgs);
        List<RecycleItemVO> rows = jdbcTemplate.query(pageSql, pageArgs.toArray(), (rs, rowNum) -> {
            RecycleItemVO vo = new RecycleItemVO();
            vo.setId(rs.getLong("id"));
            for (String col : meta.nameColumns()) {
                try {
                    String value = rs.getString(col);
                    if (StrUtil.isNotBlank(value)) {
                        vo.setName(value);
                        break;
                    }
                } catch (Exception ignore) {
                    // 该类型没有此名称列则顺延取下一列
                }
            }
            Timestamp createTime = rs.getTimestamp("create_time");
            vo.setCreateTime(createTime == null ? null : createTime.toLocalDateTime());
            return vo;
        });
        return PageResult.of(rows, total == null ? 0 : total);
    }

    /** 恢复：deleted 置 0 */
    public void restore(String type, Long id) {
        RecycleType meta = requireType(type);
        checkTableExists(meta.table());
        int rows = jdbcTemplate.update(
                "UPDATE " + meta.table() + " SET deleted = 0 WHERE id = ? AND tenant_id = ? AND deleted = 1",
                id, currentTenant());
        if (rows == 0) {
            throw new BizException("记录不存在或不在回收站中");
        }
    }

    /** 彻底删除：物理 DELETE（仅回收站中的行） */
    public void purge(String type, Long id) {
        RecycleType meta = requireType(type);
        checkTableExists(meta.table());
        int rows = jdbcTemplate.update(
                "DELETE FROM " + meta.table() + " WHERE id = ? AND tenant_id = ? AND deleted = 1",
                id, currentTenant());
        if (rows == 0) {
            throw new BizException("记录不存在或不在回收站中");
        }
    }

    // ==================== 私有方法 ====================

    /** 类型校验（白名单） */
    private RecycleType requireType(String type) {
        if (StrUtil.isBlank(type)) {
            throw new BizException("回收站类型不能为空");
        }
        RecycleType meta = TYPES.get(type);
        if (meta == null) {
            throw new BizException("不支持的回收站类型：" + type);
        }
        return meta;
    }

    /**
     * information_schema 校验目标表在当前库中存在（白名单之外的兜底防线）；
     * 非 MySQL 系方言不支持该查询时仅告警，按白名单继续执行
     */
    private void checkTableExists(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            if (count == null || count == 0) {
                throw new BizException("数据表不存在：" + table);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("校验回收站数据表 [{}] 存在性失败，按白名单继续执行", table, e);
        }
    }

    /** 当前租户（回收站 SQL 不经租户插件，租户条件手动携带） */
    private String currentTenant() {
        String tenantId = TenantContext.getTenantId();
        return StrUtil.isBlank(tenantId) ? Constants.DEFAULT_TENANT : tenantId;
    }

    /** 回收站类型元数据（record：白名单静态数据，不可变） */
    private record RecycleType(String type, String label, String table, List<String> nameColumns) {
    }
}
