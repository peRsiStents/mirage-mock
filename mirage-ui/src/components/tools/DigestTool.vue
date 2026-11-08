<template>
  <div class="dg">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="dg-hint">
          MD5 / SHA-1 / SHA-256 / SHA-384 / SHA-512 <b>摘要</b>（<b>不是加密，不可逆</b>，不能拿来存密码）。
          文本直接送服务端算；文件按 Base64 上传由服务端算——浏览器没有 MD5 原生实现，
          而 <code>crypto.subtle</code> 既不支持 MD5、又在 http 访问（非安全上下文）下不可用。
        </span>
      </template>
    </el-alert>

    <div class="dg-bar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="text">文本</el-radio-button>
        <el-radio-button value="file">文件</el-radio-button>
      </el-radio-group>

      <span class="dg-label">算法：</span>
      <el-checkbox-group v-model="algos" size="small">
        <el-checkbox-button v-for="a in ALL_ALGOS" :key="a" :value="a">{{ a }}</el-checkbox-button>
      </el-checkbox-group>

      <el-checkbox v-model="upper" size="small">Hex 大写</el-checkbox>
    </div>

    <!-- 文本输入 -->
    <template v-if="mode === 'text'">
      <div class="dg-pane">
        <textarea v-model="text" class="dg-io" placeholder="输入要计算摘要的文本（UTF-8）"></textarea>
      </div>
    </template>

    <!-- 文件输入 -->
    <template v-else>
      <div
        class="dg-drop"
        :class="{ 'dg-drop-active': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
        @click="pickFile"
      >
        <input ref="fileInput" type="file" class="dg-file-input" @change="onPick" />
        <div v-if="!fileInfo" class="dg-drop-empty">
          <b>点击选择文件</b> 或拖拽到此处
          <div class="dg-sub">单个文件不超过 {{ MAX_FILE_MB }} MB</div>
        </div>
        <div v-else class="dg-file-info">
          <div class="dg-file-name">{{ fileInfo.name }}</div>
          <div class="dg-sub">{{ fileInfo.sizeText }} · {{ fileInfo.mime || '未知类型' }}</div>
        </div>
      </div>
      <el-button size="small" style="margin-top:6px" @click="pickFile">选择文件</el-button>
    </template>

    <div class="dg-bar">
      <el-button size="small" type="primary" :loading="loading" :disabled="!canCompute" @click="compute">计算摘要</el-button>
      <el-button size="small" :disabled="!fileB64" @click="clearFile">清除文件</el-button>
      <span class="dg-label" style="margin-left:8px">期望值比对：</span>
      <el-input v-model="expected" size="small" style="width:340px" placeholder="粘贴期望的摘要值（与首个算法结果比对）" />
    </div>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom:8px" />

    <template v-if="results.length">
      <div class="dg-stats">
        <el-tag size="small" type="info">原始 {{ resultSizeText }}</el-tag>
        <el-tag v-if="resultFromFile" size="small" type="warning">文件内容</el-tag>
        <el-tag v-if="verdict" size="small" :type="verdict.ok ? 'success' : 'danger'">{{ verdict.text }}</el-tag>
      </div>

      <el-table :data="results" size="small" border>
        <el-table-column prop="algo" label="算法" width="90" />
        <el-table-column label="Hex">
          <template #default="{ row }">
            <span class="dg-mono">{{ row.hex }}</span>
            <el-button size="small" link type="primary" style="margin-left:6px" @click="copyText(row.hex)">复制</el-button>
          </template>
        </el-table-column>
        <el-table-column label="Base64" width="260">
          <template #default="{ row }">
            <span class="dg-mono dg-ellipsis" :title="row.base64">{{ row.base64 }}</span>
            <el-button size="small" link type="primary" style="margin-left:6px" @click="copyText(row.base64)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { copyText } from '../../utils/clipboard'
import { formatBytes } from '../../utils/filebase64'

const ALL_ALGOS = ['MD5', 'SHA1', 'SHA256', 'SHA384', 'SHA512']
const MAX_FILE_MB = 20
const MAX_FILE = MAX_FILE_MB * 1024 * 1024

