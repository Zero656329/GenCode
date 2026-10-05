<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getMenuTree, addMenu, updateMenu, removeMenu } from '@/api/menu'
import DynamicIcon from '@/components/DynamicIcon.vue'

interface MenuFormState {
  parentId: string
  name: string
  path: string
  component: string
  menuType: MenuType
  perms: string
  icon: string
  sort: number
  visible: number
  status: number
}

const menuTypeMeta: Record<MenuType, { text: string; color: string }> = {
  M: { text: '目录', color: 'blue' },
  C: { text: '菜单', color: 'green' },
  F: { text: '按钮', color: 'orange' }
}

const loading = ref(false)
const menuTree = ref<MenuNode[]>([])
const expandedKeys = ref<string[]>([])
const query = reactive({ name: '' })

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '图标', key: 'icon', width: 70 },
  { title: '类型', dataIndex: 'menuType', width: 80 },
  { title: '权限标识', dataIndex: 'perms' },
  { title: '路由地址', dataIndex: 'path' },
  { title: '组件路径', dataIndex: 'component' },
  { title: '排序', dataIndex: 'sort', width: 70 },
  { title: '可见', dataIndex: 'visible', width: 80 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', key: 'action', width: 160 }
]

function collectKeys(nodes: MenuNode[], acc: string[]): string[] {
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
    menuTree.value = (await getMenuTree(query.name ? { name: query.name } : undefined)) || []
    expandedKeys.value = collectKeys(menuTree.value, [])
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

const defaultForm = (): MenuFormState => ({
  parentId: '0',
  name: '',
  path: '',
  component: '',
  menuType: 'C',
  perms: '',
  icon: '',
  sort: 0,
  visible: 0,
  status: 0
})
const formState = reactive<MenuFormState>(defaultForm())

/** 上级菜单选项：根(0) + 完整菜单树 */
const parentTreeData = computed<MenuNode[]>(() => [
  {
    id: '0',
    parentId: '-1',
    name: '根目录',
    path: null,
    component: null,
    menuType: 'M',
    perms: null,
    icon: null,
    sort: 0,
    visible: 0,
    status: 0,
    children: menuTree.value
  }
])

const parentFieldNames = { label: 'name', value: 'id', children: 'children' }

function openAdd(record?: MenuNode): void {
  editingId.value = null
  Object.assign(formState, defaultForm(), {
    parentId: record ? record.id : '0'
  })
  modalOpen.value = true
}

function openEdit(record: MenuNode): void {
  editingId.value = record.id
  Object.assign(formState, {
    parentId: record.parentId || '0',
    name: record.name,
    path: record.path || '',
    component: record.component || '',
    menuType: record.menuType,
    perms: record.perms || '',
    icon: record.icon || '',
    sort: record.sort,
    visible: record.visible,
    status: record.status
  })
  modalOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!formState.name.trim()) {
    message.warning('请输入菜单名称')
    return
  }
  saving.value = true
  try {
    const body: MenuSaveBody = {
      id: editingId.value ?? undefined,
      parentId: formState.parentId,
      name: formState.name,
      path: formState.menuType === 'F' ? undefined : formState.path || undefined,
      component: formState.menuType === 'C' ? formState.component || undefined : undefined,
      menuType: formState.menuType,
      perms: formState.menuType === 'M' ? undefined : formState.perms || undefined,
      icon: formState.menuType === 'F' ? undefined : formState.icon || undefined,
      sort: formState.sort,
      visible: formState.visible,
      status: formState.status
    }
    if (editingId.value) {
      await updateMenu(body)
    } else {
      await addMenu(body)
    }
    message.success('保存成功')
    modalOpen.value = false
    loadTree()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: MenuNode): Promise<void> {
  await removeMenu(record.id)
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
          placeholder="菜单名称"
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
      <a-button type="primary" v-perm="'system:menu:add'" @click="openAdd()">
        <template #icon><plus-outlined /></template>
        新增菜单
      </a-button>
      <span></span>
    </div>

    <a-table
      :data-source="menuTree"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="false"
      :expanded-row-keys="expandedKeys"
      :scroll="{ x: 1000 }"
      @expanded-rows-change="onExpandedChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <span>{{ (record as MenuNode).name }}</span>
        </template>
        <template v-else-if="column.key === 'icon'">
          <dynamic-icon v-if="(record as MenuNode).icon" :icon="(record as MenuNode).icon" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'menuType'">
          <a-tag :color="menuTypeMeta[(record as MenuNode).menuType]?.color">
            {{ menuTypeMeta[(record as MenuNode).menuType]?.text || (record as MenuNode).menuType }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'perms'">
          {{ (record as MenuNode).perms || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'path'">
          {{ (record as MenuNode).path || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'component'">
          {{ (record as MenuNode).component || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'visible'">
          <a-tag :color="(record as MenuNode).visible === 0 ? 'success' : 'default'">
            {{ (record as MenuNode).visible === 0 ? '显示' : '隐藏' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="(record as MenuNode).status === 0 ? 'success' : 'error'">
            {{ (record as MenuNode).status === 0 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button
              v-if="(record as MenuNode).menuType !== 'F'"
              type="link"
              size="small"
              v-perm="'system:menu:add'"
              @click="openAdd(record as MenuNode)"
            >
              新增
            </a-button>
            <a-button type="link" size="small" v-perm="'system:menu:edit'" @click="openEdit(record as MenuNode)">
              编辑
            </a-button>
            <a-popconfirm
              title="确定删除该菜单吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as MenuNode)"
            >
              <a-button type="link" danger size="small" v-perm="'system:menu:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑菜单' : '新增菜单'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="600"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form :model="formState" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="菜单类型">
              <a-radio-group v-model:value="formState.menuType">
                <a-radio-button value="M">目录</a-radio-button>
                <a-radio-button value="C">菜单</a-radio-button>
                <a-radio-button value="F">按钮</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="上级菜单" required>
              <a-tree-select
                v-model:value="formState.parentId"
                :tree-data="parentTreeData"
                :field-names="parentFieldNames"
                placeholder="请选择上级菜单"
                tree-default-expand-all
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="菜单名称" required>
              <a-input v-model:value="formState.name" placeholder="请输入菜单名称" />
            </a-form-item>
          </a-col>
          <a-col v-if="formState.menuType !== 'F'" :span="12">
            <a-form-item label="路由地址">
              <a-input v-model:value="formState.path" placeholder="如 /system/user" />
            </a-form-item>
          </a-col>
          <a-col v-if="formState.menuType === 'C'" :span="12">
            <a-form-item label="组件路径">
              <a-input v-model:value="formState.component" placeholder="如 system/user/index" />
            </a-form-item>
          </a-col>
          <a-col v-if="formState.menuType !== 'M'" :span="12">
            <a-form-item label="权限标识">
              <a-input v-model:value="formState.perms" placeholder="如 system:user:add" />
            </a-form-item>
          </a-col>
          <a-col v-if="formState.menuType !== 'F'" :span="12">
            <a-form-item label="图标">
              <a-input v-model:value="formState.icon" placeholder="AntD 图标名，如 UserOutlined" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="显示排序">
              <a-input-number v-model:value="formState.sort" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="是否显示">
              <a-switch
                v-model:checked="formState.visible"
                :checked-value="0"
                :unchecked-value="1"
                checked-children="显示"
                un-checked-children="隐藏"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-radio-group v-model:value="formState.status">
                <a-radio :value="0">正常</a-radio>
                <a-radio :value="1">停用</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
