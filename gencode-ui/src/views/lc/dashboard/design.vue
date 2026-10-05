<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import {
  ArrowLeftOutlined,
  BarChartOutlined,
  DeleteOutlined,
  LineChartOutlined,
  PieChartOutlined,
  PlayCircleOutlined,
  RocketOutlined,
  SaveOutlined,
  SyncOutlined
} from '@ant-design/icons-vue'
import {
  getLcDashboard,
  publishLcDashboard,
  updateLcDashboard,
  type DashboardChartType,
  type DashboardItem,
  type DashboardLayout,
  type LcDashboardStatus
} from '@/api/lc-dashboard'
import {
  buildDefaultParams,
  getLcDatasetPage,
  getLcDatasetPublishData,
  parseDatasetParams,
  previewLcDataset,
  type LcDataset
} from '@/api/lc-dataset'

/**
 * 大屏设计器（/lc/dashboard/design/:id）
 * 设计尺寸固定 1280x720，transform: scale 适配窗口；items 绝对定位（x/y/w/h）。
 * 图表用 ECharts 渲染真实数据（GET /lc/dataset/publish/{code}/data）。
 */

const DESIGN_W = 1280
const DESIGN_H = 720
/** 图表默认尺寸 */
const DEFAULT_W = 420
const DEFAULT_H = 280

const CHART_TYPES: Array<{ value: DashboardChartType; label: string }> = [
  { value: 'line', label: '折线图' },
  { value: 'bar', label: '柱状图' },
  { value: 'pie', label: '饼图' }
]
const CHART_LABELS: Record<DashboardChartType, string> = { line: '折线图', bar: '柱状图', pie: '饼图' }
const CHART_ICONS: Record<DashboardChartType, typeof LineChartOutlined> = {
  line: LineChartOutlined,
  bar: BarChartOutlined,
  pie: PieChartOutlined
}
const PALETTE_COLORS = ['#1677ff', '#2fc25b', '#fa8c16', '#722ed1', '#13c2c2', '#eb2f96']

/** 设计器内部项：附加稳定内部 id（保存时剔除，不写入 layoutJson） */
type DesignItem = DashboardItem & { iid: string }

let iidSeq = 0
function genIid(): string {
  iidSeq += 1
  return `it_${Date.now().toString(36)}_${iidSeq}`
}

function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max)
}

const route = useRoute()
const router = useRouter()
const dashId = String(route.params.id ?? '')

// ============ 大屏基础信息 ============

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const name = ref('')
const code = ref('')
const remark = ref('')
const version = ref(0)
const status = ref<LcDashboardStatus>(0)

// ============ 画布与选中 ============

const items = ref<DesignItem[]>([])
const selectedIid = ref('')

const selectedItem = computed<DesignItem | null>(() => {
  return items.value.find((it) => it.iid === selectedIid.value) ?? null
})

const canvasRef = ref<HTMLDivElement | null>(null)
const scale = ref(1)

function updateScale(): void {
  const el = canvasRef.value
  if (!el) return
  scale.value = Math.max(0.1, Math.min(el.clientWidth / DESIGN_W, el.clientHeight / DESIGN_H))
}

// ============ 加载布局 ============

function normalizeItem(raw: Partial<DashboardItem>): DesignItem {
  const chartType: DashboardChartType =
    raw.chartType === 'bar' || raw.chartType === 'pie' ? raw.chartType : 'line'
  const num = (v: unknown, def: number): number => {
    const n = Number(v)
    return Number.isFinite(n) ? n : def
  }
  const w = clamp(Math.round(num(raw.w, DEFAULT_W)), 120, DESIGN_W)
  const h = clamp(Math.round(num(raw.h, DEFAULT_H)), 120, DESIGN_H)
  return {
    iid: genIid(),
    type: 'chart',
    chartType,
    datasetCode: typeof raw.datasetCode === 'string' ? raw.datasetCode : '',
    title: typeof raw.title === 'string' ? raw.title : CHART_LABELS[chartType],
    xField: typeof raw.xField === 'string' ? raw.xField : '',
    yField: typeof raw.yField === 'string' ? raw.yField : '',
    seriesField: typeof raw.seriesField === 'string' && raw.seriesField ? raw.seriesField : undefined,
    x: clamp(Math.round(num(raw.x, 40)), 0, DESIGN_W - w),
    y: clamp(Math.round(num(raw.y, 40)), 0, DESIGN_H - h),
    w,
    h,
    refreshSec: Number(raw.refreshSec) > 0 ? Math.round(Number(raw.refreshSec)) : undefined
  }
}

