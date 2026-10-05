<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getDeptTree, addDept, updateDept, removeDept } from '@/api/dept'

interface DeptFormState {
  parentId: string
  name: string
  orderNo: number
  leader: string
  phone: string
  email: string
  status: number
  remark: string
}

const loading = ref(false)
const deptTree = ref<SysDept[]>([])
const expandedKeys = ref<string[]>([])
const query = reactive({ name: '' })

const columns = [
  { title: '部门名称', dataIndex: 'name', key: 'name' },
  { title: '负责人', dataIndex: 'leader', width: 110 },
  { title: '手机号', dataIndex: 'phone', width: 140 },
  { title: '邮箱', dataIndex: 'email' },
  { title: '排序', dataIndex: 'orderNo', width: 80 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 160 }
]

function collectKeys(nodes: SysDept[], acc: string[]): string[] {
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      acc.push(node.id)
      collectKeys(node.children, acc)
    }
  }
  return acc
}

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    deptTree.value = (await getDeptTree(query.name ? { name: query.name } : undefined)) || []
    expandedKeys.value = collectKeys(deptTree.value, [])
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  loadTree()
}

function onResetSearch(): void {
  query.name = ''
  loadTree()
}

function onExpandedChange(keys: Array<string | number>): void {
  expandedKeys.value = keys.map(String)
}

// ---------- 新增 / 编辑 ----------
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = (): DeptFormState => ({
  parentId: '0',
  name: '',
  orderNo: 0,
  leader: '',
  phone: '',
  email: '',
  status: 0,
  remark: ''
})
const formState = reactive<DeptFormState>(defaultForm())

const formRules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

/** 上级部门选项：顶级(0) + 完整部门树 */
const parentTreeData = computed<SysDept[]>(() => [
  {
    id: '0',
    parentId: '-1',
    name: '顶级部门',
    orderNo: 0,
    leader: null,
    phone: null,
    email: null,
    status: 0,
    children: deptTree.value
  }
])

const parentFieldNames = { label: 'name', value: 'id', children: 'children' }

function openAdd(record?: SysDept): void {
  editingId.value = null
  Object.assign(formState, defaultForm(), {
    parentId: record ? record.id : '0'
  })
  modalOpen.value = true
}

function openEdit(record: SysDept): void {
  editingId.value = record.id
  Object.assign(formState, {
    parentId: record.parentId || '0',
    name: record.name,
    orderNo: record.orderNo,
    leader: record.leader ?? '',
    phone: record.phone ?? '',
    email: record.email ?? '',
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
    const body: DeptSaveBody = {
      id: editingId.value ?? undefined,
      parentId: formState.parentId,
      name: formState.name,
      orderNo: formState.orderNo,
      leader: formState.leader || undefined,
      phone: formState.phone || undefined,
      email: formState.email || undefined,
      status: formState.status,
      remark: formState.remark || undefined
    }
    if (editingId.value) {
      await updateDept(body)
    } else {
      await addDept(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadTree()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: SysDept): Promise<void> {
  await removeDept(record.id)
  message.success('删除成功')
  loadTree()
}

onMounted(() => {
  loadTree()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-input
          v-model:value="query.name"
          placeholder="部门名称"
          allow-clear
          style="width: 200px"
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
      <a-button type="primary" v-perm="'system:dept:add'" @click="openAdd()">
        <template #icon><plus-outlined /></template>
        新增部门
      </a-button>
      <span></span>
    </div>

    <a-table
      :data-source="deptTree"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="false"
      :expanded-row-keys="expandedKeys"
      @expanded-rows-change="onExpandedChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'leader'">
          {{ (record as SysDept).leader || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'phone'">
          {{ (record as SysDept).phone || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'email'">
          {{ (record as SysDept).email || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="(record as SysDept).status === 0 ? 'success' : 'error'">
            {{ (record as SysDept).status === 0 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ (record as SysDept).createTime || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'system:dept:add'" @click="openAdd(record as SysDept)">
              新增
            </a-button>
            <a-button type="link" size="small" v-perm="'system:dept:edit'" @click="openEdit(record as SysDept)">
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除该部门吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as SysDept)"
            >
              <a-button type="link" danger size="small" v-perm="'system:dept:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑部门' : '新增部门'"
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
            <a-form-item label="上级部门" required>
              <a-tree-select
                v-model:value="formState.parentId"
                :tree-data="parentTreeData"
                :field-names="parentFieldNames"
                placeholder="请选择上级部门"
                tree-default-expand-all
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="部门名称" name="name">
              <a-input v-model:value="formState.name" placeholder="请输入部门名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="显示排序" name="orderNo">
              <a-input-number v-model:value="formState.orderNo" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人" name="leader">
              <a-input v-model:value="formState.leader" placeholder="负责人" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="手机号" name="phone">
              <a-input v-model:value="formState.phone" placeholder="手机号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="邮箱" name="email">
              <a-input v-model:value="formState.email" placeholder="邮箱" />
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
