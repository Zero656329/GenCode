<script setup lang="ts">
import { computed, onMounted, ref, type Component, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CheckSquareOutlined,
  DeleteOutlined,
  DragOutlined,
  EditOutlined,
  FileTextOutlined,
  GroupOutlined,
  NumberOutlined,
  PlusOutlined,
  RocketOutlined,
  SaveOutlined,
  SelectOutlined,
  SwitcherOutlined
} from '@ant-design/icons-vue'
import { useDict } from '@/hooks/useDict'
import {
  getLcForm,
  publishLcForm,
  updateLcForm,
  FORM_ITEM_LABELS,
  FORM_ITEM_TYPES,
  type FormItem,
  type FormItemType,
  type FormItemOption,
  type FormSchema,
  type LcFormStatus
} from '@/api/lc-form'

/** 带选项的组件类型（radio/checkbox/select 支持"静态选项/绑定字典"二选一） */
const OPTION_TYPES: FormItemType[] = ['radio', 'checkbox', 'select']

/** 左栏组件面板 */
interface PaletteEntry {
  type: FormItemType
  icon: Component
}
const PALETTE: PaletteEntry[] = [
  { type: 'input', icon: EditOutlined },
  { type: 'number', icon: NumberOutlined },
  { type: 'radio', icon: CheckCircleOutlined },
  { type: 'checkbox', icon: CheckSquareOutlined },
  { type: 'select', icon: SelectOutlined },
  { type: 'date', icon: CalendarOutlined },
  { type: 'switch', icon: SwitcherOutlined },
  { type: 'textarea', icon: FileTextOutlined },
  { type: 'group', icon: GroupOutlined }
]

// ============ 表单基础信息 ============

const route = useRoute()
const router = useRouter()
const formId = String(route.params.id ?? '')

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const formName = ref('')
const remark = ref('')
const version = ref(0)
const status = ref<LcFormStatus>(0)

// ============ 设计画布 ============

const list = ref<FormItem[]>([])
const selectedIdx = ref(-1)

const selectedItem = computed<FormItem | null>(() => {
  const idx = selectedIdx.value
  if (idx < 0 || idx >= list.value.length) return null
  return list.value[idx] ?? null
})

let fieldSeq = 0
function genField(): string {
  fieldSeq += 1
  return `f_${Date.now().toString(36)}_${fieldSeq}`
}

function createItem(type: FormItemType): FormItem {
  return {
    type,
    field: genField(),
    label: FORM_ITEM_LABELS[type],
    required: false,
    placeholder: null,
    defaultValue: null,
    options: OPTION_TYPES.includes(type)
      ? [
          { label: '选项1', value: '1' },
          { label: '选项2', value: '2' }
        ]
      : null,
    dictType: null,
    span: type === 'group' ? 24 : 8
  }
}

/** 加载历史 Schema 时做规范化兜底，保证渲染与保存的结构完整 */
function normalizeItem(raw: Partial<FormItem>): FormItem {
  const type: FormItemType =
    raw.type && FORM_ITEM_TYPES.includes(raw.type) ? raw.type : 'input'
  const span = Number(raw.span)
  return {
    type,
    field: typeof raw.field === 'string' ? raw.field : '',
    label: typeof raw.label === 'string' ? raw.label : FORM_ITEM_LABELS[type],
    required: raw.required === true,
    placeholder: typeof raw.placeholder === 'string' ? raw.placeholder : null,
    defaultValue: raw.defaultValue ?? null,
    options: Array.isArray(raw.options)
      ? raw.options.map((o) => ({
          label: String(o && o.label != null ? o.label : ''),
          value: String(o && o.value != null ? o.value : '')
        }))
      : null,
    dictType: typeof raw.dictType === 'string' ? raw.dictType : null,
    span:
      Number.isFinite(span) && span >= 1
        ? Math.min(24, Math.round(span))
        : type === 'group'
          ? 24
          : 8
  }
}

async function loadDetail(): Promise<void> {
  loading.value = true
  try {
    const detail = await getLcForm(formId)
    formName.value = detail.name
    remark.value = detail.remark || ''
    version.value = detail.version || 0
    status.value = detail.status
    if (detail.schemaJson) {
      try {
        const parsed = JSON.parse(detail.schemaJson) as { list?: Array<Partial<FormItem>> }
        list.value = (parsed.list || []).map(normalizeItem)
      } catch {
        message.error('设计 Schema 解析失败，已按空表单加载')
        list.value = []
      }
    }
  } finally {
    loading.value = false
  }
}

