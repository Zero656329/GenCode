import { del, get, post, put } from '@/utils/request'

export interface MenuTreeParams {
  name?: string
  status?: number
}

/** 完整菜单树 */
export function getMenuTree(params?: MenuTreeParams): Promise<MenuNode[]> {
  return get<MenuNode[]>('/system/menu/tree', params)
}

export function getMenuDetail(id: string): Promise<MenuNode> {
  return get<MenuNode>(`/system/menu/${id}`)
}

export function addMenu(body: MenuSaveBody): Promise<null> {
  return post<null>('/system/menu', body)
}

export function updateMenu(body: MenuSaveBody): Promise<null> {
  return put<null>('/system/menu', body)
}

export function removeMenu(id: string): Promise<null> {
  return del<null>(`/system/menu/${id}`)
}
