<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getOperLogPage } from '@/api/log'

const bizTypeMeta: Record<string, { text: string; color: string }> = {
  INSERT: { text: '新增', color: 'blue' },
  UPDATE: { text: '修改', color: 'green' },
  DELETE: { text: '删除', color: 'red' },
  EXPORT: { text: '导出', color: 'purple' },
  OTHER: { text: '其他', color: 'default' }
}

const loading = ref(false)
const list = ref<OperLog[]>([])
const total = ref(0)
const query = reactive({
  title: '',
  operName: '',
  status: null as number | null,
  dateRange: null as string[] | null,
  pageNum: 1,
  pageSize: 10
})

const columns = [
  { title: '模块', dataIndex: 'title', width: 110 },
  { title: '业务类型', dataIndex: 'businessType', width: 100 },
  { title: '方法', dataIndex: 'method', ellipsis: true },
  { title: '请求方式', dataIndex: 'requestMethod', width: 90 },
  { title: '操作人', dataIndex: 'operName', width: 110 },
  { title: 'IP 地址', dataIndex: 'ip', width: 130 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '耗时(ms)', dataIndex: 'costMs', width: 90 },
  { title: '时间', dataIndex: 'operTime', width: 170 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getOperLogPage({
      title: query.title || undefined,
      operName: query.operName || undefined,
      status: query.status ?? undefined,
      beginTime: query.dateRange?.[0] || undefined,
      endTime: query.dateRange?.[1] || undefined,
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
  query.title = ''
  query.operName = ''
  query.status = null
  query.dateRange = null
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

onMounted(() => {
  loadList()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-input
          v-model:value="query.title"
          placeholder="模块"
          allow-clear
          style="width: 160px"
          @press-enter="onSearch"
        />
        <a-input
          v-model:value="query.operName"
          placeholder="操作人"
          allow-clear
          style="width: 160px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 110px">
          <a-select-option :value="0">成功</a-select-option>
          <a-select-option :value="1">失败</a-select-option>
        </a-select>
        <a-range-picker
          v-model:value="query.dateRange"
          show-time
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
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

    <a-table
      :data-source="list"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'businessType'">
          <a-tag :color="bizTypeMeta[(record as OperLog).businessType as string]?.color || 'default'">
            {{ bizTypeMeta[(record as OperLog).businessType as string]?.text || (record as OperLog).businessType || '-' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="(record as OperLog).status === 0 ? 'success' : 'error'">
            {{ (record as OperLog).status === 0 ? '成功' : '失败' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'method'">
          <a-tooltip :title="(record as OperLog).method">
            <span>{{ (record as OperLog).method || '-' }}</span>
          </a-tooltip>
        </template>
      </template>
    </a-table>
  </div>
</template>
