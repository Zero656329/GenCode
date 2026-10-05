<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  DATASOURCE_DRIVERS,
  getLcDatasourcePage,
  addLcDatasource,
  updateLcDatasource,
  removeLcDatasource,
  testLcDatasource,
  type LcDatasource
} from '@/api/lc-datasource'

/**
 * 数据源管理（/lc/datasource）
 * password AES 加密落库且不回显：编辑时留空=不修改。
 */

const loading = ref(false)
const list = ref<LcDatasource[]>([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const columns = [
  { title: '名称', dataIndex: 'name', width: 160 },
  { title: '驱动', dataIndex: 'driver', width: 280 },
  { title: 'JDBC URL', dataIndex: 'jdbcUrl' },
  { title: '用户名', dataIndex: 'username', width: 130 },
  { title: '备注', dataIndex: 'remark', width: 160 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 220 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLcDatasourcePage({
      keyword: query.keyword || undefined,
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

// ============ 新建 / 编辑 ============

const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({
  name: '',
  driver: DATASOURCE_DRIVERS[0],
  jdbcUrl: '',
  username: '',
  password: '',
  remark: ''
})
const formState = reactive(defaultForm())

/** 新建密码必填；编辑留空=不修改（computed 随编辑态切换） */
const formRules = computed<Record<string, Rule[]>>(() => ({
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  driver: [{ required: true, message: '请选择或输入驱动类全名', trigger: 'blur' }],
  jdbcUrl: [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password:
    editingId.value === null
      ? [{ required: true, message: '请输入密码', trigger: 'blur' }]
      : []
}))

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

function openEdit(record: LcDatasource): void {
  editingId.value = record.id
  Object.assign(formState, {
    name: record.name,
    driver: record.driver,
    jdbcUrl: record.jdbcUrl,
    username: record.username,
    password: '',
    remark: record.remark || ''
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
    const body = {
      name: formState.name,
      driver: formState.driver,
      jdbcUrl: formState.jdbcUrl,
      username: formState.username,
      remark: formState.remark || undefined,
      // 编辑时留空=不修改（不传 password 字段）
      password: formState.password || undefined
    }
    if (editingId.value) {
      await updateLcDatasource({ id: editingId.value, ...body })
      message.success('保存成功')
    } else {
      await addLcDatasource(body)
      message.success('新建成功')
    }
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ============ 行操作 ============

const testingId = ref<string | null>(null)

async function onTest(record: LcDatasource): Promise<void> {
  testingId.value = record.id
  try {
    const res = await testLcDatasource(record.id)
    // 成功绿 / 失败红，message 为后端给出的结果说明
    if (res && res.ok) {
      message.success(res.message || '连接成功')
    } else {
      message.error((res && res.message) || '连接失败')
    }
  } finally {
    testingId.value = null
  }
}

async function onDelete(record: LcDatasource): Promise<void> {
  await removeLcDatasource(record.id)
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
          placeholder="数据源名称"
          allow-clear
          style="width: 220px"
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
      <a-button type="primary" v-perm="'lc:datasource:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建数据源
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
        <template v-if="column.dataIndex === 'remark'">
          {{ (record as LcDatasource).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" v-perm="'lc:datasource:edit'" @click="openEdit(record as LcDatasource)">
              编辑
            </a-button>
            <a-button
              type="link"
              size="small"
              v-perm="'lc:datasource:test'"
              :loading="testingId === (record as LcDatasource).id"
              @click="onTest(record as LcDatasource)"
            >
              测试连接
            </a-button>
            <a-popconfirm
              title="确定删除该数据源吗？"
              ok-text="确定"
              cancel-text="取消"
              @confirm="onDelete(record as LcDatasource)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:datasource:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建/编辑数据源弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑数据源' : '新建数据源'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="560"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如 业务从库" />
        </a-form-item>
        <a-form-item label="驱动（可选预置，支持手输）" name="driver">
          <a-auto-complete
            v-model:value="formState.driver"
            :options="DATASOURCE_DRIVERS.map((d) => ({ value: d }))"
            placeholder="com.mysql.cj.jdbc.Driver"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="JDBC URL" name="jdbcUrl">
          <a-input v-model:value="formState.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/db?useUnicode=true" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="用户名" name="username">
              <a-input v-model:value="formState.username" placeholder="数据库账号" autocomplete="off" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="密码"
              :name="editingId ? undefined : 'password'"
            >
              <a-input-password
                v-model:value="formState.password"
                :placeholder="editingId ? '留空则不修改' : '数据库密码'"
                autocomplete="new-password"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
        <a-alert
          v-if="editingId"
          type="info"
          show-icon
          class="pwd-alert"
          message="密码不回显；如需修改请输入新密码，留空保持原密码不变。"
        />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.pwd-alert {
  margin-bottom: 8px;
}
</style>
