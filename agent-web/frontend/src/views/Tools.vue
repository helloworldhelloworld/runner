<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">工具 & 技能</h2>

    <!-- Skills -->
    <section class="mb-6">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">可用技能</h3>
        <button @click="loadSkillsList" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="skills.length === 0" class="text-gray-400 text-sm">暂无可用技能</div>
      <div v-else class="space-y-2">
        <div v-for="s in skills" :key="s.name" class="bg-gray-50 rounded-lg p-3 border border-gray-200 flex items-center justify-between">
          <span class="font-medium text-purple-700">{{ s.name || '-' }}</span>
          <span class="text-xs text-gray-400">[{{ s.format || '' }}]</span>
        </div>
      </div>
    </section>

    <!-- Tools -->
    <section class="mb-6 border-t border-gray-200 pt-4">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">可用工具</h3>
        <button @click="loadToolsList" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="toolsInfo" class="text-xs text-gray-500 mb-2">共 {{ toolsInfo.total || 0 }} 个工具，启用 {{ toolsInfo.enabled || 0 }} 个</div>
      <div v-if="tools.length === 0" class="text-gray-400 text-sm">暂无可用工具</div>
      <div v-else class="space-y-2">
        <div v-for="t in tools" :key="t.name" class="bg-gray-50 rounded-lg p-3 border border-gray-200">
          <div class="flex justify-between items-start">
            <span class="font-medium text-blue-700">{{ t.name || '-' }}</span>
            <span :class="t.enabled ? 'text-green-500' : 'text-red-400'" class="text-xs">{{ t.enabled ? '启用' : '禁用' }}</span>
          </div>
          <div class="mt-1 flex flex-wrap gap-1">
            <span v-if="t.source" class="inline-block bg-blue-100 text-blue-700 text-xs px-1.5 py-0.5 rounded">{{ t.source }}</span>
            <span v-if="t.category" class="inline-block bg-green-100 text-green-700 text-xs px-1.5 py-0.5 rounded">{{ t.category }}</span>
            <span v-if="t.readOnly" class="inline-block bg-gray-100 text-gray-600 text-xs px-1.5 py-0.5 rounded">只读</span>
            <span v-if="t.destructive" class="inline-block bg-red-100 text-red-600 text-xs px-1.5 py-0.5 rounded">危险</span>
            <span v-if="t.clientSide" class="inline-block bg-yellow-100 text-yellow-700 text-xs px-1.5 py-0.5 rounded">客户端</span>
          </div>
          <router-link v-if="t.clientSide" to="/client-tools" class="mt-2 text-xs text-blue-700 hover:text-blue-900 inline-block">
            去端工具页维护
          </router-link>
        </div>
      </div>
    </section>

    <!-- MCP Servers -->
    <section class="mb-6 border-t border-gray-200 pt-4">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">MCP 服务器</h3>
        <button @click="loadMcp" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="!mcpData" class="text-gray-400 text-sm">点击刷新查看</div>
      <div v-else-if="!mcpData.enabled" class="text-gray-400 text-sm">MCP 未启用</div>
      <div v-else>
        <div class="text-xs text-gray-500 mb-2">共 {{ mcpData.serverCount }} 个服务器，{{ mcpData.totalToolCount }} 个工具</div>
        <div v-for="s in (mcpData.servers || [])" :key="s.name" class="bg-gray-50 rounded-lg p-3 border border-gray-200 mb-2">
          <div class="font-medium text-green-700">{{ s.name }} ({{ s.toolCount }} 个工具)</div>
          <div v-for="t in (s.tools || [])" :key="t.name" class="text-sm ml-4 flex justify-between mt-1">
            <span>{{ t.name }}</span>
            <span :class="t.enabled ? 'text-green-500' : 'text-red-400'" class="text-xs">{{ t.enabled ? '启用' : '禁用' }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Tool Definitions -->
    <section class="border-t border-gray-200 pt-4">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">LLM 工具定义</h3>
        <button @click="loadDefs" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="!toolDefs" class="text-gray-400 text-sm">点击刷新查看</div>
      <details v-else>
        <summary class="text-sm text-purple-600 cursor-pointer">展开查看 JSON ({{ toolDefs.count }} 个)</summary>
        <pre class="text-xs bg-gray-50 p-3 rounded-lg mt-2 overflow-auto max-h-96 border border-gray-200">{{ JSON.stringify(toolDefs.tools, null, 2) }}</pre>
      </details>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getSkills, getToolsDetail, getMcpServers, getToolDefinitions } from '../api/ws.js'

const skills = ref([])
const tools = ref([])
const toolsInfo = ref(null)
const mcpData = ref(null)
const toolDefs = ref(null)

async function loadSkillsList() {
  try { skills.value = await getSkills() } catch { skills.value = [] }
}

async function loadToolsList() {
  try {
    const result = await getToolsDetail()
    tools.value = result.tools || []
    toolsInfo.value = result
  } catch { tools.value = [] }
}

async function loadMcp() {
  try { mcpData.value = await getMcpServers() } catch { mcpData.value = null }
}

async function loadDefs() {
  try { toolDefs.value = await getToolDefinitions() } catch { toolDefs.value = null }
}

onMounted(() => { loadSkillsList(); loadToolsList() })
</script>
