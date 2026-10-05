<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined
} from '@ant-design/icons-vue'
import { useDict } from '@/hooks/useDict'
import {
  deleteLcListRow,
  getPublishedLcList,
  postLcListData,
  saveLcListRow,
  type LcListPublished,
  type LcListSourceType,
  type ListColumn,
  type ListSchema,
  type TableSourceConfig
} from '@/api/lc-list'

/**
 * 运行时列表页（/app/list/:code，菜单隐藏页）
 * 按 code 取"已发布"快照（sourceConfig+listSchema）动态渲染：
 * 搜索区按 schema.search、表格列按 schema.columns、服务端分页/排序、行操作按 schema.buttons。
 */

type RowData = Record<string, unknown>

const route = useRoute()
const listCode = String(route.params.code ?? '')

const loading = ref(false)
const loadError = ref('')
const listName = ref('')
const version = ref(0)
const sourceType = ref<LcListSourceType>('TABLE')
const schema = ref<ListSchema>({ columns: [], search: [], buttons: { add: false, edit: false, delete: false } })

/** TABLE 型主键字段（行保存/删除用），默认 id */
const pkField = ref('id')

// ============ 字典翻译（useDict，经 app store 缓存） ============

const dictRefs: Record<string, Ref<DictOption[]>> = {}
function dictOptions(dictType: string): DictOption[] {
  let r = dictRefs[dictType]
  if (!r) {
    r = useDict(dictType).options
    dictRefs[dictType] = r
  }
  return r.value
}

/** 字典列显示值：value→label，未命中回退原值 */
function dictLabel(dictType: string, value: unknown): string {
  const raw = value === null || value === undefined ? '' : String(value)
  const hit = dictOptions(dictType).find((o) => o.value === raw)
  return hit ? hit.label : raw
}

function cellText(col: ListColumn, row: RowData): string {
  const v = row[col.field]
  if (v === null || v === undefined || v === '') return '-'
  return String(v)
}

/** 单元格显示：字典列翻译，其余原值（空值显示 -） */
function cellDisplay(col: ListColumn | undefined, row: RowData): string {
  if (!col) return '-'
  if (col.dictType) return dictLabel(col.dictType, row[col.field])
  return cellText(col, row)
}

// ============ 加载发布配置 ============

async function loadConfig(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const pub: LcListPublished = await getPublishedLcList(listCode)
    listName.value = pub.name || pub.code
    version.value = pub.version || 0
    sourceType.value = pub.sourceType
    try {
      const parsed = JSON.parse(pub.listSchema || '{}') as Partial<ListSchema>
      schema.value = {
        columns: Array.isArray(parsed.columns) ? parsed.columns : [],
        search: Array.isArray(parsed.search) ? parsed.search : [],
        buttons: {
          add: parsed.buttons?.add === true,
          edit: parsed.buttons?.edit === true,
          delete: parsed.buttons?.delete === true
        }
      }
    } catch {
      loadError.value = '列表 Schema 解析失败，请联系管理员'
      return
    }
    if (pub.sourceType === 'TABLE') {
      try {
        const cfg = JSON.parse(pub.sourceConfig || '{}') as Partial<TableSourceConfig>
        pkField.value = (typeof cfg.pkField === 'string' && cfg.pkField) || 'id'
      } catch {
        pkField.value = 'id'
      }
    }
    initSearchState()
    await loadData()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// ============ 搜索区 ============

/** 单值搜索项取值（between 用 rangeValues）；动态字段容器用 any 承接组件绑定 */
const searchValues = reactive<Record<string, any>>({})
/** between 搜索项：begin/end 两个值 */
const rangeValues = reactive<Record<string, { begin: any; end: any }>>({})

function initSearchState(): void {
  Object.keys(searchValues).forEach((k) => delete searchValues[k])
  Object.keys(rangeValues).forEach((k) => delete rangeValues[k])
  for (const item of schema.value.search) {
    if (item.op === 'between') {
      rangeValues[item.field] = { begin: undefined, end: undefined }
    } else {
      searchValues[item.field] = undefined
    }
  }
}

/** 模板中取 between 值容器（兜底创建，避免非空断言） */
function rangeOf(field: string): { begin: any; end: any } {
  let r = rangeValues[field]
  if (!r) {
    r = { begin: undefined, end: undefined }
    rangeValues[field] = r
  }
  return r
}

/** 组装服务端参数：过滤空值；between 双值以逗号分隔（引擎 betweenPair 支持） */
function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  for (const item of schema.value.search) {
    if (item.op === 'between') {
      const range = rangeValues[item.field]
      const begin = range?.begin
      const end = range?.end
      if (begin !== undefined && begin !== '' && end !== undefined && end !== '') {
        params[item.field] = `${begin},${end}`
      }
      continue
    }
    const v = searchValues[item.field]
    if (v !== undefined && v !== null && v !== '') params[item.field] = v
  }
  return params
}

function onSearch(): void {
  query.pageNum = 1
  void loadData()
}

