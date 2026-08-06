<template>
  <div class="page tools-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>工具市场 · 测试常用小工具</span>
            <span class="hint">格式化 / 国密(RSA) 加解密签名；可直接粘贴密钥，或在 SM2/SM4/RSA 标签「引用密钥」选项目密钥（私钥服务端解析、不回前端）</span>
          </div>
          <el-button size="small" @click="copyCurrentOut">复制结果</el-button>
        </div>
      </template>

      <el-tabs v-model="tab" type="card">
        <!-- ===================== JSON ===================== -->
        <el-tab-pane label="JSON" name="json">
          <div class="bar">
            <el-radio-group v-model="jsonIndent" size="small">
              <el-radio-button label="2">缩进 2</el-radio-button>
              <el-radio-button label="4">缩进 4</el-radio-button>
              <el-radio-button label="tab">Tab</el-radio-button>
            </el-radio-group>
            <el-button size="small" type="primary" :loading="running" @click="doJson('format')">美化</el-button>
            <el-button size="small" :loading="running" @click="doJson('minify')">压缩</el-button>
            <el-button size="small" :loading="running" @click="doJson('validate')">校验</el-button>
            <el-button size="small" :loading="running" @click="doJson('escape')">转义</el-button>
            <el-button size="small" :loading="running" @click="doJson('unescape')">去转义</el-button>
          </div>
          <div class="pane">
            <textarea v-model="jsonIn" class="io" placeholder='{"name":"蜃楼","list":[1,2,3]}'></textarea>
            <div class="mid"><el-button circle size="small" @click="jsonIn = jsonOut; jsonOut = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="jsonOut" class="io out" readonly placeholder="结果"></textarea>
          </div>
        </el-tab-pane>

        <!-- ===================== XML ===================== -->
        <el-tab-pane label="XML" name="xml">
          <div class="bar">
            <el-radio-group v-model="xmlIndent" size="small">
              <el-radio-button label="2">缩进 2</el-radio-button>
              <el-radio-button label="4">缩进 4</el-radio-button>
            </el-radio-group>
            <el-button size="small" type="primary" :loading="running" @click="doXml('format')">美化</el-button>
            <el-button size="small" :loading="running" @click="doXml('minify')">压缩</el-button>
            <el-button size="small" :loading="running" @click="doXml('validate')">校验</el-button>
            <el-button size="small" :loading="running" @click="doXml('escape')">转义</el-button>
            <el-button size="small" :loading="running" @click="doXml('unescape')">去转义</el-button>
          </div>
          <div class="pane">
            <textarea v-model="xmlIn" class="io" placeholder="粘贴 XML 文本进行美化 / 压缩 / 校验"></textarea>
            <div class="mid"><el-button circle size="small" @click="xmlIn = xmlOut; xmlOut = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="xmlOut" class="io out" readonly placeholder="结果"></textarea>
          </div>
        </el-tab-pane>

        <!-- ===================== SQL ===================== -->
        <el-tab-pane label="SQL" name="sql">
          <div class="bar">
            <span class="muted">关键字大小写规整 + 子句换行 + AND/OR 缩进（不拆分逗号，安全）</span>
            <el-button size="small" type="primary" :loading="running" @click="doSql">美化</el-button>
          </div>
          <div class="pane">
            <textarea v-model="sqlIn" class="io" placeholder="select id,name,count(*) from t_user where status=1 and age>18 order by id"></textarea>
            <div class="mid"><el-button circle size="small" @click="sqlIn = sqlOut; sqlOut = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="sqlOut" class="io out" readonly placeholder="结果"></textarea>
          </div>
        </el-tab-pane>

        <!-- ===================== 编码 / 转换 ===================== -->
        <el-tab-pane label="编码/转换" name="enc">
          <div class="bar">
            <el-radio-group v-model="encMode" size="small">
              <el-radio-button label="base64">Base64</el-radio-button>
              <el-radio-button label="hex">Hex</el-radio-button>
              <el-radio-button label="url">URL</el-radio-button>
              <el-radio-button label="hash">哈希</el-radio-button>
              <el-radio-button label="jwt">JWT 解析</el-radio-button>
              <el-radio-button label="ts">时间戳</el-radio-button>
              <el-radio-button label="uuid">UUID</el-radio-button>
            </el-radio-group>
          </div>

          <!-- Base64 / Hex / URL -->
          <template v-if="encMode === 'base64' || encMode === 'hex' || encMode === 'url'">
            <div class="bar">
              <el-button size="small" type="primary" :loading="running" @click="enc2way('encode')">编码</el-button>
              <el-button size="small" :loading="running" @click="enc2way('decode')">解码</el-button>
            </div>
            <div class="pane">
              <textarea v-model="encIn" class="io" placeholder="输入待编码/解码文本"></textarea>
              <div class="mid"><el-button circle size="small" @click="encIn = encOut; encOut = ''" title="输出 → 输入">⇄</el-button></div>
              <textarea v-model="encOut" class="io out" readonly placeholder="结果"></textarea>
            </div>
          </template>

          <!-- 哈希 -->
          <template v-else-if="encMode === 'hash'">
            <div class="bar">
              <el-radio-group v-model="hashAlgo" size="small">
                <el-radio-button label="MD5">MD5</el-radio-button>
                <el-radio-button label="SHA1">SHA1</el-radio-button>
                <el-radio-button label="SHA256">SHA256</el-radio-button>
                <el-radio-button label="SHA512">SHA512</el-radio-button>
              </el-radio-group>
              <el-button size="small" type="primary" :loading="running" @click="doHash">计算摘要</el-button>
            </div>
            <div class="pane">
              <textarea v-model="encIn" class="io" placeholder="输入原文，输出 Hex 摘要"></textarea>
              <div class="mid"><el-button circle size="small" @click="encIn = encOut; encOut = ''" title="输出 → 输入">⇄</el-button></div>
              <textarea v-model="encOut" class="io out" readonly placeholder="摘要结果（Hex）"></textarea>
            </div>
          </template>

          <!-- JWT 解析 -->
          <template v-else-if="encMode === 'jwt'">
            <div class="bar">
              <el-button size="small" type="primary" :loading="running" @click="doJwt">解析（仅解码，不验签）</el-button>
            </div>
            <div class="pane">
              <textarea v-model="encIn" class="io" placeholder="粘贴 JWT：eyJhbGciOi...header.payload.signature"></textarea>
              <div class="mid"><el-button circle size="small" @click="encIn = encOut; encOut = ''" title="输出 → 输入">⇄</el-button></div>
              <textarea v-model="encOut" class="io out" readonly placeholder="Header / Payload / Signature"></textarea>
            </div>
          </template>

          <!-- 时间戳 -->
          <template v-else-if="encMode === 'ts'">
            <div class="bar">
              <el-button size="small" @click="doTsNow">当前时间</el-button>
              <el-button size="small" type="primary" :loading="running" @click="doTsToEpoch">日期 → 时间戳</el-button>
              <el-button size="small" type="primary" :loading="running" @click="doTsFromEpoch">时间戳 → 日期</el-button>
            </div>
            <div class="pane">
              <textarea v-model="encIn" class="io" placeholder="日期：2026-07-30 12:00:00   或   时间戳：1753905600 / 1753905600000（自动识别秒/毫秒）"></textarea>
              <div class="mid"><el-button circle size="small" @click="encIn = encOut; encOut = ''" title="输出 → 输入">⇄</el-button></div>
              <textarea v-model="encOut" class="io out" readonly placeholder="秒 / 毫秒 / ISO / 本地时间"></textarea>
            </div>
          </template>

          <!-- UUID -->
          <template v-else-if="encMode === 'uuid'">
            <div class="bar">
              <span class="label">数量</span>
              <el-input-number v-model="uuidCount" :min="1" :max="100" size="small" />
              <el-radio-group v-model="uuidUpper" size="small">
                <el-radio-button :label="false">小写</el-radio-button>
                <el-radio-button :label="true">大写</el-radio-button>
              </el-radio-group>
              <el-button size="small" type="primary" :loading="running" @click="doUuid">生成</el-button>
            </div>
            <div class="pane">
              <textarea v-model="encOut" class="io out" readonly placeholder="生成的 UUID 列表（每行一个）"></textarea>
            </div>
          </template>
        </el-tab-pane>

        <!-- ===================== SM3 ===================== -->
        <el-tab-pane label="SM3 摘要" name="sm3">
          <div class="bar">
            <span class="label">输出：</span>
            <el-radio-group v-model="sm3Out" size="small">
              <el-radio-button label="hex">Hex</el-radio-button>
              <el-radio-button label="base64">Base64</el-radio-button>
            </el-radio-group>
            <el-button size="small" type="primary" :loading="running" @click="doSm3">计算摘要</el-button>
          </div>
          <div class="pane">
            <textarea v-model="sm3In" class="io" placeholder="待摘要原文，如 abc（SM3 输出为 Hex 或 Base64）"></textarea>
            <div class="mid"><el-button circle size="small" @click="sm3In = sm3Out; sm3Out = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="sm3Out" class="io out" readonly placeholder="摘要结果"></textarea>
          </div>
        </el-tab-pane>

        <!-- ===================== SM4 ===================== -->
        <el-tab-pane label="SM4" name="sm4">
          <div class="cfg">
            <div class="row">
              <span class="label">引用密钥</span>
              <el-select v-model="sm4KeyId" clearable filterable size="small" style="width:260px" placeholder="选项目 SM4 密钥（密钥/IV 走 Base64）">
                <el-option v-for="k in sm4Keys" :key="k.id" :label="k.alias" :value="k.id" />
              </el-select>
              <span v-if="sm4KeyId" class="muted">已引用：密钥/IV 由服务端从密钥取</span>
              <span v-else-if="!proj.id" class="muted">请先选择项目</span>
              <span v-else-if="!sm4Keys.length" class="muted">本项目暂无 SM4 密钥</span>
            </div>
            <div class="row">
              <span class="label">密钥(16字节)</span>
              <el-input v-model="sm4Key" size="small" :disabled="!!sm4KeyId" style="width:320px" placeholder="密钥内容"></el-input>
              <span class="label">密钥格式</span>
              <el-select v-model="sm4KeyEnc" size="small" :disabled="!!sm4KeyId" style="width:110px">
                <el-option label="UTF-8" value="utf8" /><el-option label="Hex" value="hex" /><el-option label="Base64" value="base64" />
              </el-select>
              <el-button size="small" :disabled="!!sm4KeyId" @click="genSm4Key">生成密钥</el-button>
            </div>
            <div class="row">
              <span class="label">模式</span>
              <el-radio-group v-model="sm4Mode" size="small">
                <el-radio-button label="ECB">ECB</el-radio-button><el-radio-button label="CBC">CBC</el-radio-button>
              </el-radio-group>
              <template v-if="sm4Mode === 'CBC'">
                <span class="label">IV</span>
                <el-input v-model="sm4Iv" size="small" :disabled="!!sm4KeyId" style="width:240px" placeholder="CBC 需要 IV"></el-input>
              </template>
              <span class="label">密文编码</span>
              <el-radio-group v-model="sm4Enc" size="small">
                <el-radio-button label="base64">Base64</el-radio-button><el-radio-button label="hex">Hex</el-radio-button>
              </el-radio-group>
            </div>
            <div class="row">
              <el-button size="small" type="primary" :loading="running" @click="doSm4('encrypt')">⬆ 加密（明文→密文）</el-button>
              <el-button size="small" :loading="running" @click="doSm4('decrypt')">⬇ 解密（密文→明文）</el-button>
            </div>
          </div>
          <div class="pane">
            <textarea v-model="sm4In" class="io" placeholder="加密填明文 / 解密填密文（按所选编码）"></textarea>
            <div class="mid"><el-button circle size="small" @click="sm4In = sm4Out; sm4Out = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="sm4Out" class="io out" readonly placeholder="结果"></textarea>
          </div>
        </el-tab-pane>

        <!-- ===================== SM2 ===================== -->
        <el-tab-pane label="SM2" name="sm2">
          <div class="cfg">
            <div class="row">
              <span class="label">引用密钥</span>
              <el-select v-model="sm2KeyId" clearable filterable size="small" style="width:260px" placeholder="选项目 SM2 密钥，免粘贴">
                <el-option v-for="k in sm2Keys" :key="k.id" :label="k.alias" :value="k.id" />
              </el-select>
              <span v-if="sm2KeyId" class="muted">已引用：公/私钥由服务端从密钥取</span>
              <span v-else-if="!proj.id" class="muted">请先选择项目</span>
              <span v-else-if="!sm2Keys.length" class="muted">本项目暂无 SM2 密钥</span>
            </div>
            <div class="row">
              <span class="label">公钥(Base64)</span>
              <el-input v-model="sm2Pub" size="small" :disabled="!!sm2KeyId" style="width:420px" placeholder="65 字节未压缩公钥的 Base64"></el-input>
            </div>
            <div class="row">
              <span class="label">私钥(Base64)</span>
              <el-input v-model="sm2Priv" size="small" :disabled="!!sm2KeyId" style="width:420px" placeholder="32 字节私钥的 Base64"></el-input>
              <span class="label">签名(Base64/Hex)</span>
              <el-input v-model="sm2Sig" size="small" style="width:280px" placeholder="验签时填"></el-input>
            </div>
            <div class="row">
              <span class="label">密文/签名编码</span>
              <el-radio-group v-model="sm2Enc" size="small">
                <el-radio-button label="base64">Base64</el-radio-button><el-radio-button label="hex">Hex</el-radio-button>
              </el-radio-group>
              <el-button size="small" @click="genSm2">生成密钥对</el-button>
            </div>
            <div class="row">
              <el-button size="small" type="primary" :loading="running" @click="doSm2('encrypt')">加密(公钥)</el-button>
              <el-button size="small" :loading="running" @click="doSm2('decrypt')">解密(私钥)</el-button>
              <el-button size="small" :loading="running" @click="doSm2('sign')">签名(私钥)</el-button>
              <el-button size="small" :loading="running" @click="doSm2('verify')">验签(原文+签名+公钥)</el-button>
            </div>
          </div>
          <div class="pane">
            <textarea v-model="sm2In" class="io" placeholder="加密/签名填原文；解密填密文；验签填原文（结果见下）"></textarea>
            <div class="mid"><el-button circle size="small" @click="sm2In = sm2Out; sm2Out = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="sm2Out" class="io out" readonly placeholder="加密/解密/签名结果"></textarea>
          </div>
          <el-alert v-if="sm2VerifyRes !== null" :title="sm2VerifyRes ? '✅ 验签通过' : '❌ 验签失败（签名/公钥/原文不匹配）'"
                    :type="sm2VerifyRes ? 'success' : 'error'" :closable="false" show-icon style="margin-top:10px" />
        </el-tab-pane>

        <!-- ===================== RSA ===================== -->
        <el-tab-pane label="RSA" name="rsa">
          <div class="cfg">
            <div class="row">
              <span class="label">引用密钥</span>
              <el-select v-model="rsaKeyId" clearable filterable size="small" style="width:260px" placeholder="选项目 RSA 密钥，免粘贴">
                <el-option v-for="k in rsaKeys" :key="k.id" :label="k.alias" :value="k.id" />
              </el-select>
              <span v-if="rsaKeyId" class="muted">已引用：公/私钥由服务端从密钥取</span>
              <span v-else-if="!proj.id" class="muted">请先选择项目</span>
              <span v-else-if="!rsaKeys.length" class="muted">本项目暂无 RSA 密钥</span>
            </div>
            <div class="row">
              <span class="label">公钥</span>
              <el-input v-model="rsaPub" type="textarea" :rows="2" :disabled="!!rsaKeyId" size="small" style="width:560px" placeholder="X509 Base64 或 PEM（-----BEGIN PUBLIC KEY-----）"></el-input>
            </div>
            <div class="row">
              <span class="label">私钥</span>
              <el-input v-model="rsaPriv" type="textarea" :rows="2" :disabled="!!rsaKeyId" size="small" style="width:560px" placeholder="PKCS8 Base64 或 PEM（-----BEGIN PRIVATE KEY-----）"></el-input>
            </div>
            <div class="row">
              <span class="label">签名(Base64)</span>
              <el-input v-model="rsaSig" size="small" style="width:320px" placeholder="验签时填"></el-input>
              <span class="label">密钥位数</span>
              <el-select v-model="rsaBits" size="small" style="width:110px">
                <el-option :value="1024" label="1024" /><el-option :value="2048" label="2048" /><el-option :value="4096" label="4096" />
              </el-select>
              <el-button size="small" @click="genRsa">生成密钥对</el-button>
            </div>
            <div class="row">
              <el-button size="small" type="primary" :loading="running" @click="doRsa('encrypt')">加密(公钥)</el-button>
              <el-button size="small" :loading="running" @click="doRsa('decrypt')">解密(私钥)</el-button>
              <el-button size="small" :loading="running" @click="doRsa('sign')">签名(私钥)</el-button>
              <el-button size="small" :loading="running" @click="doRsa('verify')">验签(原文+签名+公钥)</el-button>
              <span class="muted">RSA/PKCS1 单块加密有长度上限（2048 位约 245 字节）</span>
            </div>
          </div>
          <div class="pane">
            <textarea v-model="rsaIn" class="io" placeholder="加密/签名填原文；解密填密文；验签填原文"></textarea>
            <div class="mid"><el-button circle size="small" @click="rsaIn = rsaOut; rsaOut = ''" title="输出 → 输入">⇄</el-button></div>
            <textarea v-model="rsaOut" class="io out" readonly placeholder="结果"></textarea>
          </div>
          <el-alert v-if="rsaVerifyRes !== null" :title="rsaVerifyRes ? '✅ 验签通过' : '❌ 验签失败'"
                    :type="rsaVerifyRes ? 'success' : 'error'" :closable="false" show-icon style="margin-top:10px" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { copyText } from '../utils/clipboard'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const tab = ref('json')
