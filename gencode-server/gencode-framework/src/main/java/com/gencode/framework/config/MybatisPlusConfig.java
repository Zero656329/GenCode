package com.gencode.framework.config;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gencode.common.constant.Constants;
import com.gencode.framework.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis-Plus 插件配置：租户插件在前、分页插件在后
 */
@Configuration
public class MybatisPlusConfig {

    /** 数据库方言（gencode.db-type：mysql / oracle / postgresql / dm / kingbase_es...），决定分页语句生成方式 */
    @Value("${gencode.db-type:mysql}")
    private String dbType;

    /** 忽略租户过滤的表（小写精确匹配） */
    private static final List<String> IGNORE_TABLES = List.of(
            "sys_menu", "sys_role_menu", "sys_user_role", "sys_tenant", "sys_oper_log");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 租户插件（顺序在前）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = TenantContext.getTenantId();
                return new StringValue(StrUtil.isBlank(tenantId) ? Constants.DEFAULT_TENANT : tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                String table = tableName == null ? "" : tableName.toLowerCase();
                return IGNORE_TABLES.contains(table) || table.startsWith("act_") || table.startsWith("flw_");
            }
        }));
        // 分页插件（按配置的方言生成分页语句）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.getDbType(dbType.toUpperCase())));
        return interceptor;
    }
}
