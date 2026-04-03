<template>
  <div class="h-full flex flex-col overflow-hidden">
    <!-- Tabs -->
    <div class="flex items-center border-b bg-white px-4 pt-3 shrink-0">
      <button @click="tab = 'model'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="tab === 'model' ? 'border-purple-600 text-purple-600' : 'border-transparent text-gray-500 hover:text-gray-700'">
        模型配置
      </button>
      <button @click="tab = 'client-tools'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="tab === 'client-tools' ? 'border-purple-600 text-purple-600' : 'border-transparent text-gray-500 hover:text-gray-700'">
        端工具
      </button>
      <button @click="tab = 'system'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="tab === 'system' ? 'border-purple-600 text-purple-600' : 'border-transparent text-gray-500 hover:text-gray-700'">
        系统状态
      </button>
    </div>

    <!-- Model Config Tab -->
    <div v-show="tab === 'model'" class="flex-1 overflow-y-auto p-6">
      <!-- Current Status -->
      <section class="mb-6 bg-gray-50 rounded-xl p-4 border border-gray-200">
        <h3 class="text-sm font-bold text-gray-500 mb-2">当前状态</h3>
        <div class="flex items-center gap-3">
          <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium"
            :class="modelConfig.providerName === 'mock' ? 'bg-yellow-100 text-yellow-700' : 'bg-green-100 text-green-700'">
            <span class="w-2 h-2 rounded-full" :class="modelConfig.providerName === 'mock' ? 'bg-yellow-500' : 'bg-green-500'"></span>
            {{ modelConfig.providerName || '未配置' }}
          </span>
          <span v-if="modelConfig.model" class="text-sm text-gray-600">{{ modelConfig.model }}</span>
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
                class="text-xs px-3 py-1 rounded bg-purple-500 text-white hover:bg-purple-600">切换</button>
              <button @click="deletePreset(preset.name)"
                class="text-xs px-2 py-1 rounded text-red-500 hover:bg-red-50">删除</button>
            </div>
          </div>
        </div>
      </section>

      <!-- Config Form -->
      <form @submit.prevent="saveModelConfig" class="space-y-5">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1.5">Provider 类型</label>
          <select v-model="modelForm.providerType"
            class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent bg-white">
            <option value="openrouter">OpenRouter / OpenAI 兼容 (HTTP)</option>
            <option value="ws">WebSocket 网关 (OpenAI 兼容)</option>
            <option value="api">Claude API (Anthropic 官方)</option>
            <option value="mock">Mock (测试模式)</option>
          </select>
          <p class="text-xs text-gray-400 mt-1">
            {{ modelForm.providerType === 'ws' ? 'WebSocket 模式适用于自建的 WS LLM 网关' : 'OpenRouter 兼容 OpenAI 格式，可用于自建 HTTP LLM 网关' }}
          </p>
        </div>

        <div v-if="modelForm.providerType === 'openrouter' || modelForm.providerType === 'ws'">
          <label class="block text-sm font-medium text-gray-700 mb-1.5">
            {{ modelForm.providerType === 'ws' ? 'WebSocket 地址' : 'API 地址 (Base URL)' }}
          </label>
          <input v-model="modelForm.baseUrl" type="text"
            :placeholder="modelForm.providerType === 'ws' ? 'ws://10.32.101.24:8086/ws/chat' : '留空使用 OpenRouter 默认地址，或输入自建网关地址'"
            class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent">
        </div>

        <div v-if="modelForm.providerType !== 'mock'">
          <label class="block text-sm font-medium text-gray-700 mb-1.5">API Key</label>
          <div class="relative">
            <input v-model="modelForm.apiKey" :type="showKey ? 'text' : 'password'" placeholder="输入 API Key（自建网关可留空）"
              class="w-full px-3 py-2.5 pr-20 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent">
            <button type="button" @click="showKey = !showKey"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600 px-2 py-1">
              {{ showKey ? '隐藏' : '显示' }}
            </button>
          </div>
          <p v-if="modelConfig.apiKeyMasked" class="text-xs text-gray-400 mt-1">当前: {{ modelConfig.apiKeyMasked }}</p>
        </div>

        <div v-if="modelForm.providerType !== 'mock'">
          <label class="block text-sm font-medium text-gray-700 mb-1.5">模型</label>
          <select v-model="modelForm.model"
            class="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent bg-white">
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
          <div class="mt-2">
            <input v-model="customModel" type="text" placeholder="或输入自定义模型名称..."
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-400 focus:border-transparent"
              @input="onCustomModelInput">
            <p class="text-xs text-gray-400 mt-1">输入自定义名称会覆盖上方选择，格式如: provider/model-name</p>
          </div>
        </div>

        <div class="flex gap-3 pt-2">
          <button type="submit" :disabled="modelSaving"
            class="bg-gradient-to-r from-purple-500 to-purple-600 text-white px-6 py-2.5 rounded-lg hover:from-purple-600 hover:to-purple-700 shadow-md disabled:opacity-50 text-sm font-medium">
            {{ modelSaving ? '保存中...' : '保存配置' }}
          </button>
          <button type="button" @click="showSavePresetDialog = true"
            class="px-4 py-2.5 rounded-lg border border-purple-300 text-purple-600 hover:bg-purple-50 text-sm font-medium">
            保存为预设
          </button>
          <button type="button" @click="resetModelForm"
            class="px-6 py-2.5 rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 text-sm">
            重置
          </button>
        </div>

        <div v-if="modelMessage" class="p-3 rounded-lg text-sm"
          :class="modelMessageError ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-green-50 text-green-700 border border-green-200'">
          {{ modelMessage }}
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

    <!-- Client Tools Tab -->
    <div v-show="tab === 'client-tools'" class="flex-1 overflow-y-auto p-6">
      <p class="text-gray-500 text-sm mb-4">管理端工具 metadata（含 inputSchema）与仿真结果</p>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <!-- Left: Tool Configuration Form -->
        <div class="bg-gray-50 rounded-xl border border-gray-200 p-4">
          <h3 class="text-lg font-bold mb-3">工具配置</h3>
          <div class="space-y-3">
            <input v-model="ctForm.namespace" type="text" placeholder="Namespace (如 GeoInformation)"
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
            <input v-model="ctForm.name" type="text" placeholder="Name (如 GetPosition)"
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
            <input v-model="ctForm.description" type="text" placeholder="描述（可选）"
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
            <div class="grid grid-cols-2 gap-2">
              <input v-model="ctForm.owner" type="text" placeholder="owner"
                class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
              <input v-model="ctForm.version" type="text" placeholder="version"
                class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-700">
              <input v-model="ctForm.enabled" type="checkbox">默认启用
            </label>
            <div>
              <div class="text-xs text-gray-600 mb-1">inputSchema</div>
              <textarea v-model="ctForm.inputSchema" class="w-full h-36 px-3 py-2 border border-gray-200 rounded-lg text-xs font-mono"
                placeholder='{"type":"object","properties":{},"required":[]}'></textarea>
            </div>
            <div>
              <div class="text-xs text-gray-600 mb-1">仿真结果 JSON</div>
              <textarea v-model="ctForm.mockJson" class="w-full h-40 px-3 py-2 border border-gray-200 rounded-lg text-xs font-mono"
                placeholder='{"ok":true,"data":{"result":"mock"},"error":null}'></textarea>
            </div>

            <!-- Simulation -->
            <div class="border border-blue-200 bg-blue-50 rounded-lg p-3 space-y-2">
              <div class="text-sm font-medium text-blue-800">仿真调试</div>
              <textarea v-model="simParams" class="w-full h-20 px-2 py-1 border border-blue-200 rounded text-xs font-mono"
                placeholder='{"scene":"chat"}'></textarea>
              <div class="flex gap-2">
                <button @click="runSimulation" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">执行仿真</button>
                <button @click="simResult = '暂无仿真结果'" class="bg-white border border-gray-300 text-gray-700 px-3 py-1.5 rounded text-sm">清空</button>
              </div>
              <pre class="text-xs bg-white border border-blue-200 rounded p-2 min-h-[60px] overflow-auto">{{ simResult }}</pre>
            </div>

            <div class="flex gap-2">
              <button @click="saveClientTool" class="bg-gradient-to-r from-green-500 to-green-600 text-white px-4 py-2 rounded-lg text-sm shadow-md">保存工具</button>
              <button @click="resetCtForm" class="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg text-sm">重置</button>
            </div>
            <div v-if="ctFormStatus" :class="ctFormStatusClass" class="text-xs">{{ ctFormStatus }}</div>
          </div>
        </div>

        <!-- Right: Tool List -->
        <div class="bg-gray-50 rounded-xl border border-gray-200 p-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-lg font-bold">已录入端工具</h3>
            <button @click="loadClientTools" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
          </div>
          <div v-if="ctToolList.length === 0" class="text-gray-400 text-sm">暂无已录入端工具</div>
          <div v-else class="space-y-2 max-h-[640px] overflow-y-auto">
            <div v-for="tool in ctToolList" :key="tool.key" class="bg-white rounded-lg border border-gray-200 p-3">
              <div class="flex items-center justify-between gap-2">
                <div class="font-medium text-purple-700 break-all">{{ tool.key }}</div>
                <span :class="tool.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-200 text-gray-600'" class="text-xs px-2 py-0.5 rounded">
                  {{ tool.enabled ? '启用' : '禁用' }}
                </span>
              </div>
              <div class="text-sm text-gray-600 mt-1">{{ tool.description || '无描述' }}</div>
              <div class="mt-2 flex gap-2">
                <button @click="editClientTool(tool)" class="text-xs bg-blue-500 text-white px-2 py-1 rounded">编辑</button>
                <button @click="removeClientTool(tool.key)" class="text-xs bg-red-500 text-white px-2 py-1 rounded">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- System Tab -->
    <div v-show="tab === 'system'" class="flex-1 overflow-y-auto p-6">
      <!-- Health -->
      <section class="mb-6">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-lg font-bold">健康检查</h3>
          <button @click="loadHealthData" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
        </div>
        <div v-if="!health" class="text-gray-400 text-sm">点击刷新查看</div>
        <pre v-else class="bg-gray-50 rounded-lg p-4 text-sm font-mono border border-gray-200 overflow-auto">{{ JSON.stringify(health, null, 2) }}</pre>
      </section>

      <!-- Current User -->
      <section class="border-t border-gray-200 pt-4 mb-6">
        <h3 class="text-lg font-bold mb-3">当前用户</h3>
        <div class="bg-gray-50 rounded-lg p-4 border border-gray-200 text-sm space-y-1">
          <p>用户ID: <span class="font-mono text-purple-600">{{ auth.userId }}</span></p>
          <p>用户名: <span class="font-medium">{{ auth.user?.username || '-' }}</span></p>
          <p>昵称: <span class="font-medium">{{ auth.user?.nickname || '-' }}</span></p>
          <p>角色: <span class="font-medium" :class="auth.isAdmin ? 'text-red-600' : 'text-blue-600'">{{ auth.user?.role || 'USER' }}</span></p>
          <p>会员等级: {{ auth.user?.memberLevel || '-' }}</p>
        </div>
      </section>

      <!-- Session Info -->
      <section class="border-t border-gray-200 pt-4">
        <h3 class="text-lg font-bold mb-3">会话信息</h3>
        <div class="text-sm text-gray-600 space-y-1">
          <p>会话ID: <span class="font-mono text-purple-600">{{ chatStore.sessionId }}</span></p>
          <p>WebSocket: <span :class="wsConnected ? 'text-green-600' : 'text-red-500'">{{ wsConnected ? '已连接' : '未连接' }}</span></p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { useChatStore } from '../stores/chat.js'
