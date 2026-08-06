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
        <el-select v-model="c.source" style="width: 140px">
          <el-option v-for="s in sources" :key="s.v" :label="s.l" :value="s.v" />
        </el-select>
        <el-input v-model="c.key" :placeholder="keyPlaceholder(c.source)" style="width: 240px" />
        <el-select v-model="c.op" style="width: 150px">
          <el-option v-for="o in ops" :key="o.v" :label="o.l" :value="o.v" />
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
            <el-dropdown trigger="click" @command="applyPreset">
              <el-button size="small">快速模板 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="0">成功 200</el-dropdown-item>
                  <el-dropdown-item :command="1">分页列表</el-dropdown-item>
                  <el-dropdown-item :command="2">业务错误码</el-dropdown-item>
                  <el-dropdown-item :command="3">未授权 401</el-dropdown-item>
                  <el-dropdown-item :command="4">网关超时 504</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <span class="hint">HTTP 模板 {status,headers,body}；TCP 直接写字段树</span>
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
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const sources = [
  { v: 'header', l: '请求头 header' },
  { v: 'query', l: '查询参数 query' },
  { v: 'body', l: '请求体 body' },
  { v: 'path', l: '路径变量 path' },
  { v: 'form', l: '表单 form' },
  { v: 'field', l: '报文字段 field' }
]
const ops = [
  { v: 'eq', l: '等于 eq' },
  { v: 'ne', l: '不等于 ne' },
  { v: 'contains', l: '包含 contains' },
  { v: 'exists', l: '存在 exists' },
  { v: 'not_exists', l: '不存在 not_exists' },
  { v: 'in', l: '属于 in' },
  { v: 'gt', l: '大于 gt' },
  { v: 'gte', l: '大于等于 gte' },
  { v: 'lt', l: '小于 lt' },
  { v: 'lte', l: '小于等于 lte' },
  { v: 'regex', l: '正则 regex' }
]

// 按 source 给 key 输入框动态占位符，降低新手困惑
function keyPlaceholder(source) {
  switch (source) {
    case 'header': return 'Header 名，如 X-Token'
    case 'query': return '参数名，如 page'
    case 'path': return '路径变量名，如 userId'
    case 'field': return '报文字段名，如 serialNo'
    case 'body': return '请求体字段名'
    case 'form': return '表单字段名'
    default: return '键名'
  }
}

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
const saving = ref(false)

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

// 响应模板脚手架：一键灌入常见结构，新手不用从零拼 JSON
const TPL_PRESETS = [
  '{\n  "status": 200,\n  "body": {\n    "code": "0000",\n    "msg": "success"\n  }\n}',
  '{\n  "status": 200,\n  "body": {\n    "code": "0000",\n    "total": ${int(100, 999)},\n    "list": ${repeat(3, { id: ${string(numeric, 8)}, name: ${name.cn} })}\n  }\n}',
  '{\n  "status": 200,\n  "body": {\n    "code": "9999",\n    "msg": "业务处理失败"\n  }\n}',
  '{\n  "status": 401,\n  "body": {\n    "code": "401",\n    "msg": "未授权或登录已过期"\n  }\n}',
  '{\n  "status": 504,\n  "body": {\n    "code": "504",\n    "msg": "Gateway Timeout"\n  }\n}'
]
function applyPreset(i) {
  templateText.value = TPL_PRESETS[i]
  ElMessage.success('已套用模板，可在此基础上修改')
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
    // 防呆：trim key 与 value 首尾空格（前导空格会让 JSONPath/键名失配导致不命中）
    const key = (c.key || '').trim()
    let val = c.valueText == null ? '' : String(c.valueText)
    if (c.op === 'in') {
      val = val.split(',').map((s) => s.trim()).filter((s) => s !== '')
    } else {
      val = val.trim()
    }
    return { source: c.source, key, op: c.op, value: val }
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
  if (!form.value.name || !form.value.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  let payload
  try {
    payload = buildPayload()
  } catch (e) {
    ElMessage.error(e.message || '保存失败：请检查响应模板是否为合法 JSON')
    return
  }
  // 空匹配条件 = 兜底规则（仅当所有条件都被清空时提示，避免误建）
  const hasCond = Array.isArray(payload.matchCondition) && payload.matchCondition.some((c) => c.key)
  if (!hasCond) {
    try {
      await ElMessageBox.confirm(
        '当前没有有效的匹配条件，此规则将作为「兜底规则」（优先级最低、仅当其它规则都不命中时生效）。确认这样保存？',
        '将保存为兜底规则',
        { type: 'warning', confirmButtonText: '保存为兜底', cancelButtonText: '回去补充条件' }
      )
    } catch (e) {
      return // 用户取消
    }
  }
  saving.value = true
  try {
    if (props.rule && props.rule.id) {
      await api.rules.update(props.rule.id, payload)
    } else {
      await api.rules.create(props.interfaceId, payload)
    }
    ElMessage.success('已保存并即时生效')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
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
