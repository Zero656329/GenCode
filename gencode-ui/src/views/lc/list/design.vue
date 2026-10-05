<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ApiOutlined,
  DeleteOutlined,
  PlusOutlined,
  RocketOutlined,
  SaveOutlined
} from '@ant-design/icons-vue'
import { getLcDatasourceListAll, type LcDatasourceOption } from '@/api/lc-datasource'
import {
  getLcList,
  getTableColumns,
  publishLcList,
  updateLcList,
  type LcListSourceType,
  type LcListStatus,
  type ListColumn,
  type ListSchema,
  type ListSearchItem,
  type SearchControlType,
  type SearchOp,
  type TableColumnInfo
} from '@/api/lc-list'

/** 列表状态元信息：0草稿 1已发布 2停用 */
const STATUS_META: Record<LcListStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '已发布', color: 'green' },
  2: { text: '停用', color: 'red' }
}

const SEARCH_OPS: Array<{ value: SearchOp; label: string }> = [
  { value: 'eq', label: '等于 (eq)' },
  { value: 'like', label: '包含 (like)' },
  { value: 'gt', label: '大于 (gt)' },
  { value: 'ge', label: '大于等于 (ge)' },
  { value: 'lt', label: '小于 (lt)' },
  { value: 'le', label: '小于等于 (le)' },
  { value: 'between', label: '区间 (between)' }
]

