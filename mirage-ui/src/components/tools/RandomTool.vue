<template>
  <div class="rd">
    <div class="rd-bar">
      <span class="rd-label">类型：</span>
      <el-select v-model="type" size="small" style="width:150px">
        <el-option v-for="t in TYPES" :key="t" :label="t" :value="t" />
      </el-select>

      <template v-if="type === 'string'">
        <span class="rd-label">长度：</span>
        <el-input-number v-model="opts.len" :min="1" :max="200" size="small" controls-position="right" style="width:110px" />
        <span class="rd-label">字符集：</span>
        <el-select v-model="opts.charsetKey" size="small" style="width:140px">
          <el-option v-for="(v, k) in CHARSETS" :key="k" :label="k" :value="k" />
        </el-select>
      </template>

      <template v-else-if="type === 'int' || type === 'float'">
        <span class="rd-label">最小：</span>
        <el-input-number v-model="opts.min" size="small" controls-position="right" style="width:120px" />
        <span class="rd-label">最大：</span>
        <el-input-number v-model="opts.max" size="small" controls-position="right" style="width:120px" />
        <template v-if="type === 'float'">
          <span class="rd-label">小数位：</span>
          <el-input-number v-model="opts.decimals" :min="0" :max="10" size="small" controls-position="right" style="width:100px" />
        </template>
      </template>

      <template v-else-if="type === 'datetime'">
        <span class="rd-label">天数范围（往前推）：</span>
        <el-input-number v-model="opts.startDaysAgo" :min="0" :max="3650" size="small" controls-position="right" style="width:120px" />
        <span class="rd-label">~</span>
        <el-input-number v-model="opts.endDaysAgo" :min="0" :max="3650" size="small" controls-position="right" style="width:120px" />
      </template>

      <span class="rd-label">数量：</span>
      <el-input-number v-model="count" :min="1" :max="200" size="small" controls-position="right" style="width:100px" />
    </div>

    <!-- 身份证专属选项：地区 / 年龄段 / 性别 -->
    <div v-if="type === 'idcard'" class="rd-bar rd-idcard">
      <span class="rd-label">地区：</span>
      <el-select v-model="province" size="small" style="width:170px" placeholder="随机省份">
        <el-option label="随机省份" value="" />
        <el-option v-for="p in REGIONS" :key="p.code" :label="p.name" :value="p.code" />
      </el-select>
      <el-select v-model="city" size="small" style="width:170px" :disabled="!province" placeholder="随机城市">
        <el-option label="随机城市" value="" />
        <el-option v-for="c in cities" :key="c.code" :label="c.name" :value="c.code" />
      </el-select>

      <span class="rd-label">年龄段：</span>
      <el-input-number v-model="opts.minAge" class="rd-age" :min="0" :max="120" size="small" controls-position="right" style="width:100px" />
      <span class="rd-label">~</span>
      <el-input-number v-model="opts.maxAge" class="rd-age" :min="0" :max="120" size="small" controls-position="right" style="width:100px" />
      <span class="rd-label">岁</span>

      <span class="rd-label">性别：</span>
      <el-radio-group v-model="opts.gender" size="small">
        <el-radio-button value="male">男</el-radio-button>
        <el-radio-button value="female">女</el-radio-button>
        <el-radio-button value="random">随机</el-radio-button>
      </el-radio-group>

      <span class="rd-sub">末两位为区县码（01-99 随机），仅供造测试数据</span>
    </div>

    <div class="rd-bar">
      <span class="rd-label">种子（可选，同种子结果可复现）：</span>
      <el-input v-model="seed" size="small" style="width:180px" placeholder="如 case-001" />
      <el-button size="small" type="primary" @click="gen">生成</el-button>
      <el-button size="small" :disabled="!values.length" @click="copyText(values.join('\n'))">复制全部</el-button>
      <el-button size="small" :disabled="!values.length" @click="values = []">清空</el-button>
    </div>

    <el-table v-if="values.length" :data="rows" size="small" border max-height="420">
      <el-table-column prop="idx" label="#" width="56" />
      <el-table-column prop="value" label="值" show-overflow-tooltip />
      <el-table-column v-if="type === 'idcard'" label="解析" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.detail">{{ row.detail }}</span>
          <el-tag v-else size="small" type="danger">解析失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="copyText(row.value)">复制</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-else class="rd-empty">点「生成」批量造测试数据</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { CHARSETS, generate, seededRandom, parseIdCard, REGIONS } from '../../utils/random'
import { copyText } from '../../utils/clipboard'

const TYPES = ['string', 'int', 'float', 'phone', 'idcard', 'email', 'bankcard', 'name', 'datetime', 'uuid']

const type = ref('phone')
const count = ref(5)
const seed = ref('')
/** 身份证的省/市（空 = 随机） */
const province = ref('')
const city = ref('')
const opts = reactive({
  len: 10,
  charsetKey: 'alpha',
  min: 1,
  max: 1000,
  decimals: 2,
  startDaysAgo: 30,
  endDaysAgo: 0,
  minAge: 18,
  maxAge: 60,
  gender: 'random'
})
const values = ref([])

const cities = computed(() => {
  const p = REGIONS.find((x) => x.code === province.value)
  return p ? p.cities : []
})
// 换了省，之前选的市就不作数了
watch(province, () => { city.value = '' })

const rows = computed(() => values.value.map((v, i) => {
  const row = { idx: i + 1, value: v, detail: '' }
  if (type.value === 'idcard') {
    const p = parseIdCard(v)
    row.detail = p.valid
      ? `${p.areaName || '未知地区'}　${p.birth.slice(0, 4)}-${p.birth.slice(4, 6)}-${p.birth.slice(6, 8)}　${p.age}岁　${p.genderText}`
      : ''
  }
  return row
}))

function gen() {
  // 种子非空时用可复现随机源；每个值推进一次随机数
  const rnd = seed.value.trim() ? seededRandom(seed.value.trim()) : Math.random
  const o = Object.assign({}, opts)
  if (type.value === 'string') {
    o.charset = CHARSETS[opts.charsetKey] || CHARSETS.alpha
  }
  if (type.value === 'idcard') {
    o.provinceCode = province.value
    o.cityCode = city.value
  }
  const out = []
  for (let i = 0; i < count.value; i++) {
    out.push(generate(type.value, o, rnd))
  }
  values.value = out
}
</script>

<style scoped>
.rd-label { font-size: 13px; color: #606266; white-space: nowrap; }
.rd-sub { font-size: 12px; color: #909399; }
.rd-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.rd-idcard { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px 10px; }
.rd-empty { color: #909399; font-size: 13px; padding: 20px 0; text-align: center; }
</style>
