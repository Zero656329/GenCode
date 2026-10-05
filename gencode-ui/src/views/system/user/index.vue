<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import {
  getUserPage,
  getUserDetail,
  addUser,
  updateUser,
  removeUser,
  updateUserStatus,
  resetUserPassword
} from '@/api/user'
import { getDeptTree } from '@/api/dept'
import { getRoleListAll } from '@/api/role'

interface UserFormState {
  username: string
  nickname: string
  password: string
  deptId?: string
  phone: string
  email: string
  status: number
  remark: string
  roleIds: string[]
}

const loading = ref(false)
const list = ref<SysUser[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })
const deptId = ref<string | null>(null)

// ---------- 部门树 ----------
const deptTree = ref<SysDept[]>([])
const deptSelectedKeys = ref<string[]>([])
const deptTreeFieldNames = { title: 'name', key: 'id', children: 'children' }
const deptSelectFieldNames = { label: 'name', value: 'id', children: 'children' }

async function loadDeptTree(): Promise<void> {
  try {
    deptTree.value = (await getDeptTree()) || []
  } catch {
    deptTree.value = []
  }
}

function onDeptSelect(keys: Array<string | number>): void {
  deptSelectedKeys.value = keys.map(String)
  deptId.value = keys.length > 0 ? String(keys[0]) : null
  query.pageNum = 1
  loadUsers()
}

// ---------- 角色下拉 ----------
const roleOptions = ref<SysRole[]>([])
const roleSelectOptions = computed(() =>
  roleOptions.value.map((r) => ({ label: r.name, value: r.id }))
)

async function loadRoles(): Promise<void> {
  if (roleOptions.value.length > 0) return
  try {
    roleOptions.value = (await getRoleListAll()) || []
  } catch {
    roleOptions.value = []
  }
}

