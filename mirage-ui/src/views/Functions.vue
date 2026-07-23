<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>函数市场 · 生成器函数库</span>
          <el-input v-model="keyword" placeholder="搜索函数名/说明" style="width: 240px" clearable />
        </div>
      </template>
      <el-table :data="filtered" v-loading="loading" border stripe>
        <el-table-column prop="name" label="函数" width="200">
          <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="returnType" label="返回" width="90" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="示例" width="320">
          <template #default="{ row }">
            <span class="mono">{{ row.example }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="copy(row.example)">复制示例</el-button>
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

const list = ref([])
const loading = ref(false)
const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((f) => (f.name + f.description + f.category).toLowerCase().includes(k))
})

async function load() {
  loading.value = true
  try {
    const res = await api.functions.list()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function copy(text) {
  navigator.clipboard.writeText(text || '').then(() => ElMessage.success('已复制：' + text))
}

onMounted(load)
</script>
