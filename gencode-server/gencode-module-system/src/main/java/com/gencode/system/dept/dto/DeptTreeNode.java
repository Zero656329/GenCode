package com.gencode.system.dept.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点
 */
@Data
public class DeptTreeNode {

    private Long id;
    private Long parentId;
    private String ancestors;
    private String name;
    private Integer orderNo;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private List<DeptTreeNode> children = new ArrayList<>();
}
