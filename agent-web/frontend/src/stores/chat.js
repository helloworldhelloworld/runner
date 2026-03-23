import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref(localStorage.getItem('chat_sessionId') || generateSessionId())
  const messages = ref([])
  const rawMarkdown = ref({}) // msgId -> raw markdown

  function generateSessionId() {
    const id = 'soul-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
    localStorage.setItem('chat_sessionId', id)
    return id
  }

  function resetSession() {
    sessionId.value = generateSessionId()
    messages.value = []
    rawMarkdown.value = {}
  }

  function addMessage(role, content) {
    const id = 'msg-' + Date.now() + '-' + Math.random().toString(36).substr(2, 5)
    messages.value.push({ id, role, content })
    return id
  }

  function updateMessage(id, content) {
    const msg = messages.value.find(m => m.id === id)
    if (msg) msg.content = content
  }

  function storeRawMarkdown(id, md) {
    rawMarkdown.value[id] = md
  }

  return { sessionId, messages, rawMarkdown, resetSession, addMessage, updateMessage, storeRawMarkdown }
})
