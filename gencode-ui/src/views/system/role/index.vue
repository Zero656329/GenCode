<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getRolePage,
  getRoleDetail,
  addRole,
  updateRole,
  removeRole,
  saveRoleMenus,
  updateRoleStatus
} from '@/api/role'
import { getMenuTree } from '@/api/menu'

interface RoleFormState {
  name: string
  roleKey: string
  sort: number
  dataScope: number
  status: number
  remark: string
}

const dataScopeText: Record<number, string> = {
  1: '全部数据',
  2: '本部门及以下',
  3: '本部门',
  4: '仅本人'
}
const dataScopeColor: Record<number, string> = {
  1: 'blue',
  2: 'cyan',
  3: 'purple',
  4: 'orange'
}

function isBuiltinRole(record: SysRole | null): boolean {
  if (!record) return false
  return (
    record.roleKey === 'super_admin' ||
    record.roleKey === 'common' ||
    record.id === '1' ||
    record.id === '2'
  )
}

const loading = ref(false)
const list = ref<SysRole[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '角色名称', dataIndex: 'name' },
  { title: '角色权限字符', dataIndex: 'roleKey' },
  { title: '显示顺序', dataIndex: 'sort', width: 90 },
  { title: '数据范围', dataIndex: 'dataScope', width: 130 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', key: 'action', width: 230 }
]

