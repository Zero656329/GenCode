import { del, get, post, put } from '@/utils/request'

export interface DictTypePageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface DictDataPageParams {
  dictType?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

// ============ 字典类型 ============

export function getDictTypePage(params: DictTypePageParams): Promise<PageResult<DictType>> {
  return get<PageResult<DictType>>('/system/dict/type/page', params)
}

export function getDictTypeAll(): Promise<DictType[]> {
  return get<DictType[]>('/system/dict/type/all')
}

export function addDictType(body: DictTypeSaveBody): Promise<null> {
  return post<null>('/system/dict/type', body)
}

export function updateDictType(body: DictTypeSaveBody): Promise<null> {
  return put<null>('/system/dict/type', body)
}

export function removeDictType(id: string): Promise<null> {
  return del<null>(`/system/dict/type/${id}`)
}

// ============ 字典数据 ============

export function getDictDataPage(params: DictDataPageParams): Promise<PageResult<DictData>> {
  return get<PageResult<DictData>>('/system/dict/data/page', params)
}

/** 按类型取字典项（不分页，可缓存） */
export function getDictDataByType(dictType: string): Promise<DictOption[]> {
  return get<DictOption[]>(`/system/dict/data/by-type/${dictType}`)
}

export function addDictData(body: DictDataSaveBody): Promise<null> {
  return post<null>('/system/dict/data', body)
}

export function updateDictData(body: DictDataSaveBody): Promise<null> {
  return put<null>('/system/dict/data', body)
}

export function removeDictData(id: string): Promise<null> {
  return del<null>(`/system/dict/data/${id}`)
}
