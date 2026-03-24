<template>
  <div class="h-full flex gap-4 p-4 bg-transparent">
    <!-- Left: Chat -->
    <div class="w-1/2 bg-white/95 backdrop-blur rounded-2xl shadow-xl flex flex-col">
      <div class="p-4 border-b border-gray-100 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-bold text-gray-800">Skill Creator</h2>
          <p class="text-xs text-gray-500">通过对话创建 Skill</p>
        </div>
        <button @click="resetCreator" class="text-sm text-red-600 hover:text-red-800 px-2 py-1 rounded hover:bg-red-50">重置</button>
      </div>

      <div ref="scMessagesEl" class="flex-1 overflow-y-auto p-4 space-y-3" style="background: linear-gradient(to bottom, #f9fafb 0%, #ffffff 100%);">
        <div class="text-center">
          <div class="inline-block bg-purple-50 text-purple-700 px-4 py-2 rounded-2xl text-sm">
            你好！我是 Skill 创建助手。告诉我你想创建什么类型的 Skill。
          </div>
        </div>
        <div v-for="msg in scMessages" :key="msg.id" class="message-enter-active">
          <div v-if="msg.role === 'user'" class="flex justify-end">
            <div class="max-w-[80%] bg-gradient-to-r from-purple-500 to-purple-600 text-white rounded-2xl rounded-tr-sm px-4 py-3 shadow-md">
              {{ msg.content }}
            </div>
          </div>
          <div v-else class="flex justify-start">
            <div class="max-w-[80%] md-body bg-white text-gray-800 rounded-2xl rounded-tl-sm px-4 py-3 shadow-md border border-gray-100"
              v-html="msg.content"></div>
          </div>
        </div>
      </div>

      <div class="p-4 border-t border-gray-100">
        <div class="flex gap-2">
          <input v-model="scInput" type="text" placeholder="描述你想创建的 Skill..."
            class="flex-1 px-4 py-3 border-2 border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500"
            @keypress.enter="sendSCMessage" :disabled="scSending">
          <button @click="sendSCMessage" :disabled="scSending || !scInput.trim()"
            class="bg-gradient-to-r from-purple-500 to-purple-600 text-white px-6 py-3 rounded-xl shadow-md disabled:opacity-50">
            {{ scSending ? '发送中...' : '发送' }}
          </button>
        </div>
        <div class="mt-1 text-xs text-gray-400">会话: {{ scSessionId }}</div>
      </div>
    </div>

    <!-- Right: Preview -->
    <div class="w-1/2 bg-white/95 backdrop-blur rounded-2xl shadow-xl flex flex-col">
      <div class="p-4 border-b border-gray-100 flex items-center justify-between">
        <h2 class="text-lg font-bold text-gray-800">Skill 预览</h2>
        <div class="flex gap-2">
          <button @click="saveDraft" :disabled="!currentDraft" class="bg-gradient-to-r from-green-500 to-green-600 text-white px-4 py-2 rounded-lg text-sm shadow-md disabled:opacity-50">
            保存
          </button>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto p-4">
        <div class="flex items-center gap-2 mb-2">
          <h3 class="text-sm font-bold text-gray-700">Skill 定义</h3>
          <button @click="draftView = 'markdown'" :class="draftView === 'markdown' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-500'" class="text-xs px-2 py-0.5 rounded">Markdown</button>
          <button @click="draftView = 'json'" :class="draftView === 'json' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-500'" class="text-xs px-2 py-0.5 rounded">JSON</button>
        </div>
        <div v-if="!currentDraft" class="bg-white border border-gray-200 rounded-xl p-4 text-sm text-gray-400">等待 Skill 定义...</div>
        <div v-else-if="draftView === 'markdown'" class="bg-white border border-gray-200 rounded-xl p-4 text-sm md-body" v-html="draftMarkdownHtml"></div>
        <pre v-else class="bg-gray-900 text-green-400 rounded-xl p-4 text-xs font-mono overflow-auto whitespace-pre-wrap">{{ JSON.stringify(currentDraft, null, 2) }}</pre>
      </div>

      <!-- Saved Skills -->
      <div class="p-4 border-t border-gray-100 max-h-48 overflow-y-auto">
        <div class="flex items-center justify-between mb-2">
          <h3 class="text-sm font-bold text-gray-700">已保存 Skills</h3>
          <button @click="loadSaved" class="text-xs text-purple-600 hover:text-purple-800">刷新</button>
        </div>
        <div v-if="savedSkills.length === 0" class="text-xs text-gray-400">暂无</div>
        <div v-else class="space-y-1">
          <div v-for="sk in savedSkills" :key="sk.id" class="flex items-center justify-between bg-gray-50 rounded p-2 text-xs">
            <span class="text-purple-700 font-medium">{{ sk.name }}</span>
            <div class="flex gap-1">
              <button @click="loadSkill(sk.id)" class="text-blue-600 hover:text-blue-800">加载</button>
              <button @click="deleteSkill(sk.id)" class="text-red-500 hover:text-red-700">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { skillCreatorChat, skillCreatorSave, skillCreatorList, skillCreatorDelete, skillCreatorLoad } from '../api/ws.js'
