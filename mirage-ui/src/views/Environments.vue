<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>环境管理 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建环境</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="baseUrl" label="Base URL" show-overflow-tooltip />
        <el-table-column label="变量数" width="80">
          <template #default="{ row }">{{ parseArr(row.variables).length }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑环境' : '新建环境'" width="640px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 dev / test / prod" /></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="form.baseUrl" placeholder="http://host:port（用例相对路径会拼到此后）" /></el-form-item>
        <div class="kv-title">环境变量（并入 ${var.name}）</div>
        <div v-for="(v, i) in vars" :key="i" class="kv-row">
          <el-input v-model="v.name" placeholder="变量名" style="width:38%" />
          <el-input v-model="v.value" placeholder="值" style="width:52%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="vars.splice(i, 1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="vars.push({ name: '', value: '' })">加变量</el-button>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const formVisible = ref(false)
const form = reactive({ id: null, name: '', baseUrl: '', status: 1 })
const vars = ref([])

function parseArr(s) { try { return JSON.parse(s || '[]') } catch (e) { return [] } }

async function load() {
  if (!proj.id) return
  loading.value = true
  try { const res = await api.environments.list(proj.id); list.value = res.data || [] } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', baseUrl: '', status: 1 })
  vars.value = []
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name, baseUrl: row.baseUrl || '', status: row.status == null ? 1 : row.status })
  vars.value = parseArr(row.variables)
  formVisible.value = true
}

async function onSave() {
  const payload = { name: form.name, baseUrl: form.baseUrl, status: form.status, variables: JSON.stringify(vars.value.filter((v) => v.name)) }
  if (form.id) { await api.environments.update(form.id, payload) } else { await api.environments.create(proj.id, payload) }
  ElMessage.success('已保存'); formVisible.value = false; load()
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除环境「${row.name}」？`, '警告', { type: 'warning' })
  await api.environments.remove(row.id); ElMessage.success('已删除'); load()
}

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.kv-title { font-size: 13px; color: #606266; margin: 8px 0 4px; }
.kv-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
</style>