async function loadDetail(): Promise<void> {
  loading.value = true
  try {
    const detail = await getLcDashboard(dashId)
    name.value = detail.name
    code.value = detail.code
    remark.value = detail.remark || ''
    version.value = detail.version || 0
    status.value = detail.status
    if (detail.layoutJson) {
      try {
        const parsed = JSON.parse(detail.layoutJson) as { items?: Array<Partial<DashboardItem>> }
        items.value = (parsed.items || []).map(normalizeItem)
      } catch {
        message.error('布局 JSON 解析失败，已按空画布加载')
        items.value = []
      }
    }
    // 预取已用数据集的字段候选
    for (const dsCode of new Set(items.value.map((it) => it.datasetCode).filter(Boolean))) {
      void loadFieldOptions(dsCode)
    }
    await nextTick()
    renderAllCharts()
  } finally {
    loading.value = false
  }
}

// ============ 数据集与字段候选 ============

const datasets = ref<LcDataset[]>([])
const datasetSelectOptions = computed(() =>
  datasets.value.map((d) => ({ label: `${d.name}（${d.code}）`, value: d.code }))
)

async function loadDatasets(): Promise<void> {
  try {
    const page = await getLcDatasetPage({ pageNum: 1, pageSize: 100 })
    datasets.value = page.list || []
  } catch {
    datasets.value = []
  }
}

/** 数据集 → 字段候选（选中数据集后调 preview 取 columns），按编码缓存 */
const fieldOptionsMap = reactive<Record<string, string[]>>({})
const fieldLoading = ref(false)

async function loadFieldOptions(datasetCode: string): Promise<void> {
  if (!datasetCode || fieldOptionsMap[datasetCode]) return
  fieldOptionsMap[datasetCode] = []
  const ds = datasets.value.find((d) => d.code === datasetCode)
  if (!ds) return
  fieldLoading.value = true
  try {
    const res = await previewLcDataset(ds.id, buildDefaultParams(parseDatasetParams(ds.paramsJson)))
    fieldOptionsMap[datasetCode] = res.columns || []
  } catch {
    // 预览失败（如必填参数缺默认值）：拦截器已提示，字段候选留空
  } finally {
    fieldLoading.value = false
  }
}

const fieldSelectOptions = computed(() =>
  (selectedItem.value ? fieldOptionsMap[selectedItem.value.datasetCode] || [] : []).map((f) => ({
    label: f,
    value: f
  }))
)

// ============ 添加图表（点击 / 拖入） ============

function createItem(chartType: DashboardChartType, x?: number, y?: number): DesignItem {
  const seq = items.value.length % 5
  return normalizeItem({
    type: 'chart',
    chartType,
    title: CHART_LABELS[chartType],
    x: x ?? 40 + seq * 40,
    y: y ?? 80 + seq * 32,
    w: DEFAULT_W,
    h: DEFAULT_H
  })
}

function onPaletteClick(chartType: DashboardChartType): void {
  const item = createItem(chartType)
  items.value.push(item)
  selectItem(item)
  void renderChart(item)
}

let draggingType: DashboardChartType | null = null

function onPaletteDragStart(e: DragEvent, chartType: DashboardChartType): void {
  draggingType = chartType
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'copy'
    e.dataTransfer.setData('text/plain', `chart:${chartType}`)
  }
}

function onPaletteDragEnd(): void {
  draggingType = null
}

function onStageDrop(e: DragEvent): void {
  const raw = e.dataTransfer ? e.dataTransfer.getData('text/plain') : ''
  const chartType = (raw.startsWith('chart:') && CHART_TYPES.some((c) => c.value === raw.slice(6))
    ? (raw.slice(6) as DashboardChartType)
    : draggingType) as DashboardChartType | null
  draggingType = null
  if (!chartType) return
  const stage = stageRef.value
  const item = createItem(chartType)
  if (stage) {
    const rect = stage.getBoundingClientRect()
    const s = scale.value || 1
    item.x = clamp(Math.round((e.clientX - rect.left) / s - item.w / 2), 0, DESIGN_W - item.w)
    item.y = clamp(Math.round((e.clientY - rect.top) / s - item.h / 2), 0, DESIGN_H - item.h)
  }
  items.value.push(item)
  selectItem(item)
  void renderChart(item)
}

