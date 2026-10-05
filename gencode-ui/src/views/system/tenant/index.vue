<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getTenantPage,
  addTenant,
  updateTenant,
  removeTenant,
  updateTenantStatus
} from '@/api/tenant'

interface TenantFormState {
  tenantId: string
  name: string
  contactPhone: string
  status: number
  expireTime?: string | null
  remark: string
}

const loading = ref(false)
const list = ref<SysTenant[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '租户编号', dataIndex: 'tenantId', width: 110 },
  { title: '租户名称', dataIndex: 'name' },
  { title: '联系电话', dataIndex: 'contactPhone' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '过期时间', dataIndex: 'expireTime', width: 170 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', key: 'action', width: 160 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getTenantPage({
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

async function onStatusChange(record: SysTenant, checked: string | number | boolean): Promise<void> {
  const status = checked ? 0 : 1
  try {
    await updateTenantStatus(record.id, status)
    message.success(status === 0 ? '已启用' : '已停用')
  } finally {
    loadList()
  }
}

// 新增 / 编辑
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = (): TenantFormState => ({
  tenantId: '',
  name: '',
  contactPhone: '',
  status: 0,
  expireTime: null,
  remark: ''
})
const formState = reactive<TenantFormState>(defaultForm())

const formRules: Record<string, Rule[]> = {
  tenantId: [{ required: true, message: '请输入租户编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }]
}

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

function openEdit(record: SysTenant): void {
  editingId.value = record.id
  Object.assign(formState, {
    tenantId: record.tenantId,
    name: record.name,
    contactPhone: record.contactPhone ?? '',
    status: record.status,
    expireTime: record.expireTime ?? null,
    remark: record.remark ?? ''
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
    const body: TenantSaveBody = {
      id: editingId.value ?? undefined,
      tenantId: formState.tenantId,
      name: formState.name,
      contactPhone: formState.contactPhone || undefined,
      status: formState.status,
      expireTime: formState.expireTime || null,
      remark: formState.remark || undefined
    }
    if (editingId.value) {
      await updateTenant(body)
    } else {
      await addTenant(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: SysTenant): Promise<void> {
  await removeTenant(record.id)
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
          placeholder="租户名称 / 编号"
          allow-clear
          style="width: 220px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 120px">
          <a-select-option :value="0">正常</a-select-option>
          <a-select-option :value="1">停用</a-select-option>
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
      <a-button type="primary" v-perm="'system:tenant:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新增租户
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
          <a-switch
            :checked="(record as SysTenant).status === 0"
            size="small"
            @change="(checked: string | number | boolean) => onStatusChange(record as SysTenant, checked)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'expireTime'">
          {{ (record as SysTenant).expireTime || '永久有效' }}
        </template>
        <template v-else-if="column.dataIndex === 'contactPhone'">
          {{ (record as SysTenant).contactPhone || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as SysTenant).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'system:tenant:edit'" @click="openEdit(record as SysTenant)">
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除该租户吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as SysTenant)"
            >
              <a-button
                type="link"
                danger
                size="small"
                :disabled="(record as SysTenant).tenantId === '000000'"
                v-perm="'system:tenant:delete'"
              >
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑租户' : '新增租户'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="560"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="租户编号" name="tenantId">
              <a-input v-model:value="formState.tenantId" :disabled="!!editingId" placeholder="如 000001" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="租户名称" name="name">
              <a-input v-model:value="formState.name" placeholder="租户名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="联系电话" name="contactPhone">
              <a-input v-model:value="formState.contactPhone" placeholder="联系电话" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态" name="status">
              <a-radio-group v-model:value="formState.status">
                <a-radio :value="0">正常</a-radio>
                <a-radio :value="1">停用</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="过期时间" name="expireTime">
              <a-date-picker
                v-model:value="formState.expireTime"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空表示永久有效"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark">
              <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
