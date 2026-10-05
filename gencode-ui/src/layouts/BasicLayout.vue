<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { LOGO_TEXT } from '@/settings'
import DynamicIcon from '@/components/DynamicIcon.vue'

interface SidebarItem {
  id: string
  path: string
  name: string
  icon: string | null
  children: SidebarItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 侧边栏菜单：menuType in (M,C) && visible=0 && status=0 */
const sidebarMenus = computed<SidebarItem[]>(() => {
  const build = (nodes: MenuNode[]): SidebarItem[] => {
    const items: SidebarItem[] = []
    for (const node of nodes || []) {
      if (!((node.menuType === 'M' || node.menuType === 'C') && node.visible === 0 && node.status === 0)) {
        continue
      }
      const children = build(node.children || [])
      if (node.menuType === 'M') {
        if (children.length > 0 && node.path) {
          items.push({ id: node.id, path: node.path, name: node.name, icon: node.icon, children })
        }
      } else if (node.path) {
        items.push({ id: node.id, path: node.path, name: node.name, icon: node.icon, children: [] })
      }
    }
    return items
  }
  return build(userStore.menuTree)
})

const selectedKeys = ref<string[]>([])
const openKeys = ref<string[]>([])

/** 查找目标路径所属的 M 目录 path 链（用于展开子菜单） */
function findOpenChain(nodes: MenuNode[], targetPath: string, parents: string[]): string[] | null {
  for (const node of nodes) {
    if (node.menuType === 'C' && node.path === targetPath) {
      return parents
    }
    const children = node.children || []
    if (children.length > 0) {
      const nextParents =
        node.menuType === 'M' && node.path ? [...parents, node.path] : parents
      const found = findOpenChain(children, targetPath, nextParents)
      if (found !== null) return found
    }
  }
  return null
}

watch(
  () => route.path,
  (path) => {
    selectedKeys.value = [path]
    const chain = findOpenChain(userStore.menuTree || [], path, [])
    if (chain && chain.length > 0) {
      openKeys.value = chain
    }
  },
  { immediate: true }
)

/** 面包屑：首页 / ...目录 / 当前菜单 */
const breadcrumbs = computed<{ name: string; path?: string }[]>(() => {
  const items: { name: string; path?: string }[] = [{ name: '首页', path: '/dashboard' }]
  const chain: MenuNode[] = []
  const walk = (nodes: MenuNode[], trail: MenuNode[]): boolean => {
    for (const node of nodes || []) {
      const next = [...trail, node]
      if (node.menuType === 'C' && node.path === route.path) {
        chain.push(...next)
        return true
      }
      const children = node.children || []
      if (children.length > 0 && walk(children, next)) return true
    }
    return false
  }
  walk(userStore.menuTree || [], [])
  for (const node of chain) {
    if (node.menuType === 'M') items.push({ name: node.name })
  }
  const current = chain[chain.length - 1]
  if (current && current.menuType === 'C') {
    items.push({ name: current.name, path: current.path || undefined })
  }
  return items
})

const avatarText = computed(() => (userStore.loginUser?.nickname || 'U').charAt(0))

function onLogout(): void {
  Modal.confirm({
    title: '退出登录',
    content: '确定要退出登录吗？',
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      userStore.logout().finally(() => {
        router.push('/login')
      })
    }
  })
}
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-sider
      v-model:collapsed="appStore.sidebarCollapsed"
      theme="dark"
      collapsible
      :width="220"
    >
      <div class="logo">
        <span class="logo-text">{{ appStore.sidebarCollapsed ? 'G' : LOGO_TEXT }}</span>
      </div>
      <a-menu
        theme="dark"
        mode="inline"
        v-model:open-keys="openKeys"
        v-model:selected-keys="selectedKeys"
      >
        <template v-for="menu in sidebarMenus" :key="menu.id">
          <a-sub-menu v-if="menu.children.length > 0" :key="menu.path">
            <template #title>
              <span class="menu-title">
                <dynamic-icon :icon="menu.icon" />
                <span>{{ menu.name }}</span>
              </span>
            </template>
            <a-menu-item
              v-for="child in menu.children"
              :key="child.path"
              @click="router.push(child.path)"
            >
              <dynamic-icon :icon="child.icon" />
              <span>{{ child.name }}</span>
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else :key="menu.path" @click="router.push(menu.path)">
            <dynamic-icon :icon="menu.icon" />
            <span>{{ menu.name }}</span>
          </a-menu-item>
        </template>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="header">
        <a-breadcrumb>
          <a-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
            <router-link v-if="item.path" :to="item.path">{{ item.name }}</router-link>
            <span v-else>{{ item.name }}</span>
          </a-breadcrumb-item>
        </a-breadcrumb>
        <a-dropdown>
          <span class="user-info">
            <a-avatar size="small" style="background-color: #1677ff">
              {{ avatarText }}
            </a-avatar>
            <span class="user-name">{{ userStore.loginUser?.nickname || '未知用户' }}</span>
          </span>
          <template #overlay>
            <a-menu>
              <a-menu-item key="logout" @click="onLogout">
                <logout-outlined />
                <span style="margin-left: 6px">退出登录</span>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-layout-header>

      <a-layout-content class="content">
        <div class="page-card">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
}

.logo {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

.menu-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.header {
  height: 48px;
  line-height: 48px;
  background: #fff;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.user-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 8px;
}

.user-name {
  color: rgba(0, 0, 0, 0.88);
}

.content {
  background: #f5f5f5;
}

.page-card {
  margin: 16px;
  padding: 16px;
  background: #fff;
  border-radius: 6px;
  min-height: calc(100vh - 48px - 32px);
}
</style>
