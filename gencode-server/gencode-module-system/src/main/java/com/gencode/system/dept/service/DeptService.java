package com.gencode.system.dept.service;

import com.gencode.system.dept.dto.DeptBody;
import com.gencode.system.dept.dto.DeptTreeNode;
import com.gencode.system.dept.entity.SysDept;

import java.util.List;

/**
 * 部门服务
 */
public interface DeptService {

    /** 完整部门树（name 模糊、status 过滤） */
    List<DeptTreeNode> tree(String name, Integer status);

    void create(DeptBody body);

    void update(DeptBody body);

    void delete(Long id);
}
