import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/projects',
    children: [
      { path: 'projects', name: 'projects', component: () => import('../views/Projects.vue') },
      { path: 'interfaces', name: 'interfaces', component: () => import('../views/Interfaces.vue') },
      { path: 'listeners', name: 'listeners', component: () => import('../views/TcpListeners.vue') },
      { path: 'keys', name: 'keys', component: () => import('../views/Keys.vue') },
      { path: 'logs', name: 'logs', component: () => import('../views/Logs.vue') },
      { path: 'functions', name: 'functions', component: () => import('../views/Functions.vue') },
      { path: 'file-gen', name: 'file-gen', component: () => import('../views/FileGen.vue') },
      { path: 'testcases', name: 'testcases', component: () => import('../views/TestCases.vue') },
      { path: 'scenarios', name: 'scenarios', component: () => import('../views/Scenarios.vue') },
      { path: 'environments', name: 'environments', component: () => import('../views/Environments.vue') },
      { path: 'reports', name: 'reports', component: () => import('../views/TestReports.vue') },
      { path: 'users', name: 'users', component: () => import('../views/Users.vue'), meta: { admin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.token && to.name !== 'login') {
    return { name: 'login' }
  }
  if (to.meta?.admin && !auth.isAdmin) {
    return { path: '/projects' }
  }
})

export default router
