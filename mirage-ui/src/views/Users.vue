<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column prop="nickname" label="昵称" width="160" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.isAdmin === 1 ? 'danger' : 'info'" size="small">{{ row.isAdmin === 1 ? '管理员' : '普通用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" :disabled="row.username === 'admin'" @change="(v) => toggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" link @click="openReset(row)">重置密码</el-button>
            <el-button size="small" type="danger" link :disabled="row.username === 'admin'" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item v-if="!form.id" label="密码"><el-input v-model="form.password" type="password" show-password placeholder="初始密码" /></el-form-item>
        <el-form-item label="角色">
          <el-switch v-model="form.isAdmin" :active-value="1" :inactive-value="0" active-text="管理员" inactive-text="普通用户" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="440px">
      <el-form label-width="90px">
        <el-form-item label="用户名"><el-input :model-value="resetTarget.username" disabled /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="resetPassword" type="password" show-password placeholder="新密码" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="onReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api'

const list = ref([])
const loading = ref(false)

const formVisible = ref(false)
const form = reactive({ id: null, username: '', nickname: '', password: '', isAdmin: 0, status: 1 })

const resetVisible = ref(false)
const resetTarget = reactive({ id: null, username: '' })
const resetPassword = ref('')

async function load() {
  loading.value = true
  try {
    const res = await api.users.list()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, username: '', nickname: '', password: '', isAdmin: 0, status: 1 })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, username: row.username, nickname: row.nickname || '', password: '', isAdmin: row.isAdmin, status: row.status })
  formVisible.value = true
}

async function onSave() {
  const payload = { nickname: form.nickname, isAdmin: form.isAdmin, status: form.status }
  if (form.id) {
    await api.users.update(form.id, payload)
  } else {
    payload.username = form.username
    payload.password = form.password
    await api.users.create(payload)
  }
  ElMessage.success('已保存')
  formVisible.value = false
  load()
}

function openReset(row) {
  Object.assign(resetTarget, { id: row.id, username: row.username })
  resetPassword.value = ''
  resetVisible.value = true
}

async function onReset() {
  if (!resetPassword.value) { ElMessage.warning('请输入新密码'); return }
  await api.users.update(resetTarget.id, { password: resetPassword.value })
  ElMessage.success('密码已重置')
  resetVisible.value = false
}

async function toggleStatus(row, v) {
  await api.users.update(row.id, { status: v ? 1 : 0 })
  row.status = v ? 1 : 0
  ElMessage.success(v ? '已启用' : '已停用')
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除用户「${row.username}」？`, '警告', { type: 'warning' })
  await api.users.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
