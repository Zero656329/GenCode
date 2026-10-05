<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getDictTypePage,
  addDictType,
  updateDictType,
  removeDictType,
  getDictDataPage,
  addDictData,
  updateDictData,
  removeDictData
} from '@/api/dict'

// ==================== 字典类型 ====================
interface DictTypeFormState {
  name: string
  dictType: string
  status: number
  remark: string
}

const typeLoading = ref(false)
const typeList = ref<DictType[]>([])
const typeTotal = ref(0)
const typeQuery = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const typeColumns = [
  { title: '字典名称', dataIndex: 'name' },
  { title: '类型键', dataIndex: 'dictType' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', key: 'action', width: 210 }
]

async function loadTypes(): Promise<void> {
  typeLoading.value = true
  try {
    const page = await getDictTypePage({
      keyword: typeQuery.keyword || undefined,
      status: typeQuery.status ?? undefined,
      pageNum: typeQuery.pageNum,
      pageSize: typeQuery.pageSize
    })
    typeList.value = page.list || []
    typeTotal.value = page.total || 0
  } finally {
    typeLoading.value = false
  }
}

function onTypeSearch(): void {
  typeQuery.pageNum = 1
  loadTypes()
}

function onTypeReset(): void {
  typeQuery.keyword = ''
  typeQuery.status = null
  onTypeSearch()
}

function onTypeTableChange(pag: { current?: number; pageSize?: number }): void {
  typeQuery.pageNum = pag.current || 1
  typeQuery.pageSize = pag.pageSize || 10
  loadTypes()
}

const typePagination = computed(() => ({
  current: typeQuery.pageNum,
  pageSize: typeQuery.pageSize,
  total: typeTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// 类型 新增 / 编辑
const typeModalOpen = ref(false)
const typeSaving = ref(false)
const typeEditingId = ref<string | null>(null)
const typeFormRef = ref<FormInstance | null>(null)

const defaultTypeForm = (): DictTypeFormState => ({ name: '', dictType: '', status: 0, remark: '' })
const typeForm = reactive<DictTypeFormState>(defaultTypeForm())

const typeFormRules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入类型键', trigger: 'blur' }]
}

function openTypeAdd(): void {
  typeEditingId.value = null
  Object.assign(typeForm, defaultTypeForm())
  typeModalOpen.value = true
}

function openTypeEdit(record: DictType): void {
  typeEditingId.value = record.id
  Object.assign(typeForm, {
    name: record.name,
    dictType: record.dictType,
    status: record.status,
    remark: record.remark ?? ''
  })
  typeModalOpen.value = true
}

async function handleTypeSave(): Promise<void> {
  try {
    await typeFormRef.value?.validate()
  } catch {
    return
  }
  typeSaving.value = true
  try {
    const body: DictTypeSaveBody = {
      id: typeEditingId.value ?? undefined,
      name: typeForm.name,
      dictType: typeForm.dictType,
      status: typeForm.status,
      remark: typeForm.remark || undefined
    }
    if (typeEditingId.value) {
      await updateDictType(body)
    } else {
      await addDictType(body)
    }
    message.success('保存成功')
    typeModalOpen.value = false
    loadTypes()
  } finally {
    typeSaving.value = false
  }
}

async function onDeleteType(record: DictType): Promise<void> {
  await removeDictType(record.id)
  message.success('删除成功')
  loadTypes()
}

// ==================== 字典数据（抽屉） ====================
interface DictDataFormState {
  label: string
  value: string
  sort: number
  isDefault: number
  status: number
  remark: string
}

const drawerOpen = ref(false)
const currentType = ref<DictType | null>(null)
const dataLoading = ref(false)
const dataList = ref<DictData[]>([])
const dataTotal = ref(0)
const dataQuery = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const dataColumns = [
  { title: '标签', dataIndex: 'label' },
  { title: '键值', dataIndex: 'value' },
  { title: '排序', dataIndex: 'sort', width: 80 },
  { title: '默认', dataIndex: 'isDefault', width: 80 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', key: 'action', width: 160 }
]

function openDataDrawer(record: DictType): void {
  currentType.value = record
  dataQuery.keyword = ''
  dataQuery.pageNum = 1
  drawerOpen.value = true
  loadData()
}

async function loadData(): Promise<void> {
  if (!currentType.value) return
  dataLoading.value = true
  try {
    const page = await getDictDataPage({
      dictType: currentType.value.dictType,
      keyword: dataQuery.keyword || undefined,
      pageNum: dataQuery.pageNum,
      pageSize: dataQuery.pageSize
    })
    dataList.value = page.list || []
    dataTotal.value = page.total || 0
  } finally {
    dataLoading.value = false
  }
}

function onDataSearch(): void {
  dataQuery.pageNum = 1
  loadData()
}

function onDataTableChange(pag: { current?: number; pageSize?: number }): void {
  dataQuery.pageNum = pag.current || 1
  dataQuery.pageSize = pag.pageSize || 10
  loadData()
}

const dataPagination = computed(() => ({
  current: dataQuery.pageNum,
  pageSize: dataQuery.pageSize,
  total: dataTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// 数据 新增 / 编辑
const dataModalOpen = ref(false)
const dataSaving = ref(false)
const dataEditingId = ref<string | null>(null)
const dataFormRef = ref<FormInstance | null>(null)

const defaultDataForm = (): DictDataFormState => ({
  label: '',
  value: '',
  sort: 0,
  isDefault: 0,
  status: 0,
  remark: ''
})
const dataForm = reactive<DictDataFormState>(defaultDataForm())

const dataFormRules: Record<string, Rule[]> = {
  label: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  value: [{ required: true, message: '请输入键值', trigger: 'blur' }]
}

function openDataAdd(): void {
  dataEditingId.value = null
  Object.assign(dataForm, defaultDataForm())
  dataModalOpen.value = true
}

function openDataEdit(record: DictData): void {
  dataEditingId.value = record.id
  Object.assign(dataForm, {
    label: record.label,
    value: record.value,
    sort: record.sort,
    isDefault: record.isDefault,
    status: record.status,
    remark: record.remark ?? ''
  })
  dataModalOpen.value = true
}

async function handleDataSave(): Promise<void> {
  try {
    await dataFormRef.value?.validate()
  } catch {
    return
  }
  if (!currentType.value) return
  dataSaving.value = true
  try {
    const body: DictDataSaveBody = {
      id: dataEditingId.value ?? undefined,
      dictType: currentType.value.dictType,
      label: dataForm.label,
      value: dataForm.value,
      sort: dataForm.sort,
      isDefault: dataForm.isDefault,
      status: dataForm.status,
      remark: dataForm.remark || undefined
    }
    if (dataEditingId.value) {
      await updateDictData(body)
    } else {
      await addDictData(body)
    }
    message.success('保存成功')
    dataModalOpen.value = false
    loadData()
  } finally {
    dataSaving.value = false
  }
}

async function onDeleteData(record: DictData): Promise<void> {
  await removeDictData(record.id)
  message.success('删除成功')
  loadData()
}

onMounted(() => {
  loadTypes()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-input
          v-model:value="typeQuery.keyword"
          placeholder="名称 / 类型键"
          allow-clear
          style="width: 220px"
          @press-enter="onTypeSearch"
        />
        <a-select
          v-model:value="typeQuery.status"
          placeholder="状态"
          allow-clear
          style="width: 120px"
        >
          <a-select-option :value="0">正常</a-select-option>
          <a-select-option :value="1">停用</a-select-option>
        </a-select>
        <a-button type="primary" @click="onTypeSearch">
          <template #icon><search-outlined /></template>
          查询
        </a-button>
        <a-button @click="onTypeReset">
          <template #icon><reload-outlined /></template>
          重置
        </a-button>
      </a-space>
    </div>

    <div class="toolbar">
      <a-button type="primary" v-perm="'system:dict:add'" @click="openTypeAdd">
        <template #icon><plus-outlined /></template>
        新增类型
      </a-button>
      <span></span>
    </div>

    <a-table
      :data-source="typeList"
      :columns="typeColumns"
      :loading="typeLoading"
      row-key="id"
      :pagination="typePagination"
      @change="onTypeTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="(record as DictType).status === 0 ? 'success' : 'error'">
            {{ (record as DictType).status === 0 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as DictType).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'system:dict:edit'" @click="openTypeEdit(record as DictType)">
              编辑
            </a-button>
            <a-button type="link" size="small" v-perm="'system:dict:list'" @click="openDataDrawer(record as DictType)">
              数据
            </a-button>
            <a-popconfirm
              title="删除类型将级联删除其字典数据，确定吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDeleteType(record as DictType)"
            >
              <a-button type="link" danger size="small" v-perm="'system:dict:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 类型 新增 / 编辑 -->
    <a-modal
      v-model:open="typeModalOpen"
      :title="typeEditingId ? '编辑字典类型' : '新增字典类型'"
      :confirm-loading="typeSaving"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleTypeSave"
    >
      <a-form ref="typeFormRef" :model="typeForm" :rules="typeFormRules" layout="vertical">
        <a-form-item label="字典名称" name="name">
          <a-input v-model:value="typeForm.name" placeholder="如 系统状态" />
        </a-form-item>
        <a-form-item label="类型键" name="dictType">
          <a-input v-model:value="typeForm.dictType" :disabled="!!typeEditingId" placeholder="如 sys_normal_status" />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="typeForm.status">
            <a-radio :value="0">正常</a-radio>
            <a-radio :value="1">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="typeForm.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 字典数据抽屉 -->
    <a-drawer
      v-model:open="drawerOpen"
      :title="currentType ? `字典数据 - ${currentType.name}` : '字典数据'"
      :width="720"
    >
      <div class="search-bar">
        <a-space wrap>
          <a-input
            v-model:value="dataQuery.keyword"
            placeholder="标签 / 键值"
            allow-clear
            style="width: 180px"
            @press-enter="onDataSearch"
          />
          <a-button type="primary" @click="onDataSearch">
            <template #icon><search-outlined /></template>
            查询
          </a-button>
          <a-button type="primary" v-perm="'system:dict:add'" @click="openDataAdd">
            <template #icon><plus-outlined /></template>
            新增数据
          </a-button>
        </a-space>
      </div>

      <a-table
        :data-source="dataList"
        :columns="dataColumns"
        :loading="dataLoading"
        row-key="id"
        :pagination="dataPagination"
        size="small"
        @change="onDataTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'isDefault'">
            <a-tag :color="(record as DictData).isDefault === 1 ? 'blue' : 'default'">
              {{ (record as DictData).isDefault === 1 ? '是' : '否' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="(record as DictData).status === 0 ? 'success' : 'error'">
              {{ (record as DictData).status === 0 ? '正常' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" v-perm="'system:dict:edit'" @click="openDataEdit(record as DictData)">
                编辑
              </a-button>
              <a-popconfirm
                title="确定删除该数据吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="onDeleteData(record as DictData)"
              >
                <a-button type="link" danger size="small" v-perm="'system:dict:delete'">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 数据 新增 / 编辑 -->
      <a-modal
        v-model:open="dataModalOpen"
        :title="dataEditingId ? '编辑字典数据' : '新增字典数据'"
        :confirm-loading="dataSaving"
        :mask-closable="false"
        :width="520"
        ok-text="确定"
        cancel-text="取消"
        @ok="handleDataSave"
      >
        <a-form ref="dataFormRef" :model="dataForm" :rules="dataFormRules" layout="vertical">
          <a-form-item label="所属类型">
            <a-input :value="currentType?.dictType" disabled />
          </a-form-item>
          <a-form-item label="标签" name="label">
            <a-input v-model:value="dataForm.label" placeholder="如 正常" />
          </a-form-item>
          <a-form-item label="键值" name="value">
            <a-input v-model:value="dataForm.value" placeholder="如 0" />
          </a-form-item>
          <a-form-item label="排序" name="sort">
            <a-input-number v-model:value="dataForm.sort" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="是否默认" name="isDefault">
            <a-switch
              v-model:checked="dataForm.isDefault"
              :checked-value="1"
              :unchecked-value="0"
              checked-children="是"
              un-checked-children="否"
            />
          </a-form-item>
          <a-form-item label="状态" name="status">
            <a-radio-group v-model:value="dataForm.status">
              <a-radio :value="0">正常</a-radio>
              <a-radio :value="1">停用</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="备注" name="remark">
            <a-textarea v-model:value="dataForm.remark" :rows="2" placeholder="备注" />
          </a-form-item>
        </a-form>
      </a-modal>
    </a-drawer>
  </div>
</template>
