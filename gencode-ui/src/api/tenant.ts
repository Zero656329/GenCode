import { del, get, post, put } from '@/utils/request'

export interface TenantPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export function getTenantPage(params: TenantPageParams): Promise<PageResult<SysTenant>> {
  return get<PageResult<SysTenant>>('/system/tenant/page', params)
}

export function addTenant(body: TenantSaveBody): Promise<null> {
  return post<null>('/system/tenant', body)
}

export function updateTenant(body: TenantSaveBody): Promise<null> {
  return put<null>('/system/tenant', body)
}

export function removeTenant(id: string): Promise<null> {
  return del<null>(`/system/tenant/${id}`)
}

export function updateTenantStatus(id: string, status: number): Promise<null> {
  return put<null>(`/system/tenant/${id}/status/${status}`)
}
