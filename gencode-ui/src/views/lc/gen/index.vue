<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import type { Key, TableRowSelection } from 'ant-design-vue/es/table/interface'
import { CodeOutlined, CopyOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  downloadGenZip,
  getGenTables,
  normalizeGenTables,
  previewGen,
  type GenFileVO,
  type GenPreviewBody,
  type GenTable
} from '@/api/lc-gen'

/**
 * 代码生成（/lc/gen）
 * 选择平台主库中的已有表 → 配置包名/模块/作者/业务名 → 预览或下载 zip。
 */

const BUSINESS_NAME_RE = /^[a-z][a-z0-9_]*$/

// ============ 表选择 ============

const keyword = ref('')
const loading = ref(false)
const tables = ref<GenTable[]>([])
const selectedKeys = ref<Key[]>([])
const selectedTableName = ref<string | null>(null)

const tableColumns = [
  { title: '表名', dataIndex: 'tableName', width: 280 },
  { title: '注释', dataIndex: 'tableComment' }
]

const rowSelection = computed<TableRowSelection<GenTable>>(() => ({
  type: 'radio',
  selectedRowKeys: selectedKeys.value,
  onChange: (keys: Key[]) => {
    selectedKeys.value = keys
    selectedTableName.value = keys.length ? String(keys[0]) : null
  }
}))

const pagination = computed(() => ({
  pageSize: 10,
  showSizeChanger: false,
  showTotal: (t: number) => `共 ${t} 张表`
}))

async function loadTables(): Promise<void> {
  loading.value = true
  try {
    const res = await getGenTables(keyword.value.trim() || undefined)
    tables.value = normalizeGenTables(res)
    // 重查后原选中表不在列表中则清空选中
    if (
      selectedTableName.value &&
      !tables.value.some((t) => t.tableName === selectedTableName.value)
    ) {
      selectedTableName.value = null
      selectedKeys.value = []
    }
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  loadTables()
}

const selectedTable = computed<GenTable | null>(
  () => tables.value.find((t) => t.tableName === selectedTableName.value) ?? null
)

// ============ 生成选项 ============

const formRef = ref<FormInstance | null>(null)
const options = ref({
  packageName: 'com.gencode.gen',
  moduleName: 'demo',
  author: 'gencode',
  businessName: ''
})

const optionRules: Record<string, Rule[]> = {
  packageName: [
    { required: true, message: '请输入包名', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_.]*$/,
      message: '包名需以小写字母开头，仅含小写字母、数字、下划线、点',
      trigger: 'blur'
    }
  ],
  moduleName: [
    { required: true, message: '请输入模块名', trigger: 'blur' },
    { pattern: BUSINESS_NAME_RE, message: '模块名仅含小写字母、数字、下划线', trigger: 'blur' }
  ],
  author: [{ required: true, message: '请输入作者', trigger: 'blur' }],
  businessName: [
    { required: true, message: '请输入业务名，如 task', trigger: 'blur' },
    { pattern: BUSINESS_NAME_RE, message: '业务名需以小写字母开头，仅含小写字母、数字、下划线', trigger: 'blur' }
  ]
}

/** 校验表选择与生成选项，组装请求体 */
async function validateAndBuild(): Promise<GenPreviewBody | null> {
  if (!selectedTableName.value) {
    message.error('请先选择要生成的数据表')
    return null
  }
  try {
    await formRef.value?.validate()
  } catch {
    return null
  }
  return {
    tableName: selectedTableName.value,
    options: {
      packageName: options.value.packageName.trim(),
      moduleName: options.value.moduleName.trim(),
      author: options.value.author.trim(),
      businessName: options.value.businessName.trim()
    }
  }
}

// ============ 预览 / 下载 ============

const previewLoading = ref(false)
const downloading = ref(false)
const previewOpen = ref(false)
const previewFiles = ref<GenFileVO[]>([])
const activeFile = ref('')

function fileName(path: string): string {
  const segs = path.split('/')
  return segs[segs.length - 1] || path
}

