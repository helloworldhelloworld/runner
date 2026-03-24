<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-6">模型配置</h2>

    <!-- Current Status -->
    <section class="mb-6 bg-gray-50 rounded-xl p-4 border border-gray-200">
      <h3 class="text-sm font-bold text-gray-500 mb-2">当前状态</h3>
      <div class="flex items-center gap-3">
        <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium"
          :class="config.providerName === 'mock' ? 'bg-yellow-100 text-yellow-700' : 'bg-green-100 text-green-700'">
          <span class="w-2 h-2 rounded-full" :class="config.providerName === 'mock' ? 'bg-yellow-500' : 'bg-green-500'"></span>
          {{ config.providerName || '未配置' }}
        </span>
        <span v-if="config.model" class="text-sm text-gray-600">{{ config.model }}</span>
      </div>
    </section>

    <!-- Saved Presets -->
    <section v-if="presets.length > 0" class="mb-6 bg-blue-50 rounded-xl p-4 border border-blue-200">
      <h3 class="text-sm font-bold text-gray-500 mb-3">已保存的配置</h3>
      <div class="space-y-2">
        <div v-for="preset in presets" :key="preset.name"
          class="flex items-center justify-between bg-white rounded-lg px-3 py-2 border border-blue-100">
          <div class="flex items-center gap-3 min-w-0">
            <span class="text-sm font-medium text-gray-800 truncate">{{ preset.name }}</span>
            <span class="text-xs text-gray-400">{{ preset.providerType }} / {{ preset.model || '(无模型)' }}</span>
            <span v-if="preset.baseUrl" class="text-xs text-gray-400 truncate max-w-[200px]">{{ preset.baseUrl }}</span>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <button @click="applyPreset(preset.name)"
              class="text-xs px-3 py-1 rounded bg-purple-500 text-white hover:bg-purple-600">
              切换
            </button>
            <button @click="deletePreset(preset.name)"
              class="text-xs px-2 py-1 rounded text-red-500 hover:bg-red-50">
              删除
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Config Form -->
    <form @submit.prevent="saveConfig" class="space-y-5">
      <!-- Provider Type -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1.5">Provider 类型</label>
        <select v-model="form.providerType"
          class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent bg-white">
          <option value="openrouter">OpenRouter / OpenAI 兼容 (HTTP)</option>
          <option value="ws">WebSocket 网关 (OpenAI 兼容)</option>
          <option value="api">Claude API (Anthropic 官方)</option>
          <option value="mock">Mock (测试模式)</option>
        </select>
        <p class="text-xs text-gray-400 mt-1">
          {{ form.providerType === 'ws' ? 'WebSocket 模式适用于自建的 WS LLM 网关' : 'OpenRouter 兼容 OpenAI 格式，可用于自建 HTTP LLM 网关' }}
        </p>
      </div>

      <!-- Base URL / WS URL -->
      <div v-if="form.providerType === 'openrouter' || form.providerType === 'ws'">
        <label class="block text-sm font-medium text-gray-700 mb-1.5">
          {{ form.providerType === 'ws' ? 'WebSocket 地址' : 'API 地址 (Base URL)' }}
        </label>
        <input v-model="form.baseUrl" type="text"
          :placeholder="form.providerType === 'ws' ? 'ws://10.32.101.24:8086/ws/chat' : '留空使用 OpenRouter 默认地址，或输入自建网关地址'"
          class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent">
        <p class="text-xs text-gray-400 mt-1">
          {{ form.providerType === 'ws'
            ? '示例: ws://10.32.101.24:8086/ws/chat 或 wss://llm-gateway.example.com/ws'
            : '示例: https://openrouter.ai/api/v1 或 http://10.32.101.24:8086/llm/openai' }}
        </p>
      </div>

      <!-- API Key -->
      <div v-if="form.providerType !== 'mock'">
        <label class="block text-sm font-medium text-gray-700 mb-1.5">API Key</label>
        <div class="relative">
          <input v-model="form.apiKey" :type="showKey ? 'text' : 'password'" placeholder="输入 API Key（自建网关可留空）"
            class="w-full px-3 py-2.5 pr-20 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent">
          <button type="button" @click="showKey = !showKey"
            class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600 px-2 py-1">
            {{ showKey ? '隐藏' : '显示' }}
          </button>
        </div>
        <p v-if="config.apiKeyMasked" class="text-xs text-gray-400 mt-1">当前: {{ config.apiKeyMasked }}</p>
      </div>

      <!-- Model -->
      <div v-if="form.providerType !== 'mock'">
        <label class="block text-sm font-medium text-gray-700 mb-1.5">模型</label>
        <div class="flex gap-2">
          <select v-model="form.model"
            class="flex-1 px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent bg-white">
            <option value="">-- 选择预设模型 --</option>
            <optgroup label="Claude (Anthropic)">
              <option value="anthropic/claude-sonnet-4-20250514">Claude Sonnet 4</option>
              <option value="anthropic/claude-3.5-sonnet">Claude 3.5 Sonnet</option>
              <option value="anthropic/claude-3-haiku">Claude 3 Haiku</option>
            </optgroup>
            <optgroup label="OpenAI">
              <option value="openai/gpt-4o">GPT-4o</option>
              <option value="openai/gpt-4o-mini">GPT-4o Mini</option>
              <option value="openai/gpt-4-turbo">GPT-4 Turbo</option>
            </optgroup>
            <optgroup label="Google">
              <option value="google/gemini-pro-1.5">Gemini Pro 1.5</option>
              <option value="google/gemini-2.0-flash-001">Gemini 2.0 Flash</option>
            </optgroup>
            <optgroup label="DeepSeek">
              <option value="deepseek/deepseek-chat">DeepSeek Chat</option>
              <option value="deepseek/deepseek-r1">DeepSeek R1</option>
            </optgroup>
            <optgroup label="Meta">
              <option value="meta-llama/llama-3.1-70b-instruct">Llama 3.1 70B</option>
              <option value="meta-llama/llama-3.1-8b-instruct">Llama 3.1 8B</option>
            </optgroup>
          </select>
        </div>
        <div class="mt-2">
          <input v-model="customModel" type="text" placeholder="或输入自定义模型名称..."
            class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 focus:border-transparent"
            @input="onCustomModelInput">
          <p class="text-xs text-gray-400 mt-1">输入自定义名称会覆盖上方选择，格式如: provider/model-name</p>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex gap-3 pt-2">
        <button type="submit" :disabled="saving"
          class="bg-gradient-to-r from-purple-500 to-purple-600 text-white px-6 py-2.5 rounded-lg hover:from-purple-600 hover:to-purple-700 shadow-md disabled:opacity-50 text-sm font-medium">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
        <button type="button" @click="showSavePresetDialog = true"
          class="px-4 py-2.5 rounded-lg border border-purple-300 text-purple-600 hover:bg-purple-50 text-sm font-medium">
          保存为预设
        </button>
        <button type="button" @click="resetForm"
          class="px-6 py-2.5 rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 text-sm">
          重置
        </button>
      </div>

      <!-- Result Message -->
      <div v-if="message" class="p-3 rounded-lg text-sm" :class="messageError ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-green-50 text-green-700 border border-green-200'">
        {{ message }}
      </div>
    </form>

    <!-- Save Preset Dialog -->
    <div v-if="showSavePresetDialog" class="fixed inset-0 bg-black/30 flex items-center justify-center z-50" @click.self="showSavePresetDialog = false">
      <div class="bg-white rounded-xl p-6 w-80 shadow-xl">
        <h3 class="text-sm font-bold text-gray-700 mb-3">保存当前配置为预设</h3>
        <input v-model="presetName" type="text" placeholder="输入预设名称，如: 生产环境、测试网关..."
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 mb-3"
          @keyup.enter="saveAsPreset">
        <div class="flex gap-2 justify-end">
          <button @click="showSavePresetDialog = false"
            class="px-4 py-1.5 text-sm text-gray-500 hover:bg-gray-100 rounded-lg">取消</button>
          <button @click="saveAsPreset" :disabled="!presetName.trim()"
            class="px-4 py-1.5 text-sm bg-purple-500 text-white rounded-lg hover:bg-purple-600 disabled:opacity-50">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { httpGet, httpPut, httpPost, httpDelete } from '../api/http.js'

