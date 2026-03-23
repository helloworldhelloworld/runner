<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">系统状态</h2>

    <!-- Health -->
    <section class="mb-6">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">健康检查</h3>
        <button @click="loadHealthData" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="!health" class="text-gray-400 text-sm">点击刷新查看</div>
      <pre v-else class="bg-gray-50 rounded-lg p-4 text-sm font-mono border border-gray-200 overflow-auto">{{ JSON.stringify(health, null, 2) }}</pre>
    </section>

    <!-- Current User -->
    <section class="border-t border-gray-200 pt-4 mb-6">
      <h3 class="text-lg font-bold mb-3">当前用户</h3>
      <div class="bg-gray-50 rounded-lg p-4 border border-gray-200 text-sm space-y-1">
        <p>用户ID: <span class="font-mono text-purple-600">{{ auth.userId }}</span></p>
        <p>用户名: <span class="font-medium">{{ auth.user?.username || '-' }}</span></p>
        <p>昵称: <span class="font-medium">{{ auth.user?.nickname || '-' }}</span></p>
        <p>角色: <span class="font-medium" :class="auth.isAdmin ? 'text-red-600' : 'text-blue-600'">{{ auth.user?.role || 'USER' }}</span></p>
        <p>会员等级: {{ auth.user?.memberLevel || '-' }}</p>
      </div>
    </section>

    <!-- Session Info -->
    <section class="border-t border-gray-200 pt-4">
      <h3 class="text-lg font-bold mb-3">会话信息</h3>
      <div class="text-sm text-gray-600 space-y-1">
        <p>会话ID: <span class="font-mono text-purple-600">{{ chatStore.sessionId }}</span></p>
        <p>WebSocket: <span :class="wsConnected ? 'text-green-600' : 'text-red-500'">{{ wsConnected ? '已连接' : '未连接' }}</span></p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { useChatStore } from '../stores/chat.js'
import { checkHealth, isConnected } from '../api/ws.js'

const auth = useAuthStore()
const chatStore = useChatStore()
const health = ref(null)
const wsConnected = ref(false)

async function loadHealthData() {
  try { health.value = await checkHealth() } catch { health.value = null }
}

onMounted(() => {
  wsConnected.value = isConnected()
  loadHealthData()
})
</script>
