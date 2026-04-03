<template>
  <div class="h-full flex flex-col overflow-hidden">
    <!-- Top-level tabs: Pipeline | Tools Overview -->
    <div class="flex items-center border-b bg-white px-4 pt-3 shrink-0">
      <button @click="topTab = 'pipeline'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="topTab === 'pipeline' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'">
        Skill Pipeline
      </button>
      <button @click="topTab = 'tools'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="topTab === 'tools' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'">
        工具总览
      </button>
    </div>

    <!-- Pipeline Tab -->
    <div v-show="topTab === 'pipeline'" class="flex-1 overflow-y-auto p-4 max-w-7xl mx-auto w-full">
      <!-- Header -->
      <div class="flex items-center justify-between mb-4">
        <select v-model="selectedSkillId" @change="onSkillChange" class="border rounded px-3 py-1.5 text-sm">
          <option value="">-- 选择或新建 Skill --</option>
          <option value="__new__">+ 新建 Skill</option>
          <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>

      <!-- Stage Tabs -->
      <div class="flex border-b mb-4">
        <button v-for="(s, i) in stages" :key="s.key"
          @click="goToStage(s.key)"
          :disabled="!canAccessStage(s.key)"
          class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
          :class="activeStage === s.key
            ? 'border-blue-600 text-blue-600'
            : canAccessStage(s.key)
              ? 'border-transparent text-gray-500 hover:text-gray-700'
              : 'border-transparent text-gray-300 cursor-not-allowed'">
          {{ i + 1 }}. {{ s.label }}
          <span v-if="stageStatus[s.key] === 'done'" class="ml-1 text-green-500">&#10003;</span>
        </button>
      </div>

      <!-- Next Step Banner -->
      <div v-if="nextStepHint" class="mb-4 bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-center justify-between">
        <span class="text-sm text-blue-700">{{ nextStepHint }}</span>
        <button v-if="nextStepAction" @click="nextStepAction.fn" :disabled="nextStepAction.disabled"
          class="bg-blue-600 text-white px-4 py-1 rounded text-sm hover:bg-blue-700 disabled:opacity-50">
          {{ nextStepAction.label }}
        </button>
      </div>

      <!-- Stage: Create -->
      <div v-show="activeStage === 'create'" class="grid grid-cols-2 gap-4" style="min-height:400px">
        <div class="bg-white rounded-lg shadow flex flex-col">
          <div class="p-3 border-b font-medium text-sm">Skill Creator Chat</div>
          <div ref="createChatRef" class="flex-1 overflow-y-auto p-3 space-y-2" style="max-height:450px">
            <div v-for="(m, i) in createMessages" :key="i"
              class="text-sm p-2 rounded" :class="m.role === 'user' ? 'bg-blue-50 ml-8' : 'bg-gray-50 mr-8'">
              <div v-if="m.role === 'user'" class="whitespace-pre-wrap">{{ m.text }}</div>
              <div v-else v-html="renderMarkdown(m.text)" class="prose prose-sm max-w-none"></div>
            </div>
          </div>
          <div class="p-3 border-t flex gap-2">
            <input v-model="createInput" @keyup.enter="sendCreateMessage" placeholder="描述你要创建的 Skill..."
              class="flex-1 border rounded px-3 py-1.5 text-sm" :disabled="createSending" />
            <button @click="sendCreateMessage" :disabled="createSending || !createInput.trim()"
              class="bg-blue-600 text-white px-4 py-1.5 rounded text-sm disabled:opacity-50">
              {{ createSending ? '...' : '发送' }}
            </button>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow flex flex-col">
          <div class="p-3 border-b font-medium text-sm flex justify-between">
            <span>Skill Draft</span>
            <button v-if="currentDraft?.name" @click="saveDraft" :disabled="createSending"
              class="bg-green-600 text-white px-3 py-1 rounded text-xs hover:bg-green-700 disabled:opacity-50">
              保存并继续 &rarr;
            </button>
          </div>
          <div class="flex-1 overflow-y-auto p-3" style="max-height:450px">
            <template v-if="currentDraft?.name">
              <div class="space-y-3 text-sm">
                <div class="flex items-center gap-2">
                  <span class="font-mono bg-gray-100 px-2 py-0.5 rounded text-blue-700 font-bold">{{ currentDraft.name }}</span>
                  <span v-if="currentDraft.version" class="text-gray-400 text-xs">v{{ currentDraft.version }}</span>
                  <span v-if="currentDraft.category" class="bg-indigo-100 text-indigo-700 px-1.5 py-0.5 rounded text-xs">{{ currentDraft.category }}</span>
                </div>
                <p v-if="currentDraft.description" class="text-gray-600">{{ currentDraft.description }}</p>
                <div v-if="currentDraft.triggers?.length">
                  <div class="text-gray-400 text-xs mb-1">触发词</div>
                  <div class="flex flex-wrap gap-1">
                    <span v-for="t in currentDraft.triggers" :key="t" class="bg-orange-50 text-orange-700 px-1.5 py-0.5 rounded text-xs">{{ t }}</span>
                  </div>
                </div>
                <div v-if="currentDraft.toolNames?.length">
                  <div class="text-gray-400 text-xs mb-1">工具</div>
                  <div class="flex flex-wrap gap-1">
                    <span v-for="t in currentDraft.toolNames" :key="t" class="bg-green-50 text-green-700 px-1.5 py-0.5 rounded text-xs">{{ t }}</span>
                  </div>
                </div>
                <details class="mt-2">
                  <summary class="text-gray-400 text-xs cursor-pointer hover:text-gray-600">System Prompt</summary>
                  <div v-html="renderMarkdown(currentDraft.systemPrompt || '')" class="mt-1 prose prose-sm max-w-none bg-gray-50 rounded p-2 text-xs max-h-48 overflow-y-auto"></div>
                </details>
              </div>
            </template>
            <div v-else class="text-gray-400 text-center mt-20">对话后将在此显示 Skill 定义</div>
          </div>
        </div>
      </div>

      <!-- Stage: Generate Cases -->
      <div v-show="activeStage === 'cases'" class="grid grid-cols-2 gap-4" style="min-height:400px">
        <div class="bg-white rounded-lg shadow flex flex-col">
          <div class="p-3 border-b font-medium text-sm">用例生成对话</div>
          <div ref="casesChatRef" class="flex-1 overflow-y-auto p-3 space-y-2" style="max-height:450px">
            <div v-for="(m, i) in casesMessages" :key="i"
              class="text-sm p-2 rounded" :class="m.role === 'user' ? 'bg-blue-50 ml-8' : 'bg-gray-50 mr-8'">
              <div v-if="m.role === 'user'" class="whitespace-pre-wrap">{{ m.text }}</div>
              <div v-else v-html="renderMarkdown(m.text)" class="prose prose-sm max-w-none"></div>
            </div>
          </div>
          <div class="p-3 border-t">
            <div class="flex gap-2 mb-2">
              <button v-for="q in quickCasesPrompts" :key="q" @click="quickCasesInput(q)"
                :disabled="casesSending"
                class="text-xs bg-gray-100 hover:bg-gray-200 px-2 py-1 rounded disabled:opacity-50">{{ q }}</button>
            </div>
            <div class="flex gap-2">
              <input v-model="casesInput" @keyup.enter="sendCasesMessage"
                placeholder="补充异常场景 / 修改第N个 / 增加多轮对话用例..."
                class="flex-1 border rounded px-3 py-1.5 text-sm" :disabled="casesSending" />
              <button @click="sendCasesMessage" :disabled="casesSending || !casesInput.trim()"
                class="bg-blue-600 text-white px-4 py-1.5 rounded text-sm disabled:opacity-50">
                {{ casesSending ? '...' : '发送' }}
              </button>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow flex flex-col">
          <div class="p-3 border-b font-medium text-sm flex justify-between">
            <span>测试用例 ({{ generatedCases.length }})</span>
            <button v-if="generatedCases.length" @click="confirmCases"
              class="bg-green-600 text-white px-3 py-1 rounded text-xs hover:bg-green-700">
              确认用例并执行 &rarr;
            </button>
          </div>
          <div class="flex-1 overflow-y-auto p-3 text-sm" style="max-height:450px">
            <div v-for="(tc, i) in generatedCases" :key="i"
              class="mb-2 p-2 rounded border" :class="i % 2 === 0 ? 'bg-gray-50' : 'bg-white'">
              <div class="flex items-start justify-between">
                <div class="font-medium text-gray-800">{{ i + 1 }}. {{ tc.description || tc.input }}</div>
                <span v-if="tc.category" class="text-xs bg-gray-200 px-1.5 py-0.5 rounded ml-2 shrink-0">{{ tc.category }}</span>
              </div>
              <div class="text-gray-500 text-xs mt-1">{{ tc.input }}</div>
              <div v-if="tc.assertions?.length" class="mt-1 flex flex-wrap gap-1">
                <span v-for="a in tc.assertions" :key="a" class="text-xs bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded">{{ a }}</span>
              </div>
            </div>
            <div v-if="!generatedCases.length" class="text-gray-400 text-center mt-20">
              点击上方快捷按钮或输入指令开始生成
            </div>
          </div>
        </div>
      </div>

      <!-- Stage: Execute -->
      <div v-show="activeStage === 'execute'">
        <div class="flex items-center gap-4 mb-3">
          <button @click="runTests" :disabled="testRunning"
            class="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm hover:bg-indigo-700 disabled:opacity-50">
            {{ testRunning ? '执行中...' : '执行全部测试' }}
          </button>
          <div v-if="testResults.length" class="flex items-center gap-3 text-sm">
            <span class="text-green-600 font-bold">Pass: {{ testPassCount }}</span>
            <span class="text-red-600 font-bold">Fail: {{ testFailCount }}</span>
            <div class="w-32 bg-gray-200 rounded-full h-2">
              <div class="bg-green-500 h-2 rounded-full transition-all" :style="{width: testPassRate + '%'}"></div>
            </div>
            <span class="text-gray-400">{{ testPassRate }}%</span>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow overflow-hidden">
          <table class="w-full text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-3 py-2 text-left w-12">#</th>
                <th class="px-3 py-2 text-left">描述</th>
                <th class="px-3 py-2 text-left">输入</th>
                <th class="px-3 py-2 text-left w-40">断言</th>
                <th class="px-3 py-2 text-center w-16">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(tc, idx) in testCases" :key="tc.id || idx" class="border-t cursor-pointer hover:bg-gray-50"
                :class="{'bg-green-50': getTestResult(tc)?.passed, 'bg-red-50': getTestResult(tc) && !getTestResult(tc).passed}"
                @click="expandedTestId = expandedTestId === tc.id ? null : tc.id">
                <td class="px-3 py-2 text-gray-400">{{ idx + 1 }}</td>
                <td class="px-3 py-2">{{ tc.description }}</td>
                <td class="px-3 py-2 text-gray-600 truncate max-w-xs">{{ tc.input }}</td>
                <td class="px-3 py-2 text-xs">{{ (tc.assertions || []).join(', ') }}</td>
                <td class="px-3 py-2 text-center">
                  <span v-if="getTestResult(tc)?.passed" class="text-green-600 font-bold">PASS</span>
                  <span v-else-if="getTestResult(tc) && !getTestResult(tc).passed" class="text-red-600 font-bold">FAIL</span>
                  <span v-else-if="getTestResult(tc)?.running" class="text-blue-500 animate-pulse">...</span>
                  <span v-else class="text-gray-300">-</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="expandedTestId && getTestResult({id: expandedTestId})" class="border-t p-4 bg-gray-50">
            <div class="text-sm space-y-2">
              <div v-if="getTestResult({id: expandedTestId}).assertions?.length">
                <span v-for="a in getTestResult({id: expandedTestId}).assertions" :key="a.text" class="mr-3 text-xs"
                  :class="a.pass ? 'text-green-600' : 'text-red-600'">
                  {{ a.pass ? '&#10003;' : '&#10007;' }} {{ a.text }}
                </span>
              </div>
              <div v-if="getTestResult({id: expandedTestId}).llmOutput"
                class="bg-white rounded p-2 text-xs max-h-32 overflow-y-auto whitespace-pre-wrap text-gray-600">
                {{ getTestResult({id: expandedTestId}).llmOutput }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Stage: Review -->
      <div v-show="activeStage === 'review'">
        <div class="bg-white rounded-lg shadow p-4">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-bold">测试报告</h3>
            <div class="flex items-center gap-3 text-sm">
              <div class="w-40 bg-gray-200 rounded-full h-3">
                <div class="h-3 rounded-full transition-all"
                  :class="testPassRate === 100 ? 'bg-green-500' : 'bg-yellow-500'"
                  :style="{width: testPassRate + '%'}"></div>
              </div>
              <span class="font-bold" :class="testPassRate === 100 ? 'text-green-600' : 'text-yellow-600'">{{ testPassRate }}%</span>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div v-for="(r, i) in testResults" :key="i" class="p-3 rounded border text-sm"
              :class="r.passed ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'">
              <div class="flex justify-between items-center">
                <span class="font-medium">{{ testCases.find(tc => tc.id === r.testCaseId)?.description || '...' }}</span>
                <span :class="r.passed ? 'text-green-600' : 'text-red-600'" class="font-bold">{{ r.passed ? 'PASS' : 'FAIL' }}</span>
              </div>
              <div v-if="r.assertions?.length" class="mt-1">
                <span v-for="a in r.assertions" :key="a.text" class="mr-2 text-xs"
                  :class="a.pass ? 'text-green-600' : 'text-red-600'">
                  {{ a.pass ? '&#10003;' : '&#10007;' }} {{ a.text }}
                </span>
              </div>
            </div>
          </div>
          <div v-if="!testResults.length" class="text-gray-400 text-center py-8">尚无测试结果</div>
        </div>
      </div>

      <!-- Stage: Publish -->
      <div v-show="activeStage === 'publish'">
        <div class="bg-white rounded-lg shadow p-4">
          <div class="flex items-center gap-3 mb-4">
            <button v-if="canPublishStage" @click="stageVersion" :disabled="publishing"
              class="bg-blue-600 text-white px-4 py-1.5 rounded text-sm hover:bg-blue-700 disabled:opacity-50">
              {{ publishing ? '...' : 'Stage 版本' }}
            </button>
            <button v-if="stagedVersionId" @click="activateVersion" :disabled="publishing"
              class="bg-green-600 text-white px-4 py-1.5 rounded text-sm hover:bg-green-700 disabled:opacity-50">
              激活上线
            </button>
            <span v-if="publishMsg" class="text-sm text-blue-600">{{ publishMsg }}</span>
          </div>
          <h3 class="font-bold mb-2 text-sm">版本历史</h3>
          <div v-for="v in versions" :key="v.id" class="flex items-center gap-3 text-sm p-2 rounded mb-1"
            :class="{'bg-green-50': v.status === 'active', 'bg-gray-50': v.status !== 'active'}">
            <span class="font-mono font-bold">{{ v.versionTag }}</span>
            <span class="px-1.5 py-0.5 rounded text-xs"
              :class="{'bg-green-200 text-green-800': v.status === 'active', 'bg-blue-200 text-blue-800': v.status === 'staged', 'bg-gray-200': v.status === 'archived'}">
              {{ v.status }}
            </span>
            <span class="text-gray-400 text-xs">{{ v.createdAt }}</span>
            <button v-if="v.status === 'archived'" @click="rollback(v.id)"
              class="ml-auto text-blue-600 hover:text-blue-800 text-xs">回滚</button>
          </div>
          <div v-if="!versions.length" class="text-gray-400 text-center py-4 text-sm">暂无版本</div>
        </div>
      </div>
    </div>

    <!-- Tools Overview Tab -->
    <div v-show="topTab === 'tools'" class="flex-1 overflow-y-auto p-6">
      <!-- Skills -->
      <section class="mb-6">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-lg font-bold">可用技能</h3>
          <button @click="loadSkillsList" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
        </div>
        <div v-if="skillsList.length === 0" class="text-gray-400 text-sm">暂无可用技能</div>
        <div v-else class="space-y-2">
          <div v-for="s in skillsList" :key="s.name" class="bg-gray-50 rounded-lg p-3 border border-gray-200 flex items-center justify-between">
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
        <div v-if="toolsList.length === 0" class="text-gray-400 text-sm">暂无可用工具</div>
        <div v-else class="space-y-2">
          <div v-for="t in toolsList" :key="t.name" class="bg-gray-50 rounded-lg p-3 border border-gray-200">
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { renderMarkdown } from '../composables/useMarkdown.js'
import {
  skillCreatorList, skillCreatorChat, skillCreatorSave, skillCreatorLoad,
  skillTestList,
  skillGenerateCases, skillConfirmCases,
  skillTestExecute,
  skillPublishStage, skillPublishActivate, skillPublishRollback, skillVersionHistory,
  getSkills, getToolsDetail, getMcpServers, getToolDefinitions
} from '../api/ws.js'

// ==================== Top-level tab ====================
const topTab = ref('pipeline')

// ==================== Pipeline State ====================
const stages = [
  { key: 'create', label: '创建 Skill' },
  { key: 'cases', label: '生成用例' },
  { key: 'execute', label: '执行测试' },
  { key: 'review', label: '查看结果' },
  { key: 'publish', label: '发布' }
]
const activeStage = ref('create')
const stageStatus = ref({})
const skills = ref([])
const selectedSkillId = ref('')
const expandedTestId = ref(null)

const quickCasesPrompts = ['生成基础用例', '补充异常场景', '增加边界用例', '增加多轮对话用例']

// Create stage
const createMessages = ref([])
const createInput = ref('')
const createSending = ref(false)
const currentDraft = ref(null)
const createSessionId = ref('sc-' + Date.now())
const createChatRef = ref(null)

// Cases stage
const casesMessages = ref([])
const casesInput = ref('')
const casesSending = ref(false)
const generatedCases = ref([])
const casesSessionId = ref('gen-' + Date.now())
const casesChatRef = ref(null)

// Execute stage
const testCases = ref([])
const testResults = ref([])
const testRunning = ref(false)

// Publish stage
const versions = ref([])
const stagedVersionId = ref(null)
const publishing = ref(false)
const publishMsg = ref('')

// ==================== Pipeline Computed ====================
const testPassCount = computed(() => testResults.value.filter(r => r.passed).length)
const testFailCount = computed(() => testResults.value.filter(r => !r.passed && !r.running).length)
const testDoneCount = computed(() => testResults.value.filter(r => !r.running).length)
const testPassRate = computed(() => testDoneCount.value ? Math.round((testPassCount.value / testDoneCount.value) * 100) : 0)
const canPublishStage = computed(() => testDoneCount.value > 0 && testPassCount.value === testDoneCount.value && !stagedVersionId.value)

const nextStepHint = computed(() => {
  if (activeStage.value === 'create' && currentDraft.value?.name && !stageStatus.value.create) {
    return 'Skill 定义已就绪，点击"保存并继续"进入用例生成阶段'
  }
  if (activeStage.value === 'cases' && generatedCases.value.length && !stageStatus.value.cases) {
    return `已生成 ${generatedCases.value.length} 个用例，满意后点击"确认用例并执行"进入测试`
  }
  if (activeStage.value === 'execute' && stageStatus.value.execute === 'done') {
    if (testPassRate.value === 100) return '所有测试通过！可以进入发布阶段'
    return `通过率 ${testPassRate.value}%，可查看详情或回到用例阶段调整`
  }
  if (activeStage.value === 'review' && testPassRate.value === 100) {
    return '测试全部通过，可以发布新版本'
  }
  return ''
})

const nextStepAction = computed(() => {
  if (activeStage.value === 'create' && currentDraft.value?.name && !stageStatus.value.create) {
    return { label: '保存并继续 \u2192', fn: saveDraft, disabled: createSending.value }
  }
  if (activeStage.value === 'cases' && generatedCases.value.length && !stageStatus.value.cases) {
    return { label: '确认并执行 \u2192', fn: confirmAndRun, disabled: casesSending.value }
  }
  if (activeStage.value === 'execute' && stageStatus.value.execute === 'done' && testPassRate.value === 100) {
    return { label: '去发布 \u2192', fn: () => goToStage('publish'), disabled: false }
  }
  if (activeStage.value === 'review' && testPassRate.value === 100) {
    return { label: '去发布 \u2192', fn: () => goToStage('publish'), disabled: false }
  }
  return null
})

function canAccessStage(key) {
  if (key === 'create') return true
  if (key === 'cases') return !!selectedSkillId.value && selectedSkillId.value !== '__new__'
  if (key === 'execute') return testCases.value.length > 0
  if (key === 'review') return testResults.value.length > 0
  if (key === 'publish') return testDoneCount.value > 0
  return false
}

function goToStage(key) {
  if (canAccessStage(key)) activeStage.value = key
}

// ==================== Tools Overview State ====================
const skillsList = ref([])
const toolsList = ref([])
const toolsInfo = ref(null)
const mcpData = ref(null)
const toolDefs = ref(null)

async function loadSkillsList() {
  try { skillsList.value = await getSkills() } catch { skillsList.value = [] }
}

async function loadToolsList() {
  try {
    const result = await getToolsDetail()
    toolsList.value = result.tools || []
    toolsInfo.value = result
  } catch { toolsList.value = [] }
}

async function loadMcp() {
  try { mcpData.value = await getMcpServers() } catch { mcpData.value = null }
}

async function loadDefs() {
  try { toolDefs.value = await getToolDefinitions() } catch { toolDefs.value = null }
}

// ==================== Lifecycle ====================
onMounted(async () => {
  try { skills.value = await skillCreatorList() || [] } catch (e) { console.error(e) }
  loadSkillsList()
  loadToolsList()
})

async function onSkillChange() {
  if (selectedSkillId.value === '__new__') {
    selectedSkillId.value = ''
    createSessionId.value = 'sc-' + Date.now()
    createMessages.value = []
    currentDraft.value = null
    stageStatus.value = {}
    activeStage.value = 'create'
    return
  }
  if (!selectedSkillId.value) return
  try {
    const draft = await skillCreatorLoad(selectedSkillId.value, createSessionId.value)
    currentDraft.value = draft
    stageStatus.value = { create: 'done' }
    testCases.value = await skillTestList(selectedSkillId.value) || []
    if (testCases.value.length) stageStatus.value.cases = 'done'
    testResults.value = []
    await loadVersions()
    activeStage.value = testCases.value.length ? 'execute' : 'cases'
  } catch (e) { console.error(e) }
}

// ==================== Create Stage ====================
async function sendCreateMessage() {
  if (!createInput.value.trim() || createSending.value) return
  const msg = createInput.value.trim()
  createInput.value = ''
  createMessages.value.push({ role: 'user', text: msg })
  createSending.value = true

  const assistant = reactive({ role: 'assistant', text: '' })
  createMessages.value.push(assistant)

  try {
    await skillCreatorChat(msg, {
      sessionId: createSessionId.value,
      onDelta: (d) => { assistant.text += d; scrollChat(createChatRef) },
      onDraft: (d) => { currentDraft.value = d },
      onComplete: () => {},
      onError: (e) => { assistant.text += '\n[Error: ' + e.message + ']' }
    })
  } catch (e) { assistant.text += '\n[Error: ' + e.message + ']' }
  finally { createSending.value = false }
}

async function saveDraft() {
  try {
    const saved = await skillCreatorSave(createSessionId.value)
    selectedSkillId.value = saved.id
    stageStatus.value.create = 'done'
    skills.value = await skillCreatorList() || []
    casesSessionId.value = 'gen-' + Date.now()
    casesMessages.value = []
    generatedCases.value = []
    activeStage.value = 'cases'
    await nextTick()
    casesInput.value = '生成基础用例'
    await nextTick()
    sendCasesMessage()
  } catch (e) { alert('保存失败: ' + e.message) }
}

// ==================== Cases Stage ====================
function quickCasesInput(prompt) {
  casesInput.value = prompt
  sendCasesMessage()
}

async function sendCasesMessage() {
  if (!casesInput.value.trim() || casesSending.value || !selectedSkillId.value) return
  const msg = casesInput.value.trim()
  casesInput.value = ''
  casesMessages.value.push({ role: 'user', text: msg })
  casesSending.value = true

  const assistant = reactive({ role: 'assistant', text: '' })
  casesMessages.value.push(assistant)

  try {
    await skillGenerateCases(selectedSkillId.value, casesSessionId.value, msg, {
      onDelta: (d) => { assistant.text += d; scrollChat(casesChatRef) },
      onCasesUpdate: (data) => { generatedCases.value = data.cases || [] },
      onComplete: () => {},
      onError: (e) => { assistant.text += '\n[Error: ' + e.message + ']' }
    })
  } catch (e) { assistant.text += '\n[Error: ' + e.message + ']' }
  finally { casesSending.value = false }
}

async function confirmCases() {
  try {
    await skillConfirmCases(casesSessionId.value)
    stageStatus.value.cases = 'done'
    testCases.value = await skillTestList(selectedSkillId.value) || []
    testResults.value = []
    activeStage.value = 'execute'
  } catch (e) { alert('确认失败: ' + e.message) }
}

async function confirmAndRun() {
  await confirmCases()
  await nextTick()
  runTests()
}

// ==================== Execute Stage ====================
async function runTests() {
  if (!selectedSkillId.value || testRunning.value) return
  testRunning.value = true
  stagedVersionId.value = null
  publishMsg.value = ''
  expandedTestId.value = null
  testResults.value = testCases.value.map(tc => ({ testCaseId: tc.id, running: true, passed: false }))

  try {
    await skillTestExecute(selectedSkillId.value, {
      onCaseStart: (data) => {
        const r = testResults.value.find(r => r.testCaseId === data.testCaseId)
        if (r) r.running = true
      },
      onCaseResult: (data) => {
        const idx = testResults.value.findIndex(r => r.testCaseId === data.testCaseId)
        if (idx >= 0) {
          testResults.value[idx] = {
            testCaseId: data.testCaseId, running: false, passed: !!data.passed,
            toolCalls: data.toolCalls || [], assertions: data.assertions || [],
            llmOutput: data.llmOutput || ''
          }
        }
      },
      onRunComplete: () => {
        stageStatus.value.execute = 'done'
        if (testResults.value.length) activeStage.value = 'review'
      },
      onError: (e) => { console.error('Test error:', e) }
    })
  } catch (e) { console.error(e) }
  finally { testRunning.value = false }
}

function getTestResult(tc) { return testResults.value.find(r => r.testCaseId === tc.id) }

// ==================== Publish Stage ====================
async function loadVersions() {
  if (!selectedSkillId.value) return
  try { versions.value = await skillVersionHistory(selectedSkillId.value) || [] } catch (e) { versions.value = [] }
}

async function stageVersion() {
  publishing.value = true; publishMsg.value = ''
  try {
    const resp = await skillPublishStage(selectedSkillId.value, 0.9)
    stagedVersionId.value = resp.id
    publishMsg.value = 'Staged ' + resp.versionTag
    await loadVersions()
  } catch (e) { publishMsg.value = 'Stage failed: ' + (e.message || e) }
  finally { publishing.value = false }
}

async function activateVersion() {
  if (!stagedVersionId.value) return
  publishing.value = true
  try {
    const resp = await skillPublishActivate(stagedVersionId.value)
    publishMsg.value = resp.versionTag + ' activated!'
    stagedVersionId.value = null
    stageStatus.value.publish = 'done'
    await loadVersions()
  } catch (e) { publishMsg.value = 'Activate failed: ' + (e.message || e) }
  finally { publishing.value = false }
}

async function rollback(versionId) {
  if (!confirm('确定回滚到此版本？')) return
  try {
    await skillPublishRollback(selectedSkillId.value, versionId)
    publishMsg.value = '已回滚'
    await loadVersions()
  } catch (e) { publishMsg.value = '回滚失败: ' + (e.message || e) }
}

// ==================== Util ====================
function scrollChat(refEl) {
  nextTick(() => { if (refEl.value) refEl.value.scrollTop = refEl.value.scrollHeight })
}
</script>
