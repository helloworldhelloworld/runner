<template>
  <div class="h-full flex flex-col bg-white/95 backdrop-blur">
    <!-- Header -->
    <div class="p-4 border-b border-gray-100 flex items-center justify-between shrink-0">
      <div>
        <h2 class="text-lg font-bold text-gray-800">对话</h2>
        <p class="text-xs text-gray-400">会话: {{ chatStore.sessionId }}</p>
      </div>
      <div class="flex gap-2 items-center">
        <label class="flex items-center gap-1 text-xs cursor-pointer px-2 py-1 rounded hover:bg-purple-50">
          <input type="checkbox" v-model="traceMode" class="rounded">
          <span>调用链</span>
        </label>
        <select v-model="selectedModel" class="text-xs px-2 py-1 border border-gray-200 rounded-lg">
          <option value="">默认模型</option>
          <option value="anthropic/claude-3.5-sonnet">Claude 3.5 Sonnet</option>
          <option value="openai/gpt-4o">GPT-4o</option>
          <option value="google/gemini-pro-1.5">Gemini Pro 1.5</option>
        </select>
        <button @click="showSummary" class="text-sm text-purple-600 hover:text-purple-800 px-2 py-1 rounded hover:bg-purple-50">摘要</button>
        <button @click="clearChat" class="text-sm text-red-600 hover:text-red-800 px-2 py-1 rounded hover:bg-red-50">清空</button>
      </div>
    </div>

    <!-- Messages -->
    <div ref="messagesEl" class="flex-1 overflow-y-auto p-4 space-y-3" style="background: linear-gradient(to bottom, #f9fafb 0%, #ffffff 100%);">
      <div v-if="chatStore.messages.length === 0" class="text-center">
        <div class="inline-block bg-purple-50 text-purple-700 px-4 py-2 rounded-2xl text-sm">
          欢迎使用 AI Kernel Console，开始对话吧
        </div>
      </div>
      <div v-for="msg in chatStore.messages" :key="msg.id" class="message-enter-active">
        <!-- User message -->
        <div v-if="msg.role === 'user'" class="flex justify-end">
          <div class="max-w-[80%] bg-gradient-to-r from-purple-500 to-purple-600 text-white rounded-2xl rounded-tr-sm px-4 py-3 shadow-md">
            {{ msg.content }}
          </div>
        </div>
        <!-- Assistant message -->
        <div v-else-if="msg.role === 'assistant'" class="flex justify-start">
          <div class="max-w-[80%]">
            <div class="md-body bg-white text-gray-800 rounded-2xl rounded-tl-sm px-4 py-3 shadow-md border border-gray-100"
              v-html="msg.content"></div>
          </div>
        </div>
        <!-- System message -->
        <div v-else class="flex justify-center">
          <div class="bg-blue-50 text-blue-800 px-4 py-2 rounded-xl text-sm max-w-2xl border border-blue-100"
            v-html="msg.content"></div>
        </div>
      </div>
    </div>

    <!-- Trace Panel -->
    <div v-if="traceMode && traceEvents.length > 0" class="border-t border-gray-200 max-h-48 overflow-y-auto p-3 bg-gray-50">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-xs font-bold text-indigo-700">调用链追踪</h3>
        <button @click="traceEvents = []" class="text-xs text-gray-400 hover:text-gray-600">清空</button>
      </div>
      <div v-for="(evt, i) in traceEvents" :key="i" class="flex items-center gap-1 py-0.5 text-xs font-mono">
        <span class="text-gray-400 w-14 text-right shrink-0">+{{ evt.timestamp - traceStartTime }}ms</span>
        <span class="font-semibold text-gray-700">{{ evt.phase }}</span>
        <span class="text-gray-500 truncate">{{ evt.message || '' }}</span>
      </div>
    </div>

    <!-- Input -->
    <div class="p-4 border-t border-gray-100 shrink-0">
      <div class="flex gap-2">
        <input v-model="inputMessage" type="text" placeholder="输入消息..."
          class="flex-1 px-4 py-3 border-2 border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
          @keypress.enter="sendMessage" :disabled="sending">
        <button @click="sendMessage" :disabled="sending || !inputMessage.trim()"
          class="bg-gradient-to-r from-purple-500 to-purple-600 text-white px-6 py-3 rounded-xl hover:from-purple-600 hover:to-purple-700 shadow-md disabled:opacity-50">
          {{ sending ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useChatStore } from '../stores/chat.js'
import { sendChatStream, getSessionHistory, getSessionSummary, clearSession } from '../api/ws.js'
import { renderMarkdown, escapeHtml } from '../composables/useMarkdown.js'

const chatStore = useChatStore()
const messagesEl = ref(null)
const inputMessage = ref('')
const selectedModel = ref('')
const traceMode = ref(false)
const traceEvents = ref([])
const traceStartTime = ref(0)
const sending = ref(false)

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  })
}

