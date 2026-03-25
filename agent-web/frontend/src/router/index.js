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
      { path: 'tools', name: 'Tools', component: () => import('../views/Tools.vue') },
      { path: 'client-tools', name: 'ClientTools', component: () => import('../views/ClientTools.vue') },
      { path: 'skill-creator', name: 'SkillCreator', component: () => import('../views/SkillCreator.vue') },
      { path: 'skill-tester', name: 'SkillTester', component: () => import('../views/SkillTester.vue') },
      { path: 'memory', name: 'Memory', component: () => import('../views/Memory.vue') },
      // { path: 'assessment', name: 'Assessment', component: () => import('../views/Assessment.vue') },
      // { path: 'emotion', name: 'Emotion', component: () => import('../views/Emotion.vue') },
      { path: 'model-config', name: 'ModelConfig', component: () => import('../views/ModelConfig.vue'), meta: { requiresAdmin: true } },
      { path: 'system', name: 'System', component: () => import('../views/System.vue') },
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
