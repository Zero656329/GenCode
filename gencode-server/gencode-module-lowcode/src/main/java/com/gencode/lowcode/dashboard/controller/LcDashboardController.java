package com.gencode.lowcode.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.PageResult;
import com.gencode.common.result.R;
import com.gencode.framework.operlog.OperLog;
import com.gencode.lowcode.dashboard.dto.DashboardCreateBody;
import com.gencode.lowcode.dashboard.dto.DashboardPublishedVO;
import com.gencode.lowcode.dashboard.dto.DashboardQuery;
import com.gencode.lowcode.dashboard.dto.DashboardSaveBody;
import com.gencode.lowcode.dashboard.entity.LcDashboard;
import com.gencode.lowcode.dashboard.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏管理（/api/lc/dashboard）
 */
@RestController
@RequestMapping("/lc/dashboard")
@RequiredArgsConstructor
public class LcDashboardController {

    private final DashboardService dashboardService;

    /** 分页（keyword 匹配 code/name，status 过滤） */
    @SaCheckPermission("lc:dashboard:list")
    @GetMapping("/page")
    public R<PageResult<LcDashboard>> page(DashboardQuery query) {
        return R.ok(dashboardService.page(query));
    }

    /** 详情（含 layoutJson） */
    @SaCheckPermission("lc:dashboard:list")
    @GetMapping("/{id}")
    public R<LcDashboard> detail(@PathVariable Long id) {
        return R.ok(dashboardService.detail(id));
    }

    /** 新建草稿（status=0，version=0） */
    @OperLog(module = "大屏设计", businessType = "INSERT")
    @SaCheckPermission("lc:dashboard:add")
    @PostMapping
    public R<Void> create(@Valid @RequestBody DashboardCreateBody body) {
        dashboardService.create(body);
        return R.ok();
    }

    /** 保存布局（code 不可修改） */
    @OperLog(module = "大屏设计", businessType = "UPDATE")
    @SaCheckPermission("lc:dashboard:edit")
    @PutMapping
    public R<Void> saveDesign(@Valid @RequestBody DashboardSaveBody body) {
        dashboardService.saveDesign(body);
        return R.ok();
    }

    /** 逻辑删除 */
    @OperLog(module = "大屏设计", businessType = "DELETE")
    @SaCheckPermission("lc:dashboard:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dashboardService.delete(id);
        return R.ok();
    }

    /** 发布：version+1，publishedSchema=当前 layoutJson 快照，status=1 */
    @OperLog(module = "大屏设计", businessType = "PUBLISH")
    @SaCheckPermission("lc:dashboard:publish")
    @PutMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        dashboardService.publish(id);
        return R.ok();
    }

    /** 启用/停用（1=已发布 2=停用 0=草稿；仅改状态不影响快照） */
    @OperLog(module = "大屏设计", businessType = "UPDATE")
    @SaCheckPermission("lc:dashboard:edit")
    @PutMapping("/{id}/status/{status}")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        dashboardService.updateStatus(id, status);
        return R.ok();
    }

    /** 运行时接口：按编码取已发布大屏（仅需登录，供大屏运行页渲染） */
    @GetMapping("/publish/{code}")
    public R<DashboardPublishedVO> getPublished(@PathVariable String code) {
        return R.ok(dashboardService.getPublished(code));
    }
}
