<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" title="规则编辑" width="92%" top="3vh" :close-on-click-modal="false">
    <el-form :model="form" label-width="96px">
      <el-row :gutter="16">
        <el-col :span="6"><el-form-item label="规则名"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="4"><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="9999" /></el-form-item></el-col>
        <el-col :span="4"><el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
      </el-row>

      <el-divider content-position="left">匹配条件（AND，空为兜底）</el-divider>
      <div v-for="(c, i) in conditions" :key="i" class="cond-row">
        <el-select v-model="c.source" style="width: 110px">
          <el-option v-for="s in sources" :key="s" :label="s" :value="s" />
        </el-select>
        <el-input v-model="c.key" placeholder="header名 / $.jsonPath / path变量 / 字段名" style="width: 260px" />
        <el-select v-model="c.op" style="width: 110px">
          <el-option v-for="o in ops" :key="o" :label="o" :value="o" />
        </el-select>
        <el-input v-model="c.valueText" placeholder="值（in 用逗号分隔）" style="width: 220px" />
        <el-button :icon="Delete" circle type="danger" @click="conditions.splice(i, 1)" />
      </div>
      <el-button :icon="Plus" size="small" @click="conditions.push({ source: 'header', key: '', op: 'eq', valueText: '' })">添加条件</el-button>

      <el-divider content-position="left">响应模板</el-divider>
      <el-row :gutter="12">
        <el-col :span="17">
          <textarea ref="tplArea" v-model="templateText" class="tpl-textarea" spellcheck="false"></textarea>
          <div class="tpl-toolbar">
            <el-button size="small" type="primary" @click="onPreview" :loading="previewing">试算预览</el-button>
            <span class="hint">模板为 JSON：HTTP 用 {status,headers,body}；TCP 直接写字段树</span>
          </div>
        </el-col>
        <el-col :span="7">
          <div class="fn-panel">
            <div class="fn-title">函数市场 · 点击插入到光标</div>
            <FunctionMarketSidebar :project-id="projectId" @insert="insertAtCursor" />
          </div>
        </el-col>
      </el-row>

      <el-dialog v-model="previewVisible" title="试算预览" width="640px" append-to-body>
        <el-form label-width="80px">
          <el-form-item label="上下文">
            <el-input v-model="contextText" type="textarea" :rows="3" placeholder='{"path":{"userId":"U1"}}' />
          </el-form-item>
        </el-form>
        <pre class="preview">{{ previewText }}</pre>
      </el-dialog>

      <el-divider content-position="left">延迟 / 故障注入</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="延迟类型">
            <el-select v-model="form.delayType">
              <el-option label="无 NONE" value="NONE" />
              <el-option label="固定 FIXED" value="FIXED" />
              <el-option label="随机 RANDOM" value="RANDOM" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="5" v-if="form.delayType === 'FIXED'"><el-form-item label="毫秒"><el-input-number v-model="form.delayMs" :min="0" /></el-form-item></el-col>
        <template v-if="form.delayType === 'RANDOM'">
          <el-col :span="5"><el-form-item label="最小ms"><el-input-number v-model="form.delayMinMs" :min="0" /></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="最大ms"><el-input-number v-model="form.delayMaxMs" :min="0" /></el-form-item></el-col>
        </template>
        <el-col :span="6">
          <el-form-item label="故障类型">
            <el-select v-model="form.faultType">
              <el-option label="无 NONE" value="NONE" />
              <el-option label="错误状态 ERROR_STATUS" value="ERROR_STATUS" />
              <el-option label="超时 TIMEOUT" value="TIMEOUT" />
              <el-option label="断连 RESET" value="RESET" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="5" v-if="form.faultType === 'ERROR_STATUS'"><el-form-item label="HTTP状态"><el-input-number v-model="faultStatus" :min="100" :max="599" /></el-form-item></el-col>
      </el-row>
      <el-form-item v-if="form.faultType === 'ERROR_STATUS'" label="错误体">
        <el-input v-model="faultBody" type="textarea" :rows="2" placeholder='JSON，如 {"code":"E500"}' />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { api } from '../api'
import FunctionMarketSidebar from './FunctionMarketSidebar.vue'