// ============ 拖拽（HTML5 原生） ============

let draggingType: FormItemType | null = null

function onPaletteDragStart(e: DragEvent, type: FormItemType): void {
  draggingType = type
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'copy'
    e.dataTransfer.setData('text/plain', type)
  }
}

function onPaletteDragEnd(): void {
  draggingType = null
}

function onCanvasDrop(e: DragEvent): void {
  const raw = e.dataTransfer ? e.dataTransfer.getData('text/plain') : ''
  const type = (FORM_ITEM_TYPES.includes(raw as FormItemType) ? raw : draggingType) as
    | FormItemType
    | null
  draggingType = null
  if (!type) return
  list.value.push(createItem(type))
  selectedIdx.value = list.value.length - 1
}

// ============ 画布项操作 ============

function moveItem(idx: number, dir: -1 | 1): void {
  const target = idx + dir
  if (target < 0 || target >= list.value.length) return
  const [it] = list.value.splice(idx, 1)
  list.value.splice(target, 0, it)
  selectedIdx.value = target
}

function removeItem(idx: number): void {
  list.value.splice(idx, 1)
  if (selectedIdx.value === idx) selectedIdx.value = -1
  else if (selectedIdx.value > idx) selectedIdx.value -= 1
}

function setSpan(item: FormItem | null, v: unknown): void {
  if (!item) return
  if (v === null || v === undefined || v === '') return
  const n = Number(v)
  if (!Number.isFinite(n)) return
  item.span = Math.min(24, Math.max(1, Math.round(n)))
}

// ============ 字典选项（useDict，经 app store 缓存） ============

const dictRefs: Record<string, Ref<DictOption[]>> = {}
function dictOptions(dictType: string): DictOption[] {
  let r = dictRefs[dictType]
  if (!r) {
    r = useDict(dictType).options
    dictRefs[dictType] = r
  }
  return r.value
}

/** 预览/默认值编辑可用的选项：字典优先，其次静态选项 */
function itemOptions(item: FormItem): FormItemOption[] {
  if (item.dictType) return dictOptions(item.dictType)
  return item.options || []
}

// ============ 属性面板（代理 computed，避免 union 类型直接绑定组件） ============

const placeholderProxy = computed<string>({
  get: () => selectedItem.value?.placeholder ?? '',
  set: (v) => {
    if (selectedItem.value) selectedItem.value.placeholder = v || null
  }
})

const dictTypeProxy = computed<string>({
  get: () => selectedItem.value?.dictType ?? '',
  set: (v) => {
    if (selectedItem.value) selectedItem.value.dictType = v
  }
})

/** 选项来源模式：静态 / 字典 */
const optionsMode = computed<'static' | 'dict'>({
  get: () => (selectedItem.value && selectedItem.value.dictType !== null ? 'dict' : 'static'),
  set: (mode) => {
    const item = selectedItem.value
    if (!item) return
    if (mode === 'dict') {
      item.dictType = item.dictType ?? ''
    } else {
      item.dictType = null
      if (!item.options || item.options.length === 0) {
        item.options = [
          { label: '选项1', value: '1' },
          { label: '选项2', value: '2' }
        ]
      }
    }
  }
})

const defaultString = computed<string>({
  get: () => {
    const v = selectedItem.value?.defaultValue
    return typeof v === 'string' ? v : ''
  },
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v || null
  }
})

const defaultNumber = computed<number | undefined>({
  get: () => {
    const v = selectedItem.value?.defaultValue
    return typeof v === 'number' ? v : undefined
  },
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v ?? null
  }
})

const defaultBool = computed<boolean>({
  get: () => selectedItem.value?.defaultValue === true,
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v
  }
})

const defaultDate = computed<string | undefined>({
  get: () => {
    const v = selectedItem.value?.defaultValue
    return typeof v === 'string' && v ? v : undefined
  },
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v || null
  }
})

/** 单选/下拉默认值（空串与 null 统一映射为 undefined 以支持清空） */
const defaultOptionValue = computed<string | undefined>({
  get: () => {
    const v = selectedItem.value?.defaultValue
    return typeof v === 'string' && v !== '' ? v : undefined
  },
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v || null
  }
})

/** 多选默认值 */
const defaultOptionValues = computed<string[]>({
  get: () => {
    const v = selectedItem.value?.defaultValue
    return Array.isArray(v) ? v.map(String) : []
  },
  set: (v) => {
    if (selectedItem.value) selectedItem.value.defaultValue = v && v.length ? [...v] : null
  }
})

