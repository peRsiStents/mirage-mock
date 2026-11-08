<template>
  <div class="jp">
    <div class="jp-pane">
      <div class="jp-col">
        <div class="jp-label">JSON：</div>
        <textarea v-model="json" class="jp-io" placeholder='{"data":{"items":[{"id":1},{"id":2}]}}'></textarea>
      </div>
      <div class="jp-col">
        <div class="jp-label">
          JSONPath：
          <el-input v-model="path" class="jp-path" size="small" style="width:280px" placeholder="如 $.data.items[*].id" @keyup.enter="evalPath" />
          <el-button size="small" type="primary" style="margin-left:6px" :loading="loading" @click="evalPath">求值</el-button>
        </div>
        <div class="jp-bar">
          <span class="jp-label">常用路径：</span>
          <el-button v-for="ex in EXAMPLES" :key="ex" size="small" link type="primary" @click="path = ex">{{ ex }}</el-button>
        </div>
        <div class="jp-io jp-out">
          <template v-if="!res">
            <span class="jp-sub">贴一段 JSON、填一个路径，点「求值」</span>
          </template>
          <template v-else>
            <div class="jp-result-head">
              <el-tag size="small" :type="res.found ? 'success' : 'danger'">{{ res.found ? '命中' : '未命中' }}</el-tag>
              <el-tag v-if="res.found" size="small" type="info">类型 {{ res.type }}</el-tag>
              <el-tag v-if="res.matchCount > 1" size="small" type="warning">{{ res.matchCount }} 处匹配</el-tag>
            </div>
            <pre class="jp-value">{{ res.valueText || '(空)' }}</pre>
            <div v-if="res.matchCount > 1" class="jp-matches">
              <div v-for="(m, i) in res.matches" :key="i" class="jp-match">#{{ i + 1 }} {{ displayMatch(m) }}</div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-top:8px" />
    <div v-if="res && res.found" class="jp-bar">
      <el-button size="small" @click="copyText(res.valueText)">复制取值</el-button>
      <el-button size="small" @click="copyText(res.matches.map(displayMatch).join('\n'))">复制全部匹配</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { copyText } from '../../utils/clipboard'

const EXAMPLES = ['$.data', '$.data.items', '$.data.items[0]', '$.data.items[*].id', '$..id', '$.data.items.length()']

const json = ref('')
const path = ref('')
const res = ref(null)
const err = ref('')
const loading = ref(false)

async function evalPath() {
  err.value = ''
  res.value = null
  if (!json.value.trim()) {
    err.value = '请输入 JSON'
    return
  }
  if (!path.value.trim()) {
    err.value = '请输入 JSONPath'
    return
  }
  loading.value = true
  try {
    const r = await api.tools.jsonPath({ json: json.value, path: path.value })
    res.value = r.data
  } catch (e) {
    err.value = '求值失败：' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}

function displayMatch(m) {
  if (m === null || m === undefined) return '(null)'
  if (typeof m === 'object') {
    try {
      return JSON.stringify(m)
    } catch (e) {
      return String(m)
    }
  }
  return String(m)
}
</script>

<style scoped>
.jp-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
.jp-sub { color: #909399; font-size: 12px; }
.jp-bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin: 6px 0; }
.jp-pane { display: flex; gap: 8px; }
.jp-col { flex: 1; min-width: 0; }
.jp-io { width: 100%; min-height: 240px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none; }
.jp-io:focus { border-color: #409eff; }
.jp-out { background: #f7f9fb; overflow: auto; }
.jp-result-head { display: flex; gap: 6px; margin-bottom: 6px; }
.jp-value { background: #fff; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; margin: 0;
  font-family: 'Consolas', monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.jp-matches { margin-top: 6px; }
.jp-match { font-family: 'Consolas', monospace; font-size: 12px; padding: 2px 0; word-break: break-all; }
</style>
