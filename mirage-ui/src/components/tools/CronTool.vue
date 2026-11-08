<template>
  <div class="cr">
    <div class="cr-bar">
      <span class="cr-label">常用表达式：</span>
      <el-select v-model="preset" size="small" style="width:220px" @change="applyPreset">
        <el-option v-for="p in PRESETS" :key="p.name" :label="p.name" :value="p.name" />
      </el-select>
      <span class="cr-label">表达式（6 位：秒 分 时 日 月 周）：</span>
      <el-input v-model="cron" size="small" style="width:320px" placeholder="0 0/5 * * * ?" @keyup.enter="preview" />
      <el-button size="small" type="primary" :loading="loading" @click="preview">预览触发时间</el-button>
      <el-button size="small" @click="copyText(cron)">复制</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <div class="cr-pane">
      <div class="cr-col">
        <div class="cr-label">字段说明：</div>
        <el-table :data="FIELDS" size="small" border>
          <el-table-column prop="f" label="字段" width="70" />
          <el-table-column prop="v" label="取值" width="90" />
          <el-table-column prop="d" label="说明" />
        </el-table>
      </div>
      <div class="cr-col">
        <div class="cr-label">
          未来 {{ count }} 次触发：
          <el-input-number v-model="count" :min="1" :max="10" size="small" controls-position="right" style="width:110px" />
        </div>
        <div class="cr-io">
          <template v-if="!times.length">
            <span class="cr-sub">点「预览触发时间」查看接下来几次的触发时刻</span>
          </template>
          <div v-for="(t, i) in times" :key="i" class="cr-time">#{{ i + 1 }}　{{ t }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '../../api'
import { copyText } from '../../utils/clipboard'

const PRESETS = [
  { name: '每分钟', cron: '0 * * * * ?' },
  { name: '每 5 分钟', cron: '0 0/5 * * * ?' },
  { name: '每小时整点', cron: '0 0 * * * ?' },
  { name: '每天 02:00', cron: '0 0 2 * * ?' },
  { name: '每天 12:30:00', cron: '0 30 12 * * ?' },
  { name: '每周一 09:00', cron: '0 0 9 ? * MON' },
  { name: '每月 1 号 00:00', cron: '0 0 0 1 * ?' },
  { name: '每天 08:30 工作日', cron: '0 30 8 ? * MON-FRI' }
]

const FIELDS = [
  { f: '秒', v: '0-59', d: '0/15 = 每 15 秒' },
  { f: '分', v: '0-59', d: '0/5 = 每 5 分钟' },
  { f: '时', v: '0-23', d: '2 = 凌晨 2 点' },
  { f: '日', v: '1-31', d: '与「周」互斥，必须写 ?' },
  { f: '月', v: '1-12 或 JAN-DEC', d: '1 = 1 月' },
  { f: '周', v: '1-7 或 MON-SUN', d: '与「日」互斥，必须写 ?' }
]

const preset = ref('')
const cron = ref('0 0/5 * * * ?')
const count = ref(5)
const times = ref([])
const err = ref('')
const loading = ref(false)

function applyPreset(name) {
  const p = PRESETS.find((x) => x.name === name)
  if (p) {
    cron.value = p.cron
    preview()
  }
}

async function preview() {
  err.value = ''
  times.value = []
  if (!cron.value.trim()) {
    err.value = '请输入 cron 表达式'
    return
  }
  loading.value = true
  try {
    const res = await api.schedules.cronPreview(cron.value.trim(), count.value)
    times.value = res.data || []
    if (!times.value.length) err.value = '该表达式没有未来触发时间（可能永远不会触发）'
  } catch (e) {
    err.value = '解析失败：' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cr-label { font-size: 13px; color: #606266; }
.cr-sub { color: #909399; font-size: 12px; }
.cr-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.cr-pane { display: flex; gap: 10px; }
.cr-col { flex: 1; min-width: 0; }
.cr-io { background: #f7f9fb; border: 1px solid #dcdfe6; border-radius: 4px; padding: 10px; min-height: 200px;
  font-family: 'Consolas', monospace; font-size: 13px; overflow: auto; }
.cr-time { padding: 3px 0; }
</style>
