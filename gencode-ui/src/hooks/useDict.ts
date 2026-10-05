import { ref, type Ref } from 'vue'
import { useAppStore } from '@/stores/app'

/**
 * 字典 hook：const { options } = useDict('sys_normal_status')
 * options 为 Ref<DictOption[]>，数据经 app store 缓存。
 */
export function useDict(dictType: string): { options: Ref<DictOption[]> } {
  const appStore = useAppStore()
  const options = ref<DictOption[]>([])
  appStore
    .loadDictOptions(dictType)
    .then((list) => {
      options.value = list
    })
    .catch(() => {
      options.value = []
    })
  return { options }
}