import { checkHealth, isConnected } from '../api/ws.js'
import { httpGet, httpPut, httpPost, httpDelete } from '../api/http.js'

const auth = useAuthStore()
const chatStore = useChatStore()

const tab = ref('model')

// ==================== Model Config ====================
const modelConfig = reactive({ providerName: '', model: '', baseUrl: '', hasApiKey: false, apiKeyMasked: '' })
const modelForm = reactive({ providerType: 'openrouter', apiKey: '', model: '', baseUrl: '' })
const customModel = ref('')
const showKey = ref(false)
const modelSaving = ref(false)
const modelMessage = ref('')
const modelMessageError = ref(false)
const presets = ref([])
const showSavePresetDialog = ref(false)
const presetName = ref('')

function onCustomModelInput() {
  if (customModel.value.trim()) modelForm.model = customModel.value.trim()
}

async function loadModelConfig() {
  try {
    const data = await httpGet('/api/model-config')
    Object.assign(modelConfig, data)
    modelForm.providerType = data.providerType || 'openrouter'
    modelForm.model = data.model || ''
    modelForm.baseUrl = data.baseUrl || ''
    modelForm.apiKey = ''
    customModel.value = ''
  } catch (e) { console.error('Failed to load model config', e) }
}

async function loadPresets() {
  try { presets.value = await httpGet('/api/model-config/presets') } catch (e) { console.error('Failed to load presets', e) }
}

