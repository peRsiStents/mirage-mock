<template>
  <div class="resp-wrap">
    <div class="resp-bar">
      <el-button size="small" link @click="copy">复制</el-button>
      <el-button v-if="isJson" size="small" link @click="formatted = !formatted">{{ formatted ? '压缩' : '美化' }}</el-button>
    </div>
    <pre class="resp">{{ display }}</pre>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { copyText } from '../utils/clipboard'

const props = defineProps({ body: { type: [String, Object, Array], default: '' } })
const formatted = ref(true)

const rawText = computed(() => (typeof props.body === 'string' ? props.body : JSON.stringify(props.body)))
const isJson = computed(() => {
  const t = rawText.value.trim()
  return t.startsWith('{') || t.startsWith('[')
})
const display = computed(() => {
  if (!isJson.value) return rawText.value
  try {
    const obj = JSON.parse(rawText.value)
    return formatted.value ? JSON.stringify(obj, null, 2) : JSON.stringify(obj)
  } catch (e) {
    return rawText.value
  }
})

function copy() {
  copyText(display.value)
}
</script>

<style scoped>
.resp-wrap { position: relative; }
.resp-bar { position: absolute; top: 4px; right: 6px; z-index: 1; }
.resp {
  background: #f5f7fa; padding: 8px; border-radius: 4px; max-height: 260px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px;
  white-space: pre-wrap; word-break: break-all;
}
</style>