// ============ 选中 / 画布内拖拽移动 ============

function selectItem(item: DesignItem): void {
  selectedIid.value = item.iid
  if (item.datasetCode) void loadFieldOptions(item.datasetCode)
}

const stageRef = ref<HTMLDivElement | null>(null)

function onItemMouseDown(e: MouseEvent, item: DesignItem): void {
  if (e.button !== 0) return
  selectItem(item)
  e.preventDefault()
  const startX = e.clientX
  const startY = e.clientY
  const origX = item.x
  const origY = item.y
  const onMove = (ev: MouseEvent): void => {
    const s = scale.value || 1
    item.x = clamp(Math.round(origX + (ev.clientX - startX) / s), 0, DESIGN_W - item.w)
    item.y = clamp(Math.round(origY + (ev.clientY - startY) / s), 0, DESIGN_H - item.h)
  }
  const onUp = (): void => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

// ============ 属性修改 ============

function setGeom(item: DesignItem | null, key: 'x' | 'y' | 'w' | 'h', v: unknown): void {
  if (!item) return
  if (v === null || v === undefined || v === '') return
  const n = Math.round(Number(v))
  if (!Number.isFinite(n)) return
  if (key === 'x') item.x = clamp(n, 0, DESIGN_W - item.w)
  else if (key === 'y') item.y = clamp(n, 0, DESIGN_H - item.h)
  else if (key === 'w') item.w = clamp(n, 120, DESIGN_W - item.x)
  else item.h = clamp(n, 120, DESIGN_H - item.y)
  charts.get(item.iid)?.resize()
}

function setRefreshSec(item: DesignItem | null, v: unknown): void {
  if (!item) return
  if (v === null || v === undefined || v === '') {
    item.refreshSec = undefined
    return
  }
  const n = Math.round(Number(v))
  item.refreshSec = Number.isFinite(n) && n > 0 ? n : undefined
}

function onChartTypeChange(v: unknown): void {
  const item = selectedItem.value
  if (!item) return
  if (v === 'line' || v === 'bar' || v === 'pie') item.chartType = v
  void renderChart(item)
}

function onDatasetChange(v: unknown): void {
  const item = selectedItem.value
  if (!item) return
  item.datasetCode = typeof v === 'string' ? v : ''
  item.xField = ''
  item.yField = ''
  item.seriesField = undefined
  void loadFieldOptions(item.datasetCode).then(() => renderChart(item))
}

function onFieldChange(): void {
  const item = selectedItem.value
  if (item) void renderChart(item)
}

const seriesProxy = computed<string | undefined>({
  get: () => selectedItem.value?.seriesField,
  set: (v) => {
    const item = selectedItem.value
    if (!item) return
    item.seriesField = v || undefined
    void renderChart(item)
  }
})

function removeItem(item: DesignItem): void {
  const idx = items.value.findIndex((it) => it.iid === item.iid)
  if (idx < 0) return
  items.value.splice(idx, 1)
  charts.get(item.iid)?.dispose()
  charts.delete(item.iid)
  chartEls.delete(item.iid)
  if (selectedIid.value === item.iid) selectedIid.value = ''
}

// ============ ECharts 渲染 ============

const chartEls = new Map<string, HTMLDivElement>()
const charts = new Map<string, echarts.ECharts>()
/** 每项取数序号，防止乱序响应覆盖新配置 */
const fetchSeq = new Map<string, number>()

function setChartEl(iid: string, el: unknown): void {
  if (el) chartEls.set(iid, el as HTMLDivElement)
  else chartEls.delete(iid)
}

/** 大屏图表统一 option（line/bar 类目轴-值轴；pie 按 seriesField 分组统计 yField 合计，否则取前 10 行） */
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
        {
          type: 'pie',
          radius: '58%',
          center: ['50%', '46%'],
          data,
          label: { color: textColor, fontSize: 11 }
        }
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

async function renderChart(item: DesignItem): Promise<void> {
  await nextTick()
  const el = chartEls.get(item.iid)
  if (!el) return
  let chart = charts.get(item.iid)
  if (!chart) {
    chart = echarts.init(el)
    charts.set(item.iid, chart)
  }
  if (!item.datasetCode || !item.xField || !item.yField) {
    chart.clear()
    chart.setOption(hintOption('请在左侧选择数据集并配置 X/Y 字段'))
    return
  }
  const seq = (fetchSeq.get(item.iid) || 0) + 1
  fetchSeq.set(item.iid, seq)
  try {
    const res = await getLcDatasetPublishData(item.datasetCode)
    if (fetchSeq.get(item.iid) !== seq) return
    if (!res.rows || !res.rows.length) {
      chart.clear()
      chart.setOption(hintOption('暂无数据'))
      return
    }
    chart.setOption(buildChartOption(item, res.rows), true)
  } catch {
    // 拦截器已统一提示
  }
}

function renderAllCharts(): void {
  for (const item of items.value) {
    void renderChart(item)
  }
}

// ============ 保存 / 发布 / 运行 ============

function findConfigError(): string {
  for (const item of items.value) {
    const title = item.title || CHART_LABELS[item.chartType]
    if (!item.datasetCode) return `图表「${title}」未选择数据集`
    if (!item.xField || !item.yField) return `图表「${title}」未配置 X/Y 字段`
  }
  return ''
}

/** 保存布局；silent=true 时由发布流程调用，不重复提示 */
async function doSave(silent: boolean): Promise<boolean> {
  const err = findConfigError()
  if (err) {
    message.error(err)
    return false
  }
  if (!name.value.trim()) {
    message.error('请填写大屏名称')
    return false
  }
  saving.value = true
  try {
    const layout: DashboardLayout = {
      items: items.value.map((it) => ({
        type: 'chart',
        chartType: it.chartType,
        datasetCode: it.datasetCode,
        title: it.title,
        xField: it.xField,
        yField: it.yField,
        ...(it.seriesField ? { seriesField: it.seriesField } : {}),
        x: it.x,
        y: it.y,
        w: it.w,
        h: it.h,
        ...(it.refreshSec && it.refreshSec > 0 ? { refreshSec: it.refreshSec } : {})
      }))
    }
    await updateLcDashboard({
      id: dashId,
      name: name.value.trim(),
      layoutJson: JSON.stringify(layout),
      remark: remark.value || null
    })
    if (!silent) message.success('保存成功')
    return true
  } finally {
    saving.value = false
  }
}

function onSave(): void {
  void doSave(false)
}

async function onPublish(): Promise<void> {
  publishing.value = true
  try {
    const ok = await doSave(true)
    if (!ok) return
    const res = await publishLcDashboard(dashId)
    version.value = res && res.version ? res.version : version.value + 1
    status.value = 1
    message.success(`发布成功，当前版本 v${version.value}`)
  } finally {
    publishing.value = false
  }
}

function onRun(): void {
  if (!code.value) return
  router.push(`/app/dashboard/${code.value}`)
}

function onBack(): void {
  router.push('/lc/dashboard')
}

function onResize(): void {
  updateScale()
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  if (!dashId) {
    message.error('缺少大屏 ID')
    router.replace('/lc/dashboard')
    return
  }
  // 先拉数据集列表（字段候选依赖），再加载布局
  await loadDatasets()
  void nextTick().then(updateScale)
  loadDetail()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((chart) => chart.dispose())
  charts.clear()
})
</script>