const running = ref(false)

// 引用项目密钥（免粘贴）：SM2/RSA 取公私钥，SM4 取对称密钥+IV；私钥在服务端解析、不回前端
const keys = ref([])
const sm2KeyId = ref('')
const sm4KeyId = ref('')
const rsaKeyId = ref('')
const sm2Keys = computed(() => keys.value.filter((k) => k.algorithm === 'SM2'))
const sm4Keys = computed(() => keys.value.filter((k) => k.algorithm === 'SM4'))
const rsaKeys = computed(() => keys.value.filter((k) => k.algorithm === 'RSA'))
async function loadKeys() {
  if (!proj.id) { keys.value = []; return }
  try { const res = await api.keys.list(proj.id); keys.value = res.data || [] } catch (e) { keys.value = [] }
}
watch(() => proj.id, () => { sm2KeyId.value = ''; sm4KeyId.value = ''; rsaKeyId.value = ''; loadKeys() })
onMounted(loadKeys)

async function exec(call, payload, onOk) {
  running.value = true
  try {
    const res = await call(payload)
    if (onOk) onOk(res.data)
  } catch (e) {
    /* http 拦截器已弹错误提示 */
  } finally {
    running.value = false
  }
}

// 按当前 tab 复制对应输出框内容
function copyCurrentOut() {
  const m = { json: jsonOut, xml: xmlOut, sql: sqlOut, sm3: sm3Out, sm4: sm4Out, sm2: sm2Out, rsa: rsaOut, enc: encOut }
  const r = m[tab.value]
  const v = r ? r.value : ''
  if (!v) { ElMessage.warning('内容为空'); return }
  copyText(v)
}

