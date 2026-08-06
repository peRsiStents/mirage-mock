<template>
  <div class="page">
    <el-card v-if="!proj.id">
      <el-empty description="请先在顶部选择一个项目" />
    </el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>密钥管理 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <div>
            <el-input v-model="sm2Alias" placeholder="SM2 别名" style="width: 160px; margin-right: 8px" />
            <el-button type="success" :loading="genLoading" @click="genSm2">服务端生成 SM2</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">录入密钥</el-button>
          </div>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <template #empty>
          <el-empty description="暂无密钥">
            <el-button type="primary" size="small" @click="openCreate">录入密钥</el-button>
          </el-empty>
        </template>
        <el-table-column prop="alias" label="别名" width="160" />
        <el-table-column prop="algorithm" label="算法" width="100" />
        <el-table-column label="公钥" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.publicKey || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="ivValue" label="IV" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" title="录入密钥" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="别名"><el-input v-model="form.alias" placeholder="如 RSA_PROD，表达式 ${sm2(...,'别名')} 会引用它" /></el-form-item>
        <el-form-item label="算法">
          <el-select v-model="form.algorithm">
            <el-option label="SM2（非对称）" value="SM2" />
            <el-option label="SM4（对称）" value="SM4" />
            <el-option label="AES（对称）" value="AES" />
            <el-option label="RSA（非对称）" value="RSA" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAsym" label="公钥(Base64)"><el-input v-model="form.publicKey" type="textarea" :rows="2" placeholder="X509/Base64" /></el-form-item>
        <el-form-item :label="isAsym ? '私钥(Base64)' : '密钥(Base64)'"><el-input v-model="form.privateKey" type="textarea" :rows="2" :placeholder="isAsym ? 'PKCS8/Base64 或 SM2 裸字节' : '16 字节(AES/SM4)'" /></el-form-item>
        <el-form-item v-if="!isAsym" label="IV(Base64)"><el-input v-model="form.ivValue" placeholder="CBC 模式才需要" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存（私钥加密落库）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="SM2 密钥对（私钥仅显示一次）" width="640px">
      <el-alert type="warning" :closable="false" title="请立即保存私钥，关闭后无法再次获取" style="margin-bottom: 12px" />
      <p>别名：{{ result.alias }}</p>
      <p>公钥：</p>
      <el-input :model-value="result.publicKey" type="textarea" :rows="3" readonly class="mono" />
      <p>私钥：</p>
      <el-input :model-value="result.privateKey" type="textarea" :rows="3" readonly class="mono" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const sm2Alias = ref('')

const formVisible = ref(false)
const form = reactive({ alias: '', algorithm: 'SM4', publicKey: '', privateKey: '', ivValue: '' })
const isAsym = computed(() => form.algorithm === 'SM2' || form.algorithm === 'RSA')

const resultVisible = ref(false)
const result = reactive({ alias: '', publicKey: '', privateKey: '' })
const saving = ref(false)
const genLoading = ref(false)

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.keys.list(proj.id)
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { alias: '', algorithm: 'SM4', publicKey: '', privateKey: '', ivValue: '' })
  formVisible.value = true
}

async function onSave() {
  if (!form.alias || !form.alias.trim()) { ElMessage.warning('请输入密钥别名'); return }
  const algo = form.algorithm
  const pub = (form.publicKey || '').trim()
  const priv = (form.privateKey || '').trim()
  if (isAsym.value) {
    if (!pub && !priv) { ElMessage.warning('非对称密钥请至少填写公钥或私钥（建议两者都填）'); return }
  } else {
    if (!priv) { ElMessage.warning('请填写对称密钥（' + algo + ' 通常为 16 字节）'); return }
  }
  saving.value = true
  try {
    await api.keys.create(proj.id, { ...form })
    ElMessage.success('已保存')
    formVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function genSm2() {
  if (!sm2Alias.value) {
    ElMessage.warning('请输入别名')
    return
  }
  genLoading.value = true
  try {
    const res = await api.keys.generateSm2(proj.id, sm2Alias.value)
    Object.assign(result, res.data)
    resultVisible.value = true
    sm2Alias.value = ''
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    genLoading.value = false
  }
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除密钥「${row.alias}」？引用它的模板将失效`, '警告', { type: 'warning' })
  await api.keys.remove(row.id)
  ElMessage.success('已删除')
  load()
}

watch(() => proj.id, load)
onMounted(load)
</script>