function onResetSearch(): void {
  initSearchState()
  sortState.orderBy = undefined
  sortState.orderDir = undefined
  query.pageNum = 1
  void loadData()
}

// ============ 数据加载（服务端分页 + 排序） ============

const query = reactive({ pageNum: 1, pageSize: 10 })
const sortState = reactive<{ orderBy: string | undefined; orderDir: 'asc' | 'desc' | undefined }>({
  orderBy: undefined,
  orderDir: undefined
})

const rows = ref<RowData[]>([])
const total = ref(0)
const dataLoading = ref(false)

async function loadData(): Promise<void> {
  dataLoading.value = true
  try {
    const page = await postLcListData(listCode, {
      params: buildParams(),
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      orderBy: sortState.orderBy,
      orderDir: sortState.orderDir
    })
    rows.value = (page.list || []) as RowData[]
    total.value = page.total || 0
  } finally {
    dataLoading.value = false
  }
}

const pagination = computed(() => ({
  current: query.pageNum,
  pageSize: query.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

/** 列头点击排序 + 分页变化统一走 table change */
function onTableChange(
  pag: { current?: number; pageSize?: number },
  _filters: unknown,
  sorter: { field?: string | string[]; order?: 'ascend' | 'descend' | null }
): void {
  query.pageNum = pag.current || 1
  query.pageSize = pag.pageSize || 10
  const field = Array.isArray(sorter.field) ? sorter.field.join('.') : sorter.field
  if (sorter.order && field) {
    sortState.orderBy = field
    sortState.orderDir = sorter.order === 'ascend' ? 'asc' : 'desc'
  } else {
    sortState.orderBy = undefined
    sortState.orderDir = undefined
  }
  void loadData()
}

/** 表格列：按 schema.columns，全部支持列头点击排序 */
const tableColumns = computed(() => {
  const cols = schema.value.columns.map((c) => ({
    dataIndex: c.field,
    title: c.title || c.field,
    width: c.width ?? undefined,
    sorter: true
  }))
  if (hasRowAction.value) {
    cols.push({ dataIndex: '__action', title: '操作', width: 150, sorter: false })
  }
  return cols
})

const hasRowAction = computed(() => schema.value.buttons.edit || schema.value.buttons.delete)

// ============ 行操作：新增 / 编辑 / 删除 ============

const modalOpen = ref(false)
const modalMode = ref<'add' | 'edit'>('add')
const savingRow = ref(false)
const editingPk = ref<string | undefined>(undefined)
/** 行编辑表单值：动态字段容器用 any 承接组件绑定 */
const rowForm = reactive<Record<string, any>>({})

/** 可编辑列（出现在新增/编辑表单中） */
const editableColumns = computed(() => schema.value.columns.filter((c) => c.editable))

function openAdd(): void {
  modalMode.value = 'add'
  editingPk.value = undefined
  Object.keys(rowForm).forEach((k) => delete rowForm[k])
  for (const col of editableColumns.value) rowForm[col.field] = undefined
  modalOpen.value = true
}

function openEdit(row: RowData): void {
  modalMode.value = 'edit'
  const pk = row[pkField.value]
  editingPk.value = pk === null || pk === undefined ? undefined : String(pk)
  Object.keys(rowForm).forEach((k) => delete rowForm[k])
  for (const col of editableColumns.value) {
    const v = row[col.field]
    rowForm[col.field] = v === null || v === undefined ? undefined : v
  }
  modalOpen.value = true
}

async function handleRowSave(): Promise<void> {
  const row: Record<string, unknown> = {}
  for (const col of editableColumns.value) {
    const v = rowForm[col.field]
    row[col.field] = v === undefined || v === '' ? null : v
  }
  savingRow.value = true
  try {
    await saveLcListRow(listCode, { mode: modalMode.value, pk: editingPk.value, row })
    message.success(modalMode.value === 'add' ? '新增成功' : '保存成功')
    modalOpen.value = false
    await loadData()
  } finally {
    savingRow.value = false
  }
}

async function handleRowDelete(row: RowData): Promise<void> {
  const pk = row[pkField.value]
  if (pk === null || pk === undefined) {
    message.error(`行数据缺少主键「${pkField.value}」，无法删除`)
    return
  }
  await deleteLcListRow(listCode, { pk: String(pk) })
  message.success('删除成功')
  await loadData()
}

// ============ 杂项 ============

function reloadAll(): void {
  loadConfig().catch(() => undefined)
}

onMounted(() => {
  if (!listCode) {
    loadError.value = '缺少列表编码'
    return
  }
  void loadConfig()
})
</script>

<template>
  <div class="list-render-page">
    <a-spin :spinning="loading">
      <!-- 加载失败 / 解析失败：空状态提示 -->
      <a-result v-if="loadError" status="warning" title="列表加载失败" :sub-title="loadError">
        <template #extra>
          <a-button type="primary" @click="reloadAll">
            <template #icon><reload-outlined /></template>
            重新加载
          </a-button>
        </template>
      </a-result>

      <template v-else>
        <!-- 标题 -->
        <div class="render-header">
          <span class="render-title">{{ listName }}</span>
          <a-tag v-if="version > 0" color="blue">v{{ version }}</a-tag>
          <a-tag v-if="sourceType !== 'TABLE'" color="orange">{{ sourceType }}</a-tag>
        </div>

        <!-- 搜索区（按 schema.search 渲染） -->
        <div v-if="schema.search.length > 0" class="search-bar">
          <a-space wrap :size="[12, 8]">
            <template v-for="item in schema.search" :key="item.field + item.op">
              <template v-if="item.op === 'between'">
                <template v-if="item.type === 'date'">
                  <a-date-picker
                    v-model:value="rangeOf(item.field).begin"
                    value-format="YYYY-MM-DD"
                    :placeholder="`${item.label || item.field} 开始`"
                    style="width: 140px"
                  />
                  <span class="range-sep">~</span>
                  <a-date-picker
                    v-model:value="rangeOf(item.field).end"
                    value-format="YYYY-MM-DD"
                    :placeholder="`${item.label || item.field} 结束`"
                    style="width: 140px"
                  />
                </template>
                <template v-else-if="item.type === 'number'">
                  <a-input-number
                    v-model:value="rangeOf(item.field).begin"
                    :placeholder="`${item.label || item.field} 最小`"
                    style="width: 130px"
                  />
                  <span class="range-sep">~</span>
                  <a-input-number
                    v-model:value="rangeOf(item.field).end"
                    :placeholder="`${item.label || item.field} 最大`"
                    style="width: 130px"
                  />
                </template>
                <template v-else>
                  <a-input
                    v-model:value="rangeOf(item.field).begin"
                    :placeholder="`${item.label || item.field} 开始`"
                    style="width: 130px"
                  />
                  <span class="range-sep">~</span>
                  <a-input
                    v-model:value="rangeOf(item.field).end"
                    :placeholder="`${item.label || item.field} 结束`"
                    style="width: 130px"
                  />
                </template>
              </template>
              <template v-else>
                <a-input-number
                  v-if="item.type === 'number'"
                  v-model:value="searchValues[item.field]"
                  :placeholder="item.label || item.field"
                  style="width: 160px"
                  @press-enter="onSearch"
                />
                <a-date-picker
                  v-else-if="item.type === 'date'"
                  v-model:value="searchValues[item.field]"
                  value-format="YYYY-MM-DD"
                  :placeholder="item.label || item.field"
                  style="width: 160px"
                />
                <a-input
                  v-else
                  v-model:value="searchValues[item.field]"
                  :placeholder="item.label || item.field"
                  allow-clear
                  style="width: 180px"
                  @press-enter="onSearch"
                />
              </template>
            </template>
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

        <!-- 操作按钮区（按 schema.buttons，运行时登录即可） -->
        <div v-if="schema.buttons.add" class="toolbar">
          <a-button type="primary" @click="openAdd">
            <template #icon><plus-outlined /></template>
            新增
          </a-button>
          <span></span>
        </div>

        <!-- 数据表格（列头点击排序，服务端分页） -->
        <a-table
          :data-source="rows"
          :columns="tableColumns"
          :loading="dataLoading"
          :row-key="pkField"
          :pagination="pagination"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === '__action'">
              <a-space>
                <a-button
                  v-if="schema.buttons.edit"
                  type="link"
                  size="small"
                  @click="openEdit(record as RowData)"
                >
                  编辑
                </a-button>
                <a-popconfirm
                  v-if="schema.buttons.delete"
                  title="确定删除该行数据吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleRowDelete(record as RowData)"
                >
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
            <template v-else>
              {{ cellDisplay(schema.columns.find((c) => c.field === column.dataIndex), record as RowData) }}
            </template>
          </template>
        </a-table>
      </template>
    </a-spin>

    <!-- 新增/编辑弹窗（editable 列的表单） -->
    <a-modal
      v-model:open="modalOpen"
      :title="modalMode === 'add' ? '新增' : '编辑'"
      :confirm-loading="savingRow"
      :mask-closable="false"
      :width="520"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleRowSave"
    >
      <a-form :model="rowForm" layout="vertical">
        <a-form-item
          v-for="col in editableColumns"
          :key="col.field"
          :label="col.title || col.field"
          :name="col.field"
        >
          <a-select
            v-if="col.dictType"
            v-model:value="rowForm[col.field]"
            :options="dictOptions(col.dictType)"
            placeholder="请选择"
            allow-clear
            style="width: 100%"
          />
          <a-input
            v-else
            v-model:value="rowForm[col.field]"
            :placeholder="`请输入${col.title || col.field}`"
            allow-clear
          />
        </a-form-item>
        <a-empty v-if="editableColumns.length === 0" description="该列表未配置可编辑列" />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.render-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.render-title {
  font-size: 18px;
  font-weight: 600;
}

.range-sep {
  color: #999;
}
</style>
