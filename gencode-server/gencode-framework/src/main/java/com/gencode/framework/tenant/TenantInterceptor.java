package com.gencode.framework.tenant;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器：从 Sa-Token 会话读取当前登录用户租户，放入 TenantContext；
 * afterCompletion 必须清理，防止线程复用导致租户串号。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            Object tenantId = StpUtil.getSession().get("tenantId");
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId.toString());
            }
        } catch (Exception ignored) {
            // 未登录场景（登录/验证码等）不设置，由租户插件兜底 '000000'
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
