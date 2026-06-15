import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/chat' },
      { path: 'chat', name: 'Chat', component: () => import('../views/Chat.vue') },
      { path: 'skills', name: 'Skills', component: () => import('../views/Skills.vue') },
      { path: 'memory', name: 'Memory', component: () => import('../views/Memory.vue') },
      { path: 'trace', name: 'Trace', component: () => import('../views/Trace.vue') },
      { path: 'settings', name: 'Settings', component: () => import('../views/Settings.vue') },
      { path: 'admin', name: 'Admin', component: () => import('../views/Admin.vue'), meta: { requiresAdmin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore()

  if (to.path === '/login') {
    if (auth.isLoggedIn) return next('/')
    return next()
  }

  if (to.matched.some(r => r.meta.requiresAuth)) {
    if (!auth.isLoggedIn) {
      const valid = await auth.checkAuth()
      if (!valid) return next('/login')
    }
  }

  if (to.matched.some(r => r.meta.requiresAdmin)) {
    if (!auth.isAdmin) return next('/chat')
  }

  next()
})

export default router
