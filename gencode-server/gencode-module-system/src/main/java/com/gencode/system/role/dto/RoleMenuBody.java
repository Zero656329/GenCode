package com.gencode.system.role.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色菜单分配请求体
 */
@Data
public class RoleMenuBody {

    /** 菜单 ID 集合（前端传字符串数组，Jackson 自动转 Long） */
    private List<Long> menuIds;
}
