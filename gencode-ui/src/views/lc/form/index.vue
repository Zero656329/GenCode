<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getLcFormPage,
  addLcForm,
  removeLcForm,
  publishLcForm,
  updateLcFormStatus,
  type LcForm,
  type LcFormStatus
} from '@/api/lc-form'

/** 表单状态元信息：0草稿 1已发布 2停用（lc_form 专用约定，区别于全局 0正常/1停用） */
const STATUS_META: Record<LcFormStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '已发布', color: 'green' },
  2: { text: '停用', color: 'red' }
}

const router = useRouter()

const loading = ref(false)
const list = ref<LcForm[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '表单编码', dataIndex: 'code' },
  { title: '表单名称', dataIndex: 'name' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '版本', dataIndex: 'version', width: 80 },
  { title: '发布时间', dataIndex: 'publishTime', width: 170 },
  { title: '备注', dataIndex: 'remark' },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 220 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLcFormPage({
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

// 新建表单（草稿）
const modalOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({ code: '', name: '', remark: '' })
const formState = reactive(defaultForm())

const formRules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '请输入表单编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码需以字母开头，仅含字母数字下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入表单名称', trigger: 'blur' }]
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
    await addLcForm({ code: formState.code, name: formState.name, remark: formState.remark || undefined })
    message.success('新建成功')
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 行操作
function onDesign(record: LcForm): void {
  router.push(`/lc/form/design/${record.id}`)
}

async function onPublish(record: LcForm): Promise<void> {
  const res = await publishLcForm(record.id)
  const version = res && res.version ? res.version : record.version + 1
  message.success(`发布成功，当前版本 v${version}`)
  loadList()
}

async function onStatusChange(record: LcForm, checked: string | number | boolean): Promise<void> {
  const status: LcFormStatus = checked ? 1 : 2
  await updateLcFormStatus(record.id, status)
  message.success(status === 1 ? '已启用' : '已停用')
  loadList()
}

async function onDelete(record: LcForm): Promise<void> {
  await removeLcForm(record.id)
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
          placeholder="表单编码 / 名称"
          allow-clear
          style="width: 220px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 120px">
          <a-select-option :value="0">草稿</a-select-option>
          <a-select-option :value="1">已发布</a-select-option>
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
      <a-button type="primary" v-perm="'lc:form:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建表单
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
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="STATUS_META[(record as LcForm).status].color">
            {{ STATUS_META[(record as LcForm).status].text }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'version'">
          v{{ (record as LcForm).version }}
        </template>
        <template v-else-if="column.dataIndex === 'publishTime'">
          {{ (record as LcForm).publishTime || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as LcForm).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'lc:form:edit'" @click="onDesign(record as LcForm)">
              设计
            </a-button>
            <a-popconfirm
              title="确定发布该表单吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onPublish(record as LcForm)"
            >
              <a-button type="link" size="small" v-perm="'lc:form:publish'">发布</a-button>
            </a-popconfirm>
            <a-switch
              v-perm="'lc:form:edit'"
              :checked="(record as LcForm).status === 1"
              :disabled="(record as LcForm).status === 0"
              size="small"
              @change="(checked: string | number | boolean) => onStatusChange(record as LcForm, checked)"
            />
            <a-popconfirm
              title="确定删除该表单吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as LcForm)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:form:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建表单弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      title="新建表单"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="表单编码" name="code">
          <a-input v-model:value="formState.code" placeholder="如 leave_apply（保存后不可修改）" />
        </a-form-item>
        <a-form-item label="表单名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如 请假申请" />
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
