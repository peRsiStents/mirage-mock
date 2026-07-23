<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>文件生成 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建模板</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="行模板" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.rowTemplate }}</span></template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column prop="encoding" label="编码" width="80" />
        <el-table-column prop="fileExt" label="扩展名" width="80" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="onGen(row)">生成下载</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑模板' : '新建模板'" width="780px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="行数"><el-input-number v-model="form.rowCount" :min="1" :max="100000" /></el-form-item></el-col>
          <el-col :span="4"><el-form-item label="编码">
            <el-select v-model="form.encoding"><el-option label="GBK" value="GBK" /><el-option label="UTF-8" value="UTF-8" /></el-select>
          </el-form-item></el-col>
          <el-col :span="4"><el-form-item label="扩展名">
            <el-select v-model="form.fileExt"><el-option label="txt" value="txt" /><el-option label="dat" value="dat" /></el-select>
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="行分隔符">
          <el-radio-group v-model="form.lineSeparator">
            <el-radio label="CRLF">CRLF（Windows，银行常见）</el-radio>
            <el-radio label="LF">LF（Unix）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="首行标题">
          <el-input v-model="form.headerLine" placeholder="可选，如：序号|姓名|身份证|金额 （支持 ${...}）" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="17">
            <el-form-item label="行模板">
              <el-input v-model="form.rowTemplate" type="textarea" :rows="5" placeholder="如：${seq('kh',1)}|${name.cn}|${idcard.cn}|${decimal(100,999,2)}" />
              <div v-if="hasSeq" class="warn">⚠ 该模板含 ${seq(...)}，每次生成/预览都会永久消费项目序列值。</div>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <div class="fn-panel">
              <div class="fn-title">函数市场 · 点击追加到模板</div>
              <FunctionMarketSidebar :project-id="proj.id" @insert="appendFn" />
            </div>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="onPreview" :loading="previewing">预览前 10 行</el-button>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="success" @click="onGenFromForm">生成下载</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="预览（前 10 行）" width="640px">
      <pre class="preview">{{ previewLines.join('\n') }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import FunctionMarketSidebar from '../components/FunctionMarketSidebar.vue'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)

const formVisible = ref(false)
const form = reactive({ id: null, name: '', headerLine: '', rowTemplate: '', rowCount: 100, encoding: 'GBK', lineSeparator: 'CRLF', fileExt: 'txt', remark: '' })
const hasSeq = computed(() => /\$\{seq\(/.test(form.rowTemplate || ''))

const previewVisible = ref(false)
const previewing = ref(false)
const previewLines = ref([])

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.fileTemplates.list(proj.id)
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', headerLine: '', rowTemplate: '', rowCount: 100, encoding: 'GBK', lineSeparator: 'CRLF', fileExt: 'txt', remark: '' })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  formVisible.value = true
}

function appendFn(text) {
  form.rowTemplate = (form.rowTemplate || '') + text
}

function buildReq() {
  return {
    projectId: proj.id,
    name: form.name,
    headerLine: form.headerLine,
    rowTemplate: form.rowTemplate,
    rowCount: form.rowCount,
    encoding: form.encoding,
    lineSeparator: form.lineSeparator,
    fileExt: form.fileExt
  }
}

async function onSave() {
  const payload = { ...form }
  if (form.id) {
    await api.fileTemplates.update(form.id, payload)
  } else {
    await api.fileTemplates.create(proj.id, payload)
  }
  ElMessage.success('已保存')
  formVisible.value = false
  load()
}

async function onPreview() {
  if (!form.rowTemplate) { ElMessage.warning('请填写行模板'); return }
  previewing.value = true
  try {
    const res = await api.fileTemplates.preview(buildReq())
    previewLines.value = res.data || []
    previewVisible.value = true
  } finally {
    previewing.value = false
  }
}

async function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function generate(req, name, ext) {
  try {
    const blob = await api.fileTemplates.generate(req)
    // BizException 返回 HTTP200 + JSON 错误体，需检测
    if (blob.type && blob.type.includes('json')) {
      const err = JSON.parse(await blob.text())
      ElMessage.error('生成失败：' + (err.message || '模板有误'))
      return
    }
    await downloadBlob(blob, (name || 'mirage-file') + '.' + (ext || 'txt'))
    ElMessage.success('已生成下载')
  } catch {
    ElMessage.error('生成失败，请检查模板')
  }
}

function onGenFromForm() {
  if (!form.rowTemplate) { ElMessage.warning('请填写行模板'); return }
  generate(buildReq(), form.name, form.fileExt)
}

function onGen(row) {
  generate({
    projectId: row.projectId, name: row.name, headerLine: row.headerLine, rowTemplate: row.rowTemplate,
    rowCount: row.rowCount, encoding: row.encoding, lineSeparator: row.lineSeparator, fileExt: row.fileExt
  }, row.name, row.fileExt)
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除模板「${row.name || '未命名'}」？`, '警告', { type: 'warning' })
  await api.fileTemplates.remove(row.id)
  ElMessage.success('已删除')
  load()
}

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.warn { color: #e6a23c; font-size: 12px; margin-top: 4px; }
.fn-panel { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; height: 100%; }
.fn-title { font-size: 13px; color: #606266; margin-bottom: 6px; }
.preview { background: #f5f7fa; padding: 12px; border-radius: 4px; max-height: 400px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 13px; white-space: pre; }
</style>
