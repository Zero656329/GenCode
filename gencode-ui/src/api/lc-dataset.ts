import { del, get, post, put } from '@/utils/request'

/**
 * 低代码数据集（契约见 docs/api-contract.md「低代码 /lc（四期 M4：数据集与大屏）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）。
 * sqlText 仅允许单条 SELECT（同列表 SQL 安全规则），#{param} 占位符由引擎转参数绑定。
 */

/** 数据集参数定义（paramsJson 为该结构的 JSON 数组字符串） */
export interface DatasetParam {
  /** 参数名，对应 SQL 中 #{name} 占位符 */
  name: string
  /** 显示名 */
  label: string
  /** 类型：string=文本 number=数字 date=日期 */
  type: 'string' | 'number' | 'date'
  /** 是否必填 */
  required: boolean
  /** 默认值（预览/图表取数的缺省参数） */
  defaultValue?: string | number | null
}

/** 低代码数据集定义 */
export interface LcDataset {
  id: string
  code: string
  name: string
  /** 查询 SQL，仅 SELECT，支持 #{param} 参数化 */
  sqlText: string | null
  /** 参数定义 JSON 字符串：DatasetParam[] */
  paramsJson: string | null
  remark: string | null
  createTime: string
}

/** 数据集查询结果（preview 与图表取数共用，最多 100 行） */
export interface DatasetDataResult {
  columns: string[]
  rows: Record<string, unknown>[]
}

export interface LcDatasetPageParams {
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** 新建入参（POST /lc/dataset，仅 SELECT） */
export interface LcDatasetAddBody {
  code: string
  name: string
  sqlText: string
  paramsJson?: string
  remark?: string
}

/** 保存入参（PUT /lc/dataset，id 必填，code 不可改） */
export interface LcDatasetSaveBody {
  id: string
  name: string
  sqlText: string
  paramsJson: string | null
  remark: string | null
}

// ============ 数据集定义 ============

export function getLcDatasetPage(params: LcDatasetPageParams): Promise<PageResult<LcDataset>> {
  return get<PageResult<LcDataset>>('/lc/dataset/page', params)
}

export function getLcDataset(id: string): Promise<LcDataset> {
  return get<LcDataset>(`/lc/dataset/${id}`)
}

export function addLcDataset(body: LcDatasetAddBody): Promise<null> {
  return post<null>('/lc/dataset', body)
}

export function updateLcDataset(body: LcDatasetSaveBody): Promise<null> {
  return put<null>('/lc/dataset', body)
}

export function removeLcDataset(id: string): Promise<null> {
  return del<null>(`/lc/dataset/${id}`)
}

/** 预览：body { params }，返回 { columns, rows }（限 100 行） */
export function previewLcDataset(
  id: string,
  params: Record<string, unknown> = {}
): Promise<DatasetDataResult> {
  return post<DatasetDataResult>(`/lc/dataset/${id}/preview`, { params })
}

/**
 * 图表取数（登录即可）：GET /lc/dataset/publish/{code}/data?params=
 * params 为 URL 编码 JSON，交给 axios 序列化为查询参数。
 */
export function getLcDatasetPublishData(
  code: string,
  params: Record<string, unknown> = {}
): Promise<DatasetDataResult> {
  return get<DatasetDataResult>(`/lc/dataset/publish/${code}/data`, {
    params: JSON.stringify(params)
  })
}

// ============ paramsJson 解析工具 ============

/** 解析 paramsJson（容错：非法/非数组返回空数组） */
export function parseDatasetParams(paramsJson: string | null | undefined): DatasetParam[] {
  if (!paramsJson) return []
  try {
    const parsed = JSON.parse(paramsJson) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((it): it is DatasetParam => !!it && typeof it === 'object' && typeof (it as DatasetParam).name === 'string')
      .map((it) => ({
        name: it.name,
        label: typeof it.label === 'string' ? it.label : it.name,
        type: it.type === 'number' || it.type === 'date' ? it.type : 'string',
        required: it.required === true,
        defaultValue: it.defaultValue === undefined ? null : it.defaultValue
      }))
  } catch {
    return []
  }
}

/** 由参数定义构造缺省参数（取 defaultValue 非空项） */
export function buildDefaultParams(params: DatasetParam[]): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const p of params) {
    if (p.defaultValue !== null && p.defaultValue !== undefined && p.defaultValue !== '') {
      out[p.name] = p.defaultValue
    }
  }
  return out
}
