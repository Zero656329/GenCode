package com.gencode.system.dept.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gencode.common.constant.Constants;
import com.gencode.common.exception.BizException;
import com.gencode.system.dept.dto.DeptBody;
import com.gencode.system.dept.dto.DeptTreeNode;
import com.gencode.system.dept.entity.SysDept;
import com.gencode.system.dept.mapper.SysDeptMapper;
import com.gencode.system.dept.service.DeptService;
import com.gencode.system.user.entity.SysUser;
import com.gencode.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门服务实现：ancestors 由后端维护（父的 ancestors+","+parentId，根为 "0"）
 */
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<DeptTreeNode> tree(String name, Integer status) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysDept::getOrderNo).orderByAsc(SysDept::getId);
        List<SysDept> filtered = deptMapper.selectList(wrapper).stream()
                .filter(d -> StrUtil.isBlank(name) || StrUtil.contains(d.getName(), name))
                .filter(d -> status == null || status.equals(d.getStatus()))
                .toList();
        Map<Long, DeptTreeNode> nodeMap = new HashMap<>();
        for (SysDept dept : filtered) {
            nodeMap.put(dept.getId(), BeanUtil.toBean(dept, DeptTreeNode.class));
        }
        List<DeptTreeNode> roots = new ArrayList<>();
        for (DeptTreeNode node : nodeMap.values()) {
            DeptTreeNode parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent != null && parent != node) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public void create(DeptBody body) {
        Long parentId = body.getParentId() == null ? 0L : body.getParentId();
        String ancestors = buildAncestors(parentId);
        SysDept dept = new SysDept();
        BeanUtil.copyProperties(body, dept);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        if (dept.getOrderNo() == null) {
            dept.setOrderNo(0);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(Constants.STATUS_NORMAL);
        }
        deptMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeptBody body) {
        if (body.getId() == null) {
            throw new BizException("部门ID不能为空");
        }
        SysDept old = deptMapper.selectById(body.getId());
        if (old == null) {
            throw new BizException("部门不存在");
        }
        Long parentId = body.getParentId() == null ? 0L : body.getParentId();
        if (parentId.equals(body.getId())) {
            throw new BizException("父部门不能是自己");
        }
        String ancestors;
        if (parentId == 0L) {
            ancestors = "0";
        } else {
            SysDept parent = deptMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException("父部门不存在");
            }
            // 父部门的祖级链包含自身，说明选择了自己的子孙
            if (StrUtil.isNotBlank(parent.getAncestors())
                    && Arrays.asList(parent.getAncestors().split(",")).contains(body.getId().toString())) {
                throw new BizException("父部门不能选择自己的子部门");
            }
            ancestors = parent.getAncestors() + "," + parentId;
        }

        SysDept dept = new SysDept();
        BeanUtil.copyProperties(body, dept);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        deptMapper.updateById(dept);

        // 父级变化时级联修正所有子孙的 ancestors
        if (!ancestors.equals(old.getAncestors())) {
            String oldPrefix = old.getAncestors() + "," + body.getId();
            String newPrefix = ancestors + "," + body.getId();
            List<SysDept> descendants = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .and(w -> w.eq(SysDept::getAncestors, oldPrefix)
                            .or().likeRight(SysDept::getAncestors, oldPrefix + ",")));
            for (SysDept child : descendants) {
                SysDept update = new SysDept();
                update.setId(child.getId());
                update.setAncestors(child.getAncestors().startsWith(oldPrefix + ",")
                        ? newPrefix + child.getAncestors().substring(oldPrefix.length() + 1)
                        : newPrefix);
                deptMapper.updateById(update);
            }
        }
    }

    @Override
    public void delete(Long id) {
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (childCount > 0) {
            throw new BizException("存在子部门，不允许删除");
        }
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (userCount > 0) {
            throw new BizException("部门下存在用户，不允许删除");
        }
        deptMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 私有方法

    private String buildAncestors(Long parentId) {
        if (parentId == 0L) {
            return "0";
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException("父部门不存在");
        }
        return parent.getAncestors() + "," + parentId;
    }
}