// ---------- JSON ----------
const jsonIn = ref('')
const jsonOut = ref('')
const jsonIndent = ref('2')
function doJson(act) {
  const map = { format: 'jsonFormat', minify: 'jsonMinify', validate: 'jsonValidate', escape: 'jsonEscape', unescape: 'jsonUnescape' }
  exec(api.tools[map[act]], { text: jsonIn.value, indent: jsonIndent.value }, (v) => { jsonOut.value = v; ElMessage.success('完成') })
}

// ---------- XML ----------
const xmlIn = ref('')
const xmlOut = ref('')
const xmlIndent = ref('2')
function doXml(act) {
  const map = { format: 'xmlFormat', minify: 'xmlMinify', validate: 'xmlValidate', escape: 'xmlEscape', unescape: 'xmlUnescape' }
  exec(api.tools[map[act]], { text: xmlIn.value, indent: xmlIndent.value }, (v) => { xmlOut.value = v; ElMessage.success('完成') })
}

// ---------- SQL ----------
const sqlIn = ref('')
const sqlOut = ref('')
function doSql() {
  exec(api.tools.sqlFormat, { text: sqlIn.value }, (v) => { sqlOut.value = v; ElMessage.success('完成') })
}

// ---------- 编码 / 转换 ----------
const encMode = ref('base64')
const encIn = ref('')
const encOut = ref('')
const hashAlgo = ref('MD5')
const uuidCount = ref(1)
const uuidUpper = ref(false)
const TWO_WAY = { base64: ['b64Encode', 'b64Decode'], hex: ['hexEncode', 'hexDecode'], url: ['urlEncode', 'urlDecode'] }
function enc2way(dir) {
  const apiName = TWO_WAY[encMode.value][dir === 'encode' ? 0 : 1]
  exec(api.tools[apiName], { text: encIn.value }, (v) => { encOut.value = v; ElMessage.success('完成') })
}
function doHash() {
  exec(api.tools.hash, { text: encIn.value, algo: hashAlgo.value }, (v) => { encOut.value = v; ElMessage.success('完成') })
}
function doJwt() {
  exec(api.tools.jwtDecode, { text: encIn.value }, (v) => {
    encOut.value = '【Header】\n' + v.header + '\n\n【Payload】\n' + v.payload + '\n\n【Signature】\n' + v.signature
    ElMessage.success('已解析')
  })
}
function formatTs(v) {
  return '秒  : ' + v.seconds + '\n毫秒: ' + v.millis + '\nISO : ' + v.iso + '\n本地: ' + v.local
}
function doTsNow() {
  exec(api.tools.tsNow, null, (v) => { encOut.value = formatTs(v); ElMessage.success('当前时间') })
}
function doTsToEpoch() {
  exec(api.tools.tsToEpoch, { text: encIn.value }, (v) => { encOut.value = formatTs(v); ElMessage.success('完成') })
}
function doTsFromEpoch() {
  exec(api.tools.tsFromEpoch, { text: encIn.value }, (v) => { encOut.value = formatTs(v); ElMessage.success('完成') })
}
function doUuid() {
  exec(api.tools.uuid, { count: uuidCount.value }, (v) => {
    encOut.value = uuidUpper.value ? v.toUpperCase() : v
    ElMessage.success('已生成 ' + uuidCount.value + ' 个')
  })
}

