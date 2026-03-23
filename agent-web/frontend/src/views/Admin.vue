<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">用户管理</h2>
    <p class="text-gray-500 text-sm mb-4">管理员可查看用户列表、修改角色、禁用账户</p>

    <div class="flex items-center justify-between mb-4">
      <input v-model="searchQuery" type="text" placeholder="搜索用户名/昵称..."
        class="px-4 py-2 border border-gray-200 rounded-lg text-sm w-64">
      <button @click="loadUsers" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">加载中...</div>
    <div v-else-if="error" class="text-red-500 text-sm">{{ error }}</div>
    <div v-else>
      <div class="text-xs text-gray-500 mb-2">共 {{ filteredUsers.length }} 个用户</div>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left font-medium text-gray-600">用户名</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">昵称</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">角色</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">状态</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">创建时间</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">最后活跃</th>
              <th class="px-4 py-3 text-left font-medium text-gray-600">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="u in filteredUsers" :key="u.id" class="hover:bg-gray-50">
              <td class="px-4 py-3 font-mono text-purple-700">{{ u.username || u.id }}</td>
              <td class="px-4 py-3">{{ u.nickname || '-' }}</td>
              <td class="px-4 py-3">
                <select :value="u.role" @change="changeRole(u.id, ($event.target).value)"
                  class="text-xs px-2 py-1 border border-gray-200 rounded">
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </td>
              <td class="px-4 py-3">
                <button @click="toggleStatus(u.id, !u.enabled)"
                  :class="u.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                  class="text-xs px-2 py-1 rounded">
                  {{ u.enabled ? '启用' : '禁用' }}
                </button>
              </td>
              <td class="px-4 py-3 text-xs text-gray-500">{{ formatTime(u.createdAt) }}</td>
              <td class="px-4 py-3 text-xs text-gray-500">{{ formatTime(u.lastActive) }}</td>
              <td class="px-4 py-3">
                <span class="text-xs text-gray-400 font-mono">{{ u.id }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { httpGet, httpPut } from '../api/http.js'

const users = ref([])
const searchQuery = ref('')
const loading = ref(false)
const error = ref('')

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const q = searchQuery.value.toLowerCase()
  return users.value.filter(u =>
    (u.username || '').toLowerCase().includes(q) ||
    (u.nickname || '').toLowerCase().includes(q) ||
    (u.id || '').toLowerCase().includes(q)
  )
})

async function loadUsers() {
  loading.value = true; error.value = ''
  try {
    const data = await httpGet('/user/list')
    users.value = data.users || []
  } catch (e) {
    error.value = e.message
  }
  loading.value = false
}

async function changeRole(userId, role) {
  try {
    await httpPut(`/user/${userId}/role`, { role })
    const u = users.value.find(u => u.id === userId)
    if (u) u.role = role
  } catch (e) {
    alert('修改角色失败: ' + e.message)
    loadUsers()
  }
}

async function toggleStatus(userId, enabled) {
  try {
    await httpPut(`/user/${userId}/status`, { enabled })
    const u = users.value.find(u => u.id === userId)
    if (u) u.enabled = enabled
  } catch (e) {
    alert('修改状态失败: ' + e.message)
    loadUsers()
  }
}

function formatTime(ts) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadUsers)
</script>
