<template>
  <div class="fn-market">
    <el-input v-model="keyword" placeholder="搜索函数 / 分类" size="small" clearable style="margin-bottom: 8px" />
    <div style="max-height: 360px; overflow: auto">
      <div v-for="grp in groups" :key="grp.category" style="margin-bottom: 12px">
        <div class="cat">
          {{ grp.category }}
          <span v-if="grp.category === '加解密签名'" class="cat-hint">（点击选密钥）</span>
        </div>
        <div
          v-for="f in grp.items"
          :key="f.name"
          class="fn-item mono"
          :title="f.description + '  示例：' + f.example"
        >
          <span class="fn-name" @click="onPick(f)">{{ f.name }}</span>
          <el-icon class="copy-ic" title="复制 ${...}" @click.stop="copyFn(f)"><CopyDocument /></el-icon>
        </div>
      </div>
      <el-empty v-if="!groups.length" description="无匹配函数" :image-size="40" />
    </div>

    <!-- 加密/签名函数：选择当前项目的密钥别名后插入 -->
    <el-dialog v-model="pickerVisible" title="选择密钥别名" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="函数">
          <span class="mono">{{ pendingFn && pendingFn.name }}</span>
        </el-form-item>
        <el-form-item label="密钥别名">
          <el-select v-model="chosenAlias" placeholder="选择本项目密钥" style="width: 100%" filterable>
            <el-option v-for="k in keyAliases" :key="k.alias" :label="k.alias + '（' + k.algorithm + '）'" :value="k.alias" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!chosenAlias" @click="confirmInsert">插入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { api } from '../api'
import { copyText } from '../utils/clipboard'

const props = defineProps({ projectId: { type: [Number, String], default: null } })
const emit = defineEmits(['insert'])

const list = ref([])
const keyword = ref('')
// 当前项目的密钥别名（加密/签名函数引用）
const keyAliases = ref([])

const pickerVisible = ref(false)
const pendingFn = ref(null)
const chosenAlias = ref('')

const groups = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const map = new Map()
  for (const f of list.value) {
    if (k && !(f.name + f.category + f.description).toLowerCase().includes(k)) continue
    if (!map.has(f.category)) map.set(f.category, { category: f.category, items: [] })
    map.get(f.category).items.push(f)
  }
  return Array.from(map.values())
})

function snippetFor(f, alias) {
  // sm2_verify 需三参：原文、签名、公钥别名
  if (f.name === 'sm2_verify') {
    return "${sm2_verify(${field.原文}, ${field.签名}, '" + alias + "')}"
  }
  // 其余加解密/签名：${fn(${field.xxx}, 'alias')}，data 占位由用户替换
  return '${' + f.name + "(${field.xxx}, '" + alias + "')}"
}

function onPick(f) {
  if (f.category === '加解密签名') {
    pickCrypto(f)
  } else {
    emit('insert', '${' + f.name + '}')
  }
}

function copyFn(f) {
  copyText('${' + f.name + '}')
}

function pickCrypto(f) {
  if (!props.projectId || !keyAliases.value.length) {
    ElMessage.warning('当前项目暂无密钥，请先到「密钥管理」录入或生成；已插入占位别名 key_alias')
    emit('insert', snippetFor(f, 'key_alias'))
    return
  }
  pendingFn.value = f
  chosenAlias.value = keyAliases.value[0].alias
  pickerVisible.value = true
}

function confirmInsert() {
  if (pendingFn.value && chosenAlias.value) {
    emit('insert', snippetFor(pendingFn.value, chosenAlias.value))
  }
  pickerVisible.value = false
}

async function loadKeys() {
  if (!props.projectId) {
    keyAliases.value = []
    return
  }
  try {
    const res = await api.keys.list(props.projectId)
    keyAliases.value = (res.data || []).map((k) => ({ alias: k.alias, algorithm: k.algorithm }))
  } catch {
    keyAliases.value = []
  }
}

onMounted(async () => {
  const res = await api.functions.list()
  list.value = res.data || []
  loadKeys()
})

watch(() => props.projectId, loadKeys)
</script>

<style scoped>
.fn-market .cat {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 2px;
}
.cat-hint {
  color: #67c23a;
  margin-left: 4px;
}
.fn-item {
  font-size: 13px;
  padding: 3px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}
.fn-item .fn-name {
  cursor: pointer;
  flex: 1;
  min-width: 0;
}
.fn-item .copy-ic {
  cursor: pointer;
  color: #c0c4cc;
  flex-shrink: 0;
}
.fn-item:hover .copy-ic {
  color: #409eff;
}
</style>
