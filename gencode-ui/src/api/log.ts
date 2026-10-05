import { get } from '@/utils/request'

export interface LoginPageParams {
  username?: string
  status?: number
  ip?: string
  beginTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

export interface OperPageParams {
  title?: string
  status?: number
  operName?: string
  beginTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}

/** 登录日志分页 */
export function getLoginLogPage(params: LoginPageParams): Promise<PageResult<LoginLog>> {
  return get<PageResult<LoginLog>>('/system/log/login/page', params)
}

/** 操作日志分页 */
export function getOperLogPage(params: OperPageParams): Promise<PageResult<OperLog>> {
  return get<PageResult<OperLog>>('/system/log/oper/page', params)
}
