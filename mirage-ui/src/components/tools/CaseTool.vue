<template>
  <div class="nc">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="nc-hint">
          字段命名风格整体转换：后端给的是 snake_case、前端要 camelCase（或反过来）时，
          一次把 JSON 的所有键换掉，值保持不变。也可以只转一批标识符（每行一个）。
          纯前端计算，不上传。
        </span>
      </template>
    </el-alert>

    <div class="nc-bar">
      <span class="nc-label">输入类型：</span>
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="json">JSON</el-radio-button>
        <el-radio-button value="text">标识符列表</el-radio-button>
      </el-radio-group>

      <span class="nc-label">目标风格：</span>
      <el-select v-model="style" size="small" style="width:220px">
        <el-option v-for="s in STYLES" :key="s.key" :label="s.name" :value="s.key">
          <span>{{ s.name }}</span>
          <span style="float:right;color:#909399;font-size:12px">{{ s.sample }}</span>
        </el-option>
      </el-select>

      <template v-if="mode === 'text'">
        <span class="nc-label">分隔：</span>
        <el-select v-model="sep" size="small" style="width:100px">
          <el-option label="每行" value="\n" />
          <el-option label="逗号" value="," />
        </el-select>
      </template>

      <el-button size="small" type="primary" @click="convert">转换</el-button>
      <el-button size="small" @click="fillSample">示例</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <div class="nc-pane">
      <div class="nc-col">
        <div class="nc-label">{{ mode === 'json' ? 'JSON：' : '标识符（每行一个）：' }}</div>
        <textarea v-model="input" class="nc-io" :placeholder="mode === 'json' ? '{\n  &quot;user_name&quot;: &quot;蜃楼&quot;,\n  &quot;order_list&quot;: [1, 2]\n}' : 'user_name\norderList\nID_CARD'"></textarea>
      </div>
      <div class="nc-col">
        <div class="nc-label">结果：</div>
        <textarea :value="output" class="nc-io nc-out" readonly placeholder="转换结果"></textarea>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { STYLES, convertKeys, convertText } from '../../utils/namecase'

const mode = ref('json')
const style = ref('camel')
const sep = ref('\n')
const input = ref('')
const output = ref('')
const err = ref('')

function convert() {
  err.value = ''
  if (!input.value.trim()) {
    err.value = '输入不能为空'
    return
  }
  try {
    if (mode.value === 'json') {
      let parsed
      try {
        parsed = JSON.parse(input.value)
      } catch (e) {
        throw new Error('JSON 解析失败：' + e.message)
      }
      output.value = JSON.stringify(convertKeys(parsed, style.value), null, 2)
    } else {
      output.value = convertText(input.value, style.value, sep.value === ',' ? ',' : '\n')
    }
  } catch (e) {
    err.value = e && e.message ? e.message : String(e)
    output.value = ''
  }
}

function fillSample() {
  mode.value = 'json'
  style.value = 'camel'
  input.value = '{\n  "user_name": "蜃楼",\n  "order_list": [{ "item_id": 1, "ITEM_NAME": "A" }]\n}'
  output.value = ''
  err.value = ''
}
</script>

<style scoped>
.nc-hint { font-size: 12px; line-height: 1.7; }
.nc-label { font-size: 13px; color: #606266; white-space: nowrap; }
.nc-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.nc-pane { display: flex; gap: 8px; }
.nc-col { flex: 1; min-width: 0; }
.nc-io { width: 100%; min-height: 240px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none;
  white-space: pre-wrap; word-break: break-all; }
.nc-io:focus { border-color: #409eff; }
.nc-out { background: #f7f9fb; }
</style>
