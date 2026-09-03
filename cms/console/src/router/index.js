import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue') },
      { path: 'edit/:docKey', name: 'edit', component: () => import('@/views/PageEditor.vue') },
      { path: 'history/:docKey', name: 'history', component: () => import('@/views/History.vue') },
      { path: 'system/users', name: 'system-users', component: () => import('@/views/SystemUsers.vue'), meta: { superAdmin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.token) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.superAdmin && auth.role !== 'SUPER_ADMIN') {
    return { name: 'dashboard' }
  }
  return true
})

export default router
