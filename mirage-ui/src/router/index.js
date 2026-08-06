import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { useProjectStore } from '../store/project'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'projects', name: 'projects', component: () => import('../views/Projects.vue'), meta: { title: '项目管理' } },
      { path: 'interfaces', name: 'interfaces', component: () => import('../views/Interfaces.vue'), meta: { title: 'HTTP 接口' } },
      { path: 'listeners', name: 'listeners', component: () => import('../views/TcpListeners.vue'), meta: { title: 'TCP 监听' } },
      { path: 'keys', name: 'keys', component: () => import('../views/Keys.vue'), meta: { title: '密钥管理' } },
      { path: 'logs', name: 'logs', component: () => import('../views/Logs.vue'), meta: { title: '请求日志' } },
      { path: 'functions', name: 'functions', component: () => import('../views/Functions.vue'), meta: { title: '函数库' } },
      { path: 'tools', name: 'tools', component: () => import('../views/Tools.vue'), meta: { title: '工具集' } },
      { path: 'file-gen', name: 'file-gen', component: () => import('../views/FileGen.vue'), meta: { title: '文件生成' } },
      { path: 'testcases', name: 'testcases', component: () => import('../views/TestCases.vue'), meta: { title: '测试用例' } },
      { path: 'scenarios', name: 'scenarios', component: () => import('../views/Scenarios.vue'), meta: { title: '测试场景' } },
      { path: 'environments', name: 'environments', component: () => import('../views/Environments.vue'), meta: { title: '环境管理' } },
      { path: 'reports', name: 'reports', component: () => import('../views/TestReports.vue'), meta: { title: '测试报告' } },
      { path: 'schedules', name: 'schedules', component: () => import('../views/Schedules.vue'), meta: { title: '定时任务' } },
      { path: 'users', name: 'users', component: () => import('../views/Users.vue'), meta: { title: '用户管理', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

function updateTitle(to) {
  const proj = useProjectStore()
  const parts = [to.meta?.title, proj.name].filter(Boolean)
  document.title = (parts.length ? parts.join(' · ') + ' · ' : '') + '蜃楼 Mock'
}

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.token && to.name !== 'login') {
    return { name: 'login' }
  }
  if (to.meta?.admin && !auth.isAdmin) {
    return { path: '/dashboard' }
  }
})

router.afterEach(updateTitle)

export default router
