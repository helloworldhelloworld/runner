<template>
  <div class="h-full overflow-y-auto p-6 bg-white/95 backdrop-blur">
    <h2 class="text-xl font-bold gradient-text mb-4">心理测评</h2>
    <p class="text-gray-500 text-sm mb-4">专业的心理健康评估量表</p>

    <!-- Scale Selection -->
    <div v-if="!currentScale" class="space-y-3 mb-6">
      <div v-if="scales.length === 0" class="text-gray-400 text-sm">加载中...</div>
      <div v-for="[type, scale] in scales" :key="type"
        @click="startQuiz(type, scale)"
        class="bg-gray-50 rounded-lg p-4 cursor-pointer hover:bg-purple-50 transition border border-gray-200">
        <div class="flex justify-between items-center">
          <div>
            <h4 class="font-bold text-gray-800">{{ scale.name || type }}</h4>
            <p class="text-sm text-gray-500 mt-1">{{ scale.description || '' }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ scale.questions?.length || 0 }} 题</p>
          </div>
          <span class="text-purple-500 text-2xl">→</span>
        </div>
      </div>
    </div>

    <!-- Quiz -->
    <div v-else class="mb-6">
      <div class="border-t border-gray-200 pt-4">
        <h3 class="text-lg font-bold mb-2">{{ currentScale.name }}</h3>
        <p class="text-sm text-gray-500 mb-4">{{ currentScale.description || '' }}</p>
        <div class="space-y-4">
          <div v-for="(q, idx) in currentScale.questions" :key="idx" class="bg-white rounded-lg p-4 border border-gray-200">
            <p class="font-medium text-gray-800 mb-2">{{ idx + 1 }}. {{ q.text || q }}</p>
            <div class="space-y-1">
              <label v-for="(opt, optIdx) in (currentScale.options || [])" :key="optIdx" class="flex items-center gap-2 cursor-pointer py-1">
                <input type="radio" :name="'q' + idx" :value="optIdx" v-model="answers[idx]">
                <span class="text-sm">{{ opt.label || opt.text || opt }}</span>
              </label>
            </div>
          </div>
        </div>
        <div class="mt-6 flex gap-3">
          <button @click="submitQuiz" class="bg-gradient-to-r from-green-500 to-green-600 text-white px-6 py-2 rounded-xl shadow-md">提交评估</button>
          <button @click="cancelQuiz" class="bg-gray-200 text-gray-700 px-6 py-2 rounded-xl">取消</button>
        </div>
      </div>
    </div>

    <!-- History -->
    <section class="border-t border-gray-200 pt-4">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-lg font-bold">评估历史</h3>
        <button @click="loadHistory" class="text-sm text-purple-600 hover:text-purple-800">刷新</button>
      </div>
      <div v-if="history.length === 0" class="text-gray-400 text-sm">暂无评估记录</div>
      <div v-else class="space-y-2">
        <div v-for="r in history" :key="r.id" class="bg-gray-50 rounded-lg p-3 border border-gray-200">
          <div class="flex justify-between text-sm">
            <span class="font-medium">{{ r.scaleType || r.type || '-' }}</span>
            <span class="text-gray-500">{{ r.timestamp || r.date || '-' }}</span>
          </div>
          <p class="text-sm text-gray-600 mt-1">分数: {{ r.totalScore || r.score || '-' }} | 等级: {{ r.severity || r.level || '-' }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { getScales, submitAssessment, getAssessmentHistory } from '../api/ws.js'

const auth = useAuthStore()
const scales = ref([])
const currentScaleType = ref('')
const currentScale = ref(null)
const answers = ref({})
const history = ref([])

async function loadScales() {
  try {
    const data = await getScales()
    scales.value = Object.entries(data)
  } catch { scales.value = [] }
}

function startQuiz(type, scale) {
  currentScaleType.value = type
  currentScale.value = scale
  answers.value = {}
}

function cancelQuiz() { currentScale.value = null; currentScaleType.value = '' }

async function submitQuiz() {
  const questions = currentScale.value.questions || []
  const answerArray = []
  for (let i = 0; i < questions.length; i++) {
    if (answers.value[i] === undefined) { alert(`请回答第 ${i + 1} 题`); return }
    answerArray.push(answers.value[i])
  }
  try {
    const result = await submitAssessment(auth.userId, currentScaleType.value, answerArray)
    cancelQuiz()
    history.value.unshift(result)
    alert(`评估完成！总分: ${result.totalScore || result.score || '-'}，等级: ${result.severity || result.level || '-'}`)
  } catch (e) { alert('提交失败: ' + e.message) }
}

async function loadHistory() {
  if (!auth.userId) return
  try { history.value = await getAssessmentHistory(auth.userId) || [] } catch { history.value = [] }
}

onMounted(() => { loadScales(); loadHistory() })
</script>
