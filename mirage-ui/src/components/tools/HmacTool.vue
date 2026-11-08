<template>
  <div class="hm">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px">
      <template #title>
        <span class="hm-hint">
          HMAC（带密钥的消息认证码）用于接口签名调试：对「密钥 + 请求串」算出定长摘要，
          服务端用同一把密钥重算一遍比对。密钥只在本次请求里使用，<b>不会落库、不写入 localStorage</b>。
          签名串的拼接规则（参数排序、分隔符、是否含时间戳）各家不同，请以被测接口的文档为准。
        </span>
      </template>
    </el-alert>

    <div class="hm-bar">
      <span class="hm-label">算法：</span>
      <el-radio-group v-model="algo" size="small">
        <el-radio-button value="MD5">HMAC-MD5</el-radio-button>
        <el-radio-button value="SHA1">HMAC-SHA1</el-radio-button>
        <el-radio-button value="SHA256">HMAC-SHA256</el-radio-button>
        <el-radio-button value="SHA384">HMAC-SHA384</el-radio-button>
        <el-radio-button value="SHA512">HMAC-SHA512</el-radio-button>
      </el-radio-group>

      <span class="hm-label">密钥格式：</span>
      <el-select v-model="keyEnc" size="small" style="width:110px">
        <el-option label="UTF-8" value="utf8" />
        <el-option label="Hex" value="hex" />
        <el-option label="Base64" value="base64" />
      </el-select>

      <el-button size="small" type="primary" :loading="loading" :disabled="!key" @click="calc">计算签名</el-button>
      <el-button size="small" @click="fillSample">示例</el-button>
    </div>

    <div class="hm-bar">
      <span class="hm-label">密钥：</span>
      <el-input v-model="key" size="small" style="width:460px" show-password placeholder="HMAC 密钥" />
      <span class="hm-label">待签名内容：</span>
      <el-input v-model="text" size="small" style="width:460px" placeholder="如 appId=1&timestamp=1753905600" />
    </div>

    <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" style="margin-bottom:8px" />

    <template v-if="result">
      <el-descriptions :column="1" border size="small" style="margin-bottom:8px">
        <el-descriptions-item label="算法">
          <el-tag size="small" type="info">HMAC-{{ result.algo }}</el-tag>
          <span class="hm-sub">　签名长度 {{ result.hex.length / 2 }} 字节</span>
        </el-descriptions-item>
        <el-descriptions-item label="Hex">
          <span class="hm-mono">{{ result.hex }}</span>
          <el-button size="small" link type="primary" style="margin-left:6px" @click="copyText(result.hex)">复制</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="Base64">
          <span class="hm-mono">{{ result.base64 }}</span>
          <el-button size="small" link type="primary" style="margin-left:6px" @click="copyText(result.base64)">复制</el-button>
        </el-descriptions-item>
      </el-descriptions>

      <div class="hm-bar">
        <span class="hm-label">期望值比对：</span>
        <el-input v-model="expected" size="small" style="width:420px" placeholder="粘贴期望的签名（与 Hex 或 Base64 比对）" />
        <el-tag v-if="verdict" size="small" :type="verdict.ok ? 'success' : 'danger'">{{ verdict.text }}</el-tag>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { api } from '../../api'
import { copyText } from '../../utils/clipboard'

const algo = ref('SHA256')
const keyEnc = ref('utf8')
const key = ref('')
const text = ref('')
const expected = ref('')
const result = ref(null)
const err = ref('')
const loading = ref(false)

const verdict = computed(() => {
  if (!expected.value.trim() || !result.value) return null
  const want = expected.value.trim()
  const ok = result.value.hex.toLowerCase() === want.toLowerCase() || result.value.base64 === want
  return { ok, text: ok ? '与期望值一致 ✓' : '与期望值不一致 ✗' }
})

function fillSample() {
  key.value = 'test-secret'
  keyEnc.value = 'utf8'
  text.value = 'appId=1001&nonce=abc123&timestamp=1753905600'
  expected.value = ''
}

async function calc() {
  err.value = ''
  result.value = null
  if (!key.value) {
    err.value = '请输入 HMAC 密钥'
    return
  }
  loading.value = true
  try {
    const res = await api.tools.hmac({
      text: text.value,
      key: key.value,
      algo: algo.value,
      keyEnc: keyEnc.value
    })
    result.value = res.data
  } catch (e) {
    err.value = '签名计算失败：' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hm-hint { font-size: 12px; line-height: 1.7; }
.hm-label { font-size: 13px; color: #606266; white-space: nowrap; }
.hm-sub { font-size: 12px; color: #909399; }
.hm-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.hm-mono { font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; word-break: break-all; }
</style>
