<template>
  <el-container style="height: 100vh">
    <el-aside :width="collapsed ? '64px' : '210px'" class="sidebar">
      <div class="logo">
        <img :src="logoLight" class="logo-img" alt="蜃楼" />
        <span v-show="!collapsed" class="logo-text">蜃楼</span>
      </div>
      <el-menu :default-active="route.path" :router="true" :default-openeds="openedGroups" :collapse="collapsed" background-color="#001529" text-color="#cfd8e3" active-text-color="#409eff">
        <template v-for="g in visibleGroups" :key="g.title">
          <el-menu-item v-if="g.items.length === 1" :index="g.items[0].path">
            <el-icon><component :is="g.items[0].icon" /></el-icon><span>{{ g.items[0].label }}</span>
          </el-menu-item>
          <el-sub-menu v-else :index="g.title">
            <template #title><el-icon><component :is="g.icon" /></el-icon><span>{{ g.title }}</span></template>
            <el-menu-item v-for="it in g.items" :key="it.path" :index="it.path">
              <el-icon><component :is="it.icon" /></el-icon><span>{{ it.label }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <el-button class="collapse-btn" link @click="collapsed = !collapsed" :title="collapsed ? '展开菜单' : '收起菜单'">
            <el-icon :size="20"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </el-button>
          <el-breadcrumb class="crumb" separator="/" v-if="current">
            <el-breadcrumb-item v-if="current.group !== current.label">{{ current.group }}</el-breadcrumb-item>
            <el-breadcrumb-item><b>{{ current.label }}</b></el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <div class="project-sel" v-if="showProjectSwitcher">
            <span class="label">当前项目：</span>
            <el-select v-model="projId" placeholder="请选择项目" style="width: 240px" @change="onProjectChange">
              <el-option v-for="p in projects" :key="p.id" :label="p.name + ' (' + p.code + ')'" :value="p.id" />
            </el-select>
          </div>
          <el-dropdown @command="onUserCmd">
            <span class="user">{{ auth.username }} <el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useAuthStore } from '../store/auth'
import { useProjectStore } from '../store/project'
import logoLight from '../assets/logo.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const proj = useProjectStore()

const projects = ref([])
const projId = ref(proj.id || null)
// 侧栏折叠（图标导航），记忆到 localStorage
const collapsed = ref(localStorage.getItem('mirage_sidebar_collapsed') === '1')
watch(collapsed, (v) => localStorage.setItem('mirage_sidebar_collapsed', v ? '1' : '0'))

// 数据驱动的分组菜单（同时用于面包屑）；单元素组平铺，多元素组用 el-sub-menu
const menuGroups = [
  { title: '工作台', icon: 'Odometer', items: [{ path: '/dashboard', icon: 'Odometer', label: '工作台' }] },
  { title: '项目管理', icon: 'Files', items: [{ path: '/projects', icon: 'Files', label: '项目管理' }] },
  { title: 'Mock 配置', icon: 'Connection', items: [
    { path: '/interfaces', icon: 'Connection', label: 'HTTP 接口' },
    { path: '/listeners', icon: 'Operation', label: 'TCP 监听' },
    { path: '/keys', icon: 'Key', label: '密钥管理' },
    { path: '/logs', icon: 'Document', label: '请求日志' }
  ] },
  { title: '测试平台', icon: 'Promotion', items: [
    { path: '/testcases', icon: 'Promotion', label: '测试用例' },
    { path: '/scenarios', icon: 'Share', label: '测试场景' },
    { path: '/environments', icon: 'Place', label: '环境管理' },
    { path: '/reports', icon: 'DataAnalysis', label: '测试报告' },
    { path: '/schedules', icon: 'AlarmClock', label: '定时任务' }
  ] },
  { title: '辅助工具', icon: 'Briefcase', items: [
    { path: '/functions', icon: 'Grid', label: '函数库' },
    { path: '/tools', icon: 'Tools', label: '工具集' },
    { path: '/file-gen', icon: 'DocumentCopy', label: '文件生成' }
  ] },
  { title: '系统', icon: 'Setting', admin: true, items: [{ path: '/users', icon: 'User', label: '用户管理' }] }
]
const visibleGroups = computed(() => menuGroups.filter((g) => !g.admin || auth.isAdmin))
// 仅展开当前路由所在分组，降低首屏认知负载
const activeGroupTitle = computed(() => {
  for (const g of visibleGroups.value) {
    if (g.items.some((i) => i.path === route.path)) return g.title
  }
  return null
})
const openedGroups = computed(() => (activeGroupTitle.value ? [activeGroupTitle.value] : []))
// 这些页面与项目无关或本身是跨项目管理，隐藏顶部项目切换器以免语义冲突
const NON_PROJECT_PAGES = ['/projects', '/functions', '/tools', '/users']
const showProjectSwitcher = computed(() => !NON_PROJECT_PAGES.includes(route.path))
const current = computed(() => {
  for (const g of menuGroups) {
    const it = g.items.find((i) => i.path === route.path)
    if (it) return { group: g.title, label: it.label }
  }
  return null
})

async function loadProjects() {
  const res = await api.projects.list()
  projects.value = res.data || []
  if (!proj.id && projects.value.length) {
    onProjectChange(projects.value[0].id)
  }
}

function onProjectChange(id) {
  const p = projects.value.find((x) => x.id === id)
  if (p) {
    proj.select(p)
    projId.value = id
  }
}

function onUserCmd(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push({ name: 'login' })
  }
}

onMounted(loadProjects)
watch(() => proj.id, (v) => {
  projId.value = v
  // 切换项目（无路由跳转）时同步浏览器标题
  const t = route.meta?.title
  const parts = [t, proj.name].filter(Boolean)
  document.title = (parts.length ? parts.join(' · ') + ' · ' : '') + '蜃楼 Mock'
})
</script>

<style scoped>
.sidebar {
  background: #001529;
  transition: width 0.2s ease;
  overflow-x: hidden;
}
.logo {
  height: 56px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
}
.logo-img {
  height: 32px;
}
.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  white-space: nowrap;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.collapse-btn {
  padding: 0;
  color: #606266;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.crumb {
  font-size: 14px;
}
.project-sel .label {
  color: #606266;
  margin-right: 6px;
}
.user {
  cursor: pointer;
  color: #303133;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