<template>
  <div class="designer-page">
    <!-- 顶部工具条 -->
    <div class="designer-header">
      <a-space>
        <a-button @click="onBack">
          <template #icon><arrow-left-outlined /></template>
          返回
        </a-button>
        <a-input v-model:value="name" class="name-input" placeholder="大屏名称" />
        <a-tag>设计尺寸 1280×720</a-tag>
        <a-tag v-if="version > 0" color="blue">v{{ version }}</a-tag>
        <a-tag v-else>未发布</a-tag>
        <a-tag v-if="status === 2" color="red">已停用</a-tag>
      </a-space>
      <a-space>
        <a-button v-perm="'lc:dashboard:edit'" type="primary" :loading="saving" @click="onSave">
          <template #icon><save-outlined /></template>
          保存
        </a-button>
        <a-button v-perm="'lc:dashboard:publish'" :loading="publishing" @click="onPublish">
          <template #icon><rocket-outlined /></template>
          发布
        </a-button>
        <a-button @click="onRun">
          <template #icon><play-circle-outlined /></template>
          运行预览
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div class="designer-body">
        <!-- 左栏：图表卡片 + 图表配置 -->
        <aside class="palette">
          <div class="panel-title">图表组件</div>
          <div class="palette-grid">
            <div
              v-for="c in CHART_TYPES"
              :key="c.value"
              class="palette-item"
              draggable="true"
              @click="onPaletteClick(c.value)"
              @dragstart="onPaletteDragStart($event, c.value)"
              @dragend="onPaletteDragEnd"
            >
              <component :is="CHART_ICONS[c.value]" class="palette-icon" />
              <span>{{ c.label }}</span>
            </div>
          </div>
          <div class="palette-tip">点击或拖拽卡片到画布添加图表</div>

          <div class="panel-title config-title">图表配置</div>
          <template v-if="selectedItem">
            <a-form layout="vertical" class="config-form">
              <a-form-item label="图表标题">
                <a-input v-model:value="selectedItem.title" placeholder="图表标题" allow-clear />
              </a-form-item>
              <a-form-item label="图表类型">
                <a-select
                  :value="selectedItem.chartType"
                  :options="CHART_TYPES"
                  @change="(v: unknown) => onChartTypeChange(v)"
                />
              </a-form-item>
              <a-form-item label="数据集">
                <a-select
                  :value="selectedItem.datasetCode || undefined"
                  :options="datasetSelectOptions"
                  show-search
                  option-filter-prop="label"
                  placeholder="请选择数据集"
                  @change="(v: unknown) => onDatasetChange(v)"
                />
              </a-form-item>
              <a-form-item label="X 轴字段（类目）">
                <a-select
                  v-model:value="selectedItem.xField"
                  :options="fieldSelectOptions"
                  :loading="fieldLoading"
                  show-search
                  placeholder="先选择数据集"
                  @change="onFieldChange"
                />
              </a-form-item>
              <a-form-item label="Y 轴字段（数值）">
                <a-select
                  v-model:value="selectedItem.yField"
                  :options="fieldSelectOptions"
                  :loading="fieldLoading"
                  show-search
                  placeholder="先选择数据集"
                  @change="onFieldChange"
                />
              </a-form-item>
              <a-form-item label="分组字段（饼图，可选）">
                <a-select
                  v-model:value="seriesProxy"
                  :options="fieldSelectOptions"
                  :loading="fieldLoading"
                  allow-clear
                  placeholder="按该字段分组统计"
                />
              </a-form-item>
              <a-form-item label="刷新间隔（秒，0=不刷新）">
                <a-input-number
                  :value="selectedItem.refreshSec ?? 0"
                  :min="0"
                  :max="3600"
                  :precision="0"
                  style="width: 100%"
                  @change="(v: unknown) => setRefreshSec(selectedItem, v)"
                />
              </a-form-item>
            </a-form>
          </template>
          <a-empty v-else description="选中画布中的图表后配置" :image-style="{ height: '40px' }" />
        </aside>

        <!-- 中间：画布（设计尺寸 1280x720，等比缩放） -->
        <section ref="canvasRef" class="canvas-area">
          <div
            ref="stageRef"
            class="stage"
            :style="{ transform: `scale(${scale})` }"
            @dragover.prevent
            @drop.prevent="onStageDrop"
            @mousedown.self="selectedIid = ''"
          >
            <div class="stage-title" @mousedown="selectedIid = ''">
              <span class="stage-name">{{ name || '未命名大屏' }}</span>
            </div>
            <div
              v-for="item in items"
              :key="item.iid"
              class="stage-item"
              :class="{ selected: selectedIid === item.iid }"
              :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.w}px`, height: `${item.h}px` }"
              @mousedown.stop="onItemMouseDown($event, item)"
            >
              <div class="item-title">{{ item.title || '未命名图表' }}</div>
              <div class="item-chart" :ref="(el) => setChartEl(item.iid, el)"></div>
              <div
                v-if="selectedIid === item.iid"
                class="item-toolbar"
                @mousedown.stop
              >
                <a-button size="small" type="text" title="刷新数据" @click="renderChart(item)">
                  <sync-outlined />
                </a-button>
                <a-button size="small" type="text" danger title="删除图表" @click="removeItem(item)">
                  <delete-outlined />
                </a-button>
              </div>
            </div>
            <a-empty
              v-if="items.length === 0"
              class="stage-empty"
              description="点击或拖拽左侧图表卡片到画布开始设计"
            />
          </div>
        </section>

        <!-- 右栏：位置尺寸 -->
        <aside class="props">
          <div class="panel-title">位置与尺寸</div>
          <template v-if="selectedItem">
            <a-form layout="vertical" class="props-form">
              <a-form-item label="X 坐标">
                <a-input-number
                  :value="selectedItem.x"
                  :min="0"
                  :max="DESIGN_W"
                  :step="20"
                  :precision="0"
                  style="width: 100%"
                  @change="(v: unknown) => setGeom(selectedItem, 'x', v)"
                />
              </a-form-item>
              <a-form-item label="Y 坐标">
                <a-input-number
                  :value="selectedItem.y"
                  :min="0"
                  :max="DESIGN_H"
                  :step="20"
                  :precision="0"
                  style="width: 100%"
                  @change="(v: unknown) => setGeom(selectedItem, 'y', v)"
                />
              </a-form-item>
              <a-form-item label="宽度">
                <a-input-number
                  :value="selectedItem.w"
                  :min="120"
                  :max="DESIGN_W"
                  :step="20"
                  :precision="0"
                  style="width: 100%"
                  @change="(v: unknown) => setGeom(selectedItem, 'w', v)"
                />
              </a-form-item>
              <a-form-item label="高度">
                <a-input-number
                  :value="selectedItem.h"
                  :min="120"
                  :max="DESIGN_H"
                  :step="20"
                  :precision="0"
                  style="width: 100%"
                  @change="(v: unknown) => setGeom(selectedItem, 'h', v)"
                />
              </a-form-item>
              <a-button danger block @click="removeItem(selectedItem)">
                <delete-outlined /> 删除图表
              </a-button>
            </a-form>
            <div class="props-tip">画布内按住图表可直接拖拽移动</div>
          </template>
          <a-empty v-else description="请在画布中选中图表" :image-style="{ height: '40px' }" />
        </aside>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.name-input {
  width: 220px;
}

.designer-body {
  display: flex;
  gap: 12px;
  align-items: stretch;
  height: calc(100vh - 240px);
  min-height: 520px;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.config-title {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

/* 左栏图表面板 */
.palette {
  flex: 0 0 270px;
  width: 270px;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow-y: auto;
}

.palette-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.palette-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 12px;
  cursor: grab;
  user-select: none;
}

.palette-item:active {
  cursor: grabbing;
}

.palette-item:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.palette-icon {
  font-size: 18px;
}

.palette-tip {
  margin-top: 10px;
  color: #999;
  font-size: 12px;
  text-align: center;
}

.config-form :deep(.ant-form-item) {
  margin-bottom: 10px;
}

/* 中间画布：深色舞台，等比缩放 */
.canvas-area {
  flex: 1;
  min-width: 0;
  position: relative;
  overflow: hidden;
  background: #f0f2f5;
  border-radius: 6px;
}

.stage {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1280px;
  height: 720px;
  margin-left: -640px;
  margin-top: -360px;
  background: #0b1531;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
}

.stage-title {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: default;
  z-index: 1;
}

.stage-name {
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 4px;
  text-shadow:
    0 0 12px rgba(22, 119, 255, 0.9),
    0 0 30px rgba(22, 119, 255, 0.5);
}

.stage-item {
  position: absolute;
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  cursor: move;
}

.stage-item:hover {
  border-color: rgba(22, 119, 255, 0.55);
}

.stage-item.selected {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.35);
}

.item-title {
  flex: 0 0 auto;
  margin-bottom: 4px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-chart {
  flex: 1;
  min-height: 0;
}

.item-toolbar {
  position: absolute;
  top: -14px;
  right: 8px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 4px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.stage-empty {
  position: absolute;
  top: 45%;
  left: 50%;
  transform: translate(-50%, -50%);
}

/* 右栏位置尺寸 */
.props {
  flex: 0 0 220px;
  width: 220px;
  padding: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow-y: auto;
}

.props-form :deep(.ant-form-item) {
  margin-bottom: 10px;
}

.props-tip {
  margin-top: 12px;
  color: #999;
  font-size: 12px;
  text-align: center;
}
</style>
