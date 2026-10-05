<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getRecycleTypes,
  getRecyclePage,
  restoreRecycle,
  purgeRecycle,
  type RecycleType,
  type RecycleRow
} from '@/api/lc-recycle'

/**
 * 数据回收站（/lc/recycle）
 * 删除表单/列表/大屏/数据集后会出现在这里：可恢复（deleted 置 0）或彻底删除（物理删除，不可恢复）。
 * 契约行结构仅 { id, name, createTime }，恢复/删除的 type 优先取行上带回的类型，否则取当前筛选类型。
 */

const loading = ref(false)
const list = ref<RecycleRow[]>([])
const total = ref(0)
const types = ref<RecycleType[]>([])
const query = reactive({ type: undefined as string | undefined, keyword: '', pageNum: 1, pageSize: 10 })

const columns = [
  { title: '名称', dataIndex: 'name', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 200 },
  { title: '操作', key: 'action', width: 180 }
]

const typeOptions = computed(() => types.value.map((t) => ({ label: t.label, value: t.type })))

async function loadTypes(): Promise<void> {
  try {
    types.value = (await getRecycleTypes()) || []
  } catch {
    types.value = []
  }
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await getRecyclePage({
      type: query.type || undefined,
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
  query.type = undefined
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

/** 行类型：优先行上带回，缺省回退当前筛选类型 */
function rowType(row: RecycleRow): string | undefined {
  return row.type || query.type || undefined
}

async function onRestore(row: RecycleRow): Promise<void> {
  const type = rowType(row)
  if (!type) {
    message.warning('请先在左侧筛选具体类型后再操作')
    return
  }
  await restoreRecycle({ type, id: row.id })
  message.success('恢复成功')
  loadList()
}

async function onPurge(row: RecycleRow): Promise<void> {
  const type = rowType(row)
  if (!type) {
    message.warning('请先在左侧筛选具体类型后再操作')
    return
  }
  await purgeRecycle({ type, id: row.id })
  message.success('已彻底删除')
  loadList()
}

onMounted(() => {
  loadTypes()
  loadList()
})
</script>

<template>
  <div>
    <div class="search-bar">
      <a-space wrap>
        <a-select
          v-model:value="query.type"
          :options="typeOptions"
          placeholder="全部类型"
          allow-clear
          style="width: 160px"
        />
        <a-input
          v-model:value="query.keyword"
          placeholder="名称"
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

    <a-alert
      class="recycle-tip"
      type="info"
      show-icon
      message="删除表单/列表/大屏/数据集后会出现在这里，可恢复或彻底删除（物理删除，不可恢复）。"
    />

    <a-table
      :data-source="list"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="pagination"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'createTime'">
          {{ (record as RecycleRow).createTime || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-popconfirm
              title="确定恢复该数据吗？"
              ok-text="恢复"
              cancel-text="取消"
              @confirm="onRestore(record as RecycleRow)"
            >
              <a-button type="link" size="small" v-perm="'lc:recycle:restore'">恢复</a-button>
            </a-popconfirm>
            <a-popconfirm
              title="彻底删除后不可恢复，确定吗？"
              ok-text="彻底删除"
              cancel-text="取消"
              :ok-button-props="{ danger: true }"
              @confirm="onPurge(record as RecycleRow)"
            >
              <a-button type="link" danger size="small" v-perm="'lc:recycle:restore'">彻底删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无数据 · 删除表单/列表/大屏/数据集后会出现在这里" />
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.recycle-tip {
  margin-bottom: 12px;
}
</style>
