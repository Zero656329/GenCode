<script setup lang="ts">
/**
 * 字符串图标名 → @ant-design/icons-vue 动态组件。
 * 找不到图标时回退 AppstoreOutlined。
 */
import { computed, type Component } from 'vue'
import * as Icons from '@ant-design/icons-vue'

const props = defineProps<{ icon?: string | null }>()

const iconMap = Icons as unknown as Record<string, Component | undefined>

const FALLBACK = 'AppstoreOutlined'

const resolved = computed<Component>(() => {
  if (props.icon) {
    const found = iconMap[props.icon]
    if (found) return found
    console.warn(`[GenCode] 图标未找到: ${props.icon}，已回退为 ${FALLBACK}`)
  }
  return iconMap[FALLBACK] as Component
})
</script>

<template>
  <component :is="resolved" />
</template>