const props = defineProps({
  modelValue: Boolean,
  rule: { type: Object, default: null },
  interfaceId: { type: [Number, String], required: true },
  projectId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const sources = ['header', 'query', 'body', 'path', 'form', 'field']
const ops = ['eq', 'ne', 'in', 'gt', 'gte', 'lt', 'lte', 'regex', 'contains', 'exists', 'not_exists']

const form = ref({ name: '', priority: 100, status: 1, delayType: 'NONE', delayMs: 0, delayMinMs: 0, delayMaxMs: 0, faultType: 'NONE' })
const conditions = ref([])
const templateText = ref('')
const faultStatus = ref(500)
const faultBody = ref('')

const tplArea = ref(null)
const previewVisible = ref(false)
const previewing = ref(false)
const previewText = ref('')
const contextText = ref('{}')

watch(() => props.modelValue, (v) => {
  if (v) hydrate()
})

function hydrate() {
  const r = props.rule || {}
  form.value = {
    name: r.name || '',
    priority: r.priority ?? 100,
    status: r.status ?? 1,
    delayType: r.delayType || 'NONE',
    delayMs: r.delayMs ?? 0,
    delayMinMs: r.delayMinMs ?? 0,
    delayMaxMs: r.delayMaxMs ?? 0,
    faultType: r.faultType || 'NONE'
  }
  let conds = []
  try { conds = typeof r.matchCondition === 'string' ? JSON.parse(r.matchCondition || '[]') : (r.matchCondition || []) } catch (e) { conds = [] }
  conditions.value = conds.map((c) => ({ source: c.source, key: c.key, op: c.op, valueText: Array.isArray(c.value) ? c.value.join(',') : (c.value == null ? '' : String(c.value)) }))
  let tpl = r.responseTemplate
  if (tpl && typeof tpl === 'object') tpl = JSON.stringify(tpl, null, 2)
  templateText.value = tpl || '{\n  "status": 200,\n  "body": {\n    "code": "0000"\n  }\n}'
  let fc = null
  try { fc = typeof r.faultConfig === 'string' ? JSON.parse(r.faultConfig || 'null') : r.faultConfig } catch (e) { fc = null }
  faultStatus.value = fc?.httpStatus ?? 500
  faultBody.value = fc?.body ? (typeof fc.body === 'string' ? fc.body : JSON.stringify(fc.body)) : ''
}

function insertAtCursor(text) {
  const el = tplArea.value
  if (!el) {
    templateText.value += text
    return
  }
  const start = el.selectionStart || 0
  const end = el.selectionEnd || 0
  templateText.value = templateText.value.slice(0, start) + text + templateText.value.slice(end)
  nextTick(() => { el.focus(); el.selectionStart = el.selectionEnd = start + text.length })
}

async function onPreview() {
  let template
  try { template = JSON.parse(templateText.value) } catch (e) { ElMessage.error('响应模板不是合法 JSON'); return }
  let context = {}
  try { context = JSON.parse(contextText.value || '{}') } catch (e) { ElMessage.error('上下文不是合法 JSON'); return }
  previewing.value = true
  previewVisible.value = true
  try {
    const res = await api.template.evaluate({ template, context, projectId: props.projectId })
    previewText.value = JSON.stringify(res.data, null, 2)
  } catch (e) {
    previewText.value = '试算失败'
  } finally {
    previewing.value = false
  }
}

function buildPayload() {
  let template
  try { template = JSON.parse(templateText.value) } catch (e) { throw new Error('响应模板不是合法 JSON') }
  const matchCondition = conditions.value.map((c) => {
    let val = c.valueText
    if (c.op === 'in') val = c.valueText.split(',').map((s) => s.trim()).filter((s) => s !== '')
    return { source: c.source, key: c.key, op: c.op, value: val }
  })
  let faultConfig = null
  if (form.value.faultType === 'ERROR_STATUS') {
    let body = faultBody.value
    try { body = JSON.parse(faultBody.value) } catch (e) { /* 保留字符串 */ }
    faultConfig = { httpStatus: faultStatus.value, body }
  }
  return {
    name: form.value.name,
    priority: form.value.priority,
    status: form.value.status,
    delayType: form.value.delayType,
    delayMs: form.value.delayMs,
    delayMinMs: form.value.delayMinMs,
    delayMaxMs: form.value.delayMaxMs,
    faultType: form.value.faultType,
    faultConfig,
    matchCondition,
    responseTemplate: template
  }
}

async function onSave() {
  const payload = buildPayload()
  if (props.rule && props.rule.id) {
    await api.rules.update(props.rule.id, payload)
  } else {
    await api.rules.create(props.interfaceId, payload)
  }
  ElMessage.success('已保存')
  emit('update:modelValue', false)
  emit('saved')
}
</script>

<style scoped>
.cond-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.tpl-textarea {
  width: 100%;
  min-height: 280px;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
  font-size: 13px;
  padding: 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  resize: vertical;
}
.tpl-toolbar {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.tpl-toolbar .hint {
  color: #909399;
  font-size: 12px;
}
.fn-panel {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
}
.fn-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.preview {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
