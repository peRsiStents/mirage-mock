<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>项目管理</span>
          <div>
            <el-button :loading="sampleLoading" @click="onLoadSample" title="生成含 HTTP 接口与 Mock 规则的示例项目">载入示例</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
          </div>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <template #empty>
          <el-empty description="还没有项目，新建一个或一键载入示例开始体验">
            <div style="display:flex;gap:8px;justify-content:center">
              <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
              <el-button :loading="sampleLoading" @click="onLoadSample">一键载入示例项目</el-button>
            </div>
          </el-empty>
        </template>
        <el-table-column prop="name" label="项目名称" />
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="ruleVersion" label="规则版本" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="340">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="useProject(row)">设为当前</el-button>
            <el-button size="small" type="primary" link @click="openMembers(row)">成员</el-button>
            <el-button size="small" type="primary" link @click="openCi(row)">CI 接入</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑项目' : '新建项目'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" title="项目成员" width="560px">
      <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center">
        <el-select v-model="newMember.userId" filterable placeholder="选择用户" style="width: 220px">
          <el-option v-for="u in users" :key="u.id" :label="(u.nickname || u.username) + '（' + u.username + '）'" :value="u.id" />
        </el-select>
        <el-select v-model="newMember.memberRole" style="width: 130px">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通成员" value="MEMBER" />
        </el-select>
        <el-button type="primary" @click="addMember">添加</el-button>
      </div>
      <el-table :data="members" border size="small">
        <el-table-column label="用户">
          <template #default="{ row }">{{ userLabel(row.userId) }}</template>
        </el-table-column>
        <el-table-column prop="memberRole" label="角色" width="120" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="ciVisible" title="CI / 流水线接入" width="680px">
      <div v-if="ciProject">
        <div class="kv-title">CI Token（免 JWT 触发场景运行）</div>
        <el-input :model-value="ciProject.ciToken || '(未生成)'" readonly>
          <template #append>
            <el-button :icon="CopyDocument" @click="copyText(ciProject.ciToken || '')">复制</el-button>
          </template>
        </el-input>
        <el-alert type="warning" :closable="false" style="margin-top:8px"
          title="Token 即该项目的运行密钥，请像密码一样保管；泄露后请在项目设置中重新生成。" />

        <div class="kv-title" style="margin-top:14px">调用示例（推荐用请求头传 token，避免进访问日志 / referer）</div>
        <pre class="ci-curl">{{ curlExample }}</pre>
        <div style="margin-top:6px">
          <el-button size="small" :icon="CopyDocument" @click="copyText(curlExample)">复制整段</el-button>
        </div>
        <div class="hint" style="margin-top:8px">
          <span class="mono">{场景ID}</span> 替换为「测试场景」页目标场景 ID；<span class="mono">env</span> 可选（指定运行环境）。
          末行 <span class="mono">jq -e</span> 通过返回退出码 0、失败非 0，可直接接 <span class="mono">&amp;&amp;</span> / <span class="mono">||</span>。
          运行历史见「测试报告」页；也兼容 <span class="mono">?token=xxx</span> 查询参数（旧调用，不建议）。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, CopyDocument } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { copyText } from '../utils/clipboard'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const sampleLoading = ref(false)

const formVisible = ref(false)
const form = reactive({ id: null, name: '', code: '', status: 1, remark: '' })

const memberVisible = ref(false)
const members = ref([])
const currentProject = ref(null)
const newMember = reactive({ userId: null, memberRole: 'MEMBER' })
const users = ref([])

// CI 接入
const ciVisible = ref(false)
const ciProject = ref(null)
const curlExample = computed(() => {
  const origin = window.location.origin
  const token = ciProject.value?.ciToken || '<token>'
  return [
    `curl -fsS -X POST "${origin}/api/v1/ci/scenarios/{场景ID}/run?env={环境ID,可选}" \\`,
    `  -H "X-CI-Token: ${token}" \\`,
    `  | jq -e '.data.passed == true'  # 通过退出码 0，失败非 0`
  ].join('\n')
})
function openCi(row) {
  ciProject.value = row
  ciVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const res = await api.projects.list()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', code: '', status: 1, remark: '' })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  formVisible.value = true
}

async function onSave() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请输入项目名称'); return }
  if (!form.id && (!form.code || !form.code.trim())) { ElMessage.warning('请输入项目编码 code'); return }
  saving.value = true
  try {
    if (form.id) {
      await api.projects.update(form.id, { name: form.name, status: form.status, remark: form.remark })
    } else {
      await api.projects.create({ name: form.name, code: form.code, status: form.status, remark: form.remark })
    }
    ElMessage.success('已保存')
    formVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  await ElMessageBox.confirm(`确认删除项目「${row.name}」？将级联删除其接口与规则`, '警告', { type: 'warning' })
  await api.projects.remove(row.id)
  ElMessage.success('已删除')
  load()
}

function useProject(row) {
  proj.select(row)
  ElMessage.success(`已切换到项目：${row.name}`)
}

async function onLoadSample() {
  sampleLoading.value = true
  try {
    const res = await api.projects.createSample()
    ElMessage.success('已生成示例项目：' + (res.data?.name || '示例项目') + '，含 2 个接口与若干 Mock 规则')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    sampleLoading.value = false
  }
}

async function openMembers(row) {
  currentProject.value = row
  const res = await api.projects.members(row.id)
  members.value = res.data || []
  memberVisible.value = true
}

async function addMember() {
  if (!newMember.userId) { ElMessage.warning('请先选择用户'); return }
  await api.projects.addMember(currentProject.value.id, { ...newMember })
  ElMessage.success('已添加')
  newMember.userId = null
  const res = await api.projects.members(currentProject.value.id)
  members.value = res.data || []
}

function userLabel(id) {
  const u = users.value.find((x) => String(x.id) === String(id))
  if (!u) return '#' + id
  return u.nickname ? `${u.nickname}（${u.username}）` : u.username
}

async function loadUsers() {
  try {
    const res = await api.users.list()
    users.value = res.data || []
  } catch {
    users.value = []
  }
}

async function removeMember(row) {
  await api.projects.removeMember(currentProject.value.id, row.userId)
  const res = await api.projects.members(currentProject.value.id)
  members.value = res.data || []
}

onMounted(() => { load(); loadUsers() })
</script>

<style scoped>
.kv-title { font-size: 13px; color: #606266; margin: 8px 0 6px; }
.hint { font-size: 12px; color: #909399; line-height: 1.7; margin-top: 6px; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; }
.ci-curl {
  background: #f5f7fa; padding: 10px 12px; border-radius: 4px; margin: 4px 0 8px;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px;
  white-space: pre-wrap; word-break: break-all; color: #303133;
}
</style>