// ---------- SM3 ----------
const sm3In = ref('')
const sm3Out = ref('')
const sm3OutEnc = ref('hex')
function doSm3() {
  exec(api.tools.sm3, { text: sm3In.value, outEnc: sm3OutEnc.value }, (v) => { sm3Out.value = v; ElMessage.success('完成') })
}

// ---------- SM4 ----------
const sm4In = ref('')
const sm4Out = ref('')
const sm4Key = ref('')
const sm4Iv = ref('')
const sm4Mode = ref('ECB')
const sm4KeyEnc = ref('utf8')
const sm4Enc = ref('base64')
function doSm4(act) {
  const p = { text: sm4In.value, key: sm4Key.value, iv: sm4Iv.value, mode: sm4Mode.value, keyEnc: sm4KeyEnc.value, outEnc: sm4Enc.value, inEnc: sm4Enc.value, keyId: sm4KeyId.value || undefined }
  exec(act === 'encrypt' ? api.tools.sm4Encrypt : api.tools.sm4Decrypt, p, (v) => { sm4Out.value = v; ElMessage.success('完成') })
}
function genSm4Key() {
  exec(api.tools.sm4Key, null, (v) => { sm4Key.value = v; sm4KeyEnc.value = 'hex'; ElMessage.success('已生成 128 位密钥(Hex)') })
}

