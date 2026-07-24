<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">
        <img :src="logoDark" class="brand-logo" alt="蜃楼" />
        <span class="title">蜃楼</span>
      </div>
      <div class="subtitle">海市蜃楼，'以假乱真'恰好是MOCK的本质。</div>
      <el-divider class="sep" />
      <el-form :model="form" label-position="top" @submit.prevent="onLogin" autocomplete="off">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="off" name="mirage-user" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" autocomplete="new-password" name="mirage-pwd" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="login-btn" @click="onLogin">登录</el-button>
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
const form = reactive({ username: '', password: '' })
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
  width: 380px;
  padding: 28px 28px 18px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.brand-logo {
  height: 34px;
}
.title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2a44;
  letter-spacing: 1px;
}
.subtitle {
  color: #5b6b7c;
  font-size: 13px;
  line-height: 1.7;
}
.sep {
  margin: 14px 0 20px;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
