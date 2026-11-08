<template>
  <div class="ul">
    <div class="ul-bar">
      <el-input v-model="url" size="small" style="width:620px" placeholder="粘贴 URL，如 https://api.example.com/v1/order?id=1&name=a%20b" @keyup.enter="parse" />
      <el-button size="small" type="primary" @click="parse">解析</el-button>
      <el-button size="small" @click="fillSample">示例</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <template v-if="info">
      <el-descriptions :column="3" border size="small" style="margin-bottom:10px">
        <el-descriptions-item label="协议">{{ info.protocol || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主机">{{ info.hostname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="端口">{{ info.port || '（默认）' }}</el-descriptions-item>
        <el-descriptions-item label="路径">{{ info.pathname || '/' }}</el-descriptions-item>
        <el-descriptions-item label="参数串">{{ info.search || '（无）' }}</el-descriptions-item>
        <el-descriptions-item label="锚点">{{ info.hash || '（无）' }}</el-descriptions-item>
      </el-descriptions>

      <div class="ul-pane">
        <div class="ul-col">
          <div class="ul-label">查询参数（{{ info.params.length }} 个）</div>
          <div class="ul-io ul-out">
            <el-table v-if="info.params.length" :data="info.params" size="small" border>
              <el-table-column prop="key" label="参数名" width="160" />
              <el-table-column prop="value" label="值（已解码）" show-overflow-tooltip />
            </el-table>
            <span v-else class="ul-sub">这条 URL 没有查询参数</span>
          </div>
        </div>
        <div class="ul-col">
          <div class="ul-label">参数 ⇄ JSON</div>
          <div class="ul-swap">
            <el-input v-model="queryText" size="small" placeholder="a=1&b=2" />
            <el-button size="small" @click="q2j">→ JSON</el-button>
            <el-button size="small" @click="j2q">→ 参数串</el-button>
          </div>
          <textarea v-model="jsonText" class="ul-io ul-out ul-json" placeholder='{"a":"1","b":"2"}'></textarea>
          <div class="ul-bar" style="margin-top:8px">
            <span class="ul-label">拼装 URL</span>
            <el-input v-model="base" size="small" style="width:200px" placeholder="https://api.example.com" />
            <el-input v-model="path" size="small" style="width:140px" placeholder="/v1/order" />
            <el-button size="small" type="primary" @click="build">拼装</el-button>
          </div>
          <div class="ul-built">{{ built || '（填 base + path 后点「拼装」）' }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { parseUrl, queryToJson, jsonToQuery, buildUrl } from '../../utils/urltool'

const url = ref('')
const info = ref(null)
const err = ref('')
const queryText = ref('')
const jsonText = ref('')
const base = ref('')
const path = ref('')
const built = ref('')

function parse() {
  err.value = ''
  built.value = ''
  const r = parseUrl(url.value)
  if (!r.ok) {
    err.value = r.error || 'URL 解析失败'
    info.value = null
    return
  }
  info.value = r
  queryText.value = r.search.replace(/^\?/, '')
  jsonText.value = JSON.stringify(queryToJson(queryText.value), null, 2)
  // base/path 预填，便于直接改
  base.value = r.protocol ? `${r.protocol}://${r.host}` : ''
  path.value = r.pathname || ''
}

function q2j() {
  try {
    jsonText.value = JSON.stringify(queryToJson(queryText.value), null, 2)
  } catch (e) {
    err.value = '参数串解析失败：' + e.message
  }
}

function j2q() {
  try {
    const obj = JSON.parse(jsonText.value || '{}')
    queryText.value = jsonToQuery(obj)
  } catch (e) {
    err.value = 'JSON 解析失败：' + e.message
  }
}

function build() {
  err.value = ''
  let params = {}
  if (jsonText.value.trim()) {
    try {
      params = JSON.parse(jsonText.value)
    } catch (e) {
      err.value = 'JSON 解析失败：' + e.message
      return
    }
  }
  built.value = buildUrl(base.value, path.value, params)
}

function fillSample() {
  // %E8%9C%83%E6%A5%BC = 「蜃楼」的 UTF-8 百分号编码
  url.value = 'https://api.example.com/v1/order?id=1001&name=%E8%9C%83%E6%A5%BC&tag=a&tag=b'
  parse()
}
</script>

<style scoped>
.ul-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
.ul-sub { font-size: 12px; color: #909399; }
.ul-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.ul-pane { display: flex; gap: 10px; }
.ul-col { flex: 1; min-width: 0; }
.ul-swap { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.ul-io { width: 100%; padding: 8px; border: 1px solid #dcdfe6; border-radius: 4px; outline: none;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; }
.ul-out { background: #f7f9fb; min-height: 150px; overflow: auto; }
.ul-json { min-height: 150px; resize: vertical; white-space: pre-wrap; }
.ul-built { font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; word-break: break-all;
  background: #f5f7fa; padding: 8px; border-radius: 4px; }
</style>