async function saveModelConfig() {
  modelSaving.value = true; modelMessage.value = ''
  try {
    const effectiveModel = customModel.value.trim() || modelForm.model
    const data = await httpPut('/api/model-config', {
      providerType: modelForm.providerType, apiKey: modelForm.apiKey || '',
      model: effectiveModel, baseUrl: modelForm.baseUrl
    })
    modelMessage.value = data.message || '配置已保存'
    modelMessageError.value = false
    await loadModelConfig()
  } catch (e) {
    modelMessage.value = e.message || '保存失败'
    modelMessageError.value = true
  }
  modelSaving.value = false
}

async function saveAsPreset() {
  const name = presetName.value.trim()
  if (!name) return
  try {
    const data = await httpPost('/api/model-config/presets', { name })
    modelMessage.value = data.message || '预设已保存'
    modelMessageError.value = false
    showSavePresetDialog.value = false
    presetName.value = ''
    await loadPresets()
  } catch (e) {
    modelMessage.value = e.message || '保存预设失败'
    modelMessageError.value = true
  }
}

async function applyPreset(name) {
  try {
    const data = await httpPut(`/api/model-config/presets/${encodeURIComponent(name)}/apply`, {})
    modelMessage.value = data.message || '已切换配置'
    modelMessageError.value = false
    await loadModelConfig()
  } catch (e) {
    modelMessage.value = e.message || '切换失败'
    modelMessageError.value = true
  }
}

