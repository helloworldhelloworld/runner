<template>
  <div class="h-full flex flex-col bg-white/95 backdrop-blur">
    <!-- Header -->
    <div class="p-4 border-b border-gray-100 flex items-center justify-between shrink-0">
      <div>
        <h2 class="text-lg font-bold text-gray-800">对话</h2>
        <p class="text-xs text-gray-400">
          会话: {{ chatStore.sessionId }}
          <span v-if="currentAgent" class="ml-2 px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded">
            Agent: {{ currentAgent }}
          </span>
        </p>
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

    <!-- Subagent Panel -->
    <div v-if="subagents.length > 0" class="border-t border-gray-200 max-h-36 overflow-y-auto p-3 bg-indigo-50/50">
      <h3 class="text-xs font-bold text-indigo-700 mb-2">子 Agent ({{ subagents.length }})</h3>
      <div v-for="sa in subagents" :key="sa.runId" class="flex items-center gap-2 py-1 text-xs">
        <span :class="{
          'w-2 h-2 rounded-full': true,
          'bg-blue-400 animate-pulse': sa.status === 'running',
          'bg-green-500': sa.status === 'completed',
          'bg-red-500': sa.status === 'error',
          'bg-gray-400': sa.status === 'cancelled'
        }"></span>
        <span class="font-mono text-gray-500">{{ sa.runId }}</span>
        <span class="text-gray-700">{{ sa.task }}</span>
        <span v-if="sa.duration" class="text-gray-400">{{ sa.duration }}ms</span>
        <span v-if="sa.result" class="text-green-600 truncate max-w-48">{{ sa.result }}</span>
        <span v-if="sa.error" class="text-red-500 truncate max-w-48">{{ sa.error }}</span>
      </div>
    </div>

    <!-- Tool Progress Panel -->
    <div v-if="activeTools.length > 0" class="border-t border-gray-200 p-3 bg-amber-50/50">
      <div v-for="tool in activeTools" :key="tool.name" class="flex items-center gap-2 text-xs mb-1">
        <span class="animate-spin text-amber-600">&#9881;</span>
        <span class="font-semibold text-gray-700">{{ tool.name }}</span>
        <div v-if="tool.progress >= 0" class="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
          <div class="h-full bg-amber-500 rounded-full transition-all" :style="{ width: (tool.progress * 100) + '%' }"></div>
        </div>
        <span class="text-gray-500">{{ tool.message }}</span>
      </div>
    </div>

    <!-- Trace Panel -->
    <div v-if="traceMode && traceEvents.length > 0" class="border-t border-gray-200 max-h-72 overflow-y-auto p-3 bg-gray-50">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-xs font-bold text-indigo-700">调用链追踪</h3>
        <button @click="clearTrace" class="text-xs text-gray-400 hover:text-gray-600">清空</button>
      </div>
      <div v-for="(evt, i) in traceEvents" :key="i"
           class="text-xs font-mono border-b border-gray-100 last:border-b-0">
        <!-- Summary row -->
        <div class="flex items-center gap-1 py-0.5"
             :class="evt.extra && evt.extra.isError ? 'text-red-600' : ''">
          <button v-if="hasExtra(evt)"
                  @click="toggleTrace(i)"
                  class="w-4 text-gray-400 hover:text-gray-700 shrink-0"
                  :title="expandedTraces.has(i) ? '折叠' : '展开'">
            {{ expandedTraces.has(i) ? '▾' : '▸' }}
          </button>
          <span v-else class="w-4 shrink-0"></span>
          <span class="text-gray-400 w-14 text-right shrink-0">+{{ evt.timestamp - traceStartTime }}ms</span>
          <span class="font-semibold shrink-0"
                :class="evt.extra && evt.extra.isError ? 'text-red-700' : 'text-gray-700'">{{ evt.phase }}</span>
          <span class="text-gray-500 truncate flex-1">{{ evt.message || '' }}</span>
          <!-- timing / token pills -->
          <span v-if="evt.extra && evt.extra.elapsedMs != null"
                class="px-1 bg-indigo-100 text-indigo-700 rounded shrink-0">{{ evt.extra.elapsedMs }}ms</span>
          <span v-if="evt.extra && evt.extra.latencyMs != null"
                class="px-1 bg-indigo-100 text-indigo-700 rounded shrink-0">{{ evt.extra.latencyMs }}ms</span>
          <span v-if="evt.extra && (evt.extra.inputTokens != null || evt.extra.outputTokens != null)"
                class="px-1 bg-emerald-100 text-emerald-700 rounded shrink-0">
            {{ (evt.extra.inputTokens || 0) }}/{{ (evt.extra.outputTokens || 0) }} tok
          </span>
        </div>
        <!-- Detail row (extra payload) -->
        <div v-if="expandedTraces.has(i) && evt.extra" class="pl-10 pr-2 pb-2 space-y-1">
          <!-- arguments (tool.start) -->
          <div v-if="evt.extra.arguments && isObject(evt.extra.arguments)"
               class="bg-white border border-gray-200 rounded px-2 py-1">
            <div class="text-gray-500 mb-0.5">arguments</div>
            <div v-for="(v, k) in evt.extra.arguments" :key="k" class="flex gap-2">
              <span class="text-indigo-700 shrink-0">{{ k }}:</span>
              <span class="text-gray-800 break-all whitespace-pre-wrap">{{ stringify(v, 2000) }}</span>
            </div>
          </div>
          <!-- content (tool.result / llm output) -->
          <div v-if="evt.extra.content != null && evt.extra.content !== ''"
               class="bg-white border border-gray-200 rounded px-2 py-1">
            <div class="flex items-center gap-2 mb-0.5">
              <span class="text-gray-500">content</span>
              <button v-if="String(evt.extra.content).length > 500"
                      @click="toggleFull(i)"
                      class="text-[10px] text-indigo-600 hover:underline">
                {{ fullContent.has(i) ? '折叠' : '展开全部' }}
              </button>
            </div>
            <pre class="text-gray-800 break-all whitespace-pre-wrap">{{ fullContent.has(i) ? String(evt.extra.content) : truncate(String(evt.extra.content), 500) }}</pre>
          </div>
          <!-- errorMessage -->
          <div v-if="evt.extra.errorMessage"
               class="bg-red-50 border border-red-200 text-red-700 rounded px-2 py-1">
            <span class="text-red-500">error:</span> {{ evt.extra.errorMessage }}
          </div>
          <!-- output (llm.complete) -->
          <div v-if="evt.extra.output"
               class="bg-white border border-gray-200 rounded px-2 py-1">
            <div class="flex items-center gap-2 mb-0.5">
              <span class="text-gray-500">output</span>
              <button v-if="String(evt.extra.output).length > 500"
                      @click="toggleFull(i)"
                      class="text-[10px] text-indigo-600 hover:underline">
                {{ fullContent.has(i) ? '折叠' : '展开全部' }}
              </button>
            </div>
            <pre class="text-gray-800 break-all whitespace-pre-wrap">{{ fullContent.has(i) ? String(evt.extra.output) : truncate(String(evt.extra.output), 500) }}</pre>
          </div>
          <!-- Other fields (toolCallId / stopReason / runId / agentId / hasToolCalls / task / ...) -->
          <div v-if="otherKeys(evt.extra).length > 0"
               class="bg-white border border-gray-200 rounded px-2 py-1">
            <div v-for="k in otherKeys(evt.extra)" :key="k" class="flex gap-2">
              <span class="text-indigo-700 shrink-0">{{ k }}:</span>
              <span class="text-gray-800 break-all whitespace-pre-wrap">{{ stringify(evt.extra[k], 500) }}</span>
            </div>
          </div>
        </div>
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
const expandedTraces = ref(new Set())
const fullContent = ref(new Set())

