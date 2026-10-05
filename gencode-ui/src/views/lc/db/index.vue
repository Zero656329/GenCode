<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { CodeOutlined, DatabaseOutlined, PlusOutlined, SyncOutlined } from '@ant-design/icons-vue'
import {
  DEFAULT_TYPE_OPTIONS,
  executeDdl,
  getDbColumns,
  getDbTypeMap,
  isNullable,
  isPk,
  normalizeTypeOptions,
  previewDdl,
  type ColumnInfo,
  type ColumnSpec,
  type TableSpec
} from '@/api/lc-db'

/**
 * 可视化建表（/lc/db）
 * 仅维护业务列：id（雪花 Long）/tenant_id/审计字段/逻辑删除列由平台建表时自动追加。
 * 支持逆向读表回填编辑器，修改后可另存为新表。
 */

/** 表名/列名标识符规则：小写字母开头，仅含小写字母、数字、下划线 */
const NAME_RE = /^[a-z][a-z0-9_]*$/

/** 编辑器行（uid 仅用于行 key） */
interface ColumnRow {
  uid: number
  name: string
  typeName: string
  length: number | null
  scale: number | null
  comment: string
  notNull: boolean
  pk: boolean
  defaultValue: string
}

let uidSeq = 0

function createRow(): ColumnRow {
  uidSeq += 1
  return {
    uid: uidSeq,
    name: '',
    typeName: 'varchar',
    length: 255,
    scale: null,
    comment: '',
    notNull: true,
    pk: false,
    defaultValue: ''
  }
}

/** 该类型是否需要长度（varchar=长度；decimal=精度） */
function needLength(typeName: string): boolean {
  return typeName === 'varchar' || typeName === 'decimal'
}

// ============ 类型下拉（GET /lc/db/typemap） ============

const typeOptions = ref<string[]>([...DEFAULT_TYPE_OPTIONS])
const typeSelectOptions = computed(() =>
  typeOptions.value.map((t) => ({ label: t, value: t }))
)

async function loadTypeMap(): Promise<void> {
  try {
    const raw = await getDbTypeMap()
    typeOptions.value = normalizeTypeOptions(raw)
  } catch {
    // 拉取失败时保留默认类型（request 拦截器已提示错误）
    typeOptions.value = [...DEFAULT_TYPE_OPTIONS]
  }
}

// ============ 表信息 ============

const formRef = ref<FormInstance | null>(null)
const tableName = ref('')
const tableComment = ref('')

const formState = computed(() => ({ tableName: tableName.value, tableComment: tableComment.value }))

const formRules: Record<string, Rule[]> = {
  tableName: [
    { required: true, message: '请输入表名', trigger: 'blur' },
    { pattern: NAME_RE, message: '表名需以小写字母开头，仅含小写字母、数字、下划线', trigger: 'blur' }
  ]
}

// ============ 列编辑 ============

const rows = ref<ColumnRow[]>([])

function addRow(): void {
  rows.value.push(createRow())
}

function removeRow(idx: number): void {
  rows.value.splice(idx, 1)
}

/** 类型切换：按类型补默认长度/小数位，无关项清空 */
function onTypeChange(row: ColumnRow): void {
  if (needLength(row.typeName)) {
    if (row.length == null) row.length = row.typeName === 'varchar' ? 255 : 10
    if (row.typeName === 'decimal' && row.scale == null) row.scale = 2
  } else {
    row.length = null
    row.scale = null
  }
}

/** 勾选主键时强制非空 */
function onPkChange(row: ColumnRow): void {
  if (row.pk) row.notNull = true
}

const columnDefs = [
  { title: '列名', key: 'name', width: 170 },
  { title: '类型', key: 'typeName', width: 130 },
  { title: '长度', key: 'length', width: 110 },
  { title: '小数位', key: 'scale', width: 100 },
  { title: '注释', key: 'comment', width: 200 },
  { title: '默认值', key: 'defaultValue', width: 130 },
  { title: '非空', key: 'notNull', width: 70 },
  { title: '主键', key: 'pk', width: 70 },
  { title: '操作', key: 'action', width: 70, fixed: 'right' as const }
]

// ============ 逆向读表 ============

/** 常见库类型 → 契约基础类型 的别名映射 */
const TYPE_ALIASES: Record<string, string> = {
  char: 'varchar',
  tinyint: 'int',
  smallint: 'int',
  mediumint: 'int',
  integer: 'int',
  int2: 'int',
  int4: 'int',
  serial: 'int',
  int8: 'bigint',
  bigserial: 'bigint',
  number: 'decimal',
  numeric: 'decimal',
  dec: 'decimal',
  float: 'decimal',
  double: 'decimal',
  timestamp: 'datetime',
  date: 'datetime',
  time: 'datetime',
  tinytext: 'text',
  mediumtext: 'text',
  longtext: 'text',
  clob: 'text'
}

