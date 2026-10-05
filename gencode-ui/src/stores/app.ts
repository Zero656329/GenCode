import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDictDataByType } from '@/api/dict'

/** 应用状态：侧边栏折叠 + 字典缓存 */
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const dictCache = ref<Record<string, DictOption[]>>({})

  const pending = new Map<string, Promise<DictOption[]>>()

  /** 按类型加载字典项，store 内缓存（同一类型只请求一次） */
  function loadDictOptions(type: string): Promise<DictOption[]> {
    const cached = dictCache.value[type]
    if (cached) return Promise.resolve(cached)
    const inflight = pending.get(type)
    if (inflight) return inflight
    const promise = getDictDataByType(type)
      .then((list) => {
        const options = (list || []).map((item) => ({ label: item.label, value: item.value }))
        dictCache.value[type] = options
        pending.delete(type)
        return options
      })
      .catch((err) => {
        pending.delete(type)
        throw err
      })
    pending.set(type, promise)
    return promise
  }

  return { sidebarCollapsed, dictCache, loadDictOptions }
})