const config = reactive({ providerName: '', model: '', baseUrl: '', hasApiKey: false, apiKeyMasked: '' })
const form = reactive({ providerType: 'openrouter', apiKey: '', model: '', baseUrl: '' })
const customModel = ref('')
const showKey = ref(false)
const saving = ref(false)
const message = ref('')
const messageError = ref(false)

// Presets
const presets = ref([])
const showSavePresetDialog = ref(false)
const presetName = ref('')

function onCustomModelInput() {
  if (customModel.value.trim()) {
    form.model = customModel.value.trim()
  }
}

async function loadConfig() {
  try {
    const data = await httpGet('/api/model-config')
    Object.assign(config, data)
    form.providerType = data.providerType || 'openrouter'
    form.model = data.model || ''
    form.baseUrl = data.baseUrl || ''
    form.apiKey = ''
    customModel.value = ''
  } catch (e) {
    console.error('Failed to load model config', e)
  }
}

async function loadPresets() {
  try {
    presets.value = await httpGet('/api/model-config/presets')
  } catch (e) {
    console.error('Failed to load presets', e)
  }
}

async function saveConfig() {
  saving.value = true
  message.value = ''
  try {
    const effectiveModel = customModel.value.trim() || form.model
    const data = await httpPut('/api/model-config', {
      providerType: form.providerType,
      apiKey: form.apiKey || '',
      model: effectiveModel,
      baseUrl: form.baseUrl
    })
    message.value = data.message || '配置已保存'
    messageError.value = false
    await loadConfig()
  } catch (e) {
    message.value = e.message || '保存失败'
    messageError.value = true
  }
  saving.value = false
}

async function saveAsPreset() {
  const name = presetName.value.trim()
  if (!name) return
  try {
    const data = await httpPost('/api/model-config/presets', { name })
    message.value = data.message || '预设已保存'
    messageError.value = false
    showSavePresetDialog.value = false
    presetName.value = ''
    await loadPresets()
  } catch (e) {
    message.value = e.message || '保存预设失败'
    messageError.value = true
  }
}

async function applyPreset(name) {
  try {
    const data = await httpPut(`/api/model-config/presets/${encodeURIComponent(name)}/apply`, {})
    message.value = data.message || '已切换配置'
    messageError.value = false
    await loadConfig()
  } catch (e) {
    message.value = e.message || '切换失败'
    messageError.value = true
  }
}

async function deletePreset(name) {
  if (!confirm(`确定删除预设 "${name}"？`)) return
  try {
    const data = await httpDelete(`/api/model-config/presets/${encodeURIComponent(name)}`)
    message.value = data.message || '预设已删除'
    messageError.value = false
    await loadPresets()
  } catch (e) {
    message.value = e.message || '删除失败'
    messageError.value = true
  }
}

function resetForm() {
  form.providerType = config.providerType || 'openrouter'
  form.model = config.model || ''
  form.baseUrl = config.baseUrl || ''
  form.apiKey = ''
  customModel.value = ''
  message.value = ''
}

onMounted(() => {
  loadConfig()
  loadPresets()
})
</script>
