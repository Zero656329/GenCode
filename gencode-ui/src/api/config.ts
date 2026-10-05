import { del, get, post, put } from '@/utils/request'

export interface ConfigPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export function getConfigPage(params: ConfigPageParams): Promise<PageResult<SysConfig>> {
  return get<PageResult<SysConfig>>('/system/config/page', params)
}

/** 按键取参数值（data 直接为字符串） */
export function getConfigValue(key: string): Promise<string> {
  return get<string>(`/system/config/key/${key}`)
}

export function addConfig(body: ConfigSaveBody): Promise<null> {
  return post<null>('/system/config', body)
}

export function updateConfig(body: ConfigSaveBody): Promise<null> {
  return put<null>('/system/config', body)
}

export function removeConfig(id: string): Promise<null> {
  return del<null>(`/system/config/${id}`)
}
