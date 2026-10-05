import { del, get, put, post } from '@/utils/request'

/**
 * 低代码大屏（契约见 docs/api-contract.md「低代码 /lc（四期 M4：数据集与大屏）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）；
 * 大屏状态：0=草稿 1=已发布 2=停用（与 lc_form/lc_list 一致）。
 */

/** 大屏状态：0草稿 1已发布 2停用 */
export type LcDashboardStatus = 0 | 1 | 2

/** 图表类型 */
export type DashboardChartType = 'line' | 'bar' | 'pie'

/** 大屏图表项（layoutJson.items 元素，绝对定位：设计尺寸 1280x720） */
export interface DashboardItem {
  type: 'chart'
  chartType: DashboardChartType
  /** 绑定数据集编码 */
  datasetCode: string
  title: string
  /** X 轴类目字段 */
  xField: string
  /** Y 轴值字段 */
  yField: string
  /** 分组字段（饼图按其分组统计 yField 合计；可空） */
  seriesField?: string
  /** 设计坐标（px），左上角原点 */
  x: number
  y: number
  w: number
  h: number
  /** 自动刷新间隔（秒），空/0=不刷新 */
  refreshSec?: number
}

/** 大屏布局（保存时 JSON.stringify 为 layoutJson 字段） */
export interface DashboardLayout {
  items: DashboardItem[]
}

/** 低代码大屏定义 */
export interface LcDashboard {
  id: string
  code: string
  name: string
  /** 布局 JSON 字符串：DashboardLayout */
  layoutJson: string | null
  status: LcDashboardStatus
  version: number
  publishTime: string | null
  remark: string | null
  createTime: string
}

/** 运行时发布大屏（GET /lc/dashboard/publish/{code}） */
export interface LcDashboardPublished {
  code: string
  name: string
  version: number
  layoutJson: string | null
}

export interface LcDashboardPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/** 新建入参（POST /lc/dashboard） */
export interface LcDashboardAddBody {
  code: string
  name: string
  layoutJson?: string
  remark?: string
}

/** 保存布局入参（PUT /lc/dashboard，id 必填，code 不可改） */
export interface LcDashboardSaveBody {
  id: string
  name: string
  layoutJson: string
  remark: string | null
}

// ============ 大屏定义 ============

export function getLcDashboardPage(params: LcDashboardPageParams): Promise<PageResult<LcDashboard>> {
  return get<PageResult<LcDashboard>>('/lc/dashboard/page', params)
}

export function getLcDashboard(id: string): Promise<LcDashboard> {
  return get<LcDashboard>(`/lc/dashboard/${id}`)
}

export function addLcDashboard(body: LcDashboardAddBody): Promise<null> {
  return post<null>('/lc/dashboard', body)
}

export function updateLcDashboard(body: LcDashboardSaveBody): Promise<null> {
  return put<null>('/lc/dashboard', body)
}

export function removeLcDashboard(id: string): Promise<null> {
  return del<null>(`/lc/dashboard/${id}`)
}

/** 发布：version+1，发布快照=当前 layoutJson，status→1 */
export function publishLcDashboard(id: string): Promise<LcDashboard | null> {
  return put<LcDashboard | null>(`/lc/dashboard/${id}/publish`)
}

/** 停用/启用（2=停用；启用恢复为已发布=1，与 lc_form/lc_list 一致） */
export function updateLcDashboardStatus(id: string, status: LcDashboardStatus): Promise<null> {
  return put<null>(`/lc/dashboard/${id}/status/${status}`)
}

/** 运行时接口：按编码取已发布大屏（含发布快照 layoutJson） */
export function getLcDashboardPublished(code: string): Promise<LcDashboardPublished> {
  return get<LcDashboardPublished>(`/lc/dashboard/publish/${code}`)
}
