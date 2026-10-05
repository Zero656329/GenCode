package com.gencode.framework.operlog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 写操作方法上，由 OperLogAspect 异步落库 sys_oper_log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块名称，如：用户管理 */
    String module() default "";

    /** 业务类型：INSERT / UPDATE / DELETE / EXPORT 等 */
    String businessType() default "OTHER";
}