function setDefaultNumber(v: unknown): void {
  const item = selectedItem.value
  if (!item) return
  if (v === '' || v === null || v === undefined) {
    item.defaultValue = null
    return
  }
  const n = typeof v === 'number' ? v : Number(v)
  item.defaultValue = Number.isFinite(n) ? n : null
}

function addOption(item: FormItem): void {
  if (!item.options) item.options = []
  const seq = item.options.length + 1
  item.options.push({ label: `选项${seq}`, value: String(seq) })
}

function removeOption(item: FormItem, idx: number): void {
  item.options?.splice(idx, 1)
}

// ============ 校验与保存/发布 ============

const FIELD_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/

const fieldError = computed<string>(() => {
  const item = selectedItem.value
  if (!item || item.type === 'group') return ''
  if (!item.field) return '字段名不能为空'
  if (!FIELD_RE.test(item.field)) return '字段名需以字母或下划线开头，仅含字母、数字、下划线'
  if (list.value.filter((x) => x.field === item.field).length > 1) return '字段名已存在，请修改'
  return ''
})

function findSchemaError(): string {
  for (const item of list.value) {
    if (item.type === 'group') continue
    const name = item.label || item.field || '未命名字段'
    if (!item.field) return `「${name}」字段名不能为空`
    if (!FIELD_RE.test(item.field)) return `「${name}」字段名格式不正确`
    if (list.value.filter((x) => x.field === item.field).length > 1) {
      return `字段名「${item.field}」重复`
    }
  }
  return ''
}

