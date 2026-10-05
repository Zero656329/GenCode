import { del, get, post, put } from '@/utils/request'

export interface RolePageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/** 角色分页 */
export function getRolePage(params: RolePageParams): Promise<PageResult<SysRole>> {
  return get<PageResult<SysRole>>('/system/role/page', params)
}

/** 全部角色（下拉用，不分页） */
export function getRoleListAll(): Promise<SysRole[]> {
  return get<SysRole[]>('/system/role/list/all')
}

/** 角色详情（含 menuIds） */
export function getRoleDetail(id: string): Promise<SysRole & { menuIds: string[] }> {
  return get<SysRole & { menuIds: string[] }>(`/system/role/${id}`)
}

export function addRole(body: RoleSaveBody): Promise<null> {
  return post<null>('/system/role', body)
}

export function updateRole(body: RoleSaveBody): Promise<null> {
  return put<null>('/system/role', body)
}

export function removeRole(id: string): Promise<null> {
  return del<null>(`/system/role/${id}`)
}

/** 保存角色菜单授权 */
export function saveRoleMenus(id: string, body: RoleMenuBody): Promise<null> {
  return put<null>(`/system/role/${id}/menus`, body)
}

export function updateRoleStatus(id: string, status: number): Promise<null> {
  return put<null>(`/system/role/${id}/status/${status}`)
}
