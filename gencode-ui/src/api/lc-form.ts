import { del, get, post, put } from '@/utils/request'

/**
 * 低代码表单（契约见 docs/api-contract.md「低代码 /lc」章节）
 * ID 一律 string（后端雪花 ID 序列化为字符串）；
 * 表单状态：0=草稿 1=已发布 2=停用（注意与全局 0正常/1停用 约定不同）。
 */

/** 表单状态：0草稿 1已发布 2停用 */
export type LcFormStatus = 0 | 1 | 2

/** 低代码表单定义 */
export interface LcForm {
  id: string
  code: string
  name: string
  schemaJson: string | null
  status: LcFormStatus
  version: number
  publishTime: string | null
  remark: string | null
  createTime: string
}

/** 表单项组件类型 */
export type FormItemType =
  | 'input'
  | 'number'
  | 'radio'
  | 'checkbox'
  | 'select'
  | 'date'
  | 'switch'
  | 'textarea'
  | 'group'

/** 全部组件类型（拖拽白名单 / Schema 校验用） */
export const FORM_ITEM_TYPES: FormItemType[] = [
  'input',
  'number',
  'radio',
  'checkbox',
  'select',
  'date',
  'switch',
  'textarea',
  'group'
]

/** 组件默认标签（设计器新建 / 预览用） */
export const FORM_ITEM_LABELS: Record<FormItemType, string> = {
  input: '输入框',
  number: '数字',
  radio: '单选',
  checkbox: '多选',
  select: '下拉',
  date: '日期',
  switch: '开关',
  textarea: '多行文本',
  group: '分组容器'
}

/** 静态选项 */
export interface FormItemOption {
  label: string
  value: string
}

/** 表单项默认值：按类型可为字符串 / 数字 / 布尔 / 字符串数组（多选） */
export type FormItemDefaultValue = string | number | boolean | string[] | null

/** 表单项 */
export interface FormItem {
  type: FormItemType
  field: string
  label: string
  required: boolean
  placeholder: string | null
  defaultValue: FormItemDefaultValue
  options: FormItemOption[] | null
  dictType: string | null
  span: number
}

/** 表单设计 Schema */
export interface FormSchema {
  list: FormItem[]
}

/** 表单定义保存入参（PUT /lc/form，code 不可修改） */
export interface LcFormSaveBody {
  id: string
  name: string
  remark: string | null
  schemaJson: string | null
}

/** 表单新建入参（POST /lc/form，新建草稿：schemaJson 空、version=0、status=0） */
export interface LcFormAddBody {
  code: string
  name: string
  remark?: string
}

/** 运行时发布表单（GET /lc/form/publish/{code}） */
export interface LcFormPublished {
  code: string
  name: string
  version: number
  schemaJson: string | null
}

/** 填报提交入参（复杂值由后端整体序列化为 JSON 存储） */
export interface LcFormDataBody {
  data: Record<string, unknown>
}

export interface LcFormPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

// ============ 表单定义 ============

export function getLcFormPage(params: LcFormPageParams): Promise<PageResult<LcForm>> {
  return get<PageResult<LcForm>>('/lc/form/page', params)
}

export function getLcForm(id: string): Promise<LcForm> {
  return get<LcForm>(`/lc/form/${id}`)
}

export function addLcForm(body: LcFormAddBody): Promise<null> {
  return post<null>('/lc/form', body)
}

export function updateLcForm(body: LcFormSaveBody): Promise<null> {
  return put<null>('/lc/form', body)
}

export function removeLcForm(id: string): Promise<null> {
  return del<null>(`/lc/form/${id}`)
}

/** 发布：version+1，发布快照=当前 schemaJson，status→1 */
export function publishLcForm(id: string): Promise<LcForm | null> {
  return put<LcForm | null>(`/lc/form/${id}/publish`)
}

/** 停用/启用（2=停用；启用恢复为已发布=1）。契约未单列接口，按平台 {id}/status/{status} 惯例封装 */
export function updateLcFormStatus(id: string, status: LcFormStatus): Promise<null> {
  return put<null>(`/lc/form/${id}/status/${status}`)
}

/** 运行时接口：按编码取已发布表单（未发布过报"表单未发布"） */
export function getPublishedForm(code: string): Promise<LcFormPublished> {
  return get<LcFormPublished>(`/lc/form/publish/${code}`)
}

// ============ 表单数据 ============

/** 填报提交（表单须已发布；服务端整体转 JSON 字符串存 data_json） */
export function submitLcFormData(code: string, body: LcFormDataBody): Promise<null> {
  return post<null>(`/lc/form/data/${code}`, body)
}
