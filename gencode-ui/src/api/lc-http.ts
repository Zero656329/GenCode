import { del, get, post, put } from '@/utils/request'

/**
 * 第三方 HTTP 接口（契约见 docs/api-contract.md「集成 /lc（六期 M6：HTTP 接口 / 监控 / 回收站）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）。
 * 调用时服务端替换 url/bodyTemplate 中 {param} 占位后发起真实请求（RestTemplate，超时取配置），
 * 并写 lc_http_log；respBody 截断 2000 字符。
 */

/** HTTP 接口定义 */
export interface LcHttpApi {
  id: string
  code: string
  name: string
  method: 'GET' | 'POST'
  url: string
  headersJson: string | null
  bodyTemplate: string | null
  timeoutMs: number
  /** 0=启用 1=停用 */
  status: 0 | 1
  remark: string | null
  createTime?: string | null
}

/** 接口保存入参（POST 新建 / PUT 更新；更新时 code 不可改） */
export interface LcHttpSaveBody {
  id?: string
  code: string
  name: string
  method: 'GET' | 'POST'
  url: string
  headersJson?: string
  bodyTemplate?: string
  timeoutMs: number
  status: 0 | 1
  remark?: string
}

/** 接口调用结果（respBody 截断 2k） */
export interface HttpCallResult {
  success: boolean
  costMs: number
  respBody: string
}

/** 调用日志行（lc_http_log） */
export interface HttpLogRow {
  id: string
  apiCode: string
  method: string | null
  url: string | null
  requestBody: string | null
  /** 0=成功 1=失败（同平台 status 约定的取值习惯，0 为成功） */
  success: number
  costMs: number | null
  respBody: string | null
  createTime: string | null
}

export interface LcHttpPageParams {
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** 调用入参：服务端用 params 替换 url/bodyTemplate 中 {param} */
export interface HttpCallBody {
  params: Record<string, unknown>
}

export interface HttpLogPageParams {
  apiCode: string
  pageNum?: number
  pageSize?: number
}

// ============ 接口定义 ============

export function getLcHttpPage(params: LcHttpPageParams): Promise<PageResult<LcHttpApi>> {
  return get<PageResult<LcHttpApi>>('/lc/http/page', params)
}

export function getLcHttpDetail(id: string): Promise<LcHttpApi> {
  return get<LcHttpApi>(`/lc/http/${id}`)
}

export function addLcHttp(body: LcHttpSaveBody): Promise<null> {
  return post<null>('/lc/http', body)
}

/** 更新（code 不可改） */
export function updateLcHttp(body: LcHttpSaveBody): Promise<null> {
  return put<null>('/lc/http', body)
}

/** 逻辑删除 */
export function removeLcHttp(id: string): Promise<null> {
  return del<null>(`/lc/http/${id}`)
}

// ============ 调用与日志 ============

/** 发起调用：success=false 时同样正常返回（HTTP 层失败属于业务结果） */
export function callLcHttp(code: string, body: HttpCallBody): Promise<HttpCallResult> {
  return post<HttpCallResult>(`/lc/http/${code}/call`, body)
}

/** 调用日志分页 */
export function getLcHttpLogPage(params: HttpLogPageParams): Promise<PageResult<HttpLogRow>> {
  return get<PageResult<HttpLogRow>>('/lc/http/log/page', params)
}
