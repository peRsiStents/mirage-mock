<template>
  <el-popover
    v-model:visible="visible"
    trigger="click"
    :width="360"
    placement="bottom-start"
    popper-class="prm-popper"
    @show="onShow"
  >
    <template #reference>
      <el-button size="small" link type="primary" class="prm-trigger">引用▾</el-button>
    </template>

    <div class="prm">
      <el-input v-model="kw" size="small" placeholder="搜索 变量 / 数据行 / 函数" clearable class="prm-kw" />

      <el-tabs v-model="tab" size="small" class="prm-tabs">
        <!-- 变量：项目变量 + 当前环境变量 -->
        <el-tab-pane label="变量" name="var">
          <div class="prm-list">
            <div
              v-for="v in varItems"
              :key="v.key"
              class="prm-item"
              :title="'插入 ' + v.text"
              @click="pick(v.text)"
            >
              <span class="mono prm-name">{{ v.label }}</span>
              <el-tag v-if="v.src" size="small" type="info" effect="plain">{{ v.src }}</el-tag>
              <span class="muted prm-val">{{ v.value }}</span>
            </div>
            <el-empty v-if="!varItems.length" description="暂无变量" :image-size="36" />
          </div>
        </el-tab-pane>

        <!-- 数据行：数据驱动每行注入的 ${var.<键>} -->
        <el-tab-pane :label="'数据行' + (dataKeyCount ? '(' + dataKeyCount + ')' : '')" name="row">
          <div class="prm-list">
            <div
              v-for="k in rowItems"
              :key="k"
              class="prm-item"
              :title="'插入 ' + rowText(k)"
              @click="pick(rowText(k))"
            >
              <span class="mono prm-name">{{ k }}</span>
              <span class="muted prm-val">{{ rowText(k) }}</span>
            </div>
            <el-empty v-if="!rowItems.length" description="未配置数据驱动" :image-size="36" />
          </div>
        </el-tab-pane>

        <!-- 函数：DSL 函数市场 -->
        <el-tab-pane label="函数" name="fn">
          <div class="prm-list">
            <div v-for="grp in fnGroups" :key="grp.category" class="prm-grp">
              <div class="prm-cat">{{ grp.category }}</div>
              <div
                v-for="f in grp.items"
                :key="f.name"
                class="prm-item"
                :title="f.description + '  示例：' + f.example"
                @click="pick(fnText(f))"
              >
                <span class="mono prm-name">{{ f.name }}</span>
                <span class="muted prm-val">{{ f.description }}</span>
              </div>
            </div>
            <el-empty v-if="!fnGroups.length" :description="fnLoading ? '加载中…' : '无匹配函数'" :image-size="36" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="prm-tip muted">插入到输入框光标处 · 函数仅「后端转发」模式求值</div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { api } from '../api'
import { snippetForFunction } from '../utils/fnSnippet'

const props = defineProps({
  /** 项目变量 [{name, varValue}] */
  variables: { type: Array, default: () => [] },
  /** 当前环境变量 [{name, value}] */
  envVars: { type: Array, default: () => [] },
  /** 数据驱动的数据行键名 */
  dataKeys: { type: Array, default: () => [] }
})
const emit = defineEmits(['insert'])

const visible = ref(false)
const kw = ref('')
const tab = ref('var')
const fnList = ref([])
const fnLoading = ref(false)

const k = computed(() => kw.value.trim().toLowerCase())
const match = (s) => !k.value || String(s || '').toLowerCase().includes(k.value)

/** 变量页：项目变量 + 环境变量（同名以项目变量优先，标记来源） */
const varItems = computed(() => {
  const out = []
  const seen = new Set()
  for (const v of props.variables || []) {
    if (!v || !v.name) continue
    if (seen.has(v.name)) continue
    seen.add(v.name)
    out.push({ key: 'p:' + v.name, label: v.name, value: v.varValue || '', src: '项目', text: '${var.' + v.name + '}' })
  }
  for (const v of props.envVars || []) {
    if (!v || !v.name || seen.has(v.name)) continue
    seen.add(v.name)
    out.push({ key: 'e:' + v.name, label: v.name, value: v.value || '', src: '环境', text: '${var.' + v.name + '}' })
  }
  return out.filter((v) => match(v.label) || match(v.value))
})

const rowItems = computed(() => (props.dataKeys || []).filter((x) => match(x)))
const dataKeyCount = computed(() => (props.dataKeys || []).length)

const fnGroups = computed(() => {
  const map = new Map()
  for (const f of fnList.value) {
    if (!match(f.name) && !match(f.category) && !match(f.description)) continue
    if (!map.has(f.category)) map.set(f.category, { category: f.category, items: [] })
    map.get(f.category).items.push(f)
  }
  return Array.from(map.values())
})

function fnText(f) {
  return snippetForFunction(f)
}

/** 数据行键 → 引用文本 */
function rowText(k) {
  return '${var.' + k + '}'
}

function pick(text) {
  emit('insert', text)
  visible.value = false
}

async function onShow() {
  if (fnList.value.length || fnLoading.value) return
  fnLoading.value = true
  try {
    fnList.value = await loadFunctions()
  } catch (e) {
    fnList.value = []
  } finally {
    fnLoading.value = false
  }
}

// 函数列表全局缓存：每个参数行都挂了一个菜单实例，避免重复请求
let fnCache = null
async function loadFunctions() {
  if (fnCache) return fnCache
  const res = await api.functions.list()
  fnCache = res.data || []
  return fnCache
}

// 关键字变化时，若当前页已无匹配项，自动切到有内容的一页
watch(k, () => {
  if (tab.value === 'var' && !varItems.value.length && rowItems.value.length) tab.value = 'row'
  else if (tab.value === 'row' && !rowItems.value.length && varItems.value.length) tab.value = 'var'
})
</script>

<style scoped>
.prm-trigger { font-size: 12px; padding: 0 2px; }
.prm-kw { margin-bottom: 4px; }
.prm-list { max-height: 260px; overflow: auto; }
.prm-grp { margin-bottom: 6px; }
.prm-cat { font-size: 12px; color: #909399; margin: 2px 0; }
.prm-item {
  display: flex; align-items: center; gap: 6px;
  padding: 3px 4px; border-radius: 4px; cursor: pointer;
}
.prm-item:hover { background: #f5f7fa; }
.prm-name { font-size: 13px; flex: 0 0 auto; }
.prm-val {
  font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0;
}
.prm-tip { font-size: 12px; margin-top: 4px; }
</style>
