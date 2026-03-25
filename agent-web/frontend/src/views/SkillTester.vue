<template>
  <div class="p-4 max-w-7xl mx-auto">
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-xl font-bold">Skill 验证</h1>
      <div class="flex gap-2">
        <select v-model="selectedSkillId" @change="loadTestCases" class="border rounded px-3 py-1.5 text-sm">
          <option value="">-- 选择 Skill --</option>
          <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <button @click="runAll" :disabled="!selectedSkillId || running"
          class="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm hover:bg-indigo-700 disabled:opacity-50">
          {{ running ? '运行中...' : '批量执行' }}
        </button>
        <label class="bg-gray-100 px-3 py-1.5 rounded text-sm cursor-pointer hover:bg-gray-200">
          导入 Excel
          <input type="file" accept=".xlsx,.xls,.csv" @change="importExcel" class="hidden" />
        </label>
      </div>
    </div>

    <!-- Summary -->
    <div v-if="results.length" class="mb-4 flex gap-4 text-sm">
      <span class="text-green-600 font-bold">Pass: {{ passCount }}</span>
      <span class="text-red-600 font-bold">Fail: {{ failCount }}</span>
      <span class="text-gray-500">Total: {{ results.length }}</span>
      <span class="text-gray-400">{{ passRate }}%</span>
    </div>

    <!-- Test Cases Table -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-3 py-2 text-left w-16">#</th>
            <th class="px-3 py-2 text-left w-24">分类</th>
            <th class="px-3 py-2 text-left w-40">描述</th>
            <th class="px-3 py-2 text-left">输入</th>
            <th class="px-3 py-2 text-left w-48">断言</th>
            <th class="px-3 py-2 text-center w-20">状态</th>
            <th class="px-3 py-2 text-center w-20">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(tc, idx) in testCases" :key="tc.id || idx"
            class="border-t hover:bg-gray-50 cursor-pointer"
            :class="{'bg-green-50': getResult(tc)?.passed, 'bg-red-50': getResult(tc) && !getResult(tc).passed}"
            @click="selectCase(tc)">
            <td class="px-3 py-2 text-gray-400">{{ idx + 1 }}</td>
            <td class="px-3 py-2">
              <span class="px-1.5 py-0.5 bg-gray-100 rounded text-xs">{{ tc.category || '-' }}</span>
            </td>
            <td class="px-3 py-2 font-medium">{{ tc.description }}</td>
            <td class="px-3 py-2 text-gray-600 truncate max-w-xs">{{ tc.input }}</td>
            <td class="px-3 py-2 text-xs text-gray-500">
              <div v-for="a in (tc.assertions || [])" :key="a" class="truncate">{{ a }}</div>
            </td>
            <td class="px-3 py-2 text-center">
              <span v-if="getResult(tc)?.passed" class="text-green-600 font-bold">PASS</span>
              <span v-else-if="getResult(tc) && !getResult(tc).passed" class="text-red-600 font-bold">FAIL</span>
              <span v-else-if="getResult(tc)?.running" class="text-blue-500">...</span>
              <span v-else class="text-gray-300">-</span>
            </td>
            <td class="px-3 py-2 text-center">
              <button @click.stop="deleteCase(tc)" class="text-red-400 hover:text-red-600 text-xs">删除</button>
            </td>
          </tr>
          <tr v-if="!testCases.length">
            <td colspan="7" class="px-3 py-8 text-center text-gray-400">
              暂无测试用例，请导入 Excel 或手动添加
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Detail Panel -->
    <div v-if="selectedCase" class="mt-4 bg-white rounded-lg shadow p-4">
      <h3 class="font-bold mb-2">{{ selectedCase.description }}</h3>
      <div class="grid grid-cols-2 gap-4 text-sm">
        <div>
          <div class="text-gray-500 mb-1">输入</div>
          <div class="bg-gray-50 rounded p-2">{{ selectedCase.input }}</div>
        </div>
        <div v-if="selectedCase.memory">
          <div class="text-gray-500 mb-1">记忆注入</div>
          <div class="bg-gray-50 rounded p-2 whitespace-pre-wrap">{{ selectedCase.memory }}</div>
        </div>
        <div v-if="selectedCase.userClarifications?.length">
          <div class="text-gray-500 mb-1">用户澄清</div>
          <div class="bg-gray-50 rounded p-2">
            <div v-for="(c, i) in selectedCase.userClarifications" :key="i">{{ i+1 }}. {{ c }}</div>
          </div>
        </div>
        <div>
          <div class="text-gray-500 mb-1">断言</div>
          <div class="bg-gray-50 rounded p-2">
            <div v-for="a in (selectedCase.assertions || [])" :key="a"
              :class="getAssertionClass(a)">
              {{ getAssertionIcon(a) }} {{ a }}
            </div>
          </div>
        </div>
      </div>
      <!-- Result details -->
      <div v-if="getResult(selectedCase)" class="mt-3">
        <div class="text-gray-500 mb-1 text-sm">工具调用链</div>
        <div class="bg-gray-900 text-green-400 rounded p-3 text-xs font-mono overflow-x-auto">
          <div v-for="(call, i) in (getResult(selectedCase)?.toolCalls || [])" :key="i">
            {{ i+1 }}. {{ call.functionName }}({{ JSON.stringify(call.parameters) }})
          </div>
          <div v-if="!getResult(selectedCase)?.toolCalls?.length" class="text-gray-500">（无工具调用）</div>
        </div>
        <div v-if="getResult(selectedCase)?.llmOutput" class="mt-2">
          <div class="text-gray-500 mb-1 text-sm">LLM 输出</div>
          <div class="bg-gray-50 rounded p-2 text-sm max-h-40 overflow-y-auto whitespace-pre-wrap">
            {{ getResult(selectedCase).llmOutput }}
          </div>
        </div>
      </div>
    </div>

    <!-- Add Case Form -->
    <div class="mt-4 bg-white rounded-lg shadow p-4">
      <h3 class="font-bold mb-2">添加测试用例</h3>
      <div class="grid grid-cols-2 gap-3 text-sm">
        <input v-model="newCase.category" placeholder="分类" class="border rounded px-2 py-1" />
        <input v-model="newCase.description" placeholder="描述" class="border rounded px-2 py-1" />
        <input v-model="newCase.input" placeholder="用户输入" class="border rounded px-2 py-1 col-span-2" />
        <input v-model="newCase.assertionsStr" placeholder="断言（逗号分隔）" class="border rounded px-2 py-1 col-span-2" />
        <button @click="addCase" class="bg-green-600 text-white px-3 py-1 rounded text-sm hover:bg-green-700">
          添加
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { skillCreatorList, skillTestList, skillTestSave, skillTestSaveBatch, skillTestDelete, skillTestDeleteAll } from '../api/ws.js'

