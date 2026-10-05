<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { useDict } from '@/hooks/useDict'
import {
  getPublishedForm,
  submitLcFormData,
  type FormItem,
  type FormItemOption,
  type FormSchema
} from '@/api/lc-form'

/**
 * 运行时填报页（/app/form/:code，菜单隐藏页）
 * 按 code 取"已发布"Schema 快照动态渲染；提交整体存 data_json。
 */

const route = useRoute()
const formCode = String(route.params.code ?? '')

const loading = ref(false)
const loadError = ref('')
const formName = ref('')
const version = ref(0)
const list = ref<FormItem[]>([])
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)

/** 动态字段值容器：key=field，值类型由组件类型决定，用 any 承接动态绑定 */
const formState = reactive<Record<string, any>>({})

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

/** 字典优先，其次静态选项 */
function itemOptions(item: FormItem): FormItemOption[] {
  if (item.dictType) return dictOptions(item.dictType)
  return item.options || []
}

// ============ 校验规则 ============

const rules = computed<Record<string, Rule[]>>(() => {
  const map: Record<string, Rule[]> = {}
  for (const item of list.value) {
    if (item.type === 'group' || !item.required) continue
    const trigger = ['input', 'textarea', 'number'].includes(item.type) ? 'blur' : 'change'
    map[item.field] = [{ required: true, message: `${item.label || item.field}不能为空`, trigger }]
  }
  return map
})

// ============ 加载与初始化 ============

function initDefaults(): void {
  for (const item of list.value) {
    if (item.type === 'group') continue
    const dv = item.defaultValue
    if (dv === null || dv === undefined) {
      // 给复合控件一个可控初始值，避免受控组件警告
      if (item.type === 'checkbox') formState[item.field] = []
      else if (item.type === 'switch') formState[item.field] = false
      else formState[item.field] = undefined
      continue
    }
    formState[item.field] = Array.isArray(dv) ? [...dv] : dv
  }
}

async function loadForm(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const pub = await getPublishedForm(formCode)
    formName.value = pub.name || pub.code
    version.value = pub.version || 0
    let parsed: FormSchema
    try {
      parsed = JSON.parse(pub.schemaJson || '{}') as FormSchema
    } catch {
      loadError.value = '表单 Schema 解析失败，请联系管理员'
      return
    }
    list.value = parsed.list || []
    initDefaults()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '表单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// ============ 提交 ============

function resetForm(): void {
  Object.keys(formState).forEach((k) => delete formState[k])
  initDefaults()
  formRef.value?.clearValidate()
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    // group 仅视觉占位，不参与数据提交；复杂值给原始值，JSON 由后端序列化
    const data: Record<string, unknown> = {}
    for (const item of list.value) {
      if (item.type === 'group') continue
      const v = formState[item.field]
      data[item.field] = v === undefined ? null : v
    }
    await submitLcFormData(formCode, { data })
    message.success('提交成功')
    resetForm()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!formCode) {
    loadError.value = '缺少表单编码'
    return
  }
  loadForm()
})
</script>

<template>
  <div class="form-render-page">
    <a-spin :spinning="loading">
      <!-- 加载失败 / 解析失败：空状态提示 -->
      <a-result
        v-if="loadError"
        status="warning"
        title="表单加载失败"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" @click="loadForm">
            <template #icon><reload-outlined /></template>
            重新加载
          </a-button>
        </template>
      </a-result>

      <template v-else>
        <!-- 表单名 + 版本号 -->
        <div class="render-header">
          <span class="render-title">{{ formName }}</span>
          <a-tag v-if="version > 0" color="blue">v{{ version }}</a-tag>
        </div>

        <a-empty v-if="list.length === 0" description="该表单暂无可填字段" />

        <a-form
          v-else
          ref="formRef"
          :model="formState"
          :rules="rules"
          layout="vertical"
          class="render-form"
        >
          <a-row :gutter="[16, 0]">
            <template v-for="item in list" :key="item.field">
              <a-col v-if="item.type !== 'group'" :span="item.span || 24">
                <a-form-item :label="item.label" :name="item.field">
                  <a-input
                    v-if="item.type === 'input'"
                    v-model:value="formState[item.field]"
                    :placeholder="item.placeholder || '请输入'"
                    allow-clear
                  />
                  <a-input-number
                    v-else-if="item.type === 'number'"
                    v-model:value="formState[item.field]"
                    style="width: 100%"
                    :placeholder="item.placeholder || '请输入数字'"
                  />
                  <a-textarea
                    v-else-if="item.type === 'textarea'"
                    v-model:value="formState[item.field]"
                    :rows="3"
                    :placeholder="item.placeholder || '请输入'"
                  />
                  <a-date-picker
                    v-else-if="item.type === 'date'"
                    v-model:value="formState[item.field]"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                    :placeholder="item.placeholder || '请选择日期'"
                  />
                  <a-switch
                    v-else-if="item.type === 'switch'"
                    v-model:checked="formState[item.field]"
                  />
                  <a-radio-group
                    v-else-if="item.type === 'radio'"
                    v-model:value="formState[item.field]"
                  >
                    <a-radio v-for="opt in itemOptions(item)" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </a-radio>
                  </a-radio-group>
                  <a-checkbox-group
                    v-else-if="item.type === 'checkbox'"
                    v-model:value="formState[item.field]"
                  >
                    <a-checkbox v-for="opt in itemOptions(item)" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </a-checkbox>
                  </a-checkbox-group>
                  <a-select
                    v-else-if="item.type === 'select'"
                    v-model:value="formState[item.field]"
                    style="width: 100%"
                    :placeholder="item.placeholder || '请选择'"
                    allow-clear
                  >
                    <a-select-option
                      v-for="opt in itemOptions(item)"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <!-- 分组容器：仅视觉标题 -->
              <a-col v-else :span="24">
                <div class="group-divider">{{ item.label }}</div>
              </a-col>
            </template>
          </a-row>

          <a-form-item class="render-actions">
            <a-button type="primary" :loading="submitting" @click="handleSubmit">
              <template #icon><save-outlined /></template>
              提交
            </a-button>
            <a-button class="reset-btn" :disabled="submitting" @click="resetForm">重置</a-button>
          </a-form-item>
        </a-form>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.form-render-page {
  max-width: 960px;
  margin: 0 auto;
}

.render-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.render-title {
  font-size: 18px;
  font-weight: 600;
}

.group-divider {
  margin: 8px 0 16px;
  padding: 8px 12px;
  background: #fafafa;
  border-left: 3px solid #1677ff;
  border-radius: 2px;
  font-weight: 600;
}

.render-actions {
  margin-top: 8px;
  margin-bottom: 0;
}

.reset-btn {
  margin-left: 8px;
}
</style>
