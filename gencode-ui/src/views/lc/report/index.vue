<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { EyeOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  addLcDataset,
  getLcDataset,
  getLcDatasetPage,
  parseDatasetParams,
  previewLcDataset,
  removeLcDataset,
  updateLcDataset,
  type DatasetDataResult,
  type DatasetParam,
  type LcDataset
} from '@/api/lc-dataset'

/**
 * 数据集管理页（/lc/report）
 * SQL 仅允许单条 SELECT，#{param} 占位符由引擎转参数绑定（前端仅提示，后端强校验）。
 */

const loading = ref(false)
const list = ref<LcDataset[]>([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const columns = [
  { title: '数据集编码', dataIndex: 'code', width: 180 },
  { title: '数据集名称', dataIndex: 'name', width: 180 },
  { title: 'SQL', dataIndex: 'sqlText', ellipsis: true },
  { title: '参数', key: 'params', width: 90 },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 160 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLcDatasetPage({
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

/** 参数列展示：参数个数（解析失败显示 -） */
function paramCount(record: LcDataset): string {
  const params = parseDatasetParams(record.paramsJson)
  return params.length > 0 ? `${params.length} 个` : '-'
}

// ============ 新建 / 编辑弹窗 ============

const modalOpen = ref(false)
const saving = ref(false)
/** null=新建，否则为编辑中的数据集 id */
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance | null>(null)

const defaultForm = () => ({
  code: '',
  name: '',
  sqlText: '',
  paramsJson: '',
  remark: ''
})
const formState = reactive(defaultForm())

/** 参数定义校验：JSON 合法 + 数组元素含 name */
const paramsJsonValidator = async (_rule: Rule, value: string): Promise<void> => {
  const text = (value || '').trim()
  if (!text) return
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('参数定义必须是合法 JSON')
  }
  if (!Array.isArray(parsed)) {
    throw new Error('参数定义必须是 JSON 数组，如 [{"name":"days","label":"最近天数","type":"number","required":true,"defaultValue":7}]')
  }
  for (const item of parsed) {
    if (typeof item !== 'object' || item === null || typeof (item as { name?: unknown }).name !== 'string' || !(item as { name: string }).name) {
      throw new Error('数组每个元素需为对象且包含非空 name 属性')
    }
  }
}

const formRules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '请输入数据集编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码需以字母开头，仅含字母数字下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入数据集名称', trigger: 'blur' }],
  sqlText: [
    { required: true, message: '请输入查询 SQL', trigger: 'blur' },
    { pattern: /^\s*select\s/i, message: '仅允许 SELECT 查询', trigger: 'blur' }
  ],
  paramsJson: [{ validator: paramsJsonValidator, trigger: 'blur' }]
}

function openAdd(): void {
  editingId.value = null
  Object.assign(formState, defaultForm())
  modalOpen.value = true
}

async function openEdit(record: LcDataset): Promise<void> {
  editingId.value = record.id
  const detail = await getLcDataset(record.id)
  formState.code = detail.code
  formState.name = detail.name
  formState.sqlText = detail.sqlText || ''
  formState.paramsJson = detail.paramsJson || ''
  formState.remark = detail.remark || ''
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
    const paramsJson = formState.paramsJson.trim() || null
    if (editingId.value) {
      await updateLcDataset({
        id: editingId.value,
        name: formState.name,
        sqlText: formState.sqlText,
        paramsJson,
        remark: formState.remark || null
      })
      message.success('保存成功')
    } else {
      await addLcDataset({
        code: formState.code,
        name: formState.name,
        sqlText: formState.sqlText,
        paramsJson: paramsJson || undefined,
        remark: formState.remark || undefined
      })
      message.success('新建成功')
    }
    modalOpen.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function onDelete(record: LcDataset): Promise<void> {
  await removeLcDataset(record.id)
  message.success('删除成功')
  loadList()
}

// ============ 数据预览 ============

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewTarget = ref<LcDataset | null>(null)
/** 参数定义（来自 paramsJson） */
const paramDefs = ref<DatasetParam[]>([])
/** 文本类参数取值（string/date） */
const paramStr = reactive<Record<string, string>>({})
/** 数字类参数取值 */
const paramNum = reactive<Record<string, number | undefined>>({})
const previewResult = ref<DatasetDataResult | null>(null)
const previewRows = computed(() => previewResult.value?.rows || [])
const previewColumns = computed(() => {
  const cols = previewResult.value?.columns || []
  return cols.map((col) => ({ title: col, dataIndex: col, key: col }))
})

function setParamNum(name: string, v: unknown): void {
  if (v === null || v === undefined || v === '') {
    paramNum[name] = undefined
    return
  }
  const n = Number(v)
  paramNum[name] = Number.isFinite(n) ? n : undefined
}

async function openPreview(record: LcDataset): Promise<void> {
  const detail = await getLcDataset(record.id)
  previewTarget.value = detail
  paramDefs.value = parseDatasetParams(detail.paramsJson)
  Object.keys(paramStr).forEach((k) => delete paramStr[k])
  Object.keys(paramNum).forEach((k) => delete paramNum[k])
  for (const p of paramDefs.value) {
    const dv = p.defaultValue
    if (p.type === 'number') {
      setParamNum(p.name, typeof dv === 'number' ? dv : dv === null || dv === undefined ? undefined : Number(dv))
    } else {
      paramStr[p.name] = typeof dv === 'string' ? dv : dv === null || dv === undefined ? '' : String(dv)
    }
  }
  previewResult.value = null
  previewOpen.value = true
  const allFilled = paramDefs.value.every((p) => {
    if (!p.required) return true
    const v = p.type === 'number' ? paramNum[p.name] : paramStr[p.name]
    return v !== undefined && v !== null && v !== ''
  })
  if (allFilled) {
    void runPreview()
  }
}

/** 收集参数并调用预览接口（结果限 100 行，由后端截断） */
async function runPreview(): Promise<void> {
  const target = previewTarget.value
  if (!target) return
  const params: Record<string, unknown> = {}
  for (const p of paramDefs.value) {
    const v = p.type === 'number' ? paramNum[p.name] : paramStr[p.name]
    if (v === undefined || v === null || v === '') {
      if (p.required) {
        message.warning(`参数「${p.label || p.name}」不能为空`)
        return
      }
      continue
    }
    params[p.name] = v
  }
  previewLoading.value = true
  try {
    previewResult.value = await previewLcDataset(target.id, params)
    if (!previewResult.value.rows || !previewResult.value.rows.length) {
      message.info('查询成功，无数据')
    }
  } finally {
    previewLoading.value = false
  }
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
          placeholder="数据集编码 / 名称"
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
      <a-button type="primary" v-perm="'lc:dataset:add'" @click="openAdd">
        <template #icon><plus-outlined /></template>
        新建数据集
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
        <template v-if="column.key === 'params'">
          {{ paramCount(record as LcDataset) }}
        </template>
        <template v-else-if="column.dataIndex === 'sqlText'">
          <span class="sql-text">{{ (record as LcDataset).sqlText || '-' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">
          {{ (record as LcDataset).remark || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openPreview(record as LcDataset)">
              <eye-outlined /> 预览
            </a-button>
            <a-button type="link" size="small" v-perm="'lc:dataset:edit'" @click="openEdit(record as LcDataset)">
              编辑
            </a-button>
            <a-popconfirm title="确定删除该数据集吗？" ok-text="确定" cancel-text="取消" @confirm="onDelete(record as LcDataset)">
              <a-button type="link" danger size="small" v-perm="'lc:dataset:delete'">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新建/编辑弹窗 -->
    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑数据集' : '新建数据集'"
      :confirm-loading="saving"
      :mask-closable="false"
      :width="640"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
        <a-form-item label="数据集编码" name="code">
          <a-input
            v-model:value="formState.code"
            placeholder="如 sales_daily（保存后不可修改）"
            :disabled="!!editingId"
          />
        </a-form-item>
        <a-form-item label="数据集名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如 每日销售统计" />
        </a-form-item>
        <a-form-item label="查询 SQL（仅 SELECT）" name="sqlText">
          <a-textarea
            v-model:value="formState.sqlText"
            :rows="6"
            placeholder="仅允许单条 SELECT；参数用 #{param} 占位，如：&#10;SELECT date, amount FROM sales WHERE date >= DATE_SUB(NOW(), INTERVAL #{days} DAY)"
          />
        </a-form-item>
        <a-form-item
          label="参数定义（JSON 数组，可空）"
          name="paramsJson"
          extra='格式：[{"name":"days","label":"最近天数","type":"number","required":true,"defaultValue":7}]'
        >
          <a-textarea
            v-model:value="formState.paramsJson"
            :rows="4"
            placeholder='[{"name":"days","label":"最近天数","type":"number","required":true,"defaultValue":7}]'
          />
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 数据预览弹窗 -->
    <a-modal
      v-model:open="previewOpen"
      :title="`数据预览 - ${previewTarget?.name || ''}`"
      :width="900"
      :footer="null"
      :mask-closable="false"
    >
      <div class="preview-params">
        <a-form v-if="paramDefs.length" layout="inline" class="preview-form">
          <a-form-item v-for="p in paramDefs" :key="p.name" :label="p.label || p.name" :required="p.required">
            <a-input-number
              v-if="p.type === 'number'"
              :value="paramNum[p.name]"
              placeholder="数值"
              style="width: 150px"
              @change="(v: unknown) => setParamNum(p.name, v)"
            />
            <a-input v-else v-model:value="paramStr[p.name]" placeholder="参数值" style="width: 150px" allow-clear />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="previewLoading" @click="runPreview">查询</a-button>
          </a-form-item>
        </a-form>
        <a-button v-else type="primary" :loading="previewLoading" @click="runPreview">查询（该数据集无需参数）</a-button>
      </div>

      <a-table
        v-if="previewRows.length"
        :data-source="previewRows"
        :columns="previewColumns"
        :loading="previewLoading"
        size="small"
        :pagination="false"
        :scroll="{ x: 'max-content', y: 380 }"
        :row-key="(_record: Record<string, unknown>, index: number | undefined) => String(index)"
      />
      <a-empty v-else-if="!previewLoading" description="点击「查询」查看数据（最多返回 100 行）" class="preview-empty" />
    </a-modal>
  </div>
</template>

<style scoped>
.sql-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.preview-params {
  margin-bottom: 12px;
}

.preview-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.preview-empty {
  margin: 48px 0;
}
</style>
