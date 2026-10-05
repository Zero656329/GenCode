package com.gencode.lowcode.monitor;

import com.gencode.lowcode.monitor.dto.MonitorServerVO;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器监控服务：纯 JMX 实现（java.lang.management + com.sun.management.OperatingSystemMXBean），无新依赖。
 * Hikari 连接池指标从平台主库 DataSource 读取，任何一步失败仅置空对应字段，不影响整体返回。
 */
@Service
@RequiredArgsConstructor
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private static final double BYTES_PER_MB = 1048576.0;
    private static final double BYTES_PER_GB = 1073741824.0;

    /** 平台主库数据源（Hikari 连接池指标来源） */
    private final DataSource dataSource;

    /** 汇总服务器监控指标 */
    public MonitorServerVO server() {
        MonitorServerVO vo = new MonitorServerVO();
        vo.setOs(osInfo());
        vo.setMemory(memoryInfo());
        vo.setJvm(jvmInfo());
        vo.setDisk(diskInfo());
        vo.setHikari(hikariInfo());
        return vo;
    }

    /** 操作系统：名称+版本、架构、核数、系统 CPU 负载百分比（systemCpuLoad×100，取不到为 null） */
    private MonitorServerVO.OsInfo osInfo() {
        MonitorServerVO.OsInfo os = new MonitorServerVO.OsInfo();
        os.setName(System.getProperty("os.name") + " " + System.getProperty("os.version"));
        os.setArch(System.getProperty("os.arch"));
        os.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
        try {
            com.sun.management.OperatingSystemMXBean osBean = sunOsBean();
            if (osBean != null) {
                double cpuLoad = osBean.getCpuLoad();
                os.setSystemLoadPercent(cpuLoad < 0 ? null : round1(cpuLoad * 100));
            }
        } catch (Exception e) {
            log.warn("读取系统 CPU 负载失败", e);
        }
        return os;
    }

    /** 物理内存（MB，保留 1 位） */
    private MonitorServerVO.MemoryInfo memoryInfo() {
        MonitorServerVO.MemoryInfo memory = new MonitorServerVO.MemoryInfo();
        try {
            com.sun.management.OperatingSystemMXBean osBean = sunOsBean();
            if (osBean != null) {
                memory.setTotalMb(round1(osBean.getTotalMemorySize() / BYTES_PER_MB));
                memory.setFreeMb(round1(osBean.getFreeMemorySize() / BYTES_PER_MB));
            }
        } catch (Exception e) {
            log.warn("读取物理内存信息失败", e);
        }
        return memory;
    }

    /** JVM：堆上限/已用（MB）、线程数、运行时长（分钟） */
    private MonitorServerVO.JvmInfo jvmInfo() {
        MonitorServerVO.JvmInfo jvm = new MonitorServerVO.JvmInfo();
        Runtime runtime = Runtime.getRuntime();
        jvm.setMaxMb(round1(runtime.maxMemory() / BYTES_PER_MB));
        jvm.setUsedMb(round1((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB));
        jvm.setThreads(ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.setUptimeMinutes(ManagementFactory.getRuntimeMXBean().getUptime() / 60000);
        return jvm;
    }

    /** 磁盘：File.listRoots() 每个根目录的总容量/剩余（GB，保留 1 位） */
    private List<MonitorServerVO.DiskInfo> diskInfo() {
        List<MonitorServerVO.DiskInfo> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            try {
                MonitorServerVO.DiskInfo disk = new MonitorServerVO.DiskInfo();
                disk.setDir(root.getPath());
                disk.setTotalGb(round1(root.getTotalSpace() / BYTES_PER_GB));
                disk.setFreeGb(round1(root.getFreeSpace() / BYTES_PER_GB));
                disks.add(disk);
            } catch (Exception e) {
                log.warn("读取磁盘信息失败：{}", root.getPath(), e);
            }
        }
        return disks;
    }

    /**
     * Hikari 连接池：平台主库 DataSource instanceof HikariDataSource 时读取 active/idle，
     * 连接池未初始化或读取失败时字段保持 null
     */
    private MonitorServerVO.HikariInfo hikariInfo() {
        MonitorServerVO.HikariInfo hikari = new MonitorServerVO.HikariInfo();
        try {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
                if (pool != null) {
                    hikari.setActive(pool.getActiveConnections());
                    hikari.setIdle(pool.getIdleConnections());
                }
            }
        } catch (Exception e) {
            log.warn("读取 Hikari 连接池指标失败", e);
        }
        return hikari;
    }

    /** com.sun.management.OperatingSystemMXBean（HotSpot JDK 均可强转） */
    private com.sun.management.OperatingSystemMXBean sunOsBean() {
        return (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    /** 保留 1 位小数 */
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
