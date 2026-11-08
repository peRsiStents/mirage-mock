<template>
  <div class="rx">
    <div class="rx-bar">
      <span class="rx-label">正则：</span>
      <el-input v-model="pattern" size="small" class="rx-pattern" placeholder="如 (?<id>\d{6})-(\w+)" />
      <el-checkbox-group v-model="flags" size="small">
        <!-- g 恒定开启（否则只能匹配第一处），做成禁用免得与实际行为不一致 -->
        <el-checkbox-button value="g" disabled>g</el-checkbox-button>
        <el-checkbox-button value="i">i</el-checkbox-button>
        <el-checkbox-button value="m">m</el-checkbox-button>
        <el-checkbox-button value="s">s</el-checkbox-button>
      </el-checkbox-group>
    </div>

    <div class="rx-bar">
      <span class="rx-label">常用：</span>
      <el-button v-for="s in PRESETS" :key="s.name" size="small" link type="primary" @click="usePreset(s)">{{ s.name }}</el-button>
      <el-button size="small" link @click="pattern = ''; flags = ['g']">清空</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <div class="rx-pane">
      <div class="rx-col">
        <div class="rx-label">测试文本（{{ text.length }} 字符）：</div>
        <textarea v-model="text" class="rx-io" placeholder="粘贴待匹配的报文"></textarea>
      </div>
      <div class="rx-col">
        <div class="rx-label">
          匹配结果：{{ matches.length }} 处
          <span v-if="matchCount > 0" class="rx-sub">（共 {{ matchCount }} 处）</span>
        </div>
        <div class="rx-io rx-highlight">
          <template v-if="!pattern">
            <span class="rx-sub">输入正则后这里会高亮命中片段</span>
          </template>
          <template v-else-if="!segments.length">
            <span class="rx-sub">没有匹配</span>
          </template>
          <template v-else>
            <span
              v-for="(s, i) in segments"
              :key="i"
              :class="s.mark ? 'rx-mark' : 'rx-txt'"
            >{{ s.text }}</span>
          </template>
        </div>
      </div>
    </div>

    <el-collapse v-if="matches.length" style="margin-top:10px">
      <el-collapse-item title="匹配明细（分组）" name="m">
        <el-table :data="matches" size="small" border max-height="260">
          <el-table-column prop="index" label="#" width="50" />
          <el-table-column prop="text" label="命中" show-overflow-tooltip />
          <el-table-column prop="pos" label="位置" width="110" />
          <el-table-column label="分组" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-for="g in row.groups" :key="g.idx" class="rx-group">
                {{ g.idx === 0 ? '整体' : (g.name || '$' + g.idx) }}={{ g.value }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>

      <el-collapse-item title="替换预览" name="r">
        <div class="rx-bar">
          <span class="rx-label">替换为：</span>
          <el-input v-model="replacement" size="small" style="width:360px" placeholder="可用 $1 $2 引用分组" />
        </div>
        <pre class="rx-out">{{ replaced }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const PRESETS = [
  { name: '手机号', p: '1[3-9]\\d{9}' },
  { name: '身份证', p: '\\d{17}[\\dXx]' },
  { name: '邮箱', p: '[\\w.+-]+@[\\w-]+\\.[\\w.]+' },
  { name: '日期', p: '\\d{4}-\\d{2}-\\d{2}' },
  { name: '金额', p: '\\d+\\.\\d{2}' },
  { name: '数字', p: '\\d+' },
  { name: 'UUID', p: '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' },
  { name: '键值对', p: '"(\\w+)"\\s*:\\s*("[^"]*"|\\d+|true|false|null)' }
]

const pattern = ref('')
const flags = ref(['g'])
const text = ref('')
const replacement = ref('')

const err = computed(() => {
  if (!pattern.value) return ''
  try {
    new RegExp(pattern.value, flagStr.value)
    return ''
  } catch (e) {
    return '正则非法：' + e.message
  }
})

const flagStr = computed(() => {
  // g 必须有，否则只能匹配第一处
  const f = new Set(flags.value)
  f.add('g')
  return Array.from(f).join('')
})

/** 匹配结果：命中列表 + 高亮片段 */
const parsed = computed(() => {
  if (!pattern.value || err.value) return { matches: [], segments: [], count: 0 }
  let re
  try {
    re = new RegExp(pattern.value, flagStr.value)
  } catch (e) {
    return { matches: [], segments: [], count: 0 }
  }
  const matches = []
  const segments = []
  let last = 0
  let count = 0
  // 扫描上限：超大报文逐字匹配会把页面卡死，只扫前 20 万字符
  const src = (text.value || '').slice(0, 200000)
  let m
  const re2 = new RegExp(pattern.value, flagStr.value)
  while ((m = re2.exec(src)) !== null) {
    count++
    if (m[0] === '') {
      // 空匹配：正则零宽匹配，防止死循环
      re2.lastIndex++
      continue
    }
    if (m.index > last) segments.push({ mark: false, text: src.slice(last, m.index) })
    segments.push({ mark: true, text: m[0] })
    last = m.index + m[0].length
    const groups = []
    // 具名分组：JS 不暴露「名字 → 序号」映射，用分组值反查名字（同名值可能歧义，仅作展示）
    const named = {}
    if (m.groups) {
      for (const k of Object.keys(m.groups)) named[m.groups[k]] = k
    }
    m.forEach((g, idx) => {
      groups.push({
        idx,
        name: g === undefined ? '' : (named[g] || ''),
        value: g === undefined ? '(未参与匹配)' : g
      })
    })
    matches.push({
      index: matches.length,
      text: m[0],
      pos: m.index + '–' + (m.index + m[0].length),
      groups
    })
    if (matches.length > 500) break
  }
  if (last < src.length) segments.push({ mark: false, text: src.slice(last) })
  return { matches, segments, count }
})

const matches = computed(() => parsed.value.matches)
const segments = computed(() => parsed.value.segments)
const matchCount = computed(() => parsed.value.count)

const replaced = computed(() => {
  if (!pattern.value || err.value) return ''
  try {
    return (text.value || '').replace(new RegExp(pattern.value, flagStr.value), replacement.value)
  } catch (e) {
    return ''
  }
})

function usePreset(s) {
  pattern.value = s.p
  if (!flags.value.includes('g')) flags.value.push('g')
}
</script>

<style scoped>
.rx-label { font-size: 13px; color: #606266; }
.rx-sub { color: #909399; font-size: 12px; }
.rx-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.rx-pattern { width: 420px; }
.rx-pane { display: flex; gap: 8px; }
.rx-col { flex: 1; min-width: 0; }
.rx-io { width: 100%; min-height: 220px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none;
  white-space: pre-wrap; word-break: break-all; }
.rx-io:focus { border-color: #409eff; }
.rx-highlight { background: #f7f9fb; overflow: auto; }
.rx-mark { background: #fdfd8a; border-radius: 2px; }
.rx-group { display: inline-block; margin-right: 10px; font-family: 'Consolas', monospace; font-size: 12px; }
.rx-out { background: #f5f7fa; padding: 8px; border-radius: 4px; max-height: 240px; overflow: auto;
  font-family: 'Consolas', monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; margin: 0; }
</style>
