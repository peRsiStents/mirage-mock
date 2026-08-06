<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>函数库 · 生成器函数说明</span>
          <div style="display:flex;gap:8px">
            <el-select v-model="category" placeholder="全部分类" clearable style="width:160px">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
            <el-input v-model="keyword" placeholder="搜索函数名/说明" style="width: 220px" clearable />
          </div>
        </div>
      </template>
      <el-table :data="filtered" v-loading="loading" border stripe size="small">
        <el-table-column label="函数" width="190">
          <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="returnType" label="返回" width="80" />
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="示例（含参数签名）" min-width="320">
          <template #default="{ row }"><span class="mono">{{ row.example }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="copy(row.name)">复制函数名</el-button>
            <el-button size="small" link @click="copy(row.example)">复制示例</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { copyText } from '../utils/clipboard'

const list = ref([])
const loading = ref(false)
const keyword = ref('')
const category = ref('')

const categories = computed(() => Array.from(new Set(list.value.map((f) => f.category).filter(Boolean))).sort())
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return list.value.filter((f) => {
    if (category.value && f.category !== category.value) return false
    if (k && !(f.name + f.description + f.category).toLowerCase().includes(k)) return false
    return true
  })
})

async function copy(text) {
  await copyText(text)
  ElMessage.success('已复制：' + text)
}

async function load() {
  loading.value = true
  try {
    const res = await api.functions.list()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
