<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  approveTask,
  getDoneTasks,
  getInstanceTrace,
  getMyProcessInstances,
  getTodoTasks,
  rejectTask,
  startProcess,
  transferTask,
  type ProcessInstance,
  type ProcessInstanceStatus,
  type ProcessTrace,
  type TodoTask,
  type TraceResult
} from '@/api/lc-process'

/** 实例状态元信息 */
const INSTANCE_STATUS_META: Record<ProcessInstanceStatus, { text: string; color: string }> = {
  running: { text: '运行中', color: 'blue' },
  approved: { text: '已通过', color: 'green' },
  rejected: { text: '已驳回', color: 'red' },
  withdrawn: { text: '已撤回', color: 'default' }
}

/** 轨迹办理结果元信息 */
const TRACE_RESULT_META: Record<TraceResult, { text: string; color: string }> = {
  pending: { text: '待处理', color: 'blue' },
  approved: { text: '已通过', color: 'green' },
  rejected: { text: '已驳回', color: 'red' },
  transferred: { text: '已转办', color: 'orange' }
}

/** 发起状态筛选选项（'' = 全部） */
const STATUS_OPTIONS: Array<{ value: ProcessInstanceStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'running', label: '运行中' },
  { value: 'approved', label: '已通过' },
  { value: 'rejected', label: '已驳回' },
  { value: 'withdrawn', label: '已撤回' }
]

type TabKey = 'started' | 'todo' | 'done'

const activeTab = ref<TabKey>('started')

function onTabChange(key: string | number): void {
  if (key === 'started') loadStarted()
  else if (key === 'todo') loadTodo()
  else loadDone()
}

// ============ Tab1：我发起的 ============

const startedLoading = ref(false)
const startedList = ref<ProcessInstance[]>([])
const startedTotal = ref(0)
const startedQuery = reactive({
  status: '' as ProcessInstanceStatus | '',
  pageNum: 1,
  pageSize: 10
})

