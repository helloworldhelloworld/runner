<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">记忆系统</h2>
    <p class="text-gray-500 text-sm mb-4">BM25 + 向量混合搜索，三层记忆架构</p>

    <!-- Search -->
    <section class="mb-6">
      <h3 class="text-lg font-bold mb-3">搜索记忆</h3>
      <div class="flex gap-2 mb-3">
        <input v-model="query" type="text" placeholder="输入搜索关键词..."
          class="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm" @keypress.enter="doSearch">
        <input v-model.number="topK" type="number" min="1" max="50"
          class="w-16 px-2 py-2 border border-gray-200 rounded-lg text-sm text-center">
        <button @click="doSearch" class="bg-gradient-to-r from-blue-500 to-blue-600 text-white px-4 py-2 rounded-lg text-sm shadow-md">搜索</button>
      </div>
      <div v-if="results === null" class="text-gray-400 text-sm">输入关键词搜索记忆</div>
      <div v-else-if="results.length === 0" class="text-gray-400 text-sm">未找到相关记忆</div>
      <div v-else class="space-y-2">
        <div v-for="(r, i) in results" :key="i" class="bg-gray-50 rounded-lg p-3 border border-gray-200">
          <div class="flex justify-between text-xs text-gray-400 mb-1">
            <span>#{{ i + 1 }} | 来源: {{ r.source || '-' }}</span>
            <span>相关度: {{ (r.score || 0).toFixed(3) }}</span>
          </div>
          <p class="text-sm text-gray-700">{{ r.content || r.snippet || '' }}</p>
        </div>
      </div>
    </section>

    <!-- Durable Memory Write -->
    <section class="border-t border-gray-200 pt-4">
      <h3 class="text-lg font-bold mb-3">写入长期记忆</h3>
      <div class="space-y-2">
        <input v-model="section" type="text" placeholder="分区名称（如：用户信息、重要话题）"
          class="w-full px-4 py-2 border border-gray-200 rounded-lg text-sm">
        <textarea v-model="content" placeholder="记忆内容..."
          class="w-full px-4 py-2 border border-gray-200 rounded-lg text-sm h-24 resize-none"></textarea>
        <div class="flex items-center gap-2">
          <button @click="doRemember" class="bg-gradient-to-r from-green-500 to-green-600 text-white px-4 py-2 rounded-lg text-sm shadow-md">保存</button>
          <span v-if="saveStatus" class="text-sm" :class="saveStatusClass">{{ saveStatus }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchMemory, rememberDurable } from '../api/ws.js'

const query = ref('')
const topK = ref(10)
const results = ref(null)
const section = ref('')
const content = ref('')
const saveStatus = ref('')
const saveStatusClass = ref('text-gray-500')

async function doSearch() {
  if (!query.value.trim()) return
  try {
    results.value = await searchMemory(query.value, topK.value)
  } catch (e) {
    results.value = []
  }
}

async function doRemember() {
  if (!section.value.trim() || !content.value.trim()) { alert('请填写分区名称和内容'); return }
  try {
    await rememberDurable(section.value, content.value)
    saveStatus.value = '已保存'
    saveStatusClass.value = 'text-green-600'
    content.value = ''
    setTimeout(() => { saveStatus.value = '' }, 3000)
  } catch (e) {
    saveStatus.value = '保存失败: ' + e.message
    saveStatusClass.value = 'text-red-500'
  }
}
</script>