const skills = ref([])
const selectedSkillId = ref('')
const testCases = ref([])
const results = ref([])
const selectedCase = ref(null)
const running = ref(false)
const newCase = ref({ category: '', description: '', input: '', assertionsStr: '' })

const passCount = computed(() => results.value.filter(r => r.passed).length)
const failCount = computed(() => results.value.filter(r => !r.passed && !r.running).length)
const passRate = computed(() => {
  const done = results.value.filter(r => !r.running).length
  return done ? Math.round((passCount.value / done) * 100) : 0
})

onMounted(async () => {
  try {
    const resp = await skillCreatorList()
    skills.value = resp || []
  } catch (e) { console.error('Failed to load skills:', e) }
})

async function loadTestCases() {
  if (!selectedSkillId.value) { testCases.value = []; return }
  try {
    const resp = await skillTestList(selectedSkillId.value)
    testCases.value = resp || []
    results.value = []
    selectedCase.value = null
  } catch (e) { console.error('Failed to load test cases:', e) }
}

function getResult(tc) {
  return results.value.find(r => r.id === (tc.id || tc.description))
}

function getAssertionClass(assertion) {
  if (!selectedCase.value || !getResult(selectedCase.value)) return ''
  const result = getResult(selectedCase.value)
  const ar = result?.assertionResults?.find(a => a.text === assertion)
  if (!ar) return ''
  return ar.pass ? 'text-green-600' : 'text-red-600 font-bold'
}

function getAssertionIcon(assertion) {
  if (!selectedCase.value || !getResult(selectedCase.value)) return ''
  const result = getResult(selectedCase.value)
  const ar = result?.assertionResults?.find(a => a.text === assertion)
  if (!ar) return ''
  return ar.pass ? '✓' : '✗'
}

function selectCase(tc) {
  selectedCase.value = tc
}

async function addCase() {
  if (!selectedSkillId.value || !newCase.value.input) return
  const tc = {
    skillId: selectedSkillId.value,
    category: newCase.value.category,
    description: newCase.value.description,
    input: newCase.value.input,
    assertions: newCase.value.assertionsStr.split(',').map(s => s.trim()).filter(Boolean)
  }
  try {
    await skillTestSave(tc)
    await loadTestCases()
    newCase.value = { category: '', description: '', input: '', assertionsStr: '' }
  } catch (e) { console.error('Failed to add test case:', e) }
}

async function deleteCase(tc) {
  if (!tc.id || !confirm('确定删除？')) return
  try {
    await skillTestDelete(tc.id)
    await loadTestCases()
  } catch (e) { console.error('Failed to delete:', e) }
}

async function importExcel(event) {
  const file = event.target.files[0]
  if (!file || !selectedSkillId.value) return
  try {
    const XLSX = await import('https://cdn.sheetjs.com/xlsx-0.20.0/package/xlsx.mjs')
    const data = await file.arrayBuffer()
    const wb = XLSX.read(data)
    const ws = wb.Sheets[wb.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(ws)

    const cases = rows.map(row => ({
      skillId: selectedSkillId.value,
      category: row['category'] || row['分类'] || '',
      description: row['desc'] || row['description'] || row['描述'] || '',
      input: row['input'] || row['输入'] || '',
      memory: row['memory'] || row['记忆'] || '',
      userClarifications: (row['userClarifications'] || row['澄清'] || '').split('|').filter(Boolean),
      assertions: (row['assertions'] || row['断言'] || '').split('|').filter(Boolean)
    }))

    await skillTestSaveBatch(cases)
    await loadTestCases()
    event.target.value = ''
  } catch (e) { console.error('Excel import failed:', e); alert('导入失败: ' + e.message) }
}

async function runAll() {
  // 占位 — 实际 LLM 执行在后续版本实现
  // 当前只做本地 assertion 校验的 demo
  running.value = true
  results.value = testCases.value.map(tc => ({
    id: tc.id || tc.description,
    running: false,
    passed: false,
    toolCalls: [],
    assertionResults: (tc.assertions || []).map(a => ({ text: a, pass: false })),
    llmOutput: '（待接入 LLM 执行）'
  }))
  running.value = false
}
</script>