async function loadRoles(): Promise<void> {
  loading.value = true
  try {
    const page = await getRolePage({
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
  loadRoles()
}

function onResetSearch(): void {
  query.keyword = ''
  query.status = null
  onSearch()
}

function onTableChange(pag: { current?: number; pageSize?: number }): void {
  query.pageNum = pag.current || 1
  query.pageSize = pag.pageSize || 10
  loadRoles()
}

const pagination = computed(() => ({
  current: query.pageNum,
  pageSize: query.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

async function onStatusChange(record: SysRole, checked: string | number | boolean): Promise<void> {
  const status = checked ? 0 : 1
  try {
    await updateRoleStatus(record.id, status)
    message.success(status === 0 ? '已启用' : '已停用')
  } finally {
    loadRoles()
  }
}

// ---------- 新增 / 编辑 ----------
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const editingBuiltin = ref(false)
const formRef = ref<FormInstance | null>(null)

const defaultForm = (): RoleFormState => ({
  name: '',
  roleKey: '',
  sort: 0,
  dataScope: 1,
  status: 0,
  remark: ''
})
const formState = reactive<RoleFormState>(defaultForm())

const formRules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色权限字符', trigger: 'blur' }]
}

function openAdd(): void {
  editingId.value = null
  editingBuiltin.value = false
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

async function openEdit(record: SysRole): Promise<void> {
  try {
    const detail = await getRoleDetail(record.id)
    editingId.value = record.id
    editingBuiltin.value = isBuiltinRole(record)
    Object.assign(formState, {
      name: detail.name,
      roleKey: detail.roleKey,
      sort: detail.sort,
      dataScope: detail.dataScope,
      status: detail.status,
      remark: detail.remark ?? ''
    })
    modalOpen.value = true
  } catch {
    // 拦截器已提示
  }
}

async function handleSave(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const body: RoleSaveBody = {
      id: editingId.value ?? undefined,
      name: formState.name,
      roleKey: formState.roleKey,
      sort: formState.sort,
      dataScope: formState.dataScope,
      status: formState.status,
      remark: formState.remark || undefined
    }
    if (editingId.value) {
      await updateRole(body)
    } else {
      await addRole(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadRoles()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: SysRole): Promise<void> {
  await removeRole(record.id)
  message.success('删除成功')
  loadRoles()
}

// ---------- 菜单授权 ----------
const menuAuthOpen = ref(false)
const menuAuthSaving = ref(false)
const menuAuthRole = ref<SysRole | null>(null)
const menuAuthTree = ref<MenuNode[]>([])
const menuAuthExpanded = ref<string[]>([])
const menuAuthChecked = ref<{ checked: Array<string | number>; halfChecked: Array<string | number> }>({
  checked: [],
  halfChecked: []
})

function collectIds(nodes: MenuNode[], acc: string[]): string[] {
  for (const node of nodes) {
    acc.push(node.id)
    if (node.children && node.children.length > 0) collectIds(node.children, acc)
  }
  return acc
}

async function openMenuAuth(record: SysRole): Promise<void> {
  menuAuthRole.value = record
  menuAuthOpen.value = true
  try {
    const [tree, detail] = await Promise.all([getMenuTree(), getRoleDetail(record.id)])
    menuAuthTree.value = tree || []
    menuAuthExpanded.value = collectIds(menuAuthTree.value, [])
    menuAuthChecked.value = { checked: detail.menuIds || [], halfChecked: [] }
  } catch {
    menuAuthTree.value = []
  }
}

async function saveMenuAuth(): Promise<void> {
  if (!menuAuthRole.value) return
  menuAuthSaving.value = true
  try {
    await saveRoleMenus(menuAuthRole.value.id, {
      menuIds: menuAuthChecked.value.checked.map(String)
    })
    message.success('授权成功')
    menuAuthOpen.value = false
  } finally {
    menuAuthSaving.value = false
  }
}

onMounted(() => {
  loadRoles()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-input
          v-model:value="query.keyword"
          placeholder="角色名称 / 权限字符"
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
      <a-button type="primary" v-perm="'system:role:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新增角色
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
        <template v-if="column.key === 'dataScope'">
          <a-tag :color="dataScopeColor[record.dataScope] || 'default'">
            {{ dataScopeText[record.dataScope] || '未知' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-switch
            :checked="record.status === 0"
            size="small"
            @change="(checked: string | number | boolean) => onStatusChange(record as SysRole, checked)"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'system:role:edit'" @click="openEdit(record as SysRole)">
              编辑
            </a-button>
            <a-button type="link" size="small" v-perm="'system:role:edit'" @click="openMenuAuth(record as SysRole)">
              菜单权限
            </a-button>
            <a-popconfirm
              title="确定删除该角色吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as SysRole)"
            >
              <a-button
                type="link"
                danger
                size="small"
                :disabled="isBuiltinRole(record as SysRole)"
                v-perm="'system:role:delete'"
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
      :title="editingId ? '编辑角色' : '新增角色'"
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
            <a-form-item label="角色名称" name="name">
              <a-input v-model:value="formState.name" placeholder="请输入角色名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="角色权限字符" name="roleKey">
              <a-input
                v-model:value="formState.roleKey"
                :disabled="editingBuiltin"
                placeholder="如 common"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="显示顺序" name="sort">
              <a-input-number v-model:value="formState.sort" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="数据范围" name="dataScope">
              <a-select v-model:value="formState.dataScope">
                <a-select-option :value="1">全部数据</a-select-option>
                <a-select-option :value="2">本部门及以下</a-select-option>
                <a-select-option :value="3">本部门</a-select-option>
                <a-select-option :value="4">仅本人</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="24">
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

    <!-- 菜单授权弹窗 -->
    <a-modal
      v-model:open="menuAuthOpen"
      :title="`菜单权限${menuAuthRole ? ' - ' + menuAuthRole.name : ''}`"
      :confirm-loading="menuAuthSaving"
      :width="480"
      ok-text="确定"
      cancel-text="取消"
      @ok="saveMenuAuth"
    >
      <a-tree
        v-if="menuAuthTree.length > 0"
        checkable
        :check-strictly="true"
        v-model:checked="menuAuthChecked"
        :tree-data="menuAuthTree"
        v-model:expanded-keys="menuAuthExpanded"
        :field-names="{ label: 'name', key: 'id', children: 'children' }"
      />
    </a-modal>
  </div>
</template>