// ---------- SM2 ----------
const sm2In = ref('')
const sm2Out = ref('')
const sm2Pub = ref('')
const sm2Priv = ref('')
const sm2Sig = ref('')
const sm2Enc = ref('base64')
const sm2VerifyRes = ref(null)
function doSm2(act) {
  sm2VerifyRes.value = null
  const keyId = sm2KeyId.value || undefined
  if (act === 'encrypt') {
    exec(api.tools.sm2Encrypt, { text: sm2In.value, pub: sm2Pub.value, outEnc: sm2Enc.value, keyId }, (v) => { sm2Out.value = v; ElMessage.success('加密完成') })
  } else if (act === 'decrypt') {
    exec(api.tools.sm2Decrypt, { text: sm2In.value, priv: sm2Priv.value, inEnc: sm2Enc.value, keyId }, (v) => { sm2Out.value = v; ElMessage.success('解密完成') })
  } else if (act === 'sign') {
    exec(api.tools.sm2Sign, { text: sm2In.value, priv: sm2Priv.value, outEnc: sm2Enc.value, keyId }, (v) => { sm2Out.value = v; ElMessage.success('签名完成') })
  } else if (act === 'verify') {
    exec(api.tools.sm2Verify, { text: sm2In.value, sig: sm2Sig.value, pub: sm2Pub.value, inEnc: sm2Enc.value, keyId }, (v) => { sm2VerifyRes.value = v })
  }
}
function genSm2() {
  exec(api.tools.sm2Keypair, null, (v) => { sm2Pub.value = v.publicKey; sm2Priv.value = v.privateKey; ElMessage.success('已生成 SM2 密钥对') })
}