// 这些字段在 summary 行 / 专用详情块里已渲染，详情区里"其它字段"里就别再重复打印。
const HANDLED_EXTRA_KEYS = new Set([
  'arguments', 'content', 'errorMessage', 'output',
  'isError', 'elapsedMs', 'latencyMs', 'inputTokens', 'outputTokens'
])

function hasExtra(evt) {
  return evt && evt.extra && typeof evt.extra === 'object' && Object.keys(evt.extra).length > 0
}
function toggleTrace(i) {
  const s = new Set(expandedTraces.value)
  if (s.has(i)) s.delete(i); else s.add(i)
  expandedTraces.value = s
}
function toggleFull(i) {
  const s = new Set(fullContent.value)
  if (s.has(i)) s.delete(i); else s.add(i)
  fullContent.value = s
}
function clearTrace() {
  traceEvents.value = []
  expandedTraces.value = new Set()
  fullContent.value = new Set()
  traceStartTime.value = 0
}
function isObject(v) {
  return v != null && typeof v === 'object' && !Array.isArray(v)
}
function truncate(s, n) {
  if (s == null) return ''
  return s.length <= n ? s : s.slice(0, n) + '…[+' + (s.length - n) + ' chars]'
}
function stringify(v, maxLen) {
  if (v == null) return ''
  const s = typeof v === 'string' ? v : JSON.stringify(v)
  return truncate(s, maxLen)
}
function otherKeys(extra) {
  if (!isObject(extra)) return []
  return Object.keys(extra).filter(k => !HANDLED_EXTRA_KEYS.has(k) && extra[k] != null && extra[k] !== '')
}
const sending = ref(false)

