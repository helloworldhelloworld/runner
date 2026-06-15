<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-1">全链路调用链</h2>
    <p class="text-gray-500 text-sm mb-4">
      实时观察某会话的 StreamEvent 调用链：agent_route / 工具调用(名+参) / 结果 / token / 语音块 / turn-end
    </p>

    <div class="flex gap-2 mb-4 items-center">
      <input v-model="sessionId" type="text" placeholder="sessionId（如 voice-duplex）"
        class="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm" @keypress.enter="start">
      <button @click="start"
        class="bg-gradient-to-r from-purple-500 to-purple-600 text-white px-4 py-2 rounded-lg text-sm shadow-md">
        观察
      </button>
      <button @click="clear"
        class="border border-gray-200 text-gray-600 px-4 py-2 rounded-lg text-sm hover:bg-gray-50">清屏</button>
      <span class="text-xs ml-1" :class="statusClass">{{ status }}</span>
    </div>

    <div ref="logEl"
      class="rounded-lg bg-gray-900 text-gray-100 p-3 font-mono text-xs h-[62vh] overflow-y-auto">
      <div v-if="events.length === 0" class="text-gray-500">未观察 / 暂无事件</div>
      <div v-for="(e, i) in events" :key="i" class="whitespace-pre-wrap break-all py-0.5">
        <span class="text-gray-500 mr-2">{{ e.t }}</span>
        <span class="font-bold mr-2" :class="e.cls">{{ e.tag }}</span>
        <span :class="e.cls">{{ e.text }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue'
import { observeSession } from '../api/ws.js'

const sessionId = ref('voice-duplex')
const events = ref([])
const status = ref('未连接')
const logEl = ref(null)
let closeFn = null
let tokenBuf = ''

const statusClass = computed(() =>
  status.value.startsWith('观察') ? 'text-green-500'
  : (status.value === '错误' || status.value === '断开') ? 'text-red-500' : 'text-gray-400')

function now() { return new Date().toLocaleTimeString('zh-CN') }

function push(cls, tag, text) {
  events.value.push({ t: now(), cls, tag, text })
  nextTick(() => { if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight })
}
function flushTok() { if (tokenBuf) { push('text-gray-400', 'token', tokenBuf); tokenBuf = '' } }

function render(m) {
  const d = m.data
  switch (m.type) {
    case 'token': tokenBuf += (typeof d === 'string' ? d : ''); return
    case 'agent_route': flushTok(); push('text-blue-400', '▸ agent_route', d?.agentId || ''); return
    case 'tool_call_start': flushTok(); push('text-yellow-400', '▸ tool_call', `${d.toolName}(${JSON.stringify(d.arguments || {})})`); return
    case 'tool_result': {
      flushTok(); const r = d.result || {}
      push(r.isError ? 'text-red-400' : 'text-green-400', '◂ tool_result',
        `${d.toolName}: ${typeof r.content === 'string' ? r.content : JSON.stringify(r.content)}`)
      return
    }
    case 'tool_error': flushTok(); push('text-red-400', '◂ tool_error', JSON.stringify(d)); return
    case 'speakable_chunk': flushTok(); push('text-purple-400', '🔊 speak', `${d.text}${d.emotion ? '  [' + d.emotion + ']' : ''}`); return
    case 'speech_interrupted': flushTok(); push('text-red-400', '⏹ interrupted', JSON.stringify(d)); return
    case 'error': flushTok(); push('text-red-400', '✖ error', JSON.stringify(m.message || d)); return
    case 'stream_end': flushTok(); push('text-indigo-400', '── stream_end ──', ''); return
    case 'trace':
      if (d === 'stream_end' || m.message === 'stream_end') { flushTok(); push('text-indigo-400', '── turn end ──', '') }
      return
    case 'observing': push('text-gray-500', '(已订阅)', m.data?.sessionId || ''); return
    default: return
  }
}

function start() {
  if (closeFn) closeFn()
  events.value = []
  tokenBuf = ''
  status.value = '连接中…'
  closeFn = observeSession(sessionId.value.trim(), render, (s) => {
    status.value = s === 'connected' ? ('观察 ' + sessionId.value)
      : s === 'closed' ? '断开' : '错误'
  })
}
function clear() { events.value = [] }

onUnmounted(() => { if (closeFn) closeFn() })
</script>
