<template>
  <div class="fb64">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="fb64-hint">
          文件 / 图片 ⇄ Base64 双向转换。<b>全程在浏览器本地完成</b>，文件不会上传到服务器。
          支持拖拽选择；Base64 可带 <code>data:image/png;base64,</code> 前缀，也支持 URL-safe 变体与缺失填充。
        </span>
      </template>
    </el-alert>

    <el-radio-group v-model="mode" size="small" style="margin-bottom:10px">
      <el-radio-button value="toB64">文件 / 图片 → Base64</el-radio-button>
      <el-radio-button value="toFile">Base64 → 文件 / 图片</el-radio-button>
    </el-radio-group>

    <!-- ==================== 文件 → Base64 ==================== -->
    <template v-if="mode === 'toB64'">
      <div
        class="fb64-drop"
        :class="{ 'fb64-drop-active': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
        @click="pickFile"
      >
        <input ref="fileInput" type="file" class="fb64-file-input" @change="onPick" />
        <div v-if="!srcFile" class="fb64-drop-empty">
          <b>点击选择文件</b> 或拖拽到此处
          <div class="fb64-drop-sub">单文件不超过 {{ MAX_FILE_MB }} MB</div>
        </div>
        <div v-else class="fb64-file-info">
          <div class="fb64-file-name">{{ srcFile.name }}</div>
          <div class="fb64-drop-sub">
            {{ formatBytes(srcFile.size) }} · {{ srcFile.type || '未知类型' }}
          </div>
        </div>
      </div>

      <div class="fb64-bar">
        <span class="fb64-label">输出：</span>
        <el-radio-group v-model="outFormat" size="small">
          <el-radio-button value="b64">纯 Base64</el-radio-button>
          <el-radio-button value="dataurl">Data URL</el-radio-button>
        </el-radio-group>
        <span class="fb64-label">换行：</span>
        <el-select v-model="wrapCols" size="small" style="width:110px">
          <el-option label="不换行" :value="0" />
          <el-option label="每 64 字符" :value="64" />
          <el-option label="每 76 字符" :value="76" />
          <el-option label="每 120 字符" :value="120" />
        </el-select>
        <el-button size="small" @click="pickFile">选择文件</el-button>
        <el-button size="small" :disabled="!srcB64" @click="copyB64">复制 Base64</el-button>
        <el-button size="small" :disabled="!srcB64" @click="downloadB64Text">下载 .txt</el-button>
        <el-button size="small" :disabled="!srcFile" @click="clearAll">清空</el-button>
      </div>

      <div v-if="srcInfo" class="fb64-stats">
        <el-tag size="small" type="info">原始 {{ formatBytes(srcInfo.size) }}</el-tag>
        <el-tag size="small" type="info">Base64 {{ formatBytes(srcInfo.b64Len) }}</el-tag>
        <el-tag size="small" type="info">膨胀 {{ srcInfo.ratio }}%</el-tag>
        <el-tag v-if="srcInfo.mime" size="small" type="success">{{ srcInfo.mime }}</el-tag>
      </div>

      <div class="fb64-pane">
        <textarea v-model="srcB64" class="fb64-io" readonly placeholder="转换结果（Base64）"></textarea>
      </div>

      <div v-if="previewUrl" class="fb64-preview">
        <div class="fb64-label">图片预览：</div>
        <img :src="previewUrl" class="fb64-preview-img" alt="预览" />
      </div>
    </template>

    <!-- ==================== Base64 → 文件 ==================== -->
    <template v-else>
      <div class="fb64-pane">
        <textarea v-model="b64In" class="fb64-io" placeholder="粘贴 Base64 字符串（可含 data:image/png;base64, 前缀、换行、空格）"></textarea>
      </div>

      <div class="fb64-bar">
        <span class="fb64-label">文件名：</span>
        <el-input v-model="outName" size="small" style="width:220px" placeholder="如 photo.png" />
        <span class="fb64-label">类型：</span>
        <el-input v-model="outMime" size="small" style="width:200px" placeholder="自动识别，可手改（改后需重新解码）" />
        <el-button size="small" type="primary" @click="decodeB64">解码</el-button>
        <el-button size="small" :disabled="!decodedBlob" @click="downloadBlob">下载文件</el-button>
        <el-button size="small" :disabled="!decodedBlob" @click="copyHex">复制 Hex</el-button>
        <el-button size="small" @click="clearAll">清空</el-button>
      </div>

      <div v-if="decInfo" class="fb64-stats">
        <el-tag size="small" type="success">识别类型 {{ decInfo.mime }}</el-tag>
        <el-tag size="small" type="info">解码后 {{ formatBytes(decInfo.size) }}</el-tag>
        <el-tag size="small" type="info">Base64 {{ formatBytes(decInfo.b64Len) }}</el-tag>
        <el-tag size="small" type="info">{{ decInfo.isImage ? '图片文件' : '二进制文件' }}</el-tag>
      </div>

      <el-alert v-if="decError" type="error" :closable="false" show-icon :title="decError" style="margin-top:8px" />

      <div v-if="decodedBlob && decInfo && decInfo.isImage" class="fb64-preview">
        <div class="fb64-label">图片预览：</div>
        <img :src="blobUrl" class="fb64-preview-img" alt="预览" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '../utils/clipboard'
