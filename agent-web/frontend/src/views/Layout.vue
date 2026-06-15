<template>
  <div class="flex h-screen">
    <!-- Sidebar -->
    <aside class="w-56 bg-white/95 backdrop-blur shadow-lg flex flex-col shrink-0">
      <!-- Logo -->
      <div class="p-4 border-b border-gray-100">
        <h1 class="text-lg font-bold gradient-text">AI Kernel</h1>
        <p class="text-xs text-gray-400 mt-1">Console</p>
      </div>

      <!-- Nav Items -->
      <nav class="flex-1 py-2 overflow-y-auto">
        <router-link v-for="item in visibleNavItems" :key="item.path" :to="item.path"
          class="flex items-center gap-3 px-4 py-2.5 mx-2 rounded-lg text-sm transition-all"
          :class="$route.path === item.path
            ? 'bg-gradient-to-r from-purple-500 to-purple-600 text-white shadow-md'
            : 'text-gray-600 hover:bg-purple-50 hover:text-purple-700'">
          <span>{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- Connection Status -->
      <div class="px-4 py-2 border-t border-gray-100">
        <div class="flex items-center gap-2 text-xs">
          <span :class="wsConnected ? 'text-green-500' : 'text-red-500'">●</span>
          <span class="text-gray-500">{{ wsConnected ? 'WebSocket 已连接' : '未连接' }}</span>
        </div>
      </div>

      <!-- User Info -->
      <div class="p-4 border-t border-gray-100">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-gray-800">{{ auth.username }}</p>
            <p class="text-xs text-gray-400">{{ auth.user?.role === 'ADMIN' ? '管理员' : '用户' }}</p>
          </div>
          <button @click="handleLogout" class="text-xs text-red-500 hover:text-red-700 px-2 py-1 rounded hover:bg-red-50">
            退出
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 overflow-hidden">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { ensureWebSocket, isConnected } from '../api/ws.js'

const router = useRouter()
const auth = useAuthStore()
const wsConnected = ref(false)

const navItems = [
  { path: '/chat', icon: '💬', label: '对话', adminOnly: false },
  { path: '/skills', icon: '🚀', label: '技能管理', adminOnly: false },
  { path: '/memory', icon: '🧠', label: '记忆', adminOnly: false },
  { path: '/trace', icon: '🔍', label: '调用链', adminOnly: false },
  { path: '/settings', icon: '⚙️', label: '设置', adminOnly: false },
  { path: '/admin', icon: '👤', label: '用户管理', adminOnly: true }
]

const visibleNavItems = computed(() =>
  navItems.filter(item => !item.adminOnly || auth.isAdmin)
)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

onMounted(async () => {
  try {
    await ensureWebSocket()
    wsConnected.value = true
  } catch {
    wsConnected.value = false
  }
  // Poll connection status
  setInterval(() => { wsConnected.value = isConnected() }, 3000)
})
</script>
