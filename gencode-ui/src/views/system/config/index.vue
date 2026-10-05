<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getConfigPage, addConfig, updateConfig, removeConfig } from '@/api/config'

interface ConfigFormState {
  configName: string
  configKey: string
  configValue: string
  status: number
  remark: string
}

const loading = ref(false)
const list = ref<SysConfig[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '参数名称', dataIndex: 'configName' },
  { title: '参数键名', dataIndex: 'configKey' },
  { title: '参数键值', dataIndex: 'configValue' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', key: 'action', width: 160 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getConfigPage({
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

// 新增 / 编辑
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = (): ConfigFormState => ({
  configName: '',
  configKey: '',
  configValue: '',
  status: 0,
  remark: ''
})
const formState = reactive<ConfigFormState>(defaultForm())

const formRules: Record<string, Rule[]> = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入参数键名', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数键值', trigger: 'blur' }]
}

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

function openEdit(record: SysConfig): void {
  editingId.value = record.id
  Object.assign(formState, {
    configName: record.configName,
    configKey: record.configKey,
    configValue: record.configValue,
    status: record.status,
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
    const body: ConfigSaveBody = {
      id: editingId.value ?? undefined,
      configName: formState.configName,
      configKey: formState.configKey,
      configValue: formState.configValue,
      status: formState.status,
      remark: formState.remark || undefined
    }
    if (editingId.value) {
      await updateConfig(body)
    } else {
      await addConfig(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: SysConfig): Promise<void> {
  await removeConfig(record.id)
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
          placeholder="参数名称 / 键名"
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
      <a-button type="primary" v-perm="'system:config:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新增参数
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
          <a-tag :color="(record as SysConfig).status === 0 ? 'success' : 'error'">
            {{ (record as SysConfig).status === 0 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as SysConfig).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'system:config:edit'" @click="openEdit(record as SysConfig)">
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除该参数吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as SysConfig)"
            >
              <a-button type="link" danger size="small" v-perm="'system:config:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑参数' : '新增参数'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="参数名称" name="configName">
          <a-input v-model:value="formState.configName" placeholder="如 平台名称" />
        </a-form-item>
        <a-form-item label="参数键名" name="configKey">
          <a-input v-model:value="formState.configKey" :disabled="!!editingId" placeholder="如 platform.name" />
        </a-form-item>
        <a-form-item label="参数键值" name="configValue">
          <a-input v-model:value="formState.configValue" placeholder="参数值" />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="formState.status">
            <a-radio :value="0">正常</a-radio>
            <a-radio :value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