// ---------- RSA ----------
const rsaIn = ref('')
const rsaOut = ref('')
const rsaPub = ref('')
const rsaPriv = ref('')
const rsaSig = ref('')
const rsaBits = ref(2048)
const rsaVerifyRes = ref(null)
function doRsa(act) {
  rsaVerifyRes.value = null
  const keyId = rsaKeyId.value || undefined
  if (act === 'encrypt') {
    exec(api.tools.rsaEncrypt, { text: rsaIn.value, pub: rsaPub.value, keyId }, (v) => { rsaOut.value = v; ElMessage.success('加密完成') })
  } else if (act === 'decrypt') {
    exec(api.tools.rsaDecrypt, { text: rsaIn.value, priv: rsaPriv.value, keyId }, (v) => { rsaOut.value = v; ElMessage.success('解密完成') })
  } else if (act === 'sign') {
    exec(api.tools.rsaSign, { text: rsaIn.value, priv: rsaPriv.value, keyId }, (v) => { rsaOut.value = v; ElMessage.success('签名完成') })
  } else if (act === 'verify') {
    exec(api.tools.rsaVerify, { text: rsaIn.value, sig: rsaSig.value, pub: rsaPub.value, keyId }, (v) => { rsaVerifyRes.value = v })
  }
}
function genRsa() {
  exec(api.tools.rsaKeypair, { bits: rsaBits.value }, (v) => { rsaPub.value = v.publicKey; rsaPriv.value = v.privateKey; ElMessage.success('已生成 RSA 密钥对') })
}

