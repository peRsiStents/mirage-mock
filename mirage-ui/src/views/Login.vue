<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="logo-wrap"><img :src="logoDark" class="brand-logo" alt="蜃楼 Mock" /></div>
      <div class="title">蜃楼 Mock</div>
      <div class="subtitle">接口 Mock 数据生成系统</div>
      <el-form :model="form" label-position="top" @submit.prevent="onLogin">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="admin123" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onLogin">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'
import logoDark from '../assets/logodark.png'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2a44, #2d4373);
}
.login-card {
  width: 360px;
  padding: 12px 8px;
}
.logo-wrap {
  text-align: center;
  margin-bottom: 10px;
}
.brand-logo {
  height: 64px;
}
.title {
  font-size: 24px;
  font-weight: 700;
  text-align: center;
  color: #1f2a44;
}
.subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 18px;
  font-size: 13px;
}
</style>