watch(() => chatStore.messages.length, scrollToBottom)

async function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message || sending.value) return

  inputMessage.value = ''
  sending.value = true
  traceEvents.value = []
  traceStartTime.value = 0

  chatStore.addMessage('user', message)
  const msgId = chatStore.addMessage('assistant', '<span class="text-gray-400">正在输入...</span>')
  let fullResponse = ''

  try {
    await sendChatStream(message, {
      sessionId: chatStore.sessionId,
      model: selectedModel.value || undefined,
      onDelta: (delta) => {
        if (delta) {
          fullResponse += delta
          chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
          scrollToBottom()
        }
      },
      onComplete: () => {
        if (fullResponse) {
          chatStore.storeRawMarkdown(msgId, fullResponse)
          chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
        }
      },
      onError: (err) => {
        chatStore.updateMessage(msgId, `<span class="text-red-500">出错了: ${escapeHtml(err.message)}</span>`)
      },
      onCrisis: (resources) => {
        let html = '<div class="bg-red-50 border border-red-200 rounded-lg p-3 mt-2"><strong>安全提醒</strong><br>'
        if (resources) resources.forEach(r => { html += `<p class="mt-1">${r.name || ''}: ${r.phone || r.url || ''}</p>` })
        html += '</div>'
        chatStore.updateMessage(msgId, html)
      },
      onToolCallStart: (data) => {
        fullResponse += `\n\n<div class="text-xs text-blue-500 mt-1">🔧 调用工具: ${data.toolName}</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },
      onToolError: (data) => {
        fullResponse += `\n\n<div class="text-xs text-red-500 mt-1">工具错误: ${data.toolName} - ${data.message}</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },
      onTrace: (trace) => {
        if (traceMode.value) {
          if (traceStartTime.value === 0) traceStartTime.value = trace.timestamp
          traceEvents.value.push(trace)
        }
      }
    })
  } catch {
    if (!fullResponse) chatStore.updateMessage(msgId, '<span class="text-red-500">连接出错</span>')
  }
  if (fullResponse && !chatStore.rawMarkdown[msgId]) chatStore.storeRawMarkdown(msgId, fullResponse)
  sending.value = false
}

async function showSummary() {
  try {
    const summary = await getSessionSummary(chatStore.sessionId)
    let text = '<strong>会话摘要</strong><br><br>'
    if (summary.summary) text += `${summary.summary}<br>`
    chatStore.addMessage('system', text)
  } catch (e) {
    chatStore.addMessage('system', `无法获取摘要: ${e.message}`)
  }
}

async function clearChat() {
  if (!confirm('确定要清空对话吗？')) return
  try {
    await clearSession(chatStore.sessionId)
    chatStore.resetSession()
  } catch (e) {
    alert('清空失败: ' + e.message)
  }
}

onMounted(async () => {
  try {
    const history = await getSessionHistory(chatStore.sessionId)
    if (history?.length) {
      chatStore.messages = []
      history.forEach(msg => {
        if (msg.role === 'user') chatStore.addMessage('user', msg.content)
        else if (msg.role === 'assistant') {
          const id = chatStore.addMessage('assistant', renderMarkdown(msg.content))
          chatStore.storeRawMarkdown(id, msg.content)
        }
      })
      scrollToBottom()
    }
  } catch { /* ignore */ }
})
</script>
