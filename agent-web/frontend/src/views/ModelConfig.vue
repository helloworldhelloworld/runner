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

    <!-- Config Form -->
    <form @submit.prevent="saveConfig" class="space-y-5">
      <!-- Provider Type -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1.5">Provider 类型</label>
        <select v-model="form.providerType"
          class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent bg-white">
          <option value="openrouter">OpenRouter / OpenAI 兼容</option>
          <option value="api">Claude API (Anthropic 官方)</option>
          <option value="mock">Mock (测试模式)</option>
        </select>
        <p class="text-xs text-gray-400 mt-1">OpenRouter 兼容 OpenAI 格式，可用于自建 LLM 网关</p>
      </div>

      <!-- Base URL -->
      <div v-if="form.providerType === 'openrouter'">
        <label class="block text-sm font-medium text-gray-700 mb-1.5">API 地址 (Base URL)</label>
        <input v-model="form.baseUrl" type="text" placeholder="留空使用 OpenRouter 默认地址，或输入自建网关地址"
          class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent">
        <p class="text-xs text-gray-400 mt-1">示例: https://openrouter.ai/api/v1 或 http://10.32.101.24:8086/llm/openai</p>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { httpGet, httpPut } from '../api/http.js'

const config = reactive({ providerName: '', model: '', baseUrl: '', hasApiKey: false, apiKeyMasked: '' })
const form = reactive({ providerType: 'openrouter', apiKey: '', model: '', baseUrl: '' })
const customModel = ref('')
const showKey = ref(false)
const saving = ref(false)
const message = ref('')
const messageError = ref(false)

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
    // If the model matches a preset, keep the select; otherwise set custom
    customModel.value = ''
  } catch (e) {
    console.error('Failed to load model config', e)
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

function resetForm() {
  form.providerType = config.providerType || 'openrouter'
  form.model = config.model || ''
  form.baseUrl = config.baseUrl || ''
  form.apiKey = ''
  customModel.value = ''
  message.value = ''
}

onMounted(loadConfig)
</script>
