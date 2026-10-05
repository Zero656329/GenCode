package com.gencode.system.dashboard.controller;

import com.gencode.common.result.R;
import com.gencode.system.dashboard.service.DashboardService;
import com.gencode.system.dashboard.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public R<DashboardVO> stats() {
        return R.ok(dashboardService.stats());
    }
}
