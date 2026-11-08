<template>
  <div class="cs">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="cs-hint">
          排查中文乱码：把「同一个字」分别按 UTF-8 / GBK / GB2312 / Big5 编码，看它到底是哪几个字节。
          <b>文本 → 字节</b>给出目标字符集下的原始字节（Hex / Base64）；
          <b>字节 → 文本</b>把抓到的报文按指定字符集解回中文，字节不合法时会直接报错（而不是给你一堆问号）。
        </span>
      </template>
    </el-alert>

    <div class="cs-bar">
      <el-radio-group v-model="dir" size="small">
        <el-radio-button value="encode">文本 → 字节</el-radio-button>
        <el-radio-button value="decode">字节 → 文本</el-radio-button>
      </el-radio-group>

      <span class="cs-label">字符集：</span>
      <el-select v-model="charset" size="small" style="width:150px">
        <el-option v-for="c in CHARSETS" :key="c" :label="c" :value="c" />
      </el-select>

      <template v-if="dir === 'decode'">
        <span class="cs-label">字节格式：</span>
        <el-radio-group v-model="inEnc" size="small">
          <el-radio-button value="hex">Hex</el-radio-button>
          <el-radio-button value="base64">Base64</el-radio-button>
        </el-radio-group>
      </template>

      <el-button size="small" type="primary" :loading="loading" @click="convert">转换</el-button>
      <el-button size="small" @click="fillSample">示例</el-button>
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />
    <el-alert v-if="lossy" type="warning" :closable="false" show-icon style="margin-bottom:8px">
      <template #title>
        <span class="cs-hint">
          有字符无法用 {{ charset }} 表示，已按 '?' 替换：{{ lossyList }}。
          换成 UTF-8 或 GBK 通常都能表示。
        </span>
      </template>
    </el-alert>

    <div class="cs-pane">
      <div class="cs-col">
        <div class="cs-label">
          <template v-if="dir === 'encode'">待编码文本：</template>
          <template v-else>待解码字节（{{ inEnc === 'hex' ? 'Hex' : 'Base64' }}）：</template>
        </div>
        <textarea v-model="input" class="cs-io" :placeholder="dir === 'encode' ? '中文测试 abc 123' : '例如 UTF-8 的「中文」= E4B8AD E69687'"></textarea>
      </div>
      <div class="cs-col">
        <div class="cs-label">
          <template v-if="dir === 'encode'">编码结果：</template>
          <template v-else>解码结果：</template>
        </div>
        <div class="cs-io cs-out">
          <template v-if="!out">点「转换」查看结果</template>
          <template v-else>
            <template v-if="dir === 'encode'">
              <div class="cs-line"><b>Hex</b>　<span class="cs-mono">{{ out.hex }}</span>
                <el-button size="small" link type="primary" @click="copyText(out.hex)">复制</el-button>
              </div>
              <div class="cs-line"><b>Base64</b>　<span class="cs-mono">{{ out.base64 }}</span>
                <el-button size="small" link type="primary" @click="copyText(out.base64)">复制</el-button>
              </div>
            </template>
            <template v-else>
              <div class="cs-line"><b>文本</b>　{{ out.text }}</div>
            </template>
            <div class="cs-line cs-sub">共 {{ out.size }} 字节</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { api } from '../../api'
import { copyText } from '../../utils/clipboard'

const CHARSETS = ['UTF-8', 'GBK', 'GB2312', 'Big5', 'ISO-8859-1', 'UTF-16', 'UTF-16LE', 'UTF-16BE', 'ASCII']

const dir = ref('encode')
const charset = ref('GBK')
const inEnc = ref('hex')
const input = ref('')
const out = ref(null)
const err = ref('')
const loading = ref(false)

const lossy = computed(() => !!(out.value && out.value.lossy))
const lossyList = computed(() => (out.value && out.value.unmappable ? out.value.unmappable.join('、') : ''))

function fillSample() {
  dir.value = 'encode'
  charset.value = 'GBK'
  input.value = '中文测试 abc'
  out.value = null
}

async function convert() {
  err.value = ''
  out.value = null
  if (!input.value.trim()) {
    err.value = '输入不能为空'
    return
  }
  loading.value = true
  try {
    if (dir.value === 'encode') {
      const res = await api.tools.charsetEncode({ text: input.value, charset: charset.value })
      out.value = res.data
    } else {
      const res = await api.tools.charsetDecode({ data: input.value, inEnc: inEnc.value, charset: charset.value })
      out.value = res.data
    }
  } catch (e) {
    err.value = '转换失败：' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cs-hint { font-size: 12px; line-height: 1.7; }
.cs-label { font-size: 13px; color: #606266; margin-bottom: 4px; }
.cs-sub { font-size: 12px; color: #909399; }
.cs-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.cs-pane { display: flex; gap: 8px; }
.cs-col { flex: 1; min-width: 0; }
.cs-io { width: 100%; min-height: 220px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none;
  white-space: pre-wrap; word-break: break-all; }
.cs-io:focus { border-color: #409eff; }
.cs-out { background: #f7f9fb; overflow: auto; }
.cs-line { margin-bottom: 6px; word-break: break-all; }
.cs-mono { font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; }
</style>