// ---- 输入/配置持久化到 localStorage（刷新不丢，常用工具免重填密钥/原文）----
const PERSIST_KEY = 'mirage_tools_state'
const persistRefs = {
  tab, jsonIn, xmlIn, sqlIn, encIn, encMode, hashAlgo, uuidCount, uuidUpper,
  jsonIndent, xmlIndent,
  sm3In, sm3OutEnc, sm4In, sm4Key, sm4Iv, sm4Mode, sm4KeyEnc, sm4Enc,
  sm2In, sm2Pub, sm2Priv, sm2Sig, sm2Enc,
  rsaIn, rsaPub, rsaPriv, rsaSig, rsaBits
}
try {
  const saved = JSON.parse(localStorage.getItem(PERSIST_KEY) || '{}')
  for (const [k, r] of Object.entries(persistRefs)) {
    if (saved[k] != null && typeof saved[k] !== 'object') r.value = saved[k]
  }
} catch (e) { /* 忽略损坏的缓存 */ }
watchEffect(() => {
  const data = {}
  for (const [k, r] of Object.entries(persistRefs)) data[k] = r.value
  try { localStorage.setItem(PERSIST_KEY, JSON.stringify(data)) } catch (e) { /* 配额满忽略 */ }
})
</script>

<style scoped>
.tools-page .card-header { display: flex; align-items: baseline; justify-content: space-between; }
.tools-page .hint { font-size: 12px; color: #909399; }
.bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.cfg { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; margin-bottom: 10px; }
.cfg .row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.cfg .row:last-child { margin-bottom: 0; }
.label { font-size: 13px; color: #606266; white-space: nowrap; }
.muted { font-size: 12px; color: #909399; }
.pane { display: flex; align-items: stretch; gap: 6px; }
.io { flex: 1; min-height: 220px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; resize: vertical; outline: none; }
.io:focus { border-color: #409eff; }
.io.out { background: #f7f9fb; }
.mid { display: flex; align-items: center; }
</style>
