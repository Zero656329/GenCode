import { del, get, post, put } from '@/utils/request'

/**
 * 低代码流程（契约见 docs/api-contract.md「低代码 /lc/process（三期 M3：流程）」）
 * ID 一律 string（后端雪花 ID 序列化为字符串）；
 * 流程定义状态：0=草稿 1=已部署 2=停用（与 lc_form/lc_list 一致，区别于全局 0正常/1停用 约定）。
 */

/** 流程定义状态：0草稿 1已部署 2停用 */
export type LcProcessStatus = 0 | 1 | 2

/** 流程定义 */
export interface LcProcessDef {
  id: string
  code: string
  name: string
  category: string | null
  /** BPMN 2.0 XML 文本 */
  bpmnXml: string | null
  /** 部署时从 BPMN process id 提取的流程 key */
  flowKey: string | null
  /** 部署版本（每次 deploy +1） */
  publishVersion: number
  status: LcProcessStatus
  remark: string | null
  createTime: string
}

/** 流程实例状态 */
export type ProcessInstanceStatus = 'running' | 'approved' | 'rejected' | 'withdrawn'

/** 流程实例（我发起的） */
export interface ProcessInstance {
  id: string
  title: string
  /** 当前停留节点名 */
  currentNode: string | null
  status: ProcessInstanceStatus
  startUser: string
  createTime: string
  formCode: string | null
  formDataId: string | null
}

/** 待办/已办任务 */
export interface TodoTask {
  taskId: string
  instanceId: string
  title: string
  nodeName: string
  startUser: string
  createTime: string
}

/** 轨迹节点办理结果 */
export type TraceResult = 'pending' | 'approved' | 'rejected' | 'transferred'

/** 轨迹任务 */
export interface TraceTask {
  nodeName: string
  assignee: string | null
  result: TraceResult
  comment: string | null
  handleTime: string | null
}

/** 实例详情 + 轨迹（GET /lc/process/instance/{id}） */
export interface ProcessTrace {
  instance: ProcessInstance
  tasks: TraceTask[]
}

// ============ 入参结构 ============

/** 流程定义新建入参（POST /lc/process，新建草稿） */
export interface LcProcessAddBody {
  code: string
  name: string
  category?: string
  bpmnXml?: string | null
  remark?: string
}

/** 流程定义保存入参（PUT /lc/process，code 不可改） */
export interface LcProcessSaveBody {
  id: string
  name: string
  category?: string | null
  bpmnXml?: string | null
  remark?: string | null
}

/** 发起流程入参（POST /lc/process/{code}/start） */
export interface ProcessStartBody {
  title: string
  formCode?: string
  formDataId?: string
  vars: {
    /** 主管审批人账号（模板一/二级审批首节点 ${approver}） */
    approver: string
    /** 第二审批人账号（两级审批模板第二节点 ${approver2}） */
    approver2?: string
  }
}

/** 审批通过/驳回入参 */
export interface TaskCommentBody {
  comment?: string
}

/** 转办入参 */
export interface TaskTransferBody {
  assignee: string
  comment?: string
}

export interface LcProcessPageParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/** 运行时已部署定义（GET /lc/process/publish/{code}） */
export interface LcProcessPublished {
  code: string
  name: string
  flowKey: string
  publishVersion: number
}

// ============ 流程定义 ============

export function getLcProcessPage(params: LcProcessPageParams): Promise<PageResult<LcProcessDef>> {
  return get<PageResult<LcProcessDef>>('/lc/process/page', params)
}

export function getLcProcess(id: string): Promise<LcProcessDef> {
  return get<LcProcessDef>(`/lc/process/${id}`)
}

export function addLcProcess(body: LcProcessAddBody): Promise<null> {
  return post<null>('/lc/process', body)
}

export function updateLcProcess(body: LcProcessSaveBody): Promise<null> {
  return put<null>('/lc/process', body)
}

export function removeLcProcess(id: string): Promise<null> {
  return del<null>(`/lc/process/${id}`)
}

/** 部署到 Flowable：flow_key 取 BPMN process id，publish_version+1，status→1 */
export function deployLcProcess(id: string): Promise<LcProcessDef | null> {
  return put<LcProcessDef | null>(`/lc/process/${id}/deploy`)
}

/** 停用/启用（2=停用；启用恢复为已部署=1）。契约未单列接口，按平台 {id}/status/{status} 惯例封装 */
export function updateLcProcessStatus(id: string, status: LcProcessStatus): Promise<null> {
  return put<null>(`/lc/process/${id}/status/${status}`)
}

/** 运行时接口：按编码取已部署定义 */
export function getPublishedProcess(code: string): Promise<LcProcessPublished> {
  return get<LcProcessPublished>(`/lc/process/publish/${code}`)
}

// ============ 流程运行 ============

/** 发起流程（写实例 + 首个待办任务） */
export function startProcess(code: string, body: ProcessStartBody): Promise<null> {
  return post<null>(`/lc/process/${code}/start`, body)
}

/** 我发起的实例分页 */
export function getMyProcessInstances(params: {
  status?: ProcessInstanceStatus
  pageNum?: number
  pageSize?: number
}): Promise<PageResult<ProcessInstance>> {
  return get<PageResult<ProcessInstance>>('/lc/process/mine/instances', params)
}

/** 我的待办（Flowable assignee=当前人） */
export function getTodoTasks(params: {
  pageNum?: number
  pageSize?: number
}): Promise<PageResult<TodoTask>> {
  return get<PageResult<TodoTask>>('/lc/process/todo', params)
}

/** 我的已办（HistoryService） */
export function getDoneTasks(params: {
  pageNum?: number
  pageSize?: number
}): Promise<PageResult<TodoTask>> {
  return get<PageResult<TodoTask>>('/lc/process/done', params)
}

/** 实例详情 + 办理轨迹（登录即可） */
export function getInstanceTrace(id: string): Promise<ProcessTrace> {
  return get<ProcessTrace>(`/lc/process/instance/${id}`)
}

/** 审批通过并流转到下一节点 */
export function approveTask(taskId: string, body: TaskCommentBody): Promise<null> {
  return post<null>(`/lc/process/task/${taskId}/approve`, body)
}

/** 驳回：终止流程 */
export function rejectTask(taskId: string, body: TaskCommentBody): Promise<null> {
  return post<null>(`/lc/process/task/${taskId}/reject`, body)
}

/** 转办（改 Flowable assignee + 记录 transferred） */
export function transferTask(taskId: string, body: TaskTransferBody): Promise<null> {
  return post<null>(`/lc/process/task/${taskId}/transfer`, body)
}

/** 撤回：仅 running 且当前待办尚未办理 */
export function withdrawInstance(id: string): Promise<null> {
  return post<null>(`/lc/process/instance/${id}/withdraw`)
}
