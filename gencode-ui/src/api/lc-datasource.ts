import { del, get, post, put } from '@/utils/request'

/**
 * 低代码数据源（契约见 docs/api-contract.md「低代码 /lc（二期 M2：列表与数据源）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）。
 * 注意：password AES 加密落库，分页/详情永远不回显；更新时 password 不传=不修改。
 */

/** 数据源定义（不含 password，后端不回显） */
export interface LcDatasource {
  id: string
  name: string
  driver: string
  jdbcUrl: string
  username: string
  remark: string | null
  createTime: string
}

/** 数据源保存入参（POST 新建 password 必填；PUT 更新 password 不传=不修改） */
export interface LcDatasourceSaveBody {
  id?: string
  name: string
  driver: string
  jdbcUrl: string
  username: string
  password?: string
  remark?: string
}

/** 数据源下拉项（GET /lc/datasource/list/all） */
export interface LcDatasourceOption {
  id: string
  name: string
}

/** 连接测试结果（POST /lc/datasource/{id}/test） */
export interface DatasourceTestResult {
  ok: boolean
  message: string
}

export interface LcDatasourcePageParams {
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** 驱动预置项（下拉可选、亦可手输） */
export const DATASOURCE_DRIVERS: string[] = [
  'com.mysql.cj.jdbc.Driver',
  'org.postgresql.Driver',
  'oracle.jdbc.OracleDriver',
  'com.microsoft.sqlserver.jdbc.SQLServerDriver',
  'dm.jdbc.driver.DmDriver'
]

// ============ 数据源 ============

export function getLcDatasourcePage(params: LcDatasourcePageParams): Promise<PageResult<LcDatasource>> {
  return get<PageResult<LcDatasource>>('/lc/datasource/page', params)
}

/** 不分页（下拉用） */
export function getLcDatasourceListAll(): Promise<LcDatasourceOption[]> {
  return get<LcDatasourceOption[]>('/lc/datasource/list/all')
}

export function addLcDatasource(body: LcDatasourceSaveBody): Promise<null> {
  return post<null>('/lc/datasource', body)
}

export function updateLcDatasource(body: LcDatasourceSaveBody): Promise<null> {
  return put<null>('/lc/datasource', body)
}

export function removeLcDatasource(id: string): Promise<null> {
  return del<null>(`/lc/datasource/${id}`)
}

/** 连接测试：ok=true 连接成功；message 为可直接展示的结果说明 */
export function testLcDatasource(id: string): Promise<DatasourceTestResult> {
  return post<DatasourceTestResult>(`/lc/datasource/${id}/test`)
}