const mode = ref('text')
const algos = ref(['MD5'])
const upper = ref(false)
const text = ref('')
const expected = ref('')
const fileInfo = ref(null)
/** 文件内容的 Base64（上传给服务端算摘要） */
const fileB64 = ref('')
const dragOver = ref(false)
const fileInput = ref(null)
const loading = ref(false)
const error = ref('')
const result = ref(null)

const canCompute = computed(() => {
  if (!algos.value.length) return false
  return mode.value === 'file' ? !!fileB64.value : true
})

const results = computed(() => {
  const r = result.value
  if (!r || !r.results) return []
  return Object.keys(r.results).map((algo) => ({
    algo,
    hex: r.results[algo].hex,
    base64: r.results[algo].base64
  }))
})

const resultSizeText = computed(() => formatBytes(result.value ? result.value.size : 0))
const resultFromFile = computed(() => !!(result.value && result.value.fromFile))

/** 与期望值比对（忽略大小写与首尾空白）。 */
const verdict = computed(() => {
  if (!expected.value.trim() || !results.value.length) return null
  const want = expected.value.trim().toLowerCase()
  const first = results.value[0]
  const ok = first.hex.toLowerCase() === want || first.base64.trim() === expected.value.trim()
  return { ok, text: ok ? '与期望值一致 ✓' : '与期望值不一致 ✗' }
})

function pickFile() {
  if (fileInput.value) fileInput.value.click()
}

function onPick(e) {
  const f = e.target.files && e.target.files[0]
  if (f) loadFile(f)
  e.target.value = ''
}

function onDrop(e) {
  dragOver.value = false
  const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  if (f) loadFile(f)
}

async function loadFile(file) {
  if (file.size > MAX_FILE) {
    ElMessage.error('文件超过 ' + MAX_FILE_MB + ' MB')
    return
  }
  try {
    const buf = await file.arrayBuffer()
    let bin = ''
    const bytes = new Uint8Array(buf)
    const chunk = 0x8000
    for (let i = 0; i < bytes.length; i += chunk) {
      bin += String.fromCharCode.apply(null, Array.from(bytes.slice(i, i + chunk)))
    }
    fileB64.value = btoa(bin)
    fileInfo.value = {
      name: file.name,
      size: file.size,
      sizeText: formatBytes(file.size),
      mime: file.type
    }
    ElMessage.success('已读取文件：' + file.name)
  } catch (e) {
    ElMessage.error('读取文件失败：' + (e && e.message ? e.message : e))
  }
}

function clearFile() {
  fileB64.value = ''
  fileInfo.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function compute() {
  error.value = ''
  result.value = null
  if (!algos.value.length) {
    error.value = '请至少选择一种算法'
    return
  }
  loading.value = true
  try {
    const payload = { algos: algos.value, upper: upper.value }
    if (mode.value === 'file') {
      if (!fileB64.value) {
        error.value = '请先选择文件'
        return
      }
      payload.contentB64 = fileB64.value
    } else {
      payload.text = text.value
    }
    const res = await api.tools.digest(payload)
    result.value = res.data
  } catch (e) {
    error.value = '摘要计算失败：' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dg-hint { font-size: 12px; line-height: 1.7; }
.dg-label { font-size: 13px; color: #606266; white-space: nowrap; }
.dg-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 10px 0; }
.dg-sub { color: #909399; font-size: 12px; margin-top: 4px; }
.dg-drop { border: 2px dashed #dcdfe6; border-radius: 6px; background: #fafbfc; padding: 18px;
  text-align: center; cursor: pointer; transition: all .15s; }
.dg-drop:hover, .dg-drop-active { border-color: #409eff; background: #ecf5ff; }
.dg-drop-empty { color: #909399; font-size: 13px; }
.dg-file-info { font-size: 13px; }
.dg-file-name { font-weight: 600; word-break: break-all; }
.dg-file-input { display: none; }
.dg-stats { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0; }
.dg-mono { font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; word-break: break-all; }
.dg-ellipsis { display: inline-block; max-width: 180px; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; vertical-align: bottom; }
</style>
