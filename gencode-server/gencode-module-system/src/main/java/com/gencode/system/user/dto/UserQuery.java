package com.gencode.system.user.dto;

import com.gencode.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 匹配 username/nickname/phone */
    private String keyword;
    private Integer status;
    /** 部门 ID（含子部门） */
    private Long deptId;
}
