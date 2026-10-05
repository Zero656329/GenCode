import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMe as fetchMeApi, login as loginApi, logout as logoutApi } from '@/api/auth'
import { getMenuTree } from '@/api/menu'
import { clearToken, getToken, setToken } from '@/utils/request'

/** 登录用户 / 菜单树全局状态（token 手动同步 localStorage） */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() ?? '')
  const loginUser = ref<LoginUser | null>(null)
  const menuTree = ref<MenuNode[]>([])

  async function login(body: LoginBody): Promise<void> {
    const result = await loginApi(body)
    token.value = result.token
    setToken(result.token)
    loginUser.value = result.user
  }

  async function fetchMe(): Promise<void> {
    loginUser.value = await fetchMeApi()
  }

  /** 侧边栏菜单数据源 */
  async function fetchMenuTree(): Promise<void> {
    menuTree.value = (await getMenuTree()) || []
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 登出接口异常时本地照常清理
    }
    reset()
  }

  function reset(): void {
    token.value = ''
    clearToken()
    loginUser.value = null
    menuTree.value = []
  }

  return { token, loginUser, menuTree, login, fetchMe, fetchMenuTree, logout, reset }
})