/** 把反读的原始类型解析为下拉可用的基础类型（解析失败回落 varchar） */
function resolveType(rawType: string, opts: string[]): string {
  const raw = (rawType || '').toLowerCase().trim()
  const base = (raw.split(/(\s|\()/)[0] || '').replace(/\s*unsigned$/, '')
  if (base && opts.includes(base)) return base
  const alias = TYPE_ALIASES[base]
  if (alias && opts.includes(alias)) return alias
  return opts[0] || 'varchar'
}

/** 逆向读表结果 → 编辑器行（长度/小数位从 varchar(50)、decimal(10,2) 之类描述中提取） */
function mapColumnRow(info: ColumnInfo, opts: string[]): ColumnRow {
  const raw = (info.typeName || '').toLowerCase()
  const m = /^\s*([a-z0-9_]+)\s*\(\s*(\d+)\s*(?:,\s*(\d+)\s*)?\)/.exec(raw)
  const typeName = resolveType(raw, opts)
  const row = createRow()
  row.name = (info.columnName || '').toLowerCase()
  row.typeName = typeName
  if (m) {
    row.length = Number(m[2]) || null
    row.scale = m[3] != null ? Number(m[3]) : null
  }
  if (needLength(typeName)) {
    if (row.length == null) row.length = typeName === 'varchar' ? 255 : 10
    if (typeName === 'decimal' && row.scale == null) row.scale = 2
  }
  row.comment = info.comment || ''
  row.notNull = !isNullable(info.nullable)
  row.pk = isPk(info.pk)
  if (row.pk) row.notNull = true
  return row
}

const reversing = ref(false)

async function onReverse(): Promise<void> {
  const name = tableName.value.trim()
  if (!name) {
    message.error('请先输入要读取的已有表名')
    return
  }
  reversing.value = true
  try {
    const infos = await getDbColumns(name)
    const arr = Array.isArray(infos) ? infos : []
    if (arr.length === 0) {
      message.warning(`未读取到表 ${name} 的列信息`)
      return
    }
    rows.value = arr.map((c) => mapColumnRow(c, typeOptions.value))
    message.success(`已读取表 ${name} 共 ${arr.length} 列，可修改后另存为新表`)
  } finally {
    reversing.value = false
  }
}

// ============ 校验与提交 ============

/** 校验并组装 TableSpec；失败时提示并返回 null */
async function validateAndBuild(): Promise<TableSpec | null> {
  try {
    await formRef.value?.validate()
  } catch {
    return null
  }
  const list = rows.value
  if (list.length === 0) {
    message.error('请至少添加一列')
    return null
  }
  const seen = new Set<string>()
  for (let i = 0; i < list.length; i += 1) {
    const r = list[i]
    const label = `第 ${i + 1} 列`
    const name = r.name.trim()
    if (!name) {
      message.error(`${label}：列名不能为空`)
      return null
    }
    if (!NAME_RE.test(name)) {
      message.error(`${label}（${name}）：列名需以小写字母开头，仅含小写字母、数字、下划线`)
      return null
    }
    if (seen.has(name)) {
      message.error(`列名「${name}」重复，请修改`)
      return null
    }
    seen.add(name)
    if (!r.typeName) {
      message.error(`${label}（${name}）：请选择类型`)
      return null
    }
    if (needLength(r.typeName) && (r.length == null || r.length < 1)) {
      message.error(`${label}（${name}）：请输入${r.typeName === 'decimal' ? '精度' : '长度'}`)
      return null
    }
    if (r.typeName === 'decimal' && (r.scale == null || r.scale < 0)) {
      message.error(`${label}（${name}）：请输入小数位`)
      return null
    }
  }
  return {
    tableName: tableName.value.trim(),
    tableComment: tableComment.value.trim() || null,
    columns: list.map<ColumnSpec>((r) => ({
      name: r.name.trim(),
      typeName: r.typeName,
      length: needLength(r.typeName) ? r.length : null,
      scale: r.typeName === 'decimal' ? r.scale : null,
      comment: r.comment.trim() || null,
      notNull: r.pk ? true : r.notNull,
      pk: r.pk,
      defaultValue: r.defaultValue.trim() || null
    }))
  }
}

// ============ DDL 预览 / 执行建表 ============

const previewLoading = ref(false)
const executing = ref(false)
const ddlOpen = ref(false)
const ddlText = ref('')

async function onPreviewDdl(): Promise<void> {
  const spec = await validateAndBuild()
  if (!spec) return
  previewLoading.value = true
  try {
    const res = await previewDdl(spec)
    ddlText.value = res?.ddl || ''
    if (!ddlText.value) {
      message.warning('预览结果为空')
      return
    }
    ddlOpen.value = true
  } finally {
    previewLoading.value = false
  }
}

async function onExecute(): Promise<void> {
  const spec = await validateAndBuild()
  if (!spec) return
  executing.value = true
  try {
    await executeDdl(spec)
    message.success(`建表成功：${spec.tableName}`)
  } finally {
    executing.value = false
  }
}

// ============ 复制 ============

async function copyText(text: string): Promise<void> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}