/** 保存设计；silent=true 时由发布流程调用，不重复提示 */
async function doSave(silent: boolean): Promise<boolean> {
  const err = findSchemaError()
  if (err) {
    message.error(err)
    return false
  }
  saving.value = true
  try {
    const schema: FormSchema = { list: list.value }
    await updateLcForm({
      id: formId,
      name: formName.value,
      remark: remark.value || null,
      schemaJson: JSON.stringify(schema)
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
    const res = await publishLcForm(formId)
    version.value = res && res.version ? res.version : version.value + 1
    status.value = 1
    message.success(`发布成功，当前版本 v${version.value}`)
  } finally {
    publishing.value = false
  }
}

function onBack(): void {
  router.push('/lc/form')
}

onMounted(() => {
  if (!formId) {
    message.error('缺少表单 ID')
    router.replace('/lc/form')
    return
  }
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
        <a-input v-model:value="formName" class="name-input" placeholder="表单名称" />
        <a-tag v-if="version > 0" color="blue">v{{ version }}</a-tag>
        <a-tag v-else>未发布</a-tag>
        <a-tag v-if="status === 2" color="red">已停用</a-tag>
      </a-space>
      <a-space>
        <a-button v-perm="'lc:form:edit'" type="primary" :loading="saving" @click="onSave">
          <template #icon><save-outlined /></template>
          保存
        </a-button>
        <a-button v-perm="'lc:form:publish'" :loading="publishing" @click="onPublish">
          <template #icon><rocket-outlined /></template>
          发布
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div class="designer-body">
        <!-- 左栏：组件面板 -->
        <aside class="palette">
          <div class="panel-title">组件库</div>
          <div class="palette-grid">
            <div
              v-for="p in PALETTE"
              :key="p.type"
              class="palette-item"
              draggable="true"
              @dragstart="onPaletteDragStart($event, p.type)"
              @dragend="onPaletteDragEnd"
            >
              <component :is="p.icon" class="palette-icon" />
              <span>{{ FORM_ITEM_LABELS[p.type] }}</span>
            </div>
          </div>
          <div class="palette-tip"><drag-outlined /> 拖拽组件到画布</div>
        </aside>

        <!-- 中间：画布 -->
        <section class="canvas-wrap" @dragover.prevent @drop.prevent="onCanvasDrop" @click="selectedIdx = -1">
          <a-row :gutter="[12, 12]">
            <a-col v-for="(item, idx) in list" :key="item.field + '-' + idx" :span="item.span">
              <div
                class="canvas-item"
                :class="{ selected: selectedIdx === idx }"
                @click.stop="selectedIdx = idx"
              >
                <!-- 分组容器：仅视觉卡片 -->
                <div v-if="item.type === 'group'" class="group-block">
                  <div class="group-title">{{ item.label || '分组容器' }}</div>
                  <div class="group-empty">分组区域（仅视觉占位）</div>
                </div>
                <template v-else>
                  <div class="field-label">
                    {{ item.label }}<span v-if="item.required" class="required-mark">*</span>
                  </div>
                  <a-input
                    v-if="item.type === 'input'"
                    disabled
                    :placeholder="item.placeholder || undefined"
                  />
                  <a-input-number
                    v-else-if="item.type === 'number'"
                    disabled
                    style="width: 100%"
                    :placeholder="item.placeholder || undefined"
                  />
                  <a-textarea
                    v-else-if="item.type === 'textarea'"
                    disabled
                    :rows="2"
                    :placeholder="item.placeholder || undefined"
                  />
                  <a-date-picker v-else-if="item.type === 'date'" disabled style="width: 100%" />
                  <a-switch v-else-if="item.type === 'switch'" disabled :checked="item.defaultValue === true" />
                  <a-radio-group v-else-if="item.type === 'radio'" disabled>
                    <a-radio v-for="opt in itemOptions(item)" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </a-radio>
                  </a-radio-group>
                  <a-checkbox-group v-else-if="item.type === 'checkbox'" disabled>
                    <a-checkbox v-for="opt in itemOptions(item)" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </a-checkbox>
                  </a-checkbox-group>
                  <a-select
                    v-else-if="item.type === 'select'"
                    disabled
                    style="width: 100%"
                    :placeholder="item.placeholder || '请选择'"
                  >
                    <a-select-option v-for="opt in itemOptions(item)" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </a-select-option>
                  </a-select>
                </template>

                <!-- 选中项悬浮小工具条 -->
                <div v-if="selectedIdx === idx" class="item-toolbar" @click.stop>
                  <span class="toolbar-label">宽</span>
                  <a-input-number
                    class="toolbar-span"
                    :value="item.span"
                    :min="1"
                    :max="24"
                    size="small"
                    @change="(v: unknown) => setSpan(item, v)"
                  />
                  <a-button size="small" type="text" :disabled="idx === 0" @click="moveItem(idx, -1)">
                    <arrow-up-outlined />
                  </a-button>
                  <a-button
                    size="small"
                    type="text"
                    :disabled="idx === list.length - 1"
                    @click="moveItem(idx, 1)"
                  >
                    <arrow-down-outlined />
                  </a-button>
                  <a-button size="small" type="text" danger @click="removeItem(idx)">
                    <delete-outlined />
                  </a-button>
                </div>
              </div>
            </a-col>
          </a-row>
          <a-empty v-if="list.length === 0" class="canvas-empty" description="从左侧拖拽组件到此处开始设计" />
        </section>

        <!-- 右栏：属性面板 -->
        <aside class="props">
          <div class="panel-title">属性设置</div>
          <template v-if="selectedItem">
            <a-form layout="vertical">
              <a-form-item label="组件类型">
                <a-tag color="blue">{{ FORM_ITEM_LABELS[selectedItem.type] }}</a-tag>
              </a-form-item>
              <a-form-item label="标签名称">
                <a-input v-model:value="selectedItem.label" placeholder="请输入标签" />
              </a-form-item>
              <template v-if="selectedItem.type !== 'group'">
                <a-form-item
                  label="字段名"
                  :validate-status="fieldError ? 'error' : ''"
                  :help="fieldError || undefined"
                >
                  <a-input v-model:value="selectedItem.field" placeholder="如 f_name" />
                </a-form-item>
                <a-form-item label="必填">
                  <a-switch v-model:checked="selectedItem.required" />
                </a-form-item>
                <a-form-item label="占位提示">
                  <a-input v-model:value="placeholderProxy" placeholder="请输入占位提示" allow-clear />
                </a-form-item>
                <a-form-item label="默认值">
                  <a-input
                    v-if="selectedItem.type === 'input' || selectedItem.type === 'textarea'"
                    v-model:value="defaultString"
                    placeholder="默认值"
                    allow-clear
                  />
                  <a-input-number
                    v-else-if="selectedItem.type === 'number'"
                    :value="defaultNumber"
                    style="width: 100%"
                    placeholder="默认值"
                    @change="(v: unknown) => setDefaultNumber(v)"
                  />
                  <a-switch v-else-if="selectedItem.type === 'switch'" v-model:checked="defaultBool" />
                  <a-date-picker
                    v-else-if="selectedItem.type === 'date'"
                    v-model:value="defaultDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                  <template v-else-if="OPTION_TYPES.includes(selectedItem.type)">
                    <a-radio-group
                      v-if="selectedItem.type === 'radio'"
                      v-model:value="defaultOptionValue"
                    >
                      <a-radio
                        v-for="opt in itemOptions(selectedItem)"
                        :key="opt.value"
                        :value="opt.value"
                      >
                        {{ opt.label }}
                      </a-radio>
                    </a-radio-group>
                    <a-select
                      v-else-if="selectedItem.type === 'select'"
                      v-model:value="defaultOptionValue"
                      allow-clear
                      style="width: 100%"
                      placeholder="请选择默认值"
                    >
                      <a-select-option
                        v-for="opt in itemOptions(selectedItem)"
                        :key="opt.value"
                        :value="opt.value"
                      >
                        {{ opt.label }}
                      </a-select-option>
                    </a-select>
                    <a-select
                      v-else
                      v-model:value="defaultOptionValues"
                      mode="multiple"
                      style="width: 100%"
                      placeholder="请选择默认值"
                    >
                      <a-select-option
                        v-for="opt in itemOptions(selectedItem)"
                        :key="opt.value"
                        :value="opt.value"
                      >
                        {{ opt.label }}
                      </a-select-option>
                    </a-select>
                  </template>
                </a-form-item>

                <!-- 选项配置（radio/checkbox/select） -->
                <a-form-item v-if="OPTION_TYPES.includes(selectedItem.type)" label="选项配置">
                  <a-radio-group v-model:value="optionsMode" size="small">
                    <a-radio-button value="static">静态</a-radio-button>
                    <a-radio-button value="dict">字典</a-radio-button>
                  </a-radio-group>
                  <template v-if="optionsMode === 'static'">
                    <div
                      v-for="(opt, i) in selectedItem.options"
                      :key="i"
                      class="option-row"
                    >
                      <a-input v-model:value="opt.label" placeholder="标签" />
                      <a-input v-model:value="opt.value" placeholder="值" />
                      <a-button type="text" danger size="small" @click="removeOption(selectedItem, i)">
                        <delete-outlined />
                      </a-button>
                    </div>
                    <a-button size="small" type="dashed" block class="option-add" @click="addOption(selectedItem)">
                      <plus-outlined /> 添加选项
                    </a-button>
                  </template>
                  <template v-else>
                    <a-input
                      v-model:value="dictTypeProxy"
                      placeholder="绑定字典类型，如 sys_sex"
                      allow-clear
                    />
                  </template>
                </a-form-item>
              </template>

              <a-form-item label="栅格宽度 span（1-24）">
                <a-input-number
                  :value="selectedItem.span"
                  :min="1"
                  :max="24"
                  style="width: 100%"
                  @change="(v: unknown) => setSpan(selectedItem, v)"
                />
              </a-form-item>
            </a-form>
          </template>
          <a-empty v-else description="请在画布中选中组件" />
        </aside>
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
  gap: 12px;
  align-items: stretch;
  height: calc(100vh - 230px);
  min-height: 460px;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 12px;
}

/* 左栏组件面板 */
.palette {
  flex: 0 0 240px;
  width: 240px;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow-y: auto;
}

.palette-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.palette-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 12px;
  cursor: grab;
  user-select: none;
}

.palette-item:active {
  cursor: grabbing;
}

.palette-item:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.palette-icon {
  font-size: 18px;
}

.palette-tip {
  margin-top: 12px;
  color: #999;
  font-size: 12px;
  text-align: center;
}

/* 中间画布 */
.canvas-wrap {
  flex: 1;
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  overflow-y: auto;
}

.canvas-item {
  position: relative;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
}

.canvas-item:hover {
  border-color: #91caff;
}

.canvas-item.selected {
  border-color: #1677ff;
  background: #f0f7ff;
}

.field-label {
  margin-bottom: 4px;
  font-size: 13px;
}

.required-mark {
  margin-left: 2px;
  color: #ff4d4f;
}

.item-toolbar {
  position: absolute;
  top: -12px;
  right: 4px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 4px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.toolbar-label {
  font-size: 12px;
  color: #999;
}

.toolbar-span {
  width: 56px;
}

.canvas-empty {
  margin-top: 60px;
}

.group-block {
  padding: 12px;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
}

.group-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.group-empty {
  padding: 16px 0;
  color: #999;
  font-size: 12px;
  text-align: center;
}

/* 右栏属性面板 */
.props {
  flex: 0 0 300px;
  width: 300px;
  padding: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow-y: auto;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.option-add {
  margin-top: 8px;
}
</style>
