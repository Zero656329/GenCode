import { del, get, post, put } from '@/utils/request'

export interface DeptTreeParams {
  name?: string
  status?: number
}

/** 完整部门树 */
export function getDeptTree(params?: DeptTreeParams): Promise<SysDept[]> {
  return get<SysDept[]>('/system/dept/tree', params)
}

export function addDept(body: DeptSaveBody): Promise<null> {
  return post<null>('/system/dept', body)
}

export function updateDept(body: DeptSaveBody): Promise<null> {
  return put<null>('/system/dept', body)
}

export function removeDept(id: string): Promise<null> {
  return del<null>(`/system/dept/${id}`)
}