async function deletePreset(name) {
  if (!confirm(`确定删除预设 "${name}"？`)) return
  try {
    const data = await httpDelete(`/api/model-config/presets/${encodeURIComponent(name)}`)
    modelMessage.value = data.message || '预设已删除'
    modelMessageError.value = false
    await loadPresets()
  } catch (e) {
    modelMessage.value = e.message || '删除失败'
    modelMessageError.value = true
  }
}

function resetModelForm() {
  modelForm.providerType = modelConfig.providerType || 'openrouter'
  modelForm.model = modelConfig.model || ''
  modelForm.baseUrl = modelConfig.baseUrl || ''
  modelForm.apiKey = ''
  customModel.value = ''
  modelMessage.value = ''
}

// ==================== Client Tools ====================
const ctForm = ref({
  namespace: '', name: '', description: '', owner: 'team-web', version: '1.0.0',
  enabled: true,
  inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
  mockJson: '{\n  "ok": true,\n  "data": { "result": "mock data" },\n  "error": null\n}'
})
const simParams = ref('{\n  "scene": "chat"\n}')
const simResult = ref('暂无仿真结果')
const ctFormStatus = ref('')
const ctFormStatusClass = ref('text-gray-500')
const ctToolList = ref([])

function resetCtForm() {
  ctForm.value = {
    namespace: '', name: '', description: '', owner: 'team-web', version: '1.0.0',
    enabled: true,
    inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
    mockJson: '{\n  "ok": true,\n  "data": { "result": "mock data" },\n  "error": null\n}'
  }
  ctFormStatus.value = '已重置'
  ctFormStatusClass.value = 'text-gray-500'
}

