<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import {
  getLcDashboardPublished,
  type DashboardChartType,
  type DashboardItem
} from '@/api/lc-dashboard'
import { getLcDatasetPublishData, type DatasetDataResult } from '@/api/lc-dataset'

/**
 * 大屏运行页（/app/dashboard/:code，菜单隐藏页）
 * GET /lc/dashboard/publish/{code} 取发布快照布局，深色背景 + ECharts 渲染；
 * 设计尺寸 1280x720，scale=min(w/1280, h/720) 等比缩放并绝对居中；
 * refreshSec>0 的图表按间隔重新取数刷新（onUnmounted 清理定时器）。
 */

const DESIGN_W = 1280
const DESIGN_H = 720

/** 运行项：附加稳定内部 id（仅用于 key/图表实例管理） */
type RenderItem = DashboardItem & { iid: string }

const PALETTE_COLORS = ['#1677ff', '#2fc25b', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96']

let iidSeq = 0
function genIid(): string {
  iidSeq += 1
  return `rt_${Date.now().toString(36)}_${iidSeq}`
}

function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max)
}

const route = useRoute()
const dashCode = String(route.params.code ?? '')

const loading = ref(true)
const loadError = ref('')
const dashName = ref('')
const version = ref(0)
const items = ref<RenderItem[]>([])

// ============ 缩放适配（等比 + 绝对居中） ============

const stageWrapRef = ref<HTMLDivElement | null>(null)
const scale = ref(1)

function updateScale(): void {
  const el = stageWrapRef.value
  if (!el) return
  scale.value = Math.max(0.1, Math.min(el.clientWidth / DESIGN_W, el.clientHeight / DESIGN_H))
}

// ============ 布局加载与规范化 ============

function normalizeItem(raw: Partial<DashboardItem>): RenderItem {
  const chartType: DashboardChartType =
    raw.chartType === 'bar' || raw.chartType === 'pie' ? raw.chartType : 'line'
  const num = (v: unknown, def: number): number => {
    const n = Number(v)
    return Number.isFinite(n) ? n : def
  }
  const w = clamp(Math.round(num(raw.w, 420)), 120, DESIGN_W)
  const h = clamp(Math.round(num(raw.h, 280)), 120, DESIGN_H)
  return {
    iid: genIid(),
    type: 'chart',
    chartType,
    datasetCode: typeof raw.datasetCode === 'string' ? raw.datasetCode : '',
    title: typeof raw.title === 'string' ? raw.title : '',
    xField: typeof raw.xField === 'string' ? raw.xField : '',
    yField: typeof raw.yField === 'string' ? raw.yField : '',
    seriesField: typeof raw.seriesField === 'string' && raw.seriesField ? raw.seriesField : undefined,
    x: clamp(Math.round(num(raw.x, 40)), 0, DESIGN_W - w),
    y: clamp(Math.round(num(raw.y, 80)), 0, DESIGN_H - h),
    w,
    h,
    refreshSec: Number(raw.refreshSec) > 0 ? Math.round(Number(raw.refreshSec)) : undefined
  }
}

async function loadDashboard(): Promise<void> {
  try {
    const pub = await getLcDashboardPublished(dashCode)
    dashName.value = pub.name || pub.code
    version.value = pub.version || 0
    let parsed: { items?: Array<Partial<DashboardItem>> } = {}
    try {
      parsed = JSON.parse(pub.layoutJson || '{}') as { items?: Array<Partial<DashboardItem>> }
    } catch {
      loadError.value = '大屏布局解析失败，请联系管理员'
      return
    }
    items.value = (parsed.items || []).map(normalizeItem)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '大屏加载失败，请稍后重试'
  } finally {
    loading.value = false
    await nextTick()
    updateScale()
    setupAllCharts()
  }
}

// ============ ECharts 渲染与定时刷新 ============

const chartEls = new Map<string, HTMLDivElement>()
const charts = new Map<string, echarts.ECharts>()
const timers = new Map<string, number>()

function setChartEl(iid: string, el: unknown): void {
  if (el) chartEls.set(iid, el as HTMLDivElement)
  else chartEls.delete(iid)
}

