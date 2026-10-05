import { del, get, post, put } from '@/utils/request'

export interface UserPageParams {
  keyword?: string
  status?: number
  deptId?: string
  pageNum?: number
  pageSize?: number
}

/** 用户分页（deptId 含子部门） */
export function getUserPage(params: UserPageParams): Promise<PageResult<SysUser>> {
  return get<PageResult<SysUser>>('/system/user/page', params)
}

/** 用户详情（含 roleIds） */
export function getUserDetail(id: string): Promise<SysUser & { roleIds: string[] }> {
  return get<SysUser & { roleIds: string[] }>(`/system/user/${id}`)
}

export function addUser(body: UserSaveBody): Promise<null> {
  return post<null>('/system/user', body)
}

export function updateUser(body: UserSaveBody): Promise<null> {
  return put<null>('/system/user', body)
}

export function removeUser(id: string): Promise<null> {
  return del<null>(`/system/user/${id}`)
}

export function updateUserStatus(id: string, status: number): Promise<null> {
  return put<null>(`/system/user/${id}/status/${status}`)
}

export function resetUserPassword(id: string, body: { password: string }): Promise<null> {
  return put<null>(`/system/user/${id}/password`, body)
}