import { renderMarkdown } from '../composables/useMarkdown.js'

const scSessionId = ref('sc-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9))
const scMessages = ref([])
const scInput = ref('')
const scSending = ref(false)
const scMessagesEl = ref(null)
const currentDraft = ref(null)
const draftView = ref('markdown')
const savedSkills = ref([])

const draftMarkdownHtml = computed(() => {
  if (!currentDraft.value) return ''
  const d = currentDraft.value
  let md = `# ${d.name || ''}\n\n${d.description || ''}\n\n`
  if (d.systemPrompt) md += `## System Prompt\n\n${d.systemPrompt}\n\n`
  if (d.triggers?.length) md += `## Triggers\n\n${d.triggers.join(', ')}\n\n`
  if (d.toolNames?.length) md += `## Tools\n\n${d.toolNames.join(', ')}\n\n`
  return renderMarkdown(md)
})

function scrollSC() { nextTick(() => { if (scMessagesEl.value) scMessagesEl.value.scrollTop = scMessagesEl.value.scrollHeight }) }

function addSCMessage(role, content) {
  const id = 'sc-' + Date.now() + '-' + Math.random().toString(36).substr(2, 5)
  scMessages.value.push({ id, role, content })
  scrollSC()
  return id
}

async function sendSCMessage() {
  const msg = scInput.value.trim()
  if (!msg || scSending.value) return
  scInput.value = ''
  scSending.value = true

  addSCMessage('user', msg)
  const msgId = addSCMessage('assistant', '')
  let fullResponse = ''

  try {
    await skillCreatorChat(msg, {
      sessionId: scSessionId.value,
      onDelta: (delta) => {
        fullResponse += delta
        const display = fullResponse.replace(/<skill_draft>[\s\S]*?<\/skill_draft>/g, '').trim()
        const m = scMessages.value.find(m => m.id === msgId)
        if (m) m.content = renderMarkdown(display)
        scrollSC()
      },
      onDraft: (draft) => { currentDraft.value = draft },
      onComplete: () => {},
      onError: (err) => {
        const m = scMessages.value.find(m => m.id === msgId)
        if (m) m.content = `<span class="text-red-500">错误: ${err.message}</span>`
      }
    })
  } catch {}
  scSending.value = false
}

async function saveDraft() {
  try {
    await skillCreatorSave(scSessionId.value)
    loadSaved()
    alert('已保存')
  } catch (e) { alert('保存失败: ' + e.message) }
}

async function loadSaved() {
  try { savedSkills.value = await skillCreatorList() || [] } catch { savedSkills.value = [] }
}

async function loadSkill(id) {
  try {
    const draft = await skillCreatorLoad(id, scSessionId.value)
    currentDraft.value = draft
  } catch (e) { alert('加载失败: ' + e.message) }
}

async function deleteSkill(id) {
  if (!confirm('确定删除？')) return
  try { await skillCreatorDelete(id); loadSaved() } catch (e) { alert('删除失败: ' + e.message) }
}

function resetCreator() {
  scSessionId.value = 'sc-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
  scMessages.value = []
  currentDraft.value = null
}

onMounted(loadSaved)
</script>