/** 大屏图表统一 option（与设计器一致）：line/bar 类目轴-值轴；pie 按 seriesField 分组统计 yField 合计，否则取前 10 行 */
function buildChartOption(item: DashboardItem, rows: Record<string, unknown>[]): echarts.EChartsOption {
  const textColor = 'rgba(255,255,255,0.85)'
  const splitColor = 'rgba(255,255,255,0.12)'
  const xKey = item.xField
  const yKey = item.yField
  const seriesKey = item.seriesField

  if (item.chartType === 'pie') {
    let data: Array<{ name: string; value: number }>
    if (seriesKey) {
      const agg = new Map<string, number>()
      for (const row of rows) {
        const k = String(row[seriesKey] ?? '-')
        const v = Number(row[yKey]) || 0
        agg.set(k, (agg.get(k) || 0) + v)
      }
      data = Array.from(agg.entries()).map(([k, v]) => ({ name: k, value: v }))
    } else {
      data = rows.slice(0, 10).map((row, i) => ({
        name: String(row[xKey] ?? `#${i + 1}`),
        value: Number(row[yKey]) || 0
      }))
    }
    return {
      color: PALETTE_COLORS,
      tooltip: { trigger: 'item' },
      legend: { bottom: 6, type: 'scroll', textStyle: { color: textColor, fontSize: 11 } },
      series: [
        { type: 'pie', radius: '58%', center: ['50%', '46%'], data, label: { color: textColor, fontSize: 11 } }
      ]
    }
  }

  return {
    color: PALETTE_COLORS,
    tooltip: { trigger: 'axis' },
    grid: { left: 12, right: 18, top: 30, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((row) => String(row[xKey] ?? '')),
      axisLabel: { color: textColor, fontSize: 11 },
      axisLine: { lineStyle: { color: splitColor } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: textColor, fontSize: 11 },
      splitLine: { lineStyle: { color: splitColor } }
    },
    series: [
      {
        name: item.title || yKey,
        type: item.chartType,
        smooth: item.chartType === 'line',
        barMaxWidth: 36,
        areaStyle: item.chartType === 'line' ? { opacity: 0.12 } : undefined,
        data: rows.map((row) => Number(row[yKey]) || 0)
      }
    ]
  }
}

function hintOption(text: string): echarts.EChartsOption {
  return {
    title: {
      text,
      left: 'center',
      top: 'middle',
      textStyle: { color: 'rgba(255,255,255,0.45)', fontSize: 13, fontWeight: 'normal' }
    }
  }
}

async function fetchData(item: RenderItem): Promise<void> {
  const chart = charts.get(item.iid)
  if (!chart) return
  if (!item.datasetCode || !item.xField || !item.yField) {
    chart.clear()
    chart.setOption(hintOption('图表配置不完整'))
    return
  }
  try {
    const res: DatasetDataResult = await getLcDatasetPublishData(item.datasetCode)
    if (!res.rows || !res.rows.length) {
      chart.clear()
      chart.setOption(hintOption('暂无数据'))
      return
    }
    chart.setOption(buildChartOption(item, res.rows), true)
  } catch {
    // 拦截器已统一提示；定时刷新会自动重试
  }
}

function setupAllCharts(): void {
  for (const item of items.value) {
    const el = chartEls.get(item.iid)
    if (!el) continue
    const chart = echarts.init(el)
    charts.set(item.iid, chart)
    void fetchData(item)
    if (item.refreshSec && item.refreshSec > 0) {
      const timer = window.setInterval(() => {
        void fetchData(item)
      }, item.refreshSec * 1000)
      timers.set(item.iid, timer)
    }
  }
}

function clearTimers(): void {
  timers.forEach((timer) => window.clearInterval(timer))
  timers.clear()
}

const titleText = computed(() => dashName.value || '数据大屏')

function onResize(): void {
  updateScale()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  if (!dashCode) {
    loading.value = false
    loadError.value = '缺少大屏编码'
    return
  }
  void loadDashboard()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  clearTimers()
  charts.forEach((chart) => chart.dispose())
  charts.clear()
})
</script>

<template>
  <div class="screen-page">
    <a-spin v-if="loading" class="screen-spin" size="large" />
    <a-result
      v-else-if="loadError"
      class="screen-error"
      status="warning"
      title="大屏加载失败"
      :sub-title="loadError"
    />
    <div v-else ref="stageWrapRef" class="stage-wrap">
      <div class="stage" :style="{ transform: `scale(${scale})` }">
        <div class="stage-header">
          <span class="stage-name">{{ titleText }}</span>
          <span class="stage-version">v{{ version }}</span>
        </div>
        <div
          v-for="item in items"
          :key="item.iid"
          class="chart-card"
          :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.w}px`, height: `${item.h}px` }"
        >
          <div class="card-title">{{ item.title }}</div>
          <div class="card-chart" :ref="(el) => setChartEl(item.iid, el)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 撑满内容区（抵消 page-card 内边距），深色大屏背景 */
.screen-page {
  margin: -16px;
  height: calc(100vh - 80px);
  background: #0b1531;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}

.screen-spin {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.screen-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.stage-wrap {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

/* 1280x720 设计尺寸，等比缩放 + 绝对居中 */
.stage {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1280px;
  height: 720px;
  margin-left: -640px;
  margin-top: -360px;
  transform-origin: center center;
}

.stage-header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.stage-name {
  color: #fff;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 6px;
  text-shadow:
    0 0 12px rgba(22, 119, 255, 0.95),
    0 0 32px rgba(22, 119, 255, 0.55);
}

.stage-version {
  color: rgba(255, 255, 255, 0.45);
  font-size: 14px;
}

.chart-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
}

.card-title {
  flex: 0 0 auto;
  margin-bottom: 4px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-chart {
  flex: 1;
  min-height: 0;
}
</style>
