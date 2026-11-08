<template>
  <div class="tcp-frame-cfg">
    <!-- ===== 帧切分方式 ===== -->
    <div class="cfg-title">帧切分方式</div>
    <el-radio-group :model-value="frame.type" size="small" @update:model-value="onType">
      <el-radio-button value="length_field">长度头</el-radio-button>
      <el-radio-button value="delimiter">分隔符</el-radio-button>
      <el-radio-button value="fixed">定长</el-radio-button>
      <el-radio-button value="close_end">读到关闭</el-radio-button>
    </el-radio-group>
    <div class="cfg-hint">{{ FRAME_HINT[frame.type] || '' }}</div>

    <!-- length_field 参数 -->
    <template v-if="frame.type === 'length_field'">
      <el-row :gutter="8">
        <el-col :span="8">
          <div class="cfg-label">长度头字节数</div>
          <el-select :model-value="frame.lenBytes" size="small" style="width:100%" @update:model-value="onLenBytes">
            <el-option v-for="n in [1, 2, 3, 4, 8]" :key="n" :label="n + ' 字节'" :value="n" />
          </el-select>
        </el-col>
        <el-col :span="8">
          <div class="cfg-label">前缀字节数 offset</div>
          <el-input-number :model-value="frame.offset" :min="0" :max="255" size="small" controls-position="right" style="width:100%" @update:model-value="onOffset" />
        </el-col>
        <el-col :span="8">
          <div class="cfg-label">长度修正 adjustment</div>
          <el-input-number :model-value="frame.adjustment" :min="-255" :max="255" size="small" controls-position="right" style="width:100%" @update:model-value="set('adjustment', $event)" />
        </el-col>
      </el-row>
      <el-row :gutter="8" style="margin-top:8px">
        <el-col :span="8">
          <div class="cfg-label">剥离字节数 initialStrip</div>
          <el-input-number :model-value="frame.initialStrip" :min="0" :max="255" size="small" controls-position="right" style="width:100%" @update:model-value="set('initialStrip', $event)" />
        </el-col>
        <el-col :span="16">
          <div class="cfg-label">前缀内容 prefix_hex（offset&gt;0 时生效，可选）</div>
          <el-input :model-value="frame.prefixHex" size="small" placeholder="如 0A0B；留空则填 0x00 占位" @update:model-value="set('prefixHex', $event)" />
        </el-col>
      </el-row>
      <div class="cfg-hint">
        长度头为<b>大端</b>；声明长度 = 正文长度 − adjustment。<br />
        initialStrip 默认 = offset + 长度头字节数（自动跟随，改上面两项会同步；手动改过就不再跟随）。
      </div>
    </template>

    <!-- delimiter 参数 -->
    <template v-if="frame.type === 'delimiter'">
      <el-row :gutter="8">
        <el-col :span="12">
          <div class="cfg-label">分隔符</div>
          <el-input :model-value="frame.delimiter" size="small" placeholder="\n 或 \r\n 或 0x0D0A" @update:model-value="set('delimiter', $event)" />
        </el-col>
      </el-row>
      <div class="cfg-hint">用 <span class="mono">\n</span> / <span class="mono">\r</span> / <span class="mono">\t</span> 表示控制字符，也可用 <span class="mono">0x</span> 十六进制。</div>
    </template>

    <!-- fixed 参数 -->
    <template v-if="frame.type === 'fixed'">
      <el-row :gutter="8">
        <el-col :span="12">
          <div class="cfg-label">定长字节数</div>
          <el-input-number :model-value="frame.fixedLength" :min="1" :max="65536" size="small" controls-position="right" style="width:100%" @update:model-value="set('fixedLength', $event)" />
        </el-col>
      </el-row>
      <div class="cfg-hint">正文按固定字节数补齐 0x00；<b>超长会被截断</b>，所以定长必须 ≥ 报文最大长度，否则截断后的响应无法解析。</div>
    </template>

    <template v-if="frame.type === 'close_end'">
      <div class="cfg-hint">不加任何帧头；对端<b>半关闭</b>（shutdownOutput，收到 FIN）即为一帧，服务端据此回写响应。</div>
    </template>

    <!-- ===== 报文格式 ===== -->
    <div class="cfg-title" style="margin-top:12px">报文格式</div>
    <el-radio-group :model-value="messageFormat" size="small" @update:model-value="onFormat">
      <el-radio-button value="json">JSON</el-radio-button>
      <el-radio-button value="key_value">键值对</el-radio-button>
      <el-radio-button value="fixed_fields">定长字段</el-radio-button>
      <el-radio-button value="hex_string">Hex</el-radio-button>
    </el-radio-group>
    <div class="cfg-hint">{{ FORMAT_HINT[messageFormat] || '' }}</div>

    <!-- key_value 参数 -->
    <el-row v-if="messageFormat === 'key_value'" :gutter="8">
      <el-col :span="8">
        <div class="cfg-label">键值分隔符 kvSep</div>
        <el-input :model-value="(formatConfig || {}).kvSep || '&'" size="small" @update:model-value="setFormatCfg('kvSep', $event)" />
      </el-col>
      <el-col :span="8">
        <div class="cfg-label">键值对之间分隔符 pairSep</div>
        <el-input :model-value="(formatConfig || {}).pairSep || '&'" size="small" @update:model-value="setFormatCfg('pairSep', $event)" />
      </el-col>
    </el-row>

    <!-- fixed_fields 参数 -->
    <template v-if="messageFormat === 'fixed_fields'">
      <div class="cfg-label" style="margin-bottom:4px">字段宽度（len 为字节数）</div>
      <div v-for="(f, i) in fixedFields" :key="i" class="cfg-row">
        <el-input :model-value="f.name" size="small" placeholder="字段名，如 orgNo" style="width:220px" @update:model-value="updateField(i, 'name', $event)" />
        <el-input-number :model-value="f.len" :min="1" :max="4096" size="small" controls-position="right" style="width:130px" @update:model-value="updateField(i, 'len', $event)" />
        <el-button size="small" :icon="Delete" circle type="danger" @click="removeField(i)" />
      </div>
      <el-button size="small" :icon="Plus" @click="addField">添加字段</el-button>
      <div class="cfg-hint">按顺序从字节流中切出定长字段；请求体里同名字段会被按此宽度补齐/截断。</div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

