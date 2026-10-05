package com.gencode.framework.security.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证鉴权数据源（原生 SQL，表多为租户忽略表）
 */
public interface AuthPermMapper {

    /** 查用户角色 key 列表 */
    @Select("SELECT r.role_key FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.status = 0 AND r.deleted = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 查用户角色关联的菜单权限标识合集（去重由 SQL DISTINCT 完成） */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "JOIN sys_user_role ur ON ur.role_id = rm.role_id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND m.status = 0 AND r.status = 0 "
            + "AND m.deleted = 0 AND r.deleted = 0 "
            + "AND m.perms IS NOT NULL AND m.perms != ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
