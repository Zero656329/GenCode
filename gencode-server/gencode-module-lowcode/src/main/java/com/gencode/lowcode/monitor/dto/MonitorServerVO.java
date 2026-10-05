package com.gencode.lowcode.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 服务器监控指标（纯 JMX 实现，hikari 读取失败时字段为 null）
 */
@Data
public class MonitorServerVO {

    /** 操作系统信息 */
    private OsInfo os;
    /** 物理内存（MB） */
    private MemoryInfo memory;
    /** JVM 信息 */
    private JvmInfo jvm;
    /** 磁盘列表（每根目录一项） */
    private List<DiskInfo> disk;
    /** Hikari 连接池指标 */
    private HikariInfo hikari;

    /** 操作系统：名称+版本 / 架构 / 核数 / 系统 CPU 负载百分比（取不到为 null） */
    @Data
    public static class OsInfo {
        private String name;
        private String arch;
        private Integer availableProcessors;
        private Double systemLoadPercent;
    }

    /** 物理内存（MB，保留 1 位） */
    @Data
    public static class MemoryInfo {
        private Double totalMb;
        private Double freeMb;
    }

    /** JVM：堆上限/已用（MB）、线程数、运行时长（分钟） */
    @Data
    public static class JvmInfo {
        private Double maxMb;
        private Double usedMb;
        private Integer threads;
        private Long uptimeMinutes;
    }

    /** 磁盘（GB，保留 1 位） */
    @Data
    public static class DiskInfo {
        private String dir;
        private Double totalGb;
        private Double freeGb;
    }

    /** Hikari 连接池：活跃/空闲连接数 */
    @Data
    public static class HikariInfo {
        private Integer active;
        private Integer idle;
    }
}
