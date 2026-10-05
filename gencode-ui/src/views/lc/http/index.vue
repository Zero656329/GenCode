<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getLcHttpPage,
  addLcHttp,
  updateLcHttp,
  removeLcHttp,
  callLcHttp,
  getLcHttpLogPage,
  type LcHttpApi,
  type HttpCallResult,
  type HttpLogRow
} from '@/api/lc-http'

/**
 * 接口管理（/lc/http）
 * - url/bodyTemplate 中可用 {param} 占位，调用时由参数 JSON 替换；
 * - 契约无独立状态切换接口，status 在新建/编辑弹窗中维护；
 * - 调用结果 respBody 后端已截断 2000 字符，日志响应同样截断展示。
 */

const loading = ref(false)
const list = ref<LcHttpApi[]>([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const columns = [
  { title: '编码', dataIndex: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', width: 160 },
  { title: '方法', dataIndex: 'method', width: 90 },
  { title: 'URL', dataIndex: 'url', ellipsis: true },
  { title: '超时', dataIndex: 'timeoutMs', width: 100 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', key: 'action', width: 250 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLcHttpPage({
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.pageNum = 1
  loadList()
}

function onResetSearch(): void {
  query.keyword = ''
  onSearch()
}

function onTableChange(pag: { current?: number; pageSize?: number }): void {
  query.pageNum = pag.current || 1
  query.pageSize = pag.pageSize || 10
  loadList()
}

const pagination = computed(() => ({
  current: query.pageNum,
  pageSize: query.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// ============ 新建 / 编辑 ============

const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({
  code: '',
  name: '',
  method: 'GET' as 'GET' | 'POST',
  url: '',
  headersJson: '',
  bodyTemplate: '',
  timeoutMs: 5000,
  status: 0 as 0 | 1,
  remark: ''
})
const formState = reactive(defaultForm())

const formRules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '请输入接口编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '仅允许字母开头的字母/数字/下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入请求 URL', trigger: 'blur' }],
  timeoutMs: [{ required: true, message: '请输入超时时间', trigger: 'change' }]
}

/** headersJson 选填，填了必须是 JSON 对象 */
function validateHeadersJson(_rule: Rule, value: string): Promise<void> {
  if (!value || !value.trim()) return Promise.resolve()
  try {
    const parsed: unknown = JSON.parse(value)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return Promise.reject('必须是 JSON 对象，如 {"Content-Type":"application/json"}')
    }
    return Promise.resolve()
  } catch {
    return Promise.reject('JSON 格式不正确')
  }
}
const headersJsonRules: Rule[] = [{ validator: validateHeadersJson, trigger: 'blur' }]

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

function openEdit(record: LcHttpApi): void {
  editingId.value = record.id
  Object.assign(formState, {
    code: record.code,
    name: record.name,
    method: record.method,
    url: record.url,
    headersJson: record.headersJson || '',
    bodyTemplate: record.bodyTemplate || '',
    timeoutMs: record.timeoutMs ?? 5000,
    status: record.status,
    remark: record.remark || ''
  })
  modalOpen.value = true
}

async function handleSave(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body = {
      code: formState.code,
      name: formState.name,
      method: formState.method,
      url: formState.url,
      headersJson: formState.headersJson || undefined,
      bodyTemplate: formState.bodyTemplate || undefined,
      timeoutMs: formState.timeoutMs,
      status: formState.status,
      remark: formState.remark || undefined
    }
    if (editingId.value) {
      await updateLcHttp({ id: editingId.value, ...body })
      message.success('保存成功')
    } else {
      await addLcHttp(body)
      message.success('新建成功')
    }
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ============ 调用 ============

const callOpen = ref(false)
const calling = ref(false)
const callRecord = ref<LcHttpApi | null>(null)
const paramsText = ref('{}')
const callResult = ref<HttpCallResult | null>(null)

function openCall(record: LcHttpApi): void {
  callRecord.value = record
  paramsText.value = '{}'
  callResult.value = null
  callOpen.value = true
}

async function handleCall(): Promise<void> {
  if (!callRecord.value) return
  let params: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(paramsText.value || '{}')
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      message.warning('参数必须是 JSON 对象，如 {"id": 1}')
      return
    }
    params = parsed as Record<string, unknown>
  } catch {
    message.warning('参数 JSON 格式不正确')
    return
  }
  calling.value = true
  try {
    callResult.value = await callLcHttp(callRecord.value.code, { params })
  } finally {
    calling.value = false
  }
}

// ============ 调用日志抽屉 ============

const logOpen = ref(false)
const logLoading = ref(false)
const logRecord = ref<LcHttpApi | null>(null)
const logList = ref<HttpLogRow[]>([])
const logTotal = ref(0)
const logQuery = reactive({ pageNum: 1, pageSize: 10 })

const logColumns = [
  { title: '时间', dataIndex: 'createTime', width: 170 },
  { title: '成功', dataIndex: 'success', width: 80 },
  { title: '耗时', dataIndex: 'costMs', width: 100 },
  { title: '响应', dataIndex: 'respBody', ellipsis: true }
]

const logPagination = computed(() => ({
  current: logQuery.pageNum,
  pageSize: logQuery.pageSize,
  total: logTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

function openLog(record: LcHttpApi): void {
  logRecord.value = record
  logQuery.pageNum = 1
  logOpen.value = true
  loadLogs()
}

async function loadLogs(): Promise<void> {
  if (!logRecord.value) return
  logLoading.value = true
  try {
    const page = await getLcHttpLogPage({
      apiCode: logRecord.value.code,
      pageNum: logQuery.pageNum,
      pageSize: logQuery.pageSize
    })
    logList.value = page.list || []
    logTotal.value = page.total || 0
  } finally {
    logLoading.value = false
  }
}

function onLogTableChange(pag: { current?: number; pageSize?: number }): void {
  logQuery.pageNum = pag.current || 1
  logQuery.pageSize = pag.pageSize || 10
  loadLogs()
}

/** 响应截断展示 */
function trunc(text: string | null, n = 80): string {
  if (!text) return '-'
  return text.length > n ? text.slice(0, n) + '…' : text
}

// ============ 删除 ============

async function onDelete(record: LcHttpApi): Promise<void> {
  await removeLcHttp(record.id)
  message.success('删除成功')
  loadList()
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-input
          v-model:value="query.keyword"
          placeholder="编码 / 名称"
          allow-clear
          style="width: 220px"
          @press-enter="onSearch"
        />
        <a-button type="primary" @click="onSearch">
          <template #icon><search-outlined /></template>
          查询
        </a-button>
        <a-button @click="onResetSearch">
          <template #icon><reload-outlined /></template>
          重置
        </a-button>
      </a-space>
    </div>

    <div class="toolbar">
      <a-button type="primary" v-perm="'lc:http:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建接口
      </a-button>
      <span></span>
    </div>

    <a-table
      :data-source="list"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="pagination"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'method'">
          <a-tag :color="(record as LcHttpApi).method === 'GET' ? 'geekblue' : 'green'">
            {{ (record as LcHttpApi).method }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'timeoutMs'">
          {{ (record as LcHttpApi).timeoutMs }} ms
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="(record as LcHttpApi).status === 0 ? 'success' : 'default'">
            {{ (record as LcHttpApi).status === 0 ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'lc:http:call'" @click="openCall(record as LcHttpApi)">
              调用
            </a-button>
            <a-button type="link" size="small" v-perm="'lc:http:list'" @click="openLog(record as LcHttpApi)">
              日志
            </a-button>
            <a-button type="link" size="small" v-perm="'lc:http:edit'" @click="openEdit(record as LcHttpApi)">
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除该接口吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as LcHttpApi)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:http:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建/编辑接口弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑接口' : '新建接口'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="620"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="编码" name="code">
              <a-input
                v-model:value="formState.code"
                placeholder="如 queryUser"
                :disabled="!!editingId"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" name="name">
              <a-input v-model:value="formState.name" placeholder="如 查询用户信息" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="请求方法" name="method">
          <a-radio-group v-model:value="formState.method">
            <a-radio-button value="GET">GET</a-radio-button>
            <a-radio-button value="POST">POST</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="URL" name="url" extra="支持 {param} 占位，调用时由参数替换，如 https://api.example.com/users/{id}">
          <a-input v-model:value="formState.url" placeholder="https://api.example.com/users/{id}" />
        </a-form-item>
        <a-form-item label="请求头（JSON）" name="headersJson" :rules="headersJsonRules" extra='JSON 对象，如 {"Content-Type":"application/json"}'>
          <a-textarea v-model:value="formState.headersJson" :rows="3" placeholder='{"Content-Type":"application/json"}' />
        </a-form-item>
        <a-form-item
          v-if="formState.method === 'POST'"
          label="请求体模板"
          name="bodyTemplate"
          extra="支持 {param} 占位，如 {&quot;id&quot;: {id}}"
        >
          <a-textarea v-model:value="formState.bodyTemplate" :rows="3" placeholder='{"id": {id}}' />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="超时时间（毫秒）" name="timeoutMs">
              <a-input-number v-model:value="formState.timeoutMs" :min="100" :max="300000" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态" name="status">
              <a-radio-group v-model:value="formState.status">
                <a-radio :value="0">启用</a-radio>
                <a-radio :value="1">停用</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
        <a-alert v-if="editingId" type="info" show-icon message="编码不可修改；启用/停用在编辑弹窗中维护。" />
      </a-form>
    </a-modal>

    <!-- 调用弹窗 -->
    <a-modal
      v-model:open="callOpen"
      :title="`调用接口 - ${callRecord?.code || ''}`"
      :mask-closable="false"
      :width="680"
      ok-text="调用"
      :confirm-loading="calling"
      cancel-text="关闭"
      :ok-button-props="{ danger: false }"
      @ok="handleCall"
    >
      <a-descriptions v-if="callRecord" size="small" :column="1" bordered class="call-desc">
        <a-descriptions-item label="方法">
          <a-tag :color="callRecord.method === 'GET' ? 'geekblue' : 'green'">{{ callRecord.method }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="URL">
          <span class="call-url">{{ callRecord.url }}</span>
        </a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="参数（JSON 对象，键对应 {param} 占位）">
          <a-textarea v-model:value="paramsText" :rows="4" placeholder='{"id": 1}' />
        </a-form-item>
      </a-form>
      <template v-if="callResult">
        <a-space class="call-result-bar">
          <a-tag :color="callResult.success ? 'success' : 'error'">
            {{ callResult.success ? '成功' : '失败' }}
          </a-tag>
          <span>耗时 {{ callResult.costMs }} ms</span>
        </a-space>
        <pre class="resp-pre">{{ callResult.respBody || '-' }}</pre>
      </template>
      <a-empty v-else description="点击「调用」发起请求" :image-style="{ height: '48px' }" />
    </a-modal>

    <!-- 调用日志抽屉 -->
    <a-drawer
      v-model:open="logOpen"
      :title="`调用日志 - ${logRecord?.code || ''}`"
      :width="760"
      destroy-on-close
    >
      <a-table
        :data-source="logList"
        :columns="logColumns"
        :loading="logLoading"
        row-key="id"
        :pagination="logPagination"
        size="small"
        @change="onLogTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'success'">
            <a-tag :color="(record as HttpLogRow).success === 0 ? 'success' : 'error'">
              {{ (record as HttpLogRow).success === 0 ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'costMs'">
            {{ (record as HttpLogRow).costMs ?? '-' }} ms
          </template>
          <template v-else-if="column.dataIndex === 'respBody'">
            <a-tooltip :title="(record as HttpLogRow).respBody || ''">
              {{ trunc((record as HttpLogRow).respBody) }}
            </a-tooltip>
          </template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<style scoped>
.call-desc {
  margin-bottom: 16px;
}

.call-url {
  word-break: break-all;
}

.call-result-bar {
  margin-bottom: 8px;
}

.resp-pre {
  max-height: 280px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background-color: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
</style>
