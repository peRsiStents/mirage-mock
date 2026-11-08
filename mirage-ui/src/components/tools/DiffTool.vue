<template>
  <div class="df">
    <div class="df-bar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="text">文本</el-radio-button>
        <el-radio-button value="json">JSON</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="swap">左右互换</el-button>
      <el-button size="small" @click="beautify">格式化 JSON</el-button>
      <el-button size="small" type="primary" @click="run">对比</el-button>
      <el-button size="small" @click="copyText(diffText)">复制差异</el-button>
      <template v-if="stat">
        <el-tag size="small" type="success">新增 {{ stat.added }}</el-tag>
        <el-tag size="small" type="danger">删除 {{ stat.removed }}</el-tag>
        <el-tag size="small" type="info">相同 {{ stat.same }}</el-tag>
        <el-tag v-if="stat.tooLarge" size="small" type="warning">内容过大，已按整块替换显示</el-tag>
      </template>
    </div>

    <div class="df-pane">
      <div class="df-col">
        <div class="df-label">左侧（旧）：</div>
        <textarea v-model="left" class="df-io" placeholder="粘贴旧版本响应"></textarea>
      </div>
      <div class="df-col">
        <div class="df-label">右侧（新）：</div>
        <textarea v-model="right" class="df-io" placeholder="粘贴新版本响应"></textarea>
      </div>
    </div>

    <el-table v-if="rows.length" :data="rows" size="small" border max-height="460" class="df-table">
      <el-table-column label="" width="46">
        <template #default="{ row }">
          <span :class="['df-tag', row.type]">{{ row.type === 'add' ? '+' : row.type === 'del' ? '-' : ' ' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="left" label="左行号" width="80" />
      <el-table-column prop="right" label="右行号" width="80" />
      <el-table-column label="内容" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="['df-text', row.type]">{{ row.text }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { diffLines } from '../../utils/diff'
import { copyText } from '../../utils/clipboard'

const mode = ref('text')
const left = ref('')
const right = ref('')

const result = ref(null)
const rows = computed(() => (result.value ? result.value.rows : []))
const stat = computed(() => (result.value ? { added: result.value.added, removed: result.value.removed, same: result.value.same, tooLarge: result.value.tooLarge } : null))

const diffText = computed(() => {
  if (!result.value) return ''
  return result.value.rows
    .filter((r) => r.type !== 'same')
    .map((r) => (r.type === 'add' ? '+ ' : '- ') + r.text)
    .join('\n')
})

function run() {
  result.value = diffLines(left.value, right.value)
}

function swap() {
  const t = left.value
  left.value = right.value
  right.value = t
  run()
}

/** 两边都按 JSON 美化（能美化才换，否则保持原样）。 */
function beautify() {
  for (const side of ['left', 'right']) {
    const raw = side === 'left' ? left.value : right.value
    const t = (raw || '').trim()
    if (!t) continue
    try {
      const v = JSON.stringify(JSON.parse(t), null, 2)
      if (side === 'left') left.value = v
      else right.value = v
    } catch (e) {
      ElMessage.warning('有一侧不是合法 JSON，未做格式化')
      break
    }
  }
  run()
}
</script>

<style scoped>
.df-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
.df-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.df-pane { display: flex; gap: 8px; }
.df-col { flex: 1; min-width: 0; }
.df-io { width: 100%; min-height: 200px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none; }
.df-io:focus { border-color: #409eff; }
.df-table { margin-top: 10px; }
.df-tag { display: inline-block; width: 18px; text-align: center; font-family: monospace; font-weight: bold; }
.df-tag.add { color: #67c23a; }
.df-tag.del { color: #f56c6c; }
.df-text { font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.df-text.add { background: #f0f9eb; color: #67c23a; }
.df-text.del { background: #fef0f0; color: #f56c6c; }
</style>