onMounted(() => {
  loadTypeMap()
})
</script>

<template>
  <div>
    <a-alert
      type="info"
      show-icon
      class="tip-bar"
      message="id、tenant_id、审计字段、逻辑删除列由平台建表时自动追加，无需在此定义。"
    />

    <div class="table-info">
      <a-form ref="formRef" :model="formState" :rules="formRules" layout="inline">
        <a-form-item label="表名" name="tableName">
          <a-input
            v-model:value="tableName"
            placeholder="如 gencode_task（保存后即建表名）"
            style="width: 260px"
          />
        </a-form-item>
        <a-form-item label="表注释" name="tableComment">
          <a-input v-model:value="tableComment" placeholder="如 任务表" style="width: 200px" />
        </a-form-item>
        <a-form-item :wrapper-col="{ offset: 0 }">
          <a-button :loading="reversing" @click="onReverse">
            <template #icon><sync-outlined /></template>
            逆向读表
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <div class="toolbar">
      <a-button type="primary" @click="addRow">
        <template #icon><plus-outlined /></template>
        添加列
      </a-button>
      <span class="col-count">共 {{ rows.length }} 列</span>
      <span></span>
    </div>

    <a-table
      :data-source="rows"
      :columns="columnDefs"
      row-key="uid"
      :pagination="false"
      :scroll="{ x: 1050 }"
      size="small"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'name'">
          <a-input v-model:value="(record as ColumnRow).name" placeholder="如 task_name" />
        </template>
        <template v-else-if="column.key === 'typeName'">
          <a-select
            v-model:value="(record as ColumnRow).typeName"
            :options="typeSelectOptions"
            style="width: 100%"
            @change="() => onTypeChange(record as ColumnRow)"
          />
        </template>
        <template v-else-if="column.key === 'length'">
          <a-input-number
            v-model:value="(record as ColumnRow).length"
            :min="1"
            :max="65535"
            :precision="0"
            :disabled="!needLength((record as ColumnRow).typeName)"
            style="width: 100%"
            :placeholder="(record as ColumnRow).typeName === 'decimal' ? '精度' : '长度'"
          />
        </template>
        <template v-else-if="column.key === 'scale'">
          <a-input-number
            v-model:value="(record as ColumnRow).scale"
            :min="0"
            :max="30"
            :precision="0"
            :disabled="(record as ColumnRow).typeName !== 'decimal'"
            style="width: 100%"
            placeholder="小数位"
          />
        </template>
        <template v-else-if="column.key === 'comment'">
          <a-input v-model:value="(record as ColumnRow).comment" placeholder="列注释" />
        </template>
        <template v-else-if="column.key === 'defaultValue'">
          <a-input v-model:value="(record as ColumnRow).defaultValue" placeholder="可空" allow-clear />
        </template>
        <template v-else-if="column.key === 'notNull'">
          <a-switch
            v-model:checked="(record as ColumnRow).notNull"
            :disabled="(record as ColumnRow).pk"
            size="small"
          />
        </template>
        <template v-else-if="column.key === 'pk'">
          <a-switch
            v-model:checked="(record as ColumnRow).pk"
            size="small"
            @change="() => onPkChange(record as ColumnRow)"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" danger size="small" @click="removeRow(index)">
            删除
          </a-button>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无列，点击上方「添加列」或「逆向读表」开始" />
      </template>
    </a-table>

    <div class="actions">
      <a-space>
        <a-button type="primary" :loading="previewLoading" @click="onPreviewDdl">
          <template #icon><code-outlined /></template>
          预览 DDL
        </a-button>
        <a-button v-perm="'lc:db:create'" :loading="executing" @click="onExecute">
          <template #icon><database-outlined /></template>
          执行建表
        </a-button>
      </a-space>
    </div>

    <!-- DDL 预览弹窗 -->
    <a-modal
      v-model:open="ddlOpen"
      :title="`DDL 预览 - ${tableName || '未命名表'}`"
      :width="760"
      :mask-closable="false"
    >
      <pre class="ddl-pre">{{ ddlText }}</pre>
      <template #footer>
        <a-space>
          <a-button type="primary" @click="copyText(ddlText)">复制 DDL</a-button>
          <a-button @click="ddlOpen = false">关闭</a-button>
        </a-space>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.tip-bar {
  margin-bottom: 12px;
}

.table-info {
  margin-bottom: 12px;
}

.col-count {
  color: #999;
  font-size: 13px;
}

.actions {
  margin-top: 16px;
}

.ddl-pre {
  max-height: 60vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-family: 'JetBrains Mono', Consolas, Menlo, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
