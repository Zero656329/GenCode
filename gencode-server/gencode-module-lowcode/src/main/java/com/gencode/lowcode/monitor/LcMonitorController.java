package com.gencode.lowcode.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gencode.common.result.R;
import com.gencode.lowcode.monitor.dto.MonitorServerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控（/api/lc/monitor）
 */
@RestController
@RequestMapping("/lc/monitor")
@RequiredArgsConstructor
public class LcMonitorController {

    private final MonitorService monitorService;

    /** 服务器监控指标（os/memory/jvm/disk/hikari） */
    @SaCheckPermission("lc:monitor:list")
    @GetMapping("/server")
    public R<MonitorServerVO> server() {
        return R.ok(monitorService.server());
    }
}