/**
 * TCP 用例的「帧 / 格式」一键配置：把 tcp_config（JSON）拆成「帧切分方式 + 参数」与「报文格式 + 专属配置」两组表单，
 * 用户不用手写 JSON 就能配好；高级模式仍可手工编辑原始 JSON。
 */
const props = defineProps({
  /** 帧切分配置（结构化对象） */
  frameConfig: { type: Object, required: true },
  /** 报文格式：json / key_value / fixed_fields / hex_string */
  messageFormat: { type: String, default: 'json' },
  /** 报文格式专属配置 */
  formatConfig: { type: Object, default: null }
})
const emit = defineEmits(['update:frameConfig', 'update:messageFormat', 'update:formatConfig'])

const FRAME_HINT = {
  length_field: '前置 N 字节大端长度头声明正文长度（最常用）。',
  delimiter: '正文后追加分隔符，对端按分隔符切帧。',
  fixed: '正文按固定字节数补齐 / 截断。',
  close_end: '不加任何帧头，对端关闭连接即为一帧。'
}

const FORMAT_HINT = {
  json: '请求体与响应体都是 JSON 对象（最常用）。',
  key_value: 'K=V 形式的文本，可自定义分隔符。',
  fixed_fields: '按固定字节宽度切分字段（如银联报文）。',
  hex_string: '不解析，整体以 hex 字符串透传（用 ${hex(...)} 处理）；路由值按小写十六进制比较。'
}

const frame = computed(() => props.frameConfig || {})
const fixedFields = computed(() => (props.formatConfig && Array.isArray(props.formatConfig.fields)) ? props.formatConfig.fields : [])

function set(key, val) {
  emit('update:frameConfig', { ...frame.value, [key]: val })
}

function onType(t) {
  emit('update:frameConfig', { ...frame.value, type: t })
}

// 长度头字节数变化：initialStrip 若仍是「自动值」就跟着走；用户手动改过则保留
function onLenBytes(v) {
  const old = frame.value
  const auto = (old.offset || 0) + (old.lenBytes || 0)
  const next = { ...old, lenBytes: v }
  if (old.initialStrip == null || old.initialStrip === auto) next.initialStrip = (next.offset || 0) + (v || 0)
  emit('update:frameConfig', next)
}

// offset 变化：同上
function onOffset(v) {
  const old = frame.value
  const auto = (old.offset || 0) + (old.lenBytes || 0)
  const next = { ...old, offset: v }
  if (old.initialStrip == null || old.initialStrip === auto) next.initialStrip = (v || 0) + (old.lenBytes || 0)
  emit('update:frameConfig', next)
}

function setFormatCfg(key, val) {
  emit('update:formatConfig', { ...(props.formatConfig || {}), [key]: val })
}

// 切换报文格式：同步切换专属配置（json/hex 无需专属配置）
function onFormat(v) {
  emit('update:messageFormat', v)
  const cfg = props.formatConfig || {}
  if (v === 'json' || v === 'hex_string') {
    emit('update:formatConfig', null)
  } else if (v === 'key_value') {
    emit('update:formatConfig', { pairSep: cfg.pairSep || '&', kvSep: cfg.kvSep || '=' })
  } else if (v === 'fixed_fields') {
    emit('update:formatConfig', {
      fields: Array.isArray(cfg.fields) && cfg.fields.length
        ? cfg.fields
        : [{ name: 'field1', len: 10 }]
    })
  }
}

function updateField(i, key, val) {
  const fields = fixedFields.value.map((f, idx) => (idx === i ? { ...f, [key]: val } : f))
  emit('update:formatConfig', { ...(props.formatConfig || {}), fields })
}

function addField() {
  const fields = [...fixedFields.value, { name: 'field' + (fixedFields.value.length + 1), len: 10 }]
  emit('update:formatConfig', { ...(props.formatConfig || {}), fields })
}

function removeField(i) {
  const fields = fixedFields.value.filter((_, idx) => idx !== i)
  emit('update:formatConfig', { ...(props.formatConfig || {}), fields })
}
</script>

<style scoped>
.cfg-title { font-size: 13px; color: #606266; margin: 8px 0 6px; }
.cfg-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.cfg-hint { font-size: 12px; color: #909399; margin: 6px 0 2px; line-height: 1.7; }
.cfg-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; }
</style>
