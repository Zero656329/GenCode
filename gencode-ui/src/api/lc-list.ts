import { del, get, post, put } from '@/utils/request'

/**
 * 低代码列表（契约见 docs/api-contract.md「低代码 /lc（二期 M2：列表与数据源）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）；
 * 列表状态：0=草稿 1=已发布 2=停用（与 lc_form 一致，区别于全局 0正常/1停用 约定）。
 */

/** 列表状态：0草稿 1已发布 2停用 */
export type LcListStatus = 0 | 1 | 2

/** 数据源类型：TABLE=物理表 SQL=自定义查询 API=预留（查询时报"暂未支持"） */
export type LcListSourceType = 'TABLE' | 'SQL' | 'API'

/** 低代码列表定义（sourceConfig/listSchema 为 JSON 字符串，见下方结构类型） */
export interface LcList {
  id: string
  code: string
  name: string
  sourceType: LcListSourceType
  sourceConfig: string | null
  listSchema: string | null
  status: LcListStatus
  version: number
  publishTime: string | null
  remark: string | null
  createTime: string
}

// ============ listSchema 结构 ============

/** 列配置 */
export interface ListColumn {
  field: string
  title: string
  width: number | null
  /** 绑定字典类型（运行时翻译显示），空=原始值 */
  dictType: string
  /** 行内可编辑（出现在新增/编辑弹窗表单中） */
  editable: boolean
}

/** 搜索操作符 */
export type SearchOp = 'eq' | 'like' | 'gt' | 'ge' | 'lt' | 'le' | 'between'

/** 搜索控件类型 */
export type SearchControlType = 'input' | 'number' | 'date'

/** 搜索项配置 */
export interface ListSearchItem {
  field: string
  label: string
  op: SearchOp
  type: SearchControlType
}

/** 操作按钮配置 */
export interface ListButtons {
  add: boolean
  edit: boolean
  delete: boolean
}

/** 列表 Schema（保存时 JSON.stringify 为 listSchema 字段） */
export interface ListSchema {
  columns: ListColumn[]
  search: ListSearchItem[]
  buttons: ListButtons
}

// ============ sourceConfig 结构 ============

/** TABLE 型来源配置 */
export interface TableSourceConfig {
  /** null=平台主库 */
  datasourceId: string | null
  tableName: string
  pkField: string
}

/** SQL 型来源配置（仅单条 SELECT，#{field} 占位符由引擎转参数绑定） */
export interface SqlSourceConfig {
  /** null=平台主库 */
  datasourceId: string | null
  sql: string
}

// ============ 入参 / 运行时结构 ============

/** 列表保存入参（PUT /lc/list，id 必填，code 不可改） */
export interface LcListSaveBody {
  id: string
  name: string
  sourceType: LcListSourceType
  sourceConfig: string
  listSchema: string
  remark: string | null
}

/** 列表新建入参（POST /lc/list） */
export interface LcListAddBody {
  code: string
  name: string
  sourceType: LcListSourceType
  sourceConfig?: string
  listSchema?: string
  remark?: string
}

/** 运行时发布列表（GET /lc/list/publish/{code}） */
export interface LcListPublished {
  code: string
  name: string
  version: number
  sourceType: LcListSourceType
  sourceConfig: string | null
  listSchema: string | null
}

/** 表列反读结果（GET /lc/list/columns） */
export interface TableColumnInfo {
  columnName: string
  typeName: string
  comment: string | null
}

/** 运行时数据查询入参（POST /lc/list/data/{code}） */
export interface LcListDataQuery {
  params: Record<string, unknown>
  pageNum: number
  pageSize: number
  orderBy?: string
  orderDir?: 'asc' | 'desc'
}

/** 行保存入参（TABLE 型，列以 list_schema.columns 白名单过滤） */
export interface LcListRowSaveBody {
  mode: 'add' | 'edit'
  /** mode=edit 时必填，为主键字段值 */
  pk?: string
  row: Record<string, unknown>
}

/** 行删除入参（TABLE 型） */
export interface LcListRowDeleteBody {
  pk: string
}

export interface LcListPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

// ============ 列表定义 ============

export function getLcListPage(params: LcListPageParams): Promise<PageResult<LcList>> {
  return get<PageResult<LcList>>('/lc/list/page', params)
}

export function getLcList(id: string): Promise<LcList> {
  return get<LcList>(`/lc/list/${id}`)
}

export function addLcList(body: LcListAddBody): Promise<null> {
  return post<null>('/lc/list', body)
}

export function updateLcList(body: LcListSaveBody): Promise<null> {
  return put<null>('/lc/list', body)
}

export function removeLcList(id: string): Promise<null> {
  return del<null>(`/lc/list/${id}`)
}

/** 发布：version+1，发布快照={sourceConfig,listSchema}，status→1 */
export function publishLcList(id: string): Promise<LcList | null> {
  return put<LcList | null>(`/lc/list/${id}/publish`)
}

/** 停用/启用（2=停用；启用恢复为已发布=1） */
export function updateLcListStatus(id: string, status: LcListStatus): Promise<null> {
  return put<null>(`/lc/list/${id}/status/${status}`)
}

/** 运行时接口：按编码取已发布列表（含发布快照 sourceConfig/listSchema） */
export function getPublishedLcList(code: string): Promise<LcListPublished> {
  return get<LcListPublished>(`/lc/list/publish/${code}`)
}

/** 反读表列（datasourceId 空=平台主库），供设计器快速添加列配置 */
export function getTableColumns(params: {
  datasourceId?: string
  tableName: string
}): Promise<TableColumnInfo[]> {
  return get<TableColumnInfo[]>('/lc/list/columns', params)
}

// ============ 列表数据（运行时，登录即可） ============

/** 服务端分页查询，仅已发布列表 */
export function postLcListData(
  code: string,
  body: LcListDataQuery
): Promise<PageResult<Record<string, unknown>>> {
  return post<PageResult<Record<string, unknown>>>(`/lc/list/data/${code}`, body)
}

/** TABLE 型行保存 */
export function saveLcListRow(code: string, body: LcListRowSaveBody): Promise<null> {
  return post<null>(`/lc/list/data/save/${code}`, body)
}

/** TABLE 型行删除（buttons.delete 为 true 时前端才显示删除按钮） */
export function deleteLcListRow(code: string, body: LcListRowDeleteBody): Promise<null> {
  return post<null>(`/lc/list/data/delete/${code}`, body)
}