const startedColumns = [
  { title: '标题', dataIndex: 'title' },
  { title: '当前节点', dataIndex: 'currentNode', width: 140 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '发起时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 100 }
]

async function loadStarted(): Promise<void> {
  startedLoading.value = true
  try {
    const page = await getMyProcessInstances({
      status: (startedQuery.status || undefined) as ProcessInstanceStatus | undefined,
      pageNum: startedQuery.pageNum,
      pageSize: startedQuery.pageSize
    })
    startedList.value = page.list || []
    startedTotal.value = page.total || 0
  } finally {
    startedLoading.value = false
  }
}

function onStartedSearch(): void {
  startedQuery.pageNum = 1
  loadStarted()
}

function onStartedTableChange(pag: { current?: number; pageSize?: number }): void {
  startedQuery.pageNum = pag.current || 1
  startedQuery.pageSize = pag.pageSize || 10
  loadStarted()
}

const startedPagination = computed(() => ({
  current: startedQuery.pageNum,
  pageSize: startedQuery.pageSize,
  total: startedTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// ============ 发起流程 ============

const startOpen = ref(false)
const startSaving = ref(false)
const startFormRef = ref<FormInstance | null>(null)

const defaultStart = () => ({
  processCode: '',
  title: '',
  formCode: '',
  approver: 'test',
  approver2: ''
})
const startState = reactive(defaultStart())

const startRules: Record<string, Rule[]> = {
  processCode: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码需以字母开头，仅含字母数字下划线', trigger: 'blur' }
  ],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  approver: [{ required: true, message: '请输入主管审批人账号', trigger: 'blur' }]
}

function openStart(): void {
  Object.assign(startState, defaultStart())
  startOpen.value = true
}

async function handleStart(): Promise<void> {
  try {
    await startFormRef.value?.validate()
  } catch {
    return
  }
  startSaving.value = true
  try {
    await startProcess(startState.processCode, {
      title: startState.title,
      formCode: startState.formCode || undefined,
      vars: {
        approver: startState.approver,
        approver2: startState.approver2 || undefined
      }
    })
    message.success('发起成功')
    startOpen.value = false
    activeTab.value = 'started'
    onStartedSearch()
  } finally {
    startSaving.value = false
  }
}

// ============ Tab2：我的待办 ============

const todoLoading = ref(false)
const todoList = ref<TodoTask[]>([])
const todoTotal = ref(0)
const todoQuery = reactive({ pageNum: 1, pageSize: 10 })

const todoColumns = [
  { title: '标题', dataIndex: 'title' },
  { title: '节点', dataIndex: 'nodeName', width: 150 },
  { title: '发起人', dataIndex: 'startUser', width: 120 },
  { title: '到达时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 100 }
]

async function loadTodo(): Promise<void> {
  todoLoading.value = true
  try {
    const page = await getTodoTasks({ pageNum: todoQuery.pageNum, pageSize: todoQuery.pageSize })
    todoList.value = page.list || []
    todoTotal.value = page.total || 0
  } finally {
    todoLoading.value = false
  }
}

function onTodoTableChange(pag: { current?: number; pageSize?: number }): void {
  todoQuery.pageNum = pag.current || 1
  todoQuery.pageSize = pag.pageSize || 10
  loadTodo()
}

const todoPagination = computed(() => ({
  current: todoQuery.pageNum,
  pageSize: todoQuery.pageSize,
  total: todoTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// 审批抽屉
const approveOpen = ref(false)
const approving = ref(false)
const currentTask = ref<TodoTask | null>(null)
const comment = ref('')
const transferTo = ref('')

function openApprove(task: TodoTask): void {
  currentTask.value = task
  comment.value = ''
  transferTo.value = ''
  approveOpen.value = true
}

async function onApprove(): Promise<void> {
  if (!currentTask.value) return
  approving.value = true
  try {
    await approveTask(currentTask.value.taskId, { comment: comment.value || undefined })
    message.success('审批通过')
    approveOpen.value = false
    loadTodo()
  } finally {
    approving.value = false
  }
}

async function onReject(): Promise<void> {
  if (!currentTask.value) return
  approving.value = true
  try {
    await rejectTask(currentTask.value.taskId, { comment: comment.value || undefined })
    message.success('已驳回')
    approveOpen.value = false
    loadTodo()
  } finally {
    approving.value = false
  }
}

async function onTransfer(): Promise<void> {
  if (!currentTask.value) return
  if (!transferTo.value.trim()) {
    message.warning('请输入转办人账号')
    return
  }
  approving.value = true
  try {
    await transferTask(currentTask.value.taskId, {
      assignee: transferTo.value.trim(),
      comment: comment.value || undefined
    })
    message.success('转办成功')
    approveOpen.value = false
    loadTodo()
  } finally {
    approving.value = false
  }
}

// ============ Tab3：我的已办 ============

const doneLoading = ref(false)
const doneList = ref<TodoTask[]>([])
const doneTotal = ref(0)
const doneQuery = reactive({ pageNum: 1, pageSize: 10 })

const doneColumns = [
  { title: '标题', dataIndex: 'title' },
  { title: '节点', dataIndex: 'nodeName', width: 150 },
  { title: '发起人', dataIndex: 'startUser', width: 120 },
  { title: '时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 100 }
]

async function loadDone(): Promise<void> {
  doneLoading.value = true
  try {
    const page = await getDoneTasks({ pageNum: doneQuery.pageNum, pageSize: doneQuery.pageSize })
    doneList.value = page.list || []
    doneTotal.value = page.total || 0
  } finally {
    doneLoading.value = false
  }
}

function onDoneTableChange(pag: { current?: number; pageSize?: number }): void {
  doneQuery.pageNum = pag.current || 1
  doneQuery.pageSize = pag.pageSize || 10
  loadDone()
}

const donePagination = computed(() => ({
  current: doneQuery.pageNum,
  pageSize: doneQuery.pageSize,
  total: doneTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// ============ 轨迹抽屉 ============

const traceOpen = ref(false)
const traceLoading = ref(false)
const trace = ref<ProcessTrace | null>(null)

async function openTrace(instanceId: string): Promise<void> {
  traceOpen.value = true
  traceLoading.value = true
  trace.value = null
  try {
    trace.value = await getInstanceTrace(instanceId)
  } finally {
    traceLoading.value = false
  }
}

const traceInstance = computed(() => trace.value?.instance ?? null)
const traceTasks = computed(() => trace.value?.tasks ?? [])

onMounted(() => {
  loadStarted()
})
</script>

<template>
  <div>
    <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
      <!-- Tab1 我发起的 -->
      <a-tab-pane key="started" tab="我发起的">
        <div class="search-bar">
          <a-space wrap>
            <a-select
              v-model:value="startedQuery.status"
              placeholder="状态"
              style="width: 120px"
              :options="STATUS_OPTIONS"
            />
            <a-button type="primary" @click="onStartedSearch">
              <template #icon><search-outlined /></template>
              查询
            </a-button>
            <a-button @click="(() => { startedQuery.status = ''; onStartedSearch() })()">
              <template #icon><reload-outlined /></template>
              重置
            </a-button>
          </a-space>
        </div>
        <div class="toolbar">
          <a-button type="primary" v-perm="'lc:process:start'" @click="openStart">
            <template #icon><plus-outlined /></template>
            发起流程
          </a-button>
          <span></span>
        </div>
        <a-table
          :data-source="startedList"
          :columns="startedColumns"
          :loading="startedLoading"
          row-key="id"
          :pagination="startedPagination"
          @change="onStartedTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'currentNode'">
              {{ (record as ProcessInstance).currentNode || '-' }}
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="INSTANCE_STATUS_META[(record as ProcessInstance).status].color">
                {{ INSTANCE_STATUS_META[(record as ProcessInstance).status].text }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openTrace((record as ProcessInstance).id)">
                轨迹
              </a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- Tab2 我的待办 -->
      <a-tab-pane key="todo" tab="我的待办">
        <a-table
          :data-source="todoList"
          :columns="todoColumns"
          :loading="todoLoading"
          row-key="taskId"
          :pagination="todoPagination"
          @change="onTodoTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button
                type="link"
                size="small"
                v-perm="'lc:process:approve'"
                @click="openApprove(record as TodoTask)"
              >
                审批
              </a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- Tab3 我的已办 -->
      <a-tab-pane key="done" tab="我的已办">
        <a-table
          :data-source="doneList"
          :columns="doneColumns"
          :loading="doneLoading"
          row-key="taskId"
          :pagination="donePagination"
          @change="onDoneTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openTrace((record as TodoTask).instanceId)">
                轨迹
              </a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 发起流程弹窗 -->
    <a-modal
      v-model:open="startOpen"
      title="发起流程"
      :confirm-loading="startSaving"
      :mask-closable="false"
      :width="520"
      ok-text="提交"
      cancel-text="取消"
      @ok="handleStart"
    >
      <a-form ref="startFormRef" :model="startState" :rules="startRules" layout="vertical">
        <a-form-item label="流程编码" name="processCode">
          <a-input v-model:value="startState.processCode" placeholder="已部署流程编码，如 leave_approval" />
        </a-form-item>
        <a-form-item label="标题" name="title">
          <a-input v-model:value="startState.title" placeholder="如 张三的请假申请" />
        </a-form-item>
        <a-form-item label="关联表单编码" name="formCode">
          <a-input v-model:value="startState.formCode" placeholder="可空，如 leave_apply" allow-clear />
        </a-form-item>
        <a-form-item label="主管审批人（approver）" name="approver">
          <a-input v-model:value="startState.approver" placeholder="审批人账号" />
        </a-form-item>
        <a-form-item label="第二审批人（approver2，两级审批用）" name="approver2">
          <a-input v-model:value="startState.approver2" placeholder="可空，两级审批流程第二节点审批人账号" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 审批抽屉 -->
    <a-drawer
      v-model:open="approveOpen"
      :title="currentTask ? `审批 - ${currentTask.title}` : '审批'"
      :width="460"
      destroy-on-close
    >
      <template v-if="currentTask">
        <a-descriptions :column="1" size="small" class="approve-desc">
          <a-descriptions-item label="当前节点">{{ currentTask.nodeName }}</a-descriptions-item>
          <a-descriptions-item label="发起人">{{ currentTask.startUser }}</a-descriptions-item>
          <a-descriptions-item label="到达时间">{{ currentTask.createTime || '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-form layout="vertical">
          <a-form-item label="审批意见">
            <a-textarea
              v-model:value="comment"
              :rows="4"
              placeholder="请输入审批意见（可空）"
              allow-clear
            />
          </a-form-item>
        </a-form>
        <a-space>
          <a-button
            type="primary"
            :loading="approving"
            v-perm="'lc:process:approve'"
            @click="onApprove"
          >
            通过
          </a-button>
          <a-button danger :loading="approving" v-perm="'lc:process:approve'" @click="onReject">
            驳回
          </a-button>
        </a-space>
        <a-divider>转办</a-divider>
        <a-space>
          <a-input
            v-model:value="transferTo"
            placeholder="转办人账号"
            style="width: 200px"
            allow-clear
          />
          <a-button :loading="approving" v-perm="'lc:process:approve'" @click="onTransfer">
            转办
          </a-button>
        </a-space>
      </template>
    </a-drawer>

    <!-- 轨迹抽屉 -->
    <a-drawer
      v-model:open="traceOpen"
      :title="traceInstance ? `流程轨迹 - ${traceInstance.title}` : '流程轨迹'"
      :width="520"
      destroy-on-close
    >
      <a-spin :spinning="traceLoading">
        <template v-if="traceInstance">
          <a-descriptions :column="1" size="small" class="trace-desc">
            <a-descriptions-item label="状态">
              <a-tag :color="INSTANCE_STATUS_META[traceInstance.status].color">
                {{ INSTANCE_STATUS_META[traceInstance.status].text }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="当前节点">
              {{ traceInstance.currentNode || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="发起人">{{ traceInstance.startUser }}</a-descriptions-item>
            <a-descriptions-item label="发起时间">{{ traceInstance.createTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="关联表单">
              {{ traceInstance.formCode || '-' }}
            </a-descriptions-item>
          </a-descriptions>
          <a-divider>办理轨迹</a-divider>
          <a-timeline v-if="traceTasks.length > 0" class="trace-timeline">
            <a-timeline-item v-for="(task, idx) in traceTasks" :key="idx">
              <div class="trace-node">
                <span class="trace-node-name">{{ task.nodeName }}</span>
                <a-tag :color="TRACE_RESULT_META[task.result]?.color || 'default'">
                  {{ TRACE_RESULT_META[task.result]?.text || task.result }}
                </a-tag>
              </div>
              <div class="trace-meta">
                办理人：{{ task.assignee || '-' }}
                <template v-if="task.handleTime">　时间：{{ task.handleTime }}</template>
              </div>
              <div v-if="task.comment" class="trace-comment">意见：{{ task.comment }}</div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无轨迹数据" />
        </template>
        <a-empty v-else-if="!traceLoading" description="未获取到实例信息" />
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped>
.approve-desc,
.trace-desc {
  margin-bottom: 8px;
}

.trace-timeline {
  margin-top: 8px;
}

.trace-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-node-name {
  font-weight: 600;
}

.trace-meta {
  margin-top: 2px;
  color: #999;
  font-size: 12px;
}

.trace-comment {
  margin-top: 2px;
  padding: 4px 8px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  color: #666;
}
</style>
