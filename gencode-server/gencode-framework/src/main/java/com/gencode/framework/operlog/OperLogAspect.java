package com.gencode.framework.operlog;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gencode.common.constant.Constants;
import com.gencode.framework.tenant.TenantContext;
import com.gencode.framework.util.IpUtil;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：@Around 拦截带 @OperLog 的方法，请求线程内组装日志事件，异步落库
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 参数最大记录长度 */
    private static final int MAX_PARAM_LENGTH = 2000;

    private final ObjectProvider<OperLogSaver> saverProvider;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        OperLogEvent event = new OperLogEvent();
        event.setTitle(operLog.module());
        event.setBusinessType(operLog.businessType());
        event.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        fillRequestInfo(event, joinPoint.getArgs());
        fillUserInfo(event);
        event.setTenantId(StrUtil.blankToDefault(TenantContext.getTenantId(), Constants.DEFAULT_TENANT));

        try {
            Object result = joinPoint.proceed();
            event.setStatus(0);
            return result;
        } catch (Throwable e) {
            event.setStatus(1);
            event.setErrorMsg(StrUtil.maxLength(e.getMessage(), MAX_PARAM_LENGTH));
            throw e;
        } finally {
            event.setCostMs(System.currentTimeMillis() - start);
            event.setOperTime(LocalDateTime.now());
            OperLogSaver saver = saverProvider.getIfAvailable();
            if (saver != null) {
                try {
                    saver.save(event);
                } catch (Exception ex) {
                    log.error("提交操作日志失败", ex);
                }
            }
        }
    }

    private void fillRequestInfo(OperLogEvent event, Object[] args) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                event.setRequestMethod(request.getMethod());
                event.setOperUrl(request.getRequestURI());
                event.setIp(IpUtil.getClientIp(request));
            }
            List<Object> serializable = new ArrayList<>();
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse || arg instanceof MultipartFile) {
                    continue;
                }
                serializable.add(arg);
            }
            // 简单脱敏 password 字段
            String json = JSONUtil.toJsonStr(serializable)
                    .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*\",?", "$1***\",");
            event.setOperParam(StrUtil.maxLength(json, MAX_PARAM_LENGTH));
        } catch (Exception ignored) {
            // 日志采集失败不影响业务
        }
    }

    private void fillUserInfo(OperLogEvent event) {
        try {
            event.setOperId(StpUtil.getLoginIdAsLong());
        } catch (Exception ignored) {
        }
        try {
            Object username = StpUtil.getSession().get("username");
            event.setOperName(username == null ? "" : username.toString());
        } catch (Exception ignored) {
        }
    }
}
