import {
  createRouter,
  createWebHistory,
  type RouteComponent,
  type RouteRecordRaw
} from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/request'
import { LOGO_TEXT, TITLE } from '@/settings'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const LAYOUT_NAME = 'Layout'

/** 常量路由：登录页（全屏）、Layout（children 动态挂载） */
const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    name: LAYOUT_NAME,
    component: BasicLayout,
    redirect: '/dashboard',
    children: []
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes
})

/** views 下全部组件的懒加载映射，key 形如 '../views/system/user/index.vue' */
const viewModules = import.meta.glob('../views/**/*.vue')
const notFoundLoader = viewModules['../views/404.vue']

/** 动态路由是否已装载（与登录态解耦：登录成功后立即跳转时路由可能尚未注册） */
let dynamicLoaded = false
/** 已注册的动态路由名，退出登录时逐一拆除，避免换账号残留上一账号路由 */
const addedNames: string[] = []

/** 拆卸动态路由 */
function teardownDynamicRoutes(): void {
  addedNames.forEach((name) => {
    try {
      router.removeRoute(name)
    } catch {
      // 路由不存在时忽略
    }
  })
  addedNames.length = 0
  try {
    router.removeRoute('NotFound')
  } catch {
    // 路由不存在时忽略
  }
  dynamicLoaded = false
}

/** 菜单 path → 路由 name：'/system/user' → 'SystemUser' */
function toRouteName(path: string): string {
  return path
    .split('/')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

/** 把菜单树中 menuType==='C' 的节点转换为 Layout 子路由，并最后挂 404 兜底 */
function addDynamicRoutes(menuTree: MenuNode[]): void {
  const walk = (nodes: MenuNode[]): void => {
    for (const node of nodes) {
      if (node.menuType === 'C' && node.path) {
        const key = `../views/${node.component}.vue`
        let loader = node.component ? viewModules[key] : undefined
        if (!loader) {
          console.warn(
            `[GenCode] 菜单组件未找到: ${node.component || '(空)'}（菜单: ${node.name}），使用 404 兜底`
          )
          loader = notFoundLoader
        }
        const name = toRouteName(node.path)
        router.addRoute(LAYOUT_NAME, {
          path: node.path,
          name,
          component: loader as unknown as RouteComponent,
          meta: { title: node.name }
        })
        addedNames.push(name)
      }
      if (node.children && node.children.length > 0) {
        walk(node.children)
      }
    }
  }
  walk(menuTree)
  // 404 兜底路由必须最后添加
  router.addRoute({
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: (notFoundLoader as unknown as RouteComponent) ?? undefined,
    meta: { title: '404' }
  })
  addedNames.push('NotFound')
}

/** 全局前置守卫：登录态检查 + 动态路由装载 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const token = userStore.token || getToken()
  if (!token) {
    // 退出登录后拆卸动态路由（换账号登录不残留上一账号路由）
    if (to.path === '/login' && dynamicLoaded) {
      teardownDynamicRoutes()
    }
    return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login') {
    return { path: '/dashboard' }
  }
  // 动态路由未装载（首次登录跳转 / 刷新页面）：拉取用户 + 菜单并注册路由后重放目标路由
  if (!dynamicLoaded) {
    try {
      if (!userStore.loginUser) {
        await userStore.fetchMe()
      }
      await userStore.fetchMenuTree()
      addDynamicRoutes(userStore.menuTree)
      dynamicLoaded = true
      // 重新解析目标路由（动态路由已就绪）
      return { ...to, replace: true }
    } catch {
      userStore.reset()
      teardownDynamicRoutes()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

/** 页面标题 */
router.afterEach((to) => {
  const title = to.meta?.title
  document.title = title ? `${title} - ${LOGO_TEXT}` : TITLE
})

export default router