async function onPreview(): Promise<void> {
  const body = await validateAndBuild()
  if (!body) return
  previewLoading.value = true
  try {
    const res = await previewGen(body)
    const files = Array.isArray(res?.files) ? res.files : []
    if (files.length === 0) {
      message.warning('未生成任何文件')
      return
    }
    previewFiles.value = files
    activeFile.value = files[0].path
    previewOpen.value = true
  } finally {
    previewLoading.value = false
  }
}

async function onDownload(): Promise<void> {
  const body = await validateAndBuild()
  if (!body) return
  downloading.value = true
  try {
    await downloadGenZip(body, `gen_${body.tableName}_${body.options.businessName}.zip`)
    message.success('代码包已开始下载')
  } finally {
    downloading.value = false
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
  loadTables()
})
</script>

<template>
  <div>
    <a-card title="① 选择数据表" class="step-card" size="small">
      <div class="search-bar">
        <a-space wrap>
          <a-input
            v-model:value="keyword"
            placeholder="表名 / 注释"
            allow-clear
            style="width: 220px"
            @press-enter="onSearch"
          />
          <a-button type="primary" @click="onSearch">
            <template #icon><search-outlined /></template>
            查询
          </a-button>
        </a-space>
      </div>

      <a-table
        :data-source="tables"
        :columns="tableColumns"
        :loading="loading"
        row-key="tableName"
        :row-selection="rowSelection"
        :pagination="pagination"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'tableComment'">
            {{ (record as GenTable).tableComment || '-' }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="② 生成选项" class="step-card" size="small">
      <a-form ref="formRef" :model="options" :rules="optionRules" layout="inline">
        <a-form-item label="包名" name="packageName">
          <a-input v-model:value="options.packageName" placeholder="com.gencode.gen" style="width: 200px" />
        </a-form-item>
        <a-form-item label="模块名" name="moduleName">
          <a-input v-model:value="options.moduleName" placeholder="demo" style="width: 140px" />
        </a-form-item>
        <a-form-item label="作者" name="author">
          <a-input v-model:value="options.author" placeholder="gencode" style="width: 140px" />
        </a-form-item>
        <a-form-item label="业务名" name="businessName">
          <a-input v-model:value="options.businessName" placeholder="必填，如 task" style="width: 160px" />
        </a-form-item>
      </a-form>
      <div class="selected-tip">
        当前选择：
        <template v-if="selectedTable">
          <a-tag color="blue">{{ selectedTable.tableName }}</a-tag>
          <span class="tip-comment">{{ selectedTable.tableComment || '-' }}</span>
        </template>
        <span v-else class="tip-comment">未选择（在上方列表单选一张表）</span>
      </div>
    </a-card>

    <div class="actions">
      <a-space>
        <a-button type="primary" :loading="previewLoading" @click="onPreview">
          <template #icon><code-outlined /></template>
          预览代码
        </a-button>
        <a-button v-perm="'lc:gen:download'" :loading="downloading" @click="onDownload">
          <template #icon><download-outlined /></template>
          下载 zip
        </a-button>
      </a-space>
    </div>

    <!-- 代码预览大弹窗：按文件路径分页签 -->
    <a-modal
      v-model:open="previewOpen"
      :title="`代码预览 - ${selectedTableName || ''}（共 ${previewFiles.length} 个文件）`"
      :width="960"
      :footer="null"
      :mask-closable="false"
      class="gen-preview-modal"
    >
      <a-tabs v-model:activeKey="activeFile" size="small">
        <a-tab-pane v-for="f in previewFiles" :key="f.path" :tab="fileName(f.path)">
          <div class="code-toolbar">
            <span class="code-path">{{ f.path }}</span>
            <a-button size="small" type="link" @click="copyText(f.content)">
              <template #icon><copy-outlined /></template>
              复制
            </a-button>
          </div>
          <pre class="code-pre">{{ f.content }}</pre>
        </a-tab-pane>
      </a-tabs>
    </a-modal>
  </div>
</template>

<style scoped>
.step-card {
  margin-bottom: 12px;
}

.selected-tip {
  margin-top: 4px;
  color: #666;
  font-size: 13px;
}

.tip-comment {
  color: #999;
}

.actions {
  margin-top: 16px;
}

.code-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.code-path {
  color: #999;
  font-size: 12px;
}

.code-pre {
  max-height: 56vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-family: 'JetBrains Mono', Consolas, Menlo, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
}
</style>
