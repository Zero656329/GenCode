<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getLcProcessPage,
  addLcProcess,
  removeLcProcess,
  deployLcProcess,
  updateLcProcessStatus,
  type LcProcessDef,
  type LcProcessStatus
} from '@/api/lc-process'

/** 流程定义状态元信息：0草稿 1已部署 2停用（lc_process 专用约定，区别于全局 0正常/1停用） */
const STATUS_META: Record<LcProcessStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '已部署', color: 'green' },
  2: { text: '停用', color: 'red' }
}

const router = useRouter()

const loading = ref(false)
const list = ref<LcProcessDef[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '流程编码', dataIndex: 'code' },
  { title: '流程名称', dataIndex: 'name' },
  { title: '分类', dataIndex: 'category', width: 110 },
  { title: '流程 Key', dataIndex: 'flowKey', width: 150 },
  { title: '部署版本', dataIndex: 'publishVersion', width: 90 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark' },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 200 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLcProcessPage({
      keyword: query.keyword || undefined,
      status: query.status ?? undefined,
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
  query.status = null
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

// 新建流程（草稿，bpmnXml 由设计器补充）
const modalOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({ code: '', name: '', category: '', remark: '' })
const formState = reactive(defaultForm())

const formRules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码需以字母开头，仅含字母数字下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
}

function openAdd(): void {
  Object.assign(formState, defaultForm())
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
    await addLcProcess({
      code: formState.code,
      name: formState.name,
      category: formState.category || undefined,
      remark: formState.remark || undefined
    })
    message.success('新建成功')
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 行操作
function onDesign(record: LcProcessDef): void {
  router.push(`/lc/process/design/${record.id}`)
}

async function onDeploy(record: LcProcessDef): Promise<void> {
  const res = await deployLcProcess(record.id)
  const version = res && res.publishVersion ? res.publishVersion : record.publishVersion + 1
  message.success(`部署成功，当前版本 v${version}`)
  loadList()
}

async function onStatusChange(record: LcProcessDef, checked: string | number | boolean): Promise<void> {
  const status: LcProcessStatus = checked ? 1 : 2
  await updateLcProcessStatus(record.id, status)
  message.success(status === 1 ? '已启用' : '已停用')
  loadList()
}

async function onDelete(record: LcProcessDef): Promise<void> {
  await removeLcProcess(record.id)
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
          placeholder="流程编码 / 名称"
          allow-clear
          style="width: 220px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 120px">
          <a-select-option :value="0">草稿</a-select-option>
          <a-select-option :value="1">已部署</a-select-option>
          <a-select-option :value="2">停用</a-select-option>
        </a-select>
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
      <a-button type="primary" v-perm="'lc:process:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建流程
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
        <template v-if="column.dataIndex === 'category'">
          {{ (record as LcProcessDef).category || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'flowKey'">
          {{ (record as LcProcessDef).flowKey || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'publishVersion'">
          {{ (record as LcProcessDef).publishVersion > 0 ? 'v' + (record as LcProcessDef).publishVersion : '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="STATUS_META[(record as LcProcessDef).status].color">
            {{ STATUS_META[(record as LcProcessDef).status].text }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as LcProcessDef).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'lc:process:edit'" @click="onDesign(record as LcProcessDef)">
              设计
            </a-button>
            <a-popconfirm
              title="确定部署该流程吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDeploy(record as LcProcessDef)"
            >
              <a-button type="link" size="small" v-perm="'lc:process:deploy'">部署</a-button>
            </a-popconfirm>
            <a-switch
              v-perm="'lc:process:edit'"
              :checked="(record as LcProcessDef).status === 1"
              :disabled="(record as LcProcessDef).status === 0"
              size="small"
              @change="(checked: string | number | boolean) => onStatusChange(record as LcProcessDef, checked)"
            />
            <a-popconfirm
              title="确定删除该流程吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as LcProcessDef)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:process:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建流程弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      title="新建流程"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="流程编码" name="code">
          <a-input v-model:value="formState.code" placeholder="如 leave_approval（保存后不可修改）" />
        </a-form-item>
        <a-form-item label="流程名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如 请假审批" />
        </a-form-item>
        <a-form-item label="分类" name="category">
          <a-input v-model:value="formState.category" placeholder="如 OA / 人事（可空）" />
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
