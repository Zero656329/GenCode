import { get, post } from '@/utils/request'

/**
 * 数据回收站（契约见 docs/api-contract.md「集成 /lc（六期 M6：HTTP 接口 / 监控 / 回收站）」）
 * 表单/列表/大屏/数据集逻辑删除后在此管理：恢复（deleted 置 0）或彻底删除（物理删除）。
 */

/** 回收站类型项（GET /lc/recycle/types） */
export interface RecycleType {
  type: string
  label: string
}

/** 已删除行（name 取 code 或 name 字段） */
export interface RecycleRow {
  id: string
  name: string
  createTime: string | null
  /** 部分后端实现会在行上带回类型；缺省时以当前筛选类型为准 */
  type?: string
}

export interface RecyclePageParams {
  type?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

/** 恢复 / 彻底删除入参 */
export interface RecycleActionBody {
  type: string
  id: string
}

/** 类型下拉 */
export function getRecycleTypes(): Promise<RecycleType[]> {
  return get<RecycleType[]>('/lc/recycle/types')
}

/** 已删除数据分页 */
export function getRecyclePage(params: RecyclePageParams): Promise<PageResult<RecycleRow>> {
  return get<PageResult<RecycleRow>>('/lc/recycle/page', params)
}

/** 恢复（deleted 置 0），权限 lc:recycle:restore */
export function restoreRecycle(body: RecycleActionBody): Promise<null> {
  return post<null>('/lc/recycle/restore', body)
}

/** 彻底删除（物理删除，不可恢复），权限 lc:recycle:restore */
export function purgeRecycle(body: RecycleActionBody): Promise<null> {
  return post<null>('/lc/recycle/purge', body)
}
