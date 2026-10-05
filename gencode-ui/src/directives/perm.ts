import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/** 是否拥有指定权限（支持 string / string[]，超管通配 *:*:*） */
export function hasPerm(value: string | string[]): boolean {
  const userStore = useUserStore()
  const perms = userStore.loginUser?.perms || []
  if (perms.includes('*:*:*')) return true
  const required = Array.isArray(value) ? value : [value]
  return required.some((p) => perms.includes(p))
}

/**
 * 按钮权限指令：v-perm="'system:user:add'" 或 v-perm="['a', 'b']"
 * 无权限时直接移除元素。
 */
export const perm: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (!hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