const SEARCH_TYPES: Array<{ value: SearchControlType; label: string }> = [
  { value: 'input', label: '输入框' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' }
]

/** 列配置表格列定义 */
const COLUMN_EDIT_COLUMNS = [
  { title: '列名', dataIndex: 'field', width: 200 },
  { title: '标题', dataIndex: 'title', width: 160 },
  { title: '宽度(px)', dataIndex: 'width', width: 110 },
  { title: '字典类型', dataIndex: 'dictType', width: 170 },
  { title: '可编辑', dataIndex: 'editable', width: 80 },
  { title: '', key: 'action', width: 60 }
]

const route = useRoute()
const router = useRouter()
const listId = String(route.params.id ?? '')

// ============ 基础信息 ============

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const name = ref('')
const remark = ref('')
const version = ref(0)
const status = ref<LcListStatus>(0)
const code = ref('')

// ============ 数据源区 ============

/** 设计器支持 TABLE/SQL 两种来源（API 为后端预留，加载后只读展示） */
const sourceType = ref<LcListSourceType>('TABLE')
const dsOptions = ref<LcDatasourceOption[]>([])

// TABLE 型
const tableDsId = ref<string>('') // '' = 平台主库
const tableName = ref('')
const pkField = ref('id')

// SQL 型
const sqlDsId = ref<string>('') // '' = 平台主库
const sqlText = ref('')

/** 数据源下拉选项：首项"平台主库"值为空 */
const dsSelectOptions = computed(() => [
  { value: '', label: '平台主库' },
  ...dsOptions.value.map((d) => ({ value: d.id, label: d.name }))
])

function dsName(id: string): string {
  if (!id) return '平台主库'
  const found = dsOptions.value.find((d) => d.id === id)
  return found ? found.name : id
}

// ============ 列配置 ============

const columns = ref<ListColumn[]>([])

const parsedColumns = ref<TableColumnInfo[]>([])
const parsing = ref(false)

function addParsedColumn(col: TableColumnInfo): void {
  if (columns.value.some((c) => c.field === col.columnName)) return
  columns.value.push({
    field: col.columnName,
    title: col.comment || col.columnName,
    width: null,
    dictType: '',
    editable: false
  })
}

function addAllParsedColumns(): void {
  for (const col of parsedColumns.value) addParsedColumn(col)
}

function isParsedAdded(col: TableColumnInfo): boolean {
  return columns.value.some((c) => c.field === col.columnName)
}

function addColumnManual(): void {
  columns.value.push({ field: '', title: '', width: null, dictType: '', editable: false })
}

function removeColumn(idx: number): void {
  columns.value.splice(idx, 1)
}

/** 宽度输入：空值回到 null（自适应） */
function setColumnWidth(col: ListColumn, v: unknown): void {
  if (v === null || v === undefined || v === '') {
    col.width = null
    return
  }
  const n = Number(v)
  col.width = Number.isFinite(n) && n > 0 ? Math.round(n) : null
}

async function onParseColumns(): Promise<void> {
  if (!tableName.value) {
    message.warning('请先输入表名')
    return
  }
  parsing.value = true
  try {
    const list = await getTableColumns({
      datasourceId: tableDsId.value || undefined,
      tableName: tableName.value
    })
    parsedColumns.value = list || []
    if (parsedColumns.value.length === 0) {
      message.warning('未解析到列，请确认表名是否正确')
    } else {
      message.success(`解析成功，共 ${parsedColumns.value.length} 列，可一键添加到列配置`)
    }
  } finally {
    parsing.value = false
  }
}

// ============ 搜索项配置 ============

const searchItems = ref<ListSearchItem[]>([])

function addSearchItem(): void {
  searchItems.value.push({ field: '', label: '', op: 'eq', type: 'input' })
}

function removeSearchItem(idx: number): void {
  searchItems.value.splice(idx, 1)
}

/** 搜索字段下拉：取列配置的字段 */
const searchFieldOptions = computed(() =>
  columns.value
    .filter((c) => c.field)
    .map((c) => ({ value: c.field, label: `${c.field}（${c.title || '-'}）` }))
)

function onSearchFieldChange(item: ListSearchItem, field: string): void {
  item.field = field
  const col = columns.value.find((c) => c.field === field)
  if (col && col.title) item.label = col.title
}

// ============ 按钮配置 ============

const buttons = reactive({ add: false, edit: false, delete: false })

// ============ 加载 / 保存 / 发布 ============

function normalizeColumn(raw: Partial<ListColumn>): ListColumn {
  const width = Number(raw.width)
  return {
    field: typeof raw.field === 'string' ? raw.field : '',
    title: typeof raw.title === 'string' ? raw.title : '',
    width: Number.isFinite(width) && width > 0 ? Math.round(width) : null,
    dictType: typeof raw.dictType === 'string' ? raw.dictType : '',
    editable: raw.editable === true
  }
}

function normalizeSearchItem(raw: Partial<ListSearchItem>): ListSearchItem {
  const op = (SEARCH_OPS.find((o) => o.value === raw.op)?.value ?? 'eq') as SearchOp
  const type = (SEARCH_TYPES.find((t) => t.value === raw.type)?.value ?? 'input') as SearchControlType
  return {
    field: typeof raw.field === 'string' ? raw.field : '',
    label: typeof raw.label === 'string' ? raw.label : '',
    op,
    type
  }
}

function loadSourceConfig(raw: string | null, type: LcListSourceType): void {
  if (!raw) return
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const dsId = typeof parsed.datasourceId === 'string' ? parsed.datasourceId : ''
    if (type === 'SQL') {
      sqlDsId.value = dsId
      sqlText.value = typeof parsed.sql === 'string' ? parsed.sql : ''
    } else {
      tableDsId.value = dsId
      tableName.value = typeof parsed.tableName === 'string' ? parsed.tableName : ''
      pkField.value =
        typeof parsed.pkField === 'string' && parsed.pkField ? parsed.pkField : 'id'
    }
  } catch {
    message.error('来源配置解析失败，已按空配置加载')
  }
}

function loadListSchema(raw: string | null): void {
  if (!raw) return
  try {
    const parsed = JSON.parse(raw) as {
      columns?: Array<Partial<ListColumn>>
      search?: Array<Partial<ListSearchItem>>
      buttons?: Partial<{ add: boolean; edit: boolean; delete: boolean }>
    }
    columns.value = (parsed.columns || []).map(normalizeColumn)
    searchItems.value = (parsed.search || []).map(normalizeSearchItem)
    buttons.add = parsed.buttons?.add === true
    buttons.edit = parsed.buttons?.edit === true
    buttons.delete = parsed.buttons?.delete === true
  } catch {
    message.error('列表 Schema 解析失败，已按空配置加载')
  }
}