import { normalizeB64, guessMime, guessMimeByName, wrap, formatBytes, bytesToHex } from '../utils/filebase64'

/** 单文件上限（Base64 会膨胀 1/3，再大浏览器容易卡） */
const MAX_FILE_MB = 20
const MAX_FILE = MAX_FILE_MB * 1024 * 1024

const mode = ref('toB64')

// ---------------- 文件 → Base64 ----------------
const fileInput = ref(null)
const srcFile = ref(null)
/** 纯 Base64（不含前缀、不换行），换行/格式由输出选项决定 */
const srcRaw = ref('')
/** FileReader 给出的原始 Data URL（前缀与浏览器实际类型一致） */
const srcDataUrl = ref('')
const srcInfo = ref(null)
const dragOver = ref(false)
const previewUrl = ref('')
const outFormat = ref('b64')
const wrapCols = ref(0)

/**
 * 输出文本 = 按当前「输出格式 / 换行」选项实时拼。
 * 做成 computed 是为了切换选项时立刻生效——否则用户转完再改换行宽度就没反应。
 */
const srcB64 = computed(() => {
  const b64 = srcRaw.value || ''
  if (!b64) return ''
  if (outFormat.value === 'dataurl') {
    const comma = srcDataUrl.value.indexOf(',')
    const prefix = comma >= 0 ? srcDataUrl.value.slice(0, comma + 1) : 'data:application/octet-stream;base64,'
    return wrapCols.value > 0 ? wrap(prefix + b64, wrapCols.value) : prefix + b64
  }
  return wrapCols.value > 0 ? wrap(b64, wrapCols.value) : b64
})

function pickFile() {
  if (fileInput.value) fileInput.value.click()
}

function onPick(e) {
  const f = e.target.files && e.target.files[0]
  if (f) loadFile(f)
  // 允许重复选择同一个文件
  e.target.value = ''
}

function onDrop(e) {
  dragOver.value = false
  const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  if (f) loadFile(f)
}

async function loadFile(file) {
  if (file.size > MAX_FILE) {
    ElMessage.error('文件超过 ' + MAX_FILE_MB + ' MB，请换一个更小的文件')
    return
  }
  srcFile.value = file
  try {
    const dataUrl = await readAsDataUrl(file)
    const comma = dataUrl.indexOf(',')
    const b64 = comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl
    srcRaw.value = b64
    srcDataUrl.value = dataUrl
    srcInfo.value = { size: file.size, b64Len: b64.length, ratio: ratio(b64.length, file.size), mime: file.type || guessMimeByName(file.name) }
    revoke(previewUrl.value)
    previewUrl.value = isImage(file.type) ? URL.createObjectURL(file) : ''
    ElMessage.success('已转换：' + file.name)
  } catch (e) {
    ElMessage.error('读取文件失败：' + (e && e.message ? e.message : e))
  }
}

function ratio(b64Len, size) {
  if (!size) return '0'
  return Math.round((b64Len / size) * 100)
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const fr = new FileReader()
    fr.onload = () => resolve(String(fr.result || ''))
    fr.onerror = () => reject(fr.error || new Error('FileReader 失败'))
    fr.readAsDataURL(file)
  })
}

async function copyB64() {
  await copyText(srcB64.value)
}