// Orchestrator state
const currentAgent = ref('')

// Subagent state
const subagents = ref([])

// Tool progress state
const activeTools = ref([])

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
  clearTrace()
  activeTools.value = []

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
        activeTools.value = []
        if (fullResponse) {
          chatStore.storeRawMarkdown(msgId, fullResponse)
          chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
        }
      },
      onError: (err) => {
        activeTools.value = []
        chatStore.updateMessage(msgId, `<span class="text-red-500">出错了: ${escapeHtml(err.message)}</span>`)
      },
      onCrisis: (resources) => {
        let html = '<div class="bg-red-50 border border-red-200 rounded-lg p-3 mt-2"><strong>安全提醒</strong><br>'
        if (resources) resources.forEach(r => { html += `<p class="mt-1">${r.name || ''}: ${r.phone || r.url || ''}</p>` })
        html += '</div>'
        chatStore.updateMessage(msgId, html)
      },

      // Tool events
      onToolCallStart: (data) => {
        activeTools.value.push({ name: data.toolName, progress: -1, message: '执行中...' })
        fullResponse += `\n\n<div class="text-xs text-blue-500 mt-1">&#128295; 调用工具: ${escapeHtml(data.toolName)}</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },
      onToolProgress: (data) => {
        const tool = activeTools.value.find(t => t.name === data.toolName)
        if (tool) {
          tool.progress = data.total > 0 ? data.progress / data.total : -1
          tool.message = data.message || ''
        }
      },
      onToolLog: (data) => {
        if (traceMode.value) {
          traceEvents.value.push({
            timestamp: Date.now(),
            phase: `tool.log.${data.toolName}`,
            message: `[${data.level || 'INFO'}] ${data.message || ''}`
          })
        }
      },
      onToolError: (data) => {
        activeTools.value = activeTools.value.filter(t => t.name !== data.toolName)
        fullResponse += `\n\n<div class="text-xs text-red-500 mt-1">工具错误: ${escapeHtml(data.toolName)} - ${escapeHtml(data.message || '')}</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },

      // Orchestrator events
      onAgentRoute: (data) => {
        currentAgent.value = data?.agentId || ''
        if (traceMode.value) {
          traceEvents.value.push({ timestamp: Date.now(), phase: 'agent.route', message: `-> ${data?.agentId}` })
        }
      },
      onAgentInterrupt: (data) => {
        fullResponse += `\n\n<div class="text-xs text-amber-600 mt-1">&#9208; 已打断 (${data?.phase || ''})</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },
      onAgentResume: (data) => {
        if (traceMode.value) {
          traceEvents.value.push({ timestamp: Date.now(), phase: 'agent.resume', message: `context=${data?.contextSize || 0}` })
        }
      },

      // Subagent events
      onSubagentSpawn: (data) => {
        subagents.value.push({
          runId: data?.runId || '?',
          agentId: data?.agentId || '',
          task: data?.task || '',
          status: 'running',
          duration: null,
          result: null,
          error: null
        })
      },
      onSubagentComplete: (data) => {
        const sa = subagents.value.find(s => s.runId === data?.runId)
        if (sa) {
          sa.status = 'completed'
          sa.duration = data?.durationMs
          sa.result = data?.result || ''
        }
        // Append result to chat
        fullResponse += `\n\n<div class="text-xs text-green-600 mt-1 bg-green-50 p-2 rounded">&#9989; Subagent [${escapeHtml(data?.runId || '')}]: ${escapeHtml(data?.result || '')}</div>`
        chatStore.updateMessage(msgId, renderMarkdown(fullResponse))
      },
      onSubagentError: (data) => {
        const sa = subagents.value.find(s => s.runId === data?.runId)
        if (sa) { sa.status = 'error'; sa.error = data?.error || '' }
      },
      onSubagentCancelled: (data) => {
        const sa = subagents.value.find(s => s.runId === data?.runId)
        if (sa) { sa.status = 'cancelled'; sa.error = data?.reason || 'cancelled' }
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
    subagents.value = []
    currentAgent.value = ''
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
