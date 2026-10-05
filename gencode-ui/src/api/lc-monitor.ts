import { get } from '@/utils/request'

/**
 * 服务监控（契约见 docs/api-contract.md「集成 /lc（六期 M6：HTTP 接口 / 监控 / 回收站）」）
 * 服务端基于 JMX / OperatingSystemMXBean 实现，无新依赖。
 */

/** 磁盘信息 */
export interface DiskInfo {
  dir: string
  totalGb: number
  freeGb: number
}

/** 操作系统信息 */
export interface MonitorOs {
  name: string
  arch: string
  availableProcessors: number
  systemLoadPercent: number
}

/** 物理内存（MB） */
export interface MonitorMemory {
  totalMb: number
  usedMb: number
}

/** JVM 信息（内存 MB） */
export interface MonitorJvm {
  maxMb: number
  usedMb: number
  threads: number
  uptimeMinutes: number
}

/** Hikari 连接池 */
export interface MonitorHikari {
  active: number
  idle: number
}

/** GET /lc/monitor/server 返回结构 */
export interface MonitorInfo {
  os: MonitorOs
  memory: MonitorMemory
  jvm: MonitorJvm
  disk: DiskInfo[]
  hikari: MonitorHikari
}

export function getServerMonitor(): Promise<MonitorInfo> {
  return get<MonitorInfo>('/lc/monitor/server')
}
