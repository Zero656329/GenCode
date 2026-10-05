<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getLoginLogPage } from '@/api/log'

const loading = ref(false)
const list = ref<LoginLog[]>([])
const total = ref(0)
const query = reactive({
  username: '',
  ip: '',
  status: null as number | null,
  dateRange: null as string[] | null,
  pageNum: 1,
  pageSize: 10
})

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: 'IP 地址', dataIndex: 'ip' },
  { title: '浏览器', dataIndex: 'browser' },
  { title: '操作系统', dataIndex: 'os' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '提示信息', dataIndex: 'msg' },
  { title: '登录时间', dataIndex: 'loginTime', width: 170 }
]

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getLoginLogPage({
      username: query.username || undefined,
      ip: query.ip || undefined,
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
  query.username = ''
  query.ip = ''
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
          v-model:value="query.username"
          placeholder="用户名"
          allow-clear
          style="width: 160px"
          @press-enter="onSearch"
        />
        <a-input
          v-model:value="query.ip"
          placeholder="IP 地址"
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
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="(record as LoginLog).status === 0 ? 'success' : 'error'">
            {{ (record as LoginLog).status === 0 ? '成功' : '失败' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'msg'">
          {{ (record as LoginLog).msg || '-' }}
        </template>
      </template>
    </a-table>
  </div>
</template>
