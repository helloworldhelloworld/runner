<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">端工具管理</h2>
    <p class="text-gray-500 text-sm mb-4">管理端工具 metadata（含 inputSchema）与仿真结果</p>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- Left: Tool Configuration Form -->
      <div class="bg-gray-50 rounded-xl border border-gray-200 p-4">
        <h3 class="text-lg font-bold mb-3">工具配置</h3>
        <div class="space-y-3">
          <input v-model="form.namespace" type="text" placeholder="Namespace (如 GeoInformation)"
            class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
          <input v-model="form.name" type="text" placeholder="Name (如 GetPosition)"
            class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
          <input v-model="form.description" type="text" placeholder="描述（可选）"
            class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
          <div class="grid grid-cols-2 gap-2">
            <input v-model="form.owner" type="text" placeholder="owner"
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
            <input v-model="form.version" type="text" placeholder="version"
              class="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm">
          </div>
          <label class="flex items-center gap-2 text-sm text-gray-700">
            <input v-model="form.enabled" type="checkbox">默认启用
          </label>
          <div>
            <div class="text-xs text-gray-600 mb-1">inputSchema</div>
            <textarea v-model="form.inputSchema" class="w-full h-36 px-3 py-2 border border-gray-200 rounded-lg text-xs font-mono"
              placeholder='{"type":"object","properties":{},"required":[]}'></textarea>
          </div>
          <div>
            <div class="text-xs text-gray-600 mb-1">仿真结果 JSON</div>
            <textarea v-model="form.mockJson" class="w-full h-40 px-3 py-2 border border-gray-200 rounded-lg text-xs font-mono"
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
            <button @click="saveTool" class="bg-gradient-to-r from-green-500 to-green-600 text-white px-4 py-2 rounded-lg text-sm shadow-md">保存工具</button>
            <button @click="resetForm" class="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg text-sm">重置</button>
          </div>
          <div v-if="formStatus" :class="formStatusClass" class="text-xs">{{ formStatus }}</div>
        </div>
      </div>

      <!-- Right: Tool List -->
      <div class="bg-gray-50 rounded-xl border border-gray-200 p-4">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-lg font-bold">已录入端工具</h3>
          <button @click="loadTools" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
        </div>
        <div v-if="toolList.length === 0" class="text-gray-400 text-sm">暂无已录入端工具</div>
        <div v-else class="space-y-2 max-h-[640px] overflow-y-auto">
          <div v-for="tool in toolList" :key="tool.key" class="bg-white rounded-lg border border-gray-200 p-3">
            <div class="flex items-center justify-between gap-2">
              <div class="font-medium text-purple-700 break-all">{{ tool.key }}</div>
              <span :class="tool.enabled ? 'bg-green-100 text-green-700' : 'bg-gray-200 text-gray-600'" class="text-xs px-2 py-0.5 rounded">
                {{ tool.enabled ? '启用' : '禁用' }}
              </span>
            </div>
            <div class="text-sm text-gray-600 mt-1">{{ tool.description || '无描述' }}</div>
            <div class="mt-2 flex gap-2">
              <button @click="editTool(tool)" class="text-xs bg-blue-500 text-white px-2 py-1 rounded">编辑</button>
              <button @click="removeTool(tool.key)" class="text-xs bg-red-500 text-white px-2 py-1 rounded">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { httpGet, httpPut, httpDelete } from '../api/http.js'

const form = ref({
  namespace: '', name: '', description: '', owner: 'team-web', version: '1.0.0',
  enabled: true,
  inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
  mockJson: '{\n  "ok": true,\n  "data": { "result": "mock data" },\n  "error": null\n}'
})
const simParams = ref('{\n  "scene": "chat"\n}')
const simResult = ref('暂无仿真结果')
const formStatus = ref('')
const formStatusClass = ref('text-gray-500')
const toolList = ref([])

function resetForm() {
  form.value = {
    namespace: '', name: '', description: '', owner: 'team-web', version: '1.0.0',
    enabled: true,
    inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
    mockJson: '{\n  "ok": true,\n  "data": { "result": "mock data" },\n  "error": null\n}'
  }
  formStatus.value = '已重置'
  formStatusClass.value = 'text-gray-500'
}

async function loadTools() {
  try {
    const data = await httpGet('/client-tools')
    toolList.value = data.tools || []
  } catch (e) {
    toolList.value = []
  }
}

async function saveTool() {
  const { namespace, name } = form.value
  if (!namespace || !name) { formStatus.value = 'Namespace 和 Name 为必填项'; formStatusClass.value = 'text-red-500'; return }

  let inputSchema, mockResponse
  try { inputSchema = JSON.parse(form.value.inputSchema) } catch (e) { formStatus.value = 'inputSchema JSON 错误: ' + e.message; formStatusClass.value = 'text-red-500'; return }
  try { mockResponse = JSON.parse(form.value.mockJson) } catch (e) { formStatus.value = '仿真 JSON 错误: ' + e.message; formStatusClass.value = 'text-red-500'; return }

  const key = namespace + '.' + name
  try {
    await httpPut(`/client-tools/${encodeURIComponent(key)}`, {
      namespace, name: form.value.name, description: form.value.description,
      owner: form.value.owner, version: form.value.version,
      inputSchema, enabled: form.value.enabled, mockResponse
    })
    formStatus.value = `已保存 ${key}`
    formStatusClass.value = 'text-green-600'
    loadTools()
  } catch (e) {
    formStatus.value = '保存失败: ' + e.message
    formStatusClass.value = 'text-red-500'
  }
}

function editTool(tool) {
  form.value = {
    namespace: tool.namespace || '', name: tool.name || '',
    description: tool.description || '', owner: tool.owner || 'team-web',
    version: tool.version || '1.0.0', enabled: tool.enabled !== false,
    inputSchema: JSON.stringify(tool.inputSchema || { type: 'object' }, null, 2),
    mockJson: JSON.stringify(tool.mockResponse || { ok: true, data: {}, error: null }, null, 2)
  }
  formStatus.value = `已载入 ${tool.key}`
  formStatusClass.value = 'text-gray-500'
}

async function removeTool(key) {
  if (!confirm('确定删除: ' + key + ' ?')) return
  try {
    await httpDelete(`/client-tools/${encodeURIComponent(key)}`)
    loadTools()
    formStatus.value = `已删除 ${key}`
    formStatusClass.value = 'text-green-600'
  } catch (e) {
    formStatus.value = '删除失败: ' + e.message
    formStatusClass.value = 'text-red-500'
  }
}

function runSimulation() {
  let params = {}
  try { params = JSON.parse(simParams.value) } catch (e) { formStatus.value = '参数 JSON 错误'; formStatusClass.value = 'text-red-500'; return }
  let mock = { ok: true, data: {}, error: null }
  try { mock = JSON.parse(form.value.mockJson) } catch (e) { formStatus.value = '仿真 JSON 错误'; formStatusClass.value = 'text-red-500'; return }

  const result = mock.ok
    ? { ok: true, data: { input: params, output: mock.data }, error: null }
    : { ok: false, data: null, error: mock.error || { code: 'SIM_ERROR', message: '仿真失败' } }
  simResult.value = JSON.stringify(result, null, 2)
  formStatus.value = '仿真执行完成'
  formStatusClass.value = 'text-green-600'
}

onMounted(loadTools)
</script>
