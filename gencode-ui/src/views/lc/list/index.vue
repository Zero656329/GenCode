<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getLcListPage,
  addLcList,
  removeLcList,
  publishLcList,
  updateLcListStatus,
  type LcList,
  type LcListSourceType,
  type LcListStatus
} from '@/api/lc-list'

/** 列表状态元信息：0草稿 1已发布 2停用（与 lc_form 一致） */
const STATUS_META: Record<LcListStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '已发布', color: 'green' },
  2: { text: '停用', color: 'red' }
}

/** 数据源类型标签 */
const SOURCE_TYPE_TEXT: Record<LcListSourceType, string> = {
  TABLE: '物理表',
  SQL: '自定义SQL',
  API: 'API（预留）'
}

const router = useRouter()

const loading = ref(false)
const list = ref<LcList[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: null as number | null, pageNum: 1, pageSize: 10 })

const columns = [
  { title: '列表编码', dataIndex: 'code' },
  { title: '列表名称', dataIndex: 'name' },
  { title: '来源类型', dataIndex: 'sourceType', width: 110 },
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
    const page = await getLcListPage({
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

// 新建列表
const modalOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({
  code: '',
  name: '',
  sourceType: 'TABLE' as LcListSourceType,
  remark: ''
})
const formState = reactive(defaultForm())

const formRules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '请输入列表编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码需以字母开头，仅含字母数字下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入列表名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }]
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
    await addLcList({
      code: formState.code,
      name: formState.name,
      sourceType: formState.sourceType,
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
function onDesign(record: LcList): void {
  router.push(`/lc/list/design/${record.id}`)
}

async function onPublish(record: LcList): Promise<void> {
  const res = await publishLcList(record.id)
  const version = res && res.version ? res.version : record.version + 1
  message.success(`发布成功，当前版本 v${version}`)
  loadList()
}

async function onStatusChange(record: LcList, checked: string | number | boolean): Promise<void> {
  const status: LcListStatus = checked ? 1 : 2
  await updateLcListStatus(record.id, status)
  message.success(status === 1 ? '已启用' : '已停用')
  loadList()
}

async function onDelete(record: LcList): Promise<void> {
  await removeLcList(record.id)
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
          placeholder="列表编码 / 名称"
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
      <a-button type="primary" v-perm="'lc:list:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建列表
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
        <template v-if="column.dataIndex === 'sourceType'">
          {{ SOURCE_TYPE_TEXT[(record as LcList).sourceType] || (record as LcList).sourceType }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="STATUS_META[(record as LcList).status].color">
            {{ STATUS_META[(record as LcList).status].text }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'version'">
          v{{ (record as LcList).version }}
        </template>
        <template v-else-if="column.dataIndex === 'publishTime'">
          {{ (record as LcList).publishTime || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as LcList).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'lc:list:edit'" @click="onDesign(record as LcList)">
              设计
            </a-button>
            <a-popconfirm
              title="确定发布该列表吗？发布后运行时按当前配置快照展示。"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onPublish(record as LcList)"
            >
              <a-button type="link" size="small" v-perm="'lc:list:publish'">发布</a-button>
            </a-popconfirm>
            <a-switch
              v-perm="'lc:list:edit'"
              :checked="(record as LcList).status === 1"
              :disabled="(record as LcList).status === 0"
              size="small"
              @change="(checked: string | number | boolean) => onStatusChange(record as LcList, checked)"
            />
            <a-popconfirm
              title="确定删除该列表吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as LcList)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:list:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建列表弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      title="新建列表"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="列表编码" name="code">
          <a-input v-model:value="formState.code" placeholder="如 sys_config_list（保存后不可修改）" />
        </a-form-item>
        <a-form-item label="列表名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如 参数配置列表" />
        </a-form-item>
        <a-form-item label="来源类型" name="sourceType">
          <a-radio-group v-model:value="formState.sourceType">
            <a-radio value="TABLE">物理表（TABLE）</a-radio>
            <a-radio value="SQL">自定义SQL（SQL）</a-radio>
            <a-radio value="API" disabled>API（预留）</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
