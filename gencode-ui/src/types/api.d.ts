/**
 * GenCode API 类型定义（与 docs/api-contract.md 保持一致）
 * 全局环境类型，无需 import。
 * 约定：ID 一律 string（后端雪花 ID 序列化为字符串）；status 0=正常/启用 1=停用；
 *      时间格式 yyyy-MM-dd HH:mm:ss。
 */

/** 统一响应包装 */
interface R<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 分页响应 */
interface PageResult<T> {
  list: T[]
  total: number
}

/** 登录用户信息 */
interface LoginUser {
  id: string
  username: string
  nickname: string
  avatar: string | null
  tenantId: string
  deptId: string | null
  deptName: string | null
  roles: string[]
  perms: string[]
}

/** 登录入参 */
interface LoginBody {
  username: string
  password: string
  tenantId: string
  captchaId: string
  captchaCode: string
}

/** 登录出参 */
interface LoginResult {
  token: string
  user: LoginUser
}

/** 图形验证码 */
interface CaptchaResult {
  captchaId: string
  image: string
}

/** 菜单类型：M目录 C菜单 F按钮 */
type MenuType = 'M' | 'C' | 'F'

/** 菜单树节点 */
interface MenuNode {
  id: string
  parentId: string
  name: string
  path: string | null
  component: string | null
  menuType: MenuType
  perms: string | null
  icon: string | null
  sort: number
  visible: number
  status: number
  children: MenuNode[]
}

/** 菜单保存入参 */
interface MenuSaveBody {
  id?: string
  parentId: string
  name: string
  path?: string | null
  component?: string | null
  menuType: MenuType
  perms?: string | null
  icon?: string | null
  sort: number
  visible: number
  status: number
}

/** 用户 */
interface SysUser {
  id: string
  tenantId?: string
  deptId: string | null
  username: string
  nickname: string
  phone: string | null
  email: string | null
  avatar?: string | null
  status: number
  remark: string | null
  deptName?: string | null
  createTime?: string | null
}

/** 用户保存入参 */
interface UserSaveBody {
  id?: string
  username: string
  nickname: string
  password?: string
  deptId?: string | null
  phone?: string | null
  email?: string | null
  status: number
  remark?: string | null
  roleIds?: string[] | null
}

/** 角色 */
interface SysRole {
  id: string
  name: string
  roleKey: string
  sort: number
  dataScope: number
  status: number
  remark: string | null
  createTime?: string | null
}

/** 角色保存入参 */
interface RoleSaveBody {
  id?: string
  name: string
  roleKey: string
  sort: number
  dataScope: number
  status: number
  remark?: string | null
}

/** 角色菜单保存入参 */
interface RoleMenuBody {
  menuIds: string[]
}

/** 部门树节点 */
interface SysDept {
  id: string
  parentId: string
  ancestors?: string
  name: string
  orderNo: number
  leader: string | null
  phone: string | null
  email: string | null
  status: number
  remark?: string | null
  createTime?: string | null
  children?: SysDept[]
}

/** 部门保存入参 */
interface DeptSaveBody {
  id?: string
  parentId: string
  name: string
  orderNo: number
  leader?: string | null
  phone?: string | null
  email?: string | null
  status: number
  remark?: string | null
}

/** 字典类型 */
interface DictType {
  id: string
  name: string
  dictType: string
  status: number
  remark: string | null
  createTime?: string | null
}

/** 字典类型保存入参 */
interface DictTypeSaveBody {
  id?: string
  name: string
  dictType: string
  status: number
  remark?: string | null
}

/** 字典数据 */
interface DictData {
  id: string
  dictType: string
  label: string
  value: string
  sort: number
  isDefault: number
  status: number
  remark: string | null
  createTime?: string | null
}

/** 字典数据保存入参 */
interface DictDataSaveBody {
  id?: string
  dictType: string
  label: string
  value: string
  sort: number
  isDefault: number
  status: number
  remark?: string | null
}

/** 字典下拉选项 */
interface DictOption {
  label: string
  value: string
}

/** 系统参数 */
interface SysConfig {
  id: string
  configName: string
  configKey: string
  configValue: string
  status: number
  remark: string | null
  createTime?: string | null
}

/** 系统参数保存入参 */
interface ConfigSaveBody {
  id?: string
  configName: string
  configKey: string
  configValue: string
  status: number
  remark?: string | null
}

/** 租户 */
interface SysTenant {
  id: string
  tenantId: string
  name: string
  contactPhone: string | null
  status: number
  expireTime: string | null
  remark: string | null
  createTime?: string | null
}

/** 租户保存入参 */
interface TenantSaveBody {
  id?: string
  tenantId: string
  name: string
  contactPhone?: string | null
  status: number
  expireTime?: string | null
  remark?: string | null
}

/** 登录日志 */
interface LoginLog {
  id: string
  username: string
  ip: string | null
  browser: string | null
  os: string | null
  status: number
  msg: string | null
  loginTime: string | null
}

/** 操作日志 */
interface OperLog {
  id: string
  title: string | null
  businessType: string | null
  method: string | null
  requestMethod: string | null
  operUrl?: string | null
  operParam?: string | null
  operName: string | null
  ip: string | null
  status: number
  errorMsg: string | null
  costMs: number | null
  operTime: string | null
}

/** 登录趋势项 */
interface LoginTrendItem {
  date: string
  count: number
}

/** 最近登录记录 */
interface LatestLogin {
  username: string
  ip: string | null
  status: number
  msg: string | null
  loginTime: string | null
}

/** 仪表盘统计 */
interface DashboardStats {
  userCount: number
  roleCount: number
  deptCount: number
  tenantCount: number
  menuCount: number
  loginTrend: LoginTrendItem[]
  loginTrend7d: number[]
  latestLogins: LatestLogin[]
}