async function loadClientTools() {
  try {
    const data = await httpGet('/client-tools')
    ctToolList.value = data.tools || []
  } catch (e) { ctToolList.value = [] }
}

async function saveClientTool() {
  const { namespace, name } = ctForm.value
  if (!namespace || !name) { ctFormStatus.value = 'Namespace 和 Name 为必填项'; ctFormStatusClass.value = 'text-red-500'; return }

  let inputSchema, mockResponse
  try { inputSchema = JSON.parse(ctForm.value.inputSchema) } catch (e) { ctFormStatus.value = 'inputSchema JSON 错误: ' + e.message; ctFormStatusClass.value = 'text-red-500'; return }
  try { mockResponse = JSON.parse(ctForm.value.mockJson) } catch (e) { ctFormStatus.value = '仿真 JSON 错误: ' + e.message; ctFormStatusClass.value = 'text-red-500'; return }

  const key = namespace + '.' + name
  try {
    await httpPut(`/client-tools/${encodeURIComponent(key)}`, {
      namespace, name: ctForm.value.name, description: ctForm.value.description,
      owner: ctForm.value.owner, version: ctForm.value.version,
      inputSchema, enabled: ctForm.value.enabled, mockResponse
    })
    ctFormStatus.value = `已保存 ${key}`
    ctFormStatusClass.value = 'text-green-600'
    loadClientTools()
  } catch (e) {
    ctFormStatus.value = '保存失败: ' + e.message
    ctFormStatusClass.value = 'text-red-500'
  }
}

function editClientTool(tool) {
  ctForm.value = {
    namespace: tool.namespace || '', name: tool.name || '',
    description: tool.description || '', owner: tool.owner || 'team-web',
    version: tool.version || '1.0.0', enabled: tool.enabled !== false,
    inputSchema: JSON.stringify(tool.inputSchema || { type: 'object' }, null, 2),
    mockJson: JSON.stringify(tool.mockResponse || { ok: true, data: {}, error: null }, null, 2)
  }
  ctFormStatus.value = `已载入 ${tool.key}`
  ctFormStatusClass.value = 'text-gray-500'
}

async function removeClientTool(key) {
  if (!confirm('确定删除: ' + key + ' ?')) return
  try {
    await httpDelete(`/client-tools/${encodeURIComponent(key)}`)
    loadClientTools()
    ctFormStatus.value = `已删除 ${key}`
    ctFormStatusClass.value = 'text-green-600'
  } catch (e) {
    ctFormStatus.value = '删除失败: ' + e.message
    ctFormStatusClass.value = 'text-red-500'
  }
}

function runSimulation() {
  let params = {}
  try { params = JSON.parse(simParams.value) } catch (e) { ctFormStatus.value = '参数 JSON 错误'; ctFormStatusClass.value = 'text-red-500'; return }
  let mock = { ok: true, data: {}, error: null }
  try { mock = JSON.parse(ctForm.value.mockJson) } catch (e) { ctFormStatus.value = '仿真 JSON 错误'; ctFormStatusClass.value = 'text-red-500'; return }

  const result = mock.ok
    ? { ok: true, data: { input: params, output: mock.data }, error: null }
    : { ok: false, data: null, error: mock.error || { code: 'SIM_ERROR', message: '仿真失败' } }
  simResult.value = JSON.stringify(result, null, 2)
  ctFormStatus.value = '仿真执行完成'
  ctFormStatusClass.value = 'text-green-600'
}

// ==================== System ====================
const health = ref(null)
const wsConnected = ref(false)

async function loadHealthData() {
  try { health.value = await checkHealth() } catch { health.value = null }
}

// ==================== Lifecycle ====================
onMounted(() => {
  loadModelConfig()
  loadPresets()
  loadClientTools()
  wsConnected.value = isConnected()
  loadHealthData()
})
</script>
