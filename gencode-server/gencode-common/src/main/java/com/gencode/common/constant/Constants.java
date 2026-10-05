package com.gencode.common.constant;

/**
 * 平台通用常量
 */
public final class Constants {

    private Constants() {
    }

    /** 默认（系统）租户编号 */
    public static final String DEFAULT_TENANT = "000000";

    /** 内置超级管理员用户 ID */
    public static final long ADMIN_USER_ID = 1L;

    /** 内置超级管理员角色 KEY */
    public static final String ROLE_SUPER_ADMIN = "super_admin";

    /** 超级管理员权限通配符 */
    public static final String PERM_ALL = "*:*:*";

    /** 状态：正常/启用 */
    public static final Integer STATUS_NORMAL = 0;

    /** 状态：停用 */
    public static final Integer STATUS_DISABLE = 1;
}