function downloadB64Text() {
  const name = (srcFile.value ? srcFile.value.name : 'base64') + '.base64.txt'
  downloadBlob(new Blob([srcB64.value], { type: 'text/plain;charset=utf-8' }), name)
}

// ---------------- Base64 → 文件 ----------------
const b64In = ref('')
const outName = ref('')
const outMime = ref('')
const decodedBlob = ref(null)
const decInfo = ref(null)
const decError = ref('')
const blobUrl = ref('')

function decodeB64() {
  decError.value = ''
  decodedBlob.value = null
  decInfo.value = null
  revoke(blobUrl.value)
  blobUrl.value = ''

  const raw = (b64In.value || '').trim()
  if (!raw) {
    decError.value = '请输入 Base64 内容'
    return
  }
  let bytes
  let mimeFromUrl = null
  try {
    const norm = normalizeB64(raw)
    mimeFromUrl = norm.mime
    bytes = norm.bytes
  } catch (e) {
    decError.value = 'Base64 解码失败：' + (e && e.message ? e.message : e)
    return
  }
  const mime = outMime.value.trim() || mimeFromUrl || guessMime(bytes) || 'application/octet-stream'
  const blob = new Blob([bytes], { type: mime })
  decodedBlob.value = blob
  blobUrl.value = URL.createObjectURL(blob)
  decInfo.value = {
    mime,
    size: bytes.length,
    b64Len: raw.length,
    isImage: mime.startsWith('image/')
  }
  if (!outName.value.trim()) outName.value = defaultName(mime)
  ElMessage.success('解码完成：' + bytes.length + ' 字节')
}

/** 没有文件名时按 MIME 给个默认名。 */
function defaultName(mime) {
  const ext = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp', 'image/svg+xml': 'svg', 'image/bmp': 'bmp', 'application/pdf': 'pdf', 'application/zip': 'zip' }[mime]
  return ext ? 'decoded.' + ext : 'decoded.bin'
}

/** 是否是图片类型（决定要不要显示预览）。 */
function isImage(mime) {
  return !!mime && mime.startsWith('image/')
}

async function copyHex() {
  if (!decodedBlob.value) return
  const buf = await decodedBlob.value.arrayBuffer()
  const bytes = new Uint8Array(buf)
  await copyText(bytesToHex(bytes))
}

function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name || 'decoded.bin'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 交给浏览器把下载启动后再回收
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

function revoke(url) {
  if (url) URL.revokeObjectURL(url)
}

function clearAll() {
  srcFile.value = null
  srcRaw.value = ''
  srcDataUrl.value = ''
  srcInfo.value = null
  revoke(previewUrl.value)
  previewUrl.value = ''
  b64In.value = ''
  decodedBlob.value = null
  decInfo.value = null
  decError.value = ''
  revoke(blobUrl.value)
  blobUrl.value = ''
  outName.value = ''
  outMime.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

onBeforeUnmount(() => {
  revoke(previewUrl.value)
  revoke(blobUrl.value)
})
</script>

<style scoped>
.fb64-hint { font-size: 12px; line-height: 1.7; }
.fb64-label { font-size: 13px; color: #606266; white-space: nowrap; }
.fb64-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 10px 0; }
.fb64-drop { border: 2px dashed #dcdfe6; border-radius: 6px; background: #fafbfc; padding: 18px;
  text-align: center; cursor: pointer; transition: all .15s; }
.fb64-drop:hover, .fb64-drop-active { border-color: #409eff; background: #ecf5ff; }
.fb64-drop-empty { color: #909399; font-size: 13px; }
.fb64-drop-sub { color: #909399; font-size: 12px; margin-top: 4px; }
.fb64-file-info { font-size: 13px; }
.fb64-file-name { font-weight: 600; word-break: break-all; }
.fb64-file-input { display: none; }
.fb64-stats { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0; }
.fb64-pane { display: flex; }
.fb64-io { flex: 1; min-height: 220px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none; width: 100%;
  word-break: break-all; }
.fb64-io:focus { border-color: #409eff; }
.fb64-preview { margin-top: 10px; }
.fb64-preview-img { max-width: 100%; max-height: 320px; border: 1px solid #ebeef5; border-radius: 4px; background: #fff; }
</style>