// ---------- 列表 ----------
const columns = [
  { title: '账号', dataIndex: 'username' },
  { title: '昵称', dataIndex: 'nickname' },
  { title: '部门', dataIndex: 'deptName' },
  { title: '手机号', dataIndex: 'phone' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 230 }
]

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const page = await getUserPage({
      keyword: query.keyword || undefined,
      status: query.status ?? undefined,
      deptId: deptId.value || undefined,
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
  loadUsers()
}

function onResetSearch(): void {
  query.keyword = ''
  query.status = null
  deptSelectedKeys.value = []
  deptId.value = null
  onSearch()
}

function onTableChange(pag: { current?: number; pageSize?: number }): void {
  query.pageNum = pag.current || 1
  query.pageSize = pag.pageSize || 10
  loadUsers()
}

const pagination = computed(() => ({
  current: query.pageNum,
  pageSize: query.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// ---------- 状态开关 ----------
async function onStatusChange(record: SysUser, checked: string | number | boolean): Promise<void> {
  const status = checked ? 0 : 1
  try {
    await updateUserStatus(record.id, status)
    message.success(status === 0 ? '已启用' : '已停用')
  } finally {
    loadUsers()
  }
}

// ---------- 新增 / 编辑 ----------
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = (): UserFormState => ({
  username: '',
  nickname: '',
  password: '',
  deptId: undefined,
  phone: '',
  email: '',
  status: 0,
  remark: '',
  roleIds: []
})
const formState = reactive<UserFormState>(defaultForm())

const formRules = computed<Record<string, Rule[]>>(() => ({
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password:
    editingId.value === null
      ? [{ required: true, message: '请输入密码', trigger: 'blur' }]
      : []
}))

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  loadRoles()
  modalOpen.value = true
}

async function openEdit(record: SysUser): Promise<void> {
  loadRoles()
  try {
    const detail = await getUserDetail(record.id)
    editingId.value = record.id
    Object.assign(formState, defaultForm(), {
      username: detail.username,
      nickname: detail.nickname,
      deptId: detail.deptId ?? undefined,
      phone: detail.phone ?? '',
      email: detail.email ?? '',
      status: detail.status,
      remark: detail.remark ?? '',
      roleIds: detail.roleIds || []
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
    const body: UserSaveBody = {
      id: editingId.value ?? undefined,
      username: formState.username,
      nickname: formState.nickname,
      password: formState.password || undefined,
      deptId: formState.deptId || undefined,
      phone: formState.phone || undefined,
      email: formState.email || undefined,
      status: formState.status,
      remark: formState.remark || undefined,
      roleIds: formState.roleIds
    }
    if (editingId.value) {
      await updateUser(body)
    } else {
      await addUser(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadUsers()
  } finally {
    saving.value = false
  }
}

// ---------- 删除 / 重置密码 ----------
async function onDelete(record: SysUser): Promise<void> {
  await removeUser(record.id)
  message.success('删除成功')
  loadUsers()
}

const resetPwdOpen = ref(false)
const resetPwdTarget = ref<SysUser | null>(null)
const resetPwdValue = ref('')

function openResetPwd(record: SysUser): void {
  resetPwdTarget.value = record
  resetPwdValue.value = ''
  resetPwdOpen.value = true
}

async function doResetPwd(): Promise<void> {
  if (!resetPwdValue.value) {
    message.warning('请输入新密码')
    return
  }
  if (!resetPwdTarget.value) return
  await resetUserPassword(resetPwdTarget.value.id, { password: resetPwdValue.value })
  message.success('密码重置成功')
  resetPwdOpen.value = false
}

onMounted(() => {
  loadDeptTree()
  loadUsers()
})
</script>

<template>
  <div class="user-page">
    <div class="user-body">
      <!-- 左侧部门树 -->
      <div class="dept-panel">
        <div class="dept-title">部门</div>
        <a-tree
          :tree-data="deptTree"
          :field-names="deptTreeFieldNames"
          v-model:selected-keys="deptSelectedKeys"
          block-node
          default-expand-all
          @select="onDeptSelect"
        />
      </div>

      <!-- 右侧列表 -->
      <div class="user-main">
        <div class="search-bar">
          <a-space wrap>
            <a-input
              v-model:value="query.keyword"
              placeholder="账号 / 昵称 / 手机号"
              allow-clear
              style="width: 220px"
              @press-enter="onSearch"
            />
            <a-select
              v-model:value="query.status"
              placeholder="状态"
              allow-clear
              style="width: 120px"
            >
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
          <a-button type="primary" v-perm="'system:user:add'" @click="openAdd">
            <template #icon><plus-outlined /></template>
            新增用户
          </a-button>
          <span class="dept-filter-tip">
            {{ deptId ? '已按部门过滤，再次点击部门可取消' : '' }}
          </span>
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
            <template v-if="column.key === 'status'">
              <a-switch
                :checked="record.status === 0"
                :disabled="record.id === '1'"
                size="small"
                @change="(checked: string | number | boolean) => onStatusChange(record as SysUser, checked)"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" v-perm="'system:user:edit'" @click="openEdit(record as SysUser)">
                  编辑
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  v-perm="'system:user:resetPwd'"
                  @click="openResetPwd(record as SysUser)"
                >
                  重置密码
                </a-button>
                <a-popconfirm
                  title="确定删除该用户吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  :disabled="record.id === '1'"
                  @confirm="onDelete(record as SysUser)"
                >
                  <a-button type="link" danger size="small" :disabled="record.id === '1'" v-perm="'system:user:delete'">
                    删除
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑用户' : '新增用户'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="640"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="账号" name="username">
              <a-input v-model:value="formState.username" :disabled="!!editingId" placeholder="登录账号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="昵称" name="nickname">
              <a-input v-model:value="formState.nickname" placeholder="用户昵称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="密码" name="password">
              <a-input-password
                v-model:value="formState.password"
                :placeholder="editingId ? '留空表示不修改密码' : '请输入密码'"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="手机号" name="phone">
              <a-input v-model:value="formState.phone" placeholder="手机号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="部门" name="deptId">
              <a-tree-select
                v-model:value="formState.deptId"
                :tree-data="deptTree"
                :field-names="deptSelectFieldNames"
                placeholder="请选择部门"
                allow-clear
                tree-default-expand-all
                style="width: 100%"
              />
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
          <a-col :span="12">
            <a-form-item label="角色" name="roleIds">
              <a-select
                v-model:value="formState.roleIds"
                mode="multiple"
                :options="roleSelectOptions"
                placeholder="请选择角色"
                allow-clear
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

    <!-- 重置密码弹窗 -->
    <a-modal
      v-model:open="resetPwdOpen"
      :title="`重置密码${resetPwdTarget ? ' - ' + resetPwdTarget.username : ''}`"
      :width="420"
      ok-text="确定"
      cancel-text="取消"
      @ok="doResetPwd"
    >
      <a-form layout="vertical">
        <a-form-item label="新密码" required>
          <a-input-password v-model:value="resetPwdValue" placeholder="请输入新密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.user-body {
  display: flex;
  gap: 16px;
}

.dept-panel {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #f0f0f0;
  padding-right: 16px;
}

.dept-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.dept-filter-tip {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
