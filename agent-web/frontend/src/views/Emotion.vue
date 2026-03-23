<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">情绪签到</h2>

    <!-- Check-in -->
    <section class="mb-6">
      <p class="text-sm text-gray-600 mb-3">今天的心情如何？</p>
      <div class="flex flex-wrap gap-2 mb-3">
        <button v-for="e in emotions" :key="e.label" @click="selected = e.label"
          :class="selected === e.label ? 'bg-purple-100 border-purple-500' : e.borderClass"
          class="px-4 py-2 rounded-full border-2 text-sm transition-all">
          {{ e.emoji }} {{ e.label }}
        </button>
      </div>
      <input v-model="note" type="text" placeholder="备注（可选）..."
        class="w-full px-4 py-2 border border-gray-200 rounded-lg text-sm mb-3">
      <button @click="doCheckin" :disabled="!selected"
        class="bg-gradient-to-r from-pink-500 to-pink-600 text-white px-6 py-2 rounded-xl shadow-md disabled:opacity-50">
        签到
      </button>
      <span v-if="checkinStatus" class="ml-2 text-sm text-green-600">{{ checkinStatus }}</span>
    </section>

    <!-- History -->
    <section class="border-t border-gray-200 pt-4 mb-6">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">情绪记录</h3>
        <button @click="loadEmotionHistory" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="records.length === 0" class="text-gray-400 text-sm">暂无记录</div>
      <div v-else class="space-y-2">
        <div v-for="r in records" :key="r.id" class="bg-gray-50 rounded-lg p-3 border border-gray-200 flex justify-between items-center">
          <div>
            <span class="font-medium">{{ r.emotion || '-' }}</span>
            <span v-if="r.note" class="text-gray-500 text-sm ml-2">{{ r.note }}</span>
          </div>
          <span class="text-xs text-gray-400">{{ r.timestamp || r.date || '-' }}</span>
        </div>
      </div>
    </section>

    <!-- Stats -->
    <section class="border-t border-gray-200 pt-4">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">情绪统计</h3>
        <button @click="loadStatistics" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="!stats" class="text-gray-400 text-sm">点击刷新查看统计</div>
      <div v-else class="bg-gray-50 rounded-lg p-4 border border-gray-200 text-sm space-y-2">
        <p v-if="stats.streak !== undefined">连续签到: <strong>{{ stats.streak }}</strong> 天</p>
        <div v-if="stats.distribution">
          <p class="font-medium mt-2">情绪分布:</p>
          <p v-for="(count, emotion) in stats.distribution" :key="emotion" class="ml-4">{{ emotion }}: {{ count }} 次</p>
        </div>
        <p v-if="stats.totalDays">总签到天数: {{ stats.totalDays }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { checkin, getEmotions, getStats } from '../api/ws.js'

const auth = useAuthStore()
const selected = ref('')
const note = ref('')
const checkinStatus = ref('')
const records = ref([])
const stats = ref(null)

const emotions = [
  { label: '开心', emoji: '😊', borderClass: 'border-yellow-300 hover:bg-yellow-50' },
  { label: '平静', emoji: '😌', borderClass: 'border-blue-300 hover:bg-blue-50' },
  { label: '焦虑', emoji: '😰', borderClass: 'border-orange-300 hover:bg-orange-50' },
  { label: '难过', emoji: '😢', borderClass: 'border-gray-300 hover:bg-gray-50' },
  { label: '愤怒', emoji: '😠', borderClass: 'border-red-300 hover:bg-red-50' },
  { label: '疲惫', emoji: '😴', borderClass: 'border-purple-300 hover:bg-purple-50' }
]

async function doCheckin() {
  if (!selected.value) return
  try {
    await checkin(auth.userId, selected.value, note.value.trim() || null)
    checkinStatus.value = '签到成功！'
    selected.value = ''
    note.value = ''
    loadEmotionHistory()
    setTimeout(() => { checkinStatus.value = '' }, 3000)
  } catch (e) { alert('签到失败: ' + e.message) }
}

async function loadEmotionHistory() {
  if (!auth.userId) return
  try { records.value = await getEmotions(auth.userId) || [] } catch { records.value = [] }
}

async function loadStatistics() {
  if (!auth.userId) return
  try { stats.value = await getStats(auth.userId) } catch { stats.value = null }
}

onMounted(() => { loadEmotionHistory(); loadStatistics() })
</script>
