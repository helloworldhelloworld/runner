<template>
  <div class="min-h-screen flex items-center justify-center px-4">
    <div class="bg-white/95 backdrop-blur rounded-2xl shadow-2xl p-8 w-full max-w-md">
      <h1 class="text-3xl font-bold text-center mb-2">
        <span class="gradient-text">Lightweight AI Kernel</span>
      </h1>
      <p class="text-gray-500 text-sm text-center mb-6">AI Agent Framework Console</p>

      <!-- Tab Toggle -->
      <div class="flex mb-6 bg-gray-100 rounded-lg p-1">
        <button @click="mode = 'login'" :class="mode === 'login' ? 'bg-white shadow text-purple-700' : 'text-gray-500'"
          class="flex-1 py-2 rounded-md text-sm font-medium transition-all">登录</button>
        <button @click="mode = 'register'" :class="mode === 'register' ? 'bg-white shadow text-purple-700' : 'text-gray-500'"
          class="flex-1 py-2 rounded-md text-sm font-medium transition-all">注册</button>
      </div>

      <form @submit.prevent="handleSubmit" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">用户名</label>
          <input v-model="username" type="text" required autocomplete="username"
            class="w-full px-4 py-3 border-2 border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            placeholder="请输入用户名">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">密码</label>
          <input v-model="password" type="password" required autocomplete="current-password"
            class="w-full px-4 py-3 border-2 border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            placeholder="请输入密码">
        </div>
        <div v-if="mode === 'register'">
          <label class="block text-sm font-medium text-gray-700 mb-1">昵称 (可选)</label>
          <input v-model="nickname" type="text"
            class="w-full px-4 py-3 border-2 border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            placeholder="请输入昵称">
        </div>

        <div v-if="error" class="text-red-500 text-sm bg-red-50 rounded-lg p-3">{{ error }}</div>

        <button type="submit" :disabled="loading"
          class="w-full bg-gradient-to-r from-purple-500 to-purple-600 text-white py-3 rounded-xl font-medium hover:from-purple-600 hover:to-purple-700 shadow-md disabled:opacity-50 transition-all">
          <span v-if="loading" class="loading-spin mr-2"></span>
          {{ mode === 'login' ? '登录' : '注册' }}
        </button>
      </form>

      <p class="text-xs text-gray-400 text-center mt-4">默认管理员: admin / admin123</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const auth = useAuthStore()

const mode = ref('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(username.value, password.value)
    } else {
      await auth.register(username.value, password.value, nickname.value)
    }
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
