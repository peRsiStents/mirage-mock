<template>
  <div class="cv">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="cv-hint">
          CSV ⇄ JSON：把 Excel 里的表格直接转成接口要用的 JSON 数组，或把接口返回的数组转成 CSV 贴回 Excel。
          按 RFC 4180 解析：双引号里可以带逗号和换行，双引号用两个双引号表示。纯前端计算，不上传。
        </span>
      </template>
    </el-alert>

    <div class="cv-bar">
      <el-radio-group v-model="dir" size="small">
        <el-radio-button value="toJson">CSV → JSON</el-radio-button>
        <el-radio-button value="toCsv">JSON → CSV</el-radio-button>
      </el-radio-group>

      <span class="cv-label">分隔符：</span>
      <el-select v-model="delimiter" size="small" style="width:110px">
        <el-option label="逗号 ," value="," />
        <el-option label="制表符 Tab" value="\t" />
        <el-option label="分号 ;" value=";" />
        <el-option label="竖线 |" value="|" />
      </el-select>

      <template v-if="dir === 'toJson'">
        <el-checkbox v-model="hasHeader" size="small">首行是表头</el-checkbox>
        <el-checkbox v-model="typed" size="small">数字/布尔自动识别</el-checkbox>
      </template>

      <el-button size="small" type="primary" @click="convert">转换</el-button>
      <el-button size="small" @click="swap">⇄ 输出 → 输入</el-button>
      <el-button size="small" @click="fillSample">示例</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <div class="cv-pane">
      <div class="cv-col">
        <div class="cv-label">{{ dir === 'toJson' ? 'CSV 文本：' : 'JSON 文本：' }}</div>
        <textarea v-model="input" class="cv-io" :placeholder="dir === 'toJson' ? csvPlaceholder : jsonPlaceholder"></textarea>
      </div>
      <div class="cv-col">
        <div class="cv-label">
          结果：
          <span v-if="stat" class="cv-sub">{{ stat }}</span>
        </div>
        <textarea :value="output" class="cv-io cv-out" readonly placeholder="转换结果"></textarea>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { csvToJson, jsonToCsv } from '../../utils/csv'

const dir = ref('toJson')
const delimiter = ref(',')
const hasHeader = ref(true)
const typed = ref(false)
const input = ref('')
const output = ref('')
const stat = ref('')
const err = ref('')

// 占位文案放脚本里：模板属性里直接写 JSON 会和双引号打架
const csvPlaceholder = 'id,name,amount\n1,蜃楼,10.5'
const jsonPlaceholder = '[{"id":1,"name":"蜃楼"}]'

function convert() {
  err.value = ''
  stat.value = ''
  if (!input.value.trim()) {
    err.value = '输入不能为空'
    return
  }
  try {
    if (dir.value === 'toJson') {
      const arr = csvToJson(input.value, {
        delimiter: delimiter.value,
        hasHeader: hasHeader.value,
        typed: typed.value
      })
      output.value = JSON.stringify(arr, null, 2)
      stat.value = `${arr.length} 条记录`
    } else {
      let parsed
      try {
        parsed = JSON.parse(input.value)
      } catch (e) {
        throw new Error('JSON 解析失败：' + e.message)
      }
      const csv = jsonToCsv(parsed, { delimiter: delimiter.value })
      output.value = csv
      const rows = Array.isArray(parsed) ? parsed.length : 1
      stat.value = `${rows} 行数据`
    }
  } catch (e) {
    err.value = e && e.message ? e.message : String(e)
    output.value = ''
  }
}

function swap() {
  input.value = output.value
  output.value = ''
  stat.value = ''
  dir.value = dir.value === 'toJson' ? 'toCsv' : 'toJson'
}

function fillSample() {
  dir.value = 'toJson'
  delimiter.value = ','
  hasHeader.value = true
  typed.value = false
  input.value = 'id,name,amount,note\n1,蜃楼,10.50,"含,逗号和""引号"\n2,Mock,3,普通'
  output.value = ''
  stat.value = ''
}
</script>

<style scoped>
.cv-hint { font-size: 12px; line-height: 1.7; }
.cv-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
.cv-sub { font-size: 12px; color: #909399; }
.cv-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.cv-pane { display: flex; gap: 8px; }
.cv-col { flex: 1; min-width: 0; }
.cv-io { width: 100%; min-height: 240px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none;
  white-space: pre-wrap; word-break: break-all; }
.cv-io:focus { border-color: #409eff; }
.cv-out { background: #f7f9fb; }
</style>
