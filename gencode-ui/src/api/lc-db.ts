import { get, post } from '@/utils/request'

/**
 * 可视化建表（契约见 docs/api-contract.md「低代码 /lc（五期 M5：数据管理与代码生成）」）
 * 表名/列名走后端标识符白名单；仅允许 CREATE TABLE 语义（引擎内部生成，不接收裸 DDL 文本）。
 * 约定：id（雪花 Long）/tenant_id/审计字段/逻辑删除列由平台建表时自动追加，前端无需传入。
 */

/** 页面可用的基础类型（契约枚举，typemap 拉取失败时的兜底选项） */
export const DEFAULT_TYPE_OPTIONS: string[] = [
  'varchar',
  'int',
  'bigint',
  'datetime',
  'text',
  'decimal'
]

/** 列定义入参（POST /lc/db/ddl/* 的 columns 元素） */
export interface ColumnSpec {
  name: string
  /** 基础类型：varchar/int/bigint/datetime/text/decimal（以 GET /lc/db/typemap 为准） */
  typeName: string
  /** varchar=长度；decimal=精度（契约仅约定 length 字段） */
  length: number | null
  /** decimal 小数位（契约未单列，后端未实现时自动忽略） */
  scale?: number | null
  comment: string | null
  notNull: boolean
  pk: boolean
  defaultValue: string | null
}

/** 建表入参（POST /lc/db/ddl/preview 与 /lc/db/ddl/execute 同构） */
export interface TableSpec {
  tableName: string
  tableComment: string | null
  columns: ColumnSpec[]
}

/** DDL 预览结果（POST /lc/db/ddl/preview） */
export interface DdlPreviewResult {
  ddl: string
}

/** 逆向读表结果（GET /lc/db/columns） */
export interface ColumnInfo {
  columnName: string
  /** 可能带长度描述，如 varchar(50) / decimal(10,2) */
  typeName: string
  comment: string | null
  /** 可空：后端可能返回 boolean 或 'YES'/'NO' */
  nullable: boolean | string | number | null
  /** 是否主键：可能返回 boolean 或 1/0 */
  pk: boolean | string | number | null
}

/**
 * 方言类型映射表（GET /lc/db/typemap）。
 * 契约未约定具体结构（可能为 string[]、对象数组或按方言分组的 map），故以 unknown 接收后归一化。
 */
export function getDbTypeMap(): Promise<unknown> {
  return get<unknown>('/lc/db/typemap')
}

/**
 * 将 typemap 响应归一化为可下拉的类型名列表（小写、去重）。
 * 兼容：string[] / [{typeName|name|type}] / { 方言: string[] } / { 类型: 方言类型名 }；
 * 结果末尾补齐契约要求的默认类型，保证下拉始终可用。
 */
export function normalizeTypeOptions(raw: unknown): string[] {
  const out: string[] = []
  const push = (v: unknown): void => {
    if (typeof v !== 'string') return
    const s = v.trim().toLowerCase()
    if (s && /^[a-z]/.test(s) && !out.includes(s)) out.push(s)
  }
  const pushObj = (o: Record<string, unknown>): void => {
    push(o.typeName)
    push(typeof o.name === 'string' ? o.name : undefined)
    push(typeof o.type === 'string' ? o.type : undefined)
  }
  if (Array.isArray(raw)) {
    raw.forEach((it) => {
      if (it && typeof it === 'object') pushObj(it as Record<string, unknown>)
      else push(it)
    })
  } else if (raw && typeof raw === 'object') {
    // 先取值（方言→类型数组 / 类型→方言名），值收集不到时再用键名（类型名→映射 的键即类型）
    for (const val of Object.values(raw as Record<string, unknown>)) {
      if (Array.isArray(val)) val.forEach(push)
      else if (val && typeof val === 'object') pushObj(val as Record<string, unknown>)
      else push(val)
    }
    if (out.length === 0) {
      Object.keys(raw as Record<string, unknown>).forEach(push)
    }
  }
  DEFAULT_TYPE_OPTIONS.forEach((t) => {
    if (!out.includes(t)) out.push(t)
  })
  return out
}

/** 逆向读表结构（平台主库），供编辑器回填 */
export function getDbColumns(tableName: string): Promise<ColumnInfo[]> {
  return get<ColumnInfo[]>('/lc/db/columns', { tableName })
}

/** 预览 DDL（不执行） */
export function previewDdl(spec: TableSpec): Promise<DdlPreviewResult> {
  return post<DdlPreviewResult>('/lc/db/ddl/preview', spec)
}

/** 执行建表（表已存在报错） */
export function executeDdl(spec: TableSpec): Promise<null> {
  return post<null>('/lc/db/ddl/execute', spec)
}

/** nullable 是否为"可空"（兼容 boolean / 'YES'/'NO' / 1/0） */
export function isNullable(v: ColumnInfo['nullable']): boolean {
  if (v === null || v === undefined) return true
  if (typeof v === 'string') return v.toUpperCase() === 'YES'
  return v === 1 || v === true
}

/** pk 是否为真（兼容 boolean / 1/0 / 'YES'） */
export function isPk(v: ColumnInfo['pk']): boolean {
  if (v === null || v === undefined) return false
  if (typeof v === 'string') return v.toUpperCase() === 'YES'
  return v === 1 || v === true
}
