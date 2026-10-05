<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import {
  ApartmentOutlined,
  CloudOutlined,
  TeamOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import { getDashboardStats } from '@/api/dashboard'

const stats = ref<DashboardStats>({
  userCount: 0,
  roleCount: 0,
  deptCount: 0,
  tenantCount: 0,
  menuCount: 0,
  loginTrend: [],
  loginTrend7d: [],
  latestLogins: []
})

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const statCards = [
  { key: 'userCount', title: '用户总数', icon: UserOutlined, color: '#1677ff' },
  { key: 'roleCount', title: '角色数量', icon: TeamOutlined, color: '#52c41a' },
  { key: 'deptCount', title: '部门数量', icon: ApartmentOutlined, color: '#722ed1' },
  { key: 'tenantCount', title: '租户数量', icon: CloudOutlined, color: '#fa8c16' }
] as const

function renderChart(trend: LoginTrendItem[]): void {
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 24, top: 32, bottom: 32 },
    xAxis: {
      type: 'category',
      data: trend.map((item) => item.date),
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '登录次数',
        type: 'line',
        smooth: true,
        data: trend.map((item) => item.count),
        itemStyle: { color: '#1677ff' },
        areaStyle: { opacity: 0.15 }
      }
    ]
  })
}

function onResize(): void {
  chart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
  }
  try {
    stats.value = await getDashboardStats()
  } catch {
    // 拦截器已统一提示
  }
  renderChart(stats.value.loginTrend || [])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="dashboard">
    <a-row :gutter="16">
      <a-col v-for="card in statCards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card">
          <div class="stat-inner">
            <a-statistic :title="card.title" :value="stats[card.key]" />
            <span class="stat-icon" :style="{ backgroundColor: card.color }">
              <component :is="card.icon" />
            </span>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="最近 7 天登录趋势" class="block">
      <div ref="chartEl" class="chart"></div>
    </a-card>

    <a-card title="最近登录记录" class="block">
      <a-table
        :data-source="stats.latestLogins"
        :pagination="false"
        :row-key="(_record: LatestLogin, index: number | undefined) => String(index)"
      >
        <a-table-column title="用户名" data-index="username" />
        <a-table-column title="IP 地址" data-index="ip" />
        <a-table-column title="状态" data-index="status" :width="100">
          <template #default="{ record }">
            <a-tag :color="record.status === 0 ? 'success' : 'error'">
              {{ record.status === 0 ? '成功' : '失败' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="信息" data-index="msg" />
        <a-table-column title="登录时间" data-index="loginTime" :width="180" />
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.stat-card .stat-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  color: #fff;
  font-size: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.block {
  margin-top: 16px;
}

.chart {
  height: 320px;
  width: 100%;
}
</style>