async function loadDetail(): Promise<void> {
  loading.value = true
  try {
    const detail = await getLcList(listId)
    code.value = detail.code
    name.value = detail.name
    remark.value = detail.remark || ''
    version.value = detail.version || 0
    status.value = detail.status
    sourceType.value = detail.sourceType
    loadSourceConfig(detail.sourceConfig, detail.sourceType)
    loadListSchema(detail.listSchema)
  } finally {
    loading.value = false
  }
}

const FIELD_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/

function findConfigError(): string {
  if (!name.value) return '列表名称不能为空'
  if (sourceType.value === 'TABLE' && !tableName.value) return '请输入表名'
  if (sourceType.value === 'SQL' && !sqlText.value.trim()) return '请输入查询 SQL'
  const seen = new Set<string>()
  for (const col of columns.value) {
    if (!col.field) return '列配置存在未填写的列名'
    if (!FIELD_RE.test(col.field)) return `列名「${col.field}」格式不正确（字母数字下划线，字母开头）`
    if (seen.has(col.field)) return `列名「${col.field}」重复`
    seen.add(col.field)
  }
  if (columns.value.length === 0) return '请至少配置一个显示列'
  for (const item of searchItems.value) {
    if (!item.field) return '搜索项存在未选择的字段'
    if (!seen.has(item.field)) return `搜索项字段「${item.field}」不在列配置中`
  }
  return ''
}

/** sourceConfig/listSchema 均以 JSON 字符串保存 */
function buildSourceConfig(): string {
  if (sourceType.value === 'SQL') {
    return JSON.stringify({ datasourceId: sqlDsId.value || null, sql: sqlText.value.trim() })
  }
  return JSON.stringify({
    datasourceId: tableDsId.value || null,
    tableName: tableName.value.trim(),
    pkField: pkField.value.trim() || 'id'
  })
}

function buildListSchema(): string {
  const schema: ListSchema = {
    columns: columns.value.map((c) => ({
      field: c.field,
      title: c.title || c.field,
      width: c.width,
      dictType: c.dictType || '',
      editable: c.editable
    })),
    search: searchItems.value.map((s) => ({
      field: s.field,
      label: s.label || s.field,
      op: s.op,
      type: s.type
    })),
    buttons: { ...buttons }
  }
  return JSON.stringify(schema)
}

/** 保存设计；silent=true 时由发布流程调用，不重复提示 */
async function doSave(silent: boolean): Promise<boolean> {
  const err = findConfigError()
  if (err) {
    message.error(err)
    return false
  }
  saving.value = true
  try {
    await updateLcList({
      id: listId,
      name: name.value,
      sourceType: sourceType.value,
      sourceConfig: buildSourceConfig(),
      listSchema: buildListSchema(),
      remark: remark.value || null
    })
    if (!silent) message.success('保存成功')
    return true
  } finally {
    saving.value = false
  }
}

function onSave(): void {
  void doSave(false)
}

async function onPublish(): Promise<void> {
  publishing.value = true
  try {
    const ok = await doSave(true)
    if (!ok) return
    const res = await publishLcList(listId)
    version.value = res && res.version ? res.version : version.value + 1
    status.value = 1
    message.success(`发布成功，当前版本 v${version.value}`)
  } finally {
    publishing.value = false
  }
}

function onBack(): void {
  router.push('/lc/list')
}

onMounted(() => {
  if (!listId) {
    message.error('缺少列表 ID')
    router.replace('/lc/list')
    return
  }
  getLcDatasourceListAll()
    .then((list) => {
      dsOptions.value = list || []
    })
    .catch(() => {
      dsOptions.value = []
    })
  loadDetail()
})
</script>

