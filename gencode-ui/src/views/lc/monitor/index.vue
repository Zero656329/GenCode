<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getServerMonitor, type DiskInfo, type MonitorInfo } from '@/api/lc-monitor'

/**
 * 服务监控（/lc/monitor）
 * GET /lc/monitor/server：CPU / 物理内存 / JVM / 磁盘 / Hikari 连接池。
 * 手动刷新 + 30s 自动刷新（onUnmounted 清理定时器）。
 */

const REFRESH_INTERVAL_MS = 30_000

const loading = ref(false)

const info = ref<MonitorInfo>({
  os: { name: '-', arch: '-', availableProcessors: 0, systemLoadPercent: 0 },
  memory: { totalMb: 0, usedMb: 0 },
  jvm: { maxMb: 0, usedMb: 0, threads: 0, uptimeMinutes: 0 },
  disk: [],
  hikari: { active: 0, idle: 0 }
})

function clampPercent(v: number | null | undefined): number {
  if (!v || v <= 0) return 0
  return Math.min(100, Math.round(v))
}

function pct(used: number, total: number): number {
  if (!total || total <= 0) return 0
  return clampPercent((used / total) * 100)
}

function round1(n: number): number {
  return Math.round(n * 10) / 10
}

const cpuPercent = computed(() => clampPercent(info.value.os.systemLoadPercent))
const memPercent = computed(() => pct(info.value.memory.usedMb, info.value.memory.totalMb))
const jvmPercent = computed(() => pct(info.value.jvm.usedMb, info.value.jvm.maxMb))

function diskPercent(d: DiskInfo): number {
  return pct(d.totalGb - d.freeGb, d.totalGb)
}

/** 进度条文案：已用 / 总量 */
function diskFormat(d: DiskInfo): () => string {
  const text = `${round1(d.totalGb - d.freeGb)}GB / ${round1(d.totalGb)}GB`
  return () => text
}

function progressStatus(p: number): 'normal' | 'exception' {
  return p >= 90 ? 'exception' : 'normal'
}

async function loadInfo(): Promise<void> {
  loading.value = true
  try {
    info.value = await getServerMonitor()
  } catch {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

let timer: number | undefined

onMounted(() => {
  loadInfo()
  timer = window.setInterval(() => {
    loadInfo()
  }, REFRESH_INTERVAL_MS)
})

onUnmounted(() => {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
})
</script>

<template>
  <div class="monitor-page">
    <div class="toolbar">
      <span class="monitor-tip">每 30 秒自动刷新</span>
      <a-button type="primary" :loading="loading" @click="loadInfo">
        <template #icon><reload-outlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 顶部统计卡 -->
    <a-row :gutter="16">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card">
          <a-statistic title="CPU 使用率" :value="cpuPercent" suffix="%" />
          <a-progress :percent="cpuPercent" size="small" :status="progressStatus(cpuPercent)" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card">
          <a-statistic title="内存使用" :value="info.memory.usedMb" suffix="MB" />
          <a-progress
            :percent="memPercent"
            size="small"
            :status="progressStatus(memPercent)"
            :format="() => `共 ${info.memory.totalMb} MB`"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card">
          <a-statistic title="JVM 内存" :value="info.jvm.usedMb" suffix="MB" />
          <a-progress
            :percent="jvmPercent"
            size="small"
            :status="progressStatus(jvmPercent)"
            :format="() => `堆上限 ${info.jvm.maxMb} MB`"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card">
          <a-statistic title="线程数" :value="info.jvm.threads" />
          <div class="stat-sub">CPU 核数 {{ info.os.availableProcessors }}</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 磁盘 -->
    <a-card title="磁盘使用" class="block">
      <template v-if="info.disk && info.disk.length">
        <div v-for="d in info.disk" :key="d.dir" class="disk-row">
          <span class="disk-dir">{{ d.dir }}</span>
          <a-progress
            class="disk-progress"
            :percent="diskPercent(d)"
            :status="progressStatus(diskPercent(d))"
            :format="diskFormat(d)"
          />
        </div>
      </template>
      <a-empty v-else description="暂无磁盘信息" />
    </a-card>

    <!-- 系统信息 -->
    <a-card title="系统信息" class="block">
      <a-descriptions bordered :column="{ xs: 1, sm: 2, lg: 3 }" size="middle">
        <a-descriptions-item label="操作系统">{{ info.os.name || '-' }}</a-descriptions-item>
        <a-descriptions-item label="架构">{{ info.os.arch || '-' }}</a-descriptions-item>
        <a-descriptions-item label="CPU 核数">{{ info.os.availableProcessors }}</a-descriptions-item>
        <a-descriptions-item label="运行时长">{{ info.jvm.uptimeMinutes }} 分钟</a-descriptions-item>
        <a-descriptions-item label="JVM 堆上限">{{ info.jvm.maxMb }} MB</a-descriptions-item>
        <a-descriptions-item label="连接池（活跃/空闲）">
          {{ info.hikari.active }} / {{ info.hikari.idle }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<style scoped>
.monitor-tip {
  color: #999;
  font-size: 13px;
}

.stat-card .ant-statistic {
  margin-bottom: 8px;
}

.stat-sub {
  color: #999;
  font-size: 13px;
  margin-top: 4px;
}

.block {
  margin-top: 16px;
}

.disk-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.disk-row:last-child {
  margin-bottom: 0;
}

.disk-dir {
  width: 180px;
  flex-shrink: 0;
  word-break: break-all;
}

.disk-progress {
  flex: 1;
  margin-bottom: 0;
}
</style>
