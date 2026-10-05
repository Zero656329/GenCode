import { get } from '@/utils/request'

/** 仪表盘统计（数量/登录趋势/最近登录） */
export function getDashboardStats(): Promise<DashboardStats> {
  return get<DashboardStats>('/dashboard/stats')
}