<template>
  <div class="designer-page">
    <!-- 顶部工具条 -->
    <div class="designer-header">
      <a-space>
        <a-button @click="onBack">
          <template #icon><arrow-left-outlined /></template>
          返回
        </a-button>
        <a-input v-model:value="name" class="name-input" placeholder="列表名称" />
        <a-tag color="blue">{{ code }}</a-tag>
        <a-tag :color="STATUS_META[status].color">{{ STATUS_META[status].text }}</a-tag>
        <a-tag v-if="version > 0" color="geekblue">v{{ version }}</a-tag>
      </a-space>
      <a-space>
        <a-button v-perm="'lc:list:edit'" type="primary" :loading="saving" @click="onSave">
          <template #icon><save-outlined /></template>
          保存
        </a-button>
        <a-button v-perm="'lc:list:publish'" :loading="publishing" @click="onPublish">
          <template #icon><rocket-outlined /></template>
          发布
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div class="designer-body">
        <!-- 数据源区 -->
        <a-card title="数据源" size="small" class="section-card">
          <template #extra>
            <a-radio-group
              v-if="sourceType !== 'API'"
              v-model:value="sourceType"
              size="small"
            >
              <a-radio-button value="TABLE">物理表（TABLE）</a-radio-button>
              <a-radio-button value="SQL">自定义SQL（SQL）</a-radio-button>
            </a-radio-group>
            <a-tag v-else color="orange">API 型（预留）</a-tag>
          </template>

          <template v-if="sourceType === 'API'">
            <a-alert
              type="warning"
              show-icon
              message="API 型数据源为预留能力，运行时查询将提示“暂未支持”；当前仅可查看。"
            />
          </template>

          <a-form v-else layout="inline" class="source-form">
            <template v-if="sourceType === 'TABLE'">
              <a-form-item label="数据源">
                <a-select
                  v-model:value="tableDsId"
                  :options="dsSelectOptions"
                  style="width: 220px"
                  placeholder="平台主库"
                />
              </a-form-item>
              <a-form-item label="表名">
                <a-input v-model:value="tableName" style="width: 200px" placeholder="如 sys_config" />
              </a-form-item>
              <a-form-item label="主键">
                <a-input v-model:value="pkField" style="width: 140px" placeholder="默认 id" />
              </a-form-item>
              <a-form-item>
                <a-button type="primary" ghost :loading="parsing" @click="onParseColumns">
                  <template #icon><api-outlined /></template>
                  解析列
                </a-button>
              </a-form-item>
            </template>

            <template v-else>
              <a-form-item label="数据源">
                <a-select
                  v-model:value="sqlDsId"
                  :options="dsSelectOptions"
                  style="width: 220px"
                  placeholder="平台主库"
                />
              </a-form-item>
              <a-form-item class="sql-item" label="查询 SQL">
                <a-textarea
                  v-model:value="sqlText"
                  :rows="4"
                  style="width: 560px"
                  placeholder="SELECT id, name FROM sys_config WHERE 1=1 AND name LIKE CONCAT('%', #{name}, '%')"
                />
                <div class="field-tip">
                  仅允许单条 SELECT；<code>#{field}</code> 为搜索占位符（由引擎转参数绑定）；
                  动态条件请以 <code>WHERE 1=1 AND ...</code> 风格拼接；数据源：{{ dsName(sqlDsId) }}
                </div>
              </a-form-item>
            </template>
          </a-form>
        </a-card>

        <!-- 解析结果 -->
        <a-card
          v-if="parsedColumns.length > 0"
          title="解析列（点击添加到列配置）"
          size="small"
          class="section-card"
        >
          <template #extra>
            <a-button size="small" type="primary" ghost @click="addAllParsedColumns">
              <template #icon><plus-outlined /></template>
              全部添加
            </a-button>
          </template>
          <div class="parsed-chips">
            <a-tag
              v-for="col in parsedColumns"
              :key="col.columnName"
              class="parsed-chip"
              :color="isParsedAdded(col) ? 'default' : 'blue'"
              @click="!isParsedAdded(col) && addParsedColumn(col)"
            >
              {{ col.columnName }}
              <span class="chip-type">{{ col.typeName }}</span>
              <span v-if="col.comment" class="chip-comment">{{ col.comment }}</span>
            </a-tag>
          </div>
        </a-card>

        <!-- 列配置 -->
        <a-card title="列配置" size="small" class="section-card">
          <template #extra>
            <a-button size="small" type="primary" ghost @click="addColumnManual">
              <template #icon><plus-outlined /></template>
              手动添加列
            </a-button>
          </template>
          <a-table
            :data-source="columns"
            :columns="COLUMN_EDIT_COLUMNS"
            :pagination="false"
            :row-key="(record: ListColumn) => columns.indexOf(record)"
            size="small"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.dataIndex === 'field'">
                <a-input v-model:value="(record as ListColumn).field" placeholder="列名，如 user_name" />
              </template>
              <template v-else-if="column.dataIndex === 'title'">
                <a-input v-model:value="(record as ListColumn).title" placeholder="标题" />
              </template>
              <template v-else-if="column.dataIndex === 'width'">
                <a-input-number
                  :value="(record as ListColumn).width ?? undefined"
                  :min="40"
                  style="width: 100%"
                  placeholder="自适应"
                  @change="(v: unknown) => setColumnWidth(record as ListColumn, v)"
                />
              </template>
              <template v-else-if="column.dataIndex === 'dictType'">
                <a-input
                  v-model:value="(record as ListColumn).dictType"
                  placeholder="字典类型，如 sys_sex"
                  allow-clear
                />
              </template>
              <template v-else-if="column.dataIndex === 'editable'">
                <a-switch v-model:checked="(record as ListColumn).editable" size="small" />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="text" danger size="small" @click="removeColumn(index)">
                  <delete-outlined />
                </a-button>
              </template>
            </template>
          </a-table>
        </a-card>

        <!-- 搜索项配置 -->
        <a-card title="搜索项配置" size="small" class="section-card">
          <template #extra>
            <a-button size="small" type="primary" ghost :disabled="columns.length === 0" @click="addSearchItem">
              <template #icon><plus-outlined /></template>
              添加搜索项
            </a-button>
          </template>
          <a-empty v-if="searchItems.length === 0" description="暂无搜索项" />
          <div v-else class="search-rows">
            <div v-for="(item, idx) in searchItems" :key="idx" class="search-row">
              <a-select
                :value="item.field || undefined"
                :options="searchFieldOptions"
                placeholder="字段"
                show-search
                option-filter-prop="label"
                style="width: 200px"
                @change="(v: unknown) => onSearchFieldChange(item, String(v ?? ''))"
              />
              <a-input v-model:value="item.label" placeholder="标题" style="width: 160px" />
              <a-select v-model:value="item.op" :options="SEARCH_OPS" style="width: 160px" />
              <a-select v-model:value="item.type" :options="SEARCH_TYPES" style="width: 120px" />
              <a-button type="text" danger size="small" @click="removeSearchItem(idx)">
                <delete-outlined />
              </a-button>
            </div>
          </div>
          <div v-if="columns.length === 0" class="field-tip">请先在"列配置"中添加列，再配置搜索项。</div>
        </a-card>

        <!-- 按钮配置 -->
        <a-card title="按钮配置" size="small" class="section-card">
          <a-space :size="24">
            <a-checkbox v-model:checked="buttons.add">新增</a-checkbox>
            <a-checkbox v-model:checked="buttons.edit">编辑</a-checkbox>
            <a-checkbox v-model:checked="buttons.delete">删除</a-checkbox>
          </a-space>
          <div class="field-tip">勾选后运行时列表页显示对应操作按钮（仅 TABLE 型来源支持行保存/删除）。</div>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.name-input {
  width: 220px;
}

.designer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-card :deep(.ant-card-body) {
  padding-top: 12px;
}

.source-form :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.sql-item {
  display: block;
}

.field-tip {
  margin-top: 6px;
  color: #999;
  font-size: 12px;
}

.parsed-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 4px;
}

.parsed-chip {
  cursor: pointer;
  user-select: none;
}

.chip-type {
  margin-left: 4px;
  color: #999;
  font-size: 12px;
}

.chip-comment {
  margin-left: 4px;
  color: #1677ff;
  font-size: 12px;
}

.search-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
