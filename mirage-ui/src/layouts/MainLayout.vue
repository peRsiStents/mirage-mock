<template>
  <el-container style="height: 100vh">
    <el-aside width="210px" style="background: #001529">
      <div class="logo">
        <img :src="logoLight" class="logo-img" alt="蜃楼 Mock" />
        <span class="logo-text">蜃楼 Mock</span>
      </div>
      <el-menu :default-active="route.path" :router="true" background-color="#001529" text-color="#cfd8e3" active-text-color="#409eff">
        <el-menu-item index="/projects"><el-icon><Files /></el-icon><span>项目管理</span></el-menu-item>
        <el-menu-item index="/interfaces"><el-icon><Connection /></el-icon><span>HTTP 接口</span></el-menu-item>
        <el-menu-item index="/listeners"><el-icon><Operation /></el-icon><span>TCP 监听</span></el-menu-item>
        <el-menu-item index="/keys"><el-icon><Key /></el-icon><span>密钥管理</span></el-menu-item>
        <el-menu-item index="/logs"><el-icon><Document /></el-icon><span>请求日志</span></el-menu-item>
        <el-menu-item index="/functions"><el-icon><Grid /></el-icon><span>函数市场</span></el-menu-item>
        <el-menu-item index="/file-gen"><el-icon><DocumentCopy /></el-icon><span>文件生成</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div class="project-sel">
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
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
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
watch(() => proj.id, (v) => { projId.value = v })
</script>

<style scoped>
.logo {
  height: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.logo-img {
  height: 40px;
}
.logo-text {
  color: #fff;
  font-size: 13px;
  letter-spacing: 2px;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
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
