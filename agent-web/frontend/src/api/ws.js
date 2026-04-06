/**
 * WebSocket API client — migrated from soul-comfort-api.js
 * All communication through WebSocket with token-based auth.
 */

let _ws = null
let _wsConnecting = null
let _wsCallbacks = null
let _skillCreatorCallbacks = null
let _pipelineCallbacks = null
const _pendingRequests = new Map()
let _requestIdCounter = 0

// Client tool registry (in-memory)
const _clientTools = new Map()
let _onDirective = null
let _onDirectiveResult = null

function getWebSocketURL() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.hostname
  const params = new URLSearchParams(location.search)
  const port = params.get('wsPort') || location.port || (protocol === 'wss:' ? '443' : '80')
  const token = localStorage.getItem('auth_token') || ''
  return `${protocol}//${host}:${port}/ws/chat${token ? '?token=' + encodeURIComponent(token) : ''}`
}

export function ensureWebSocket() {
  if (_ws && _ws.readyState === WebSocket.OPEN) return Promise.resolve(_ws)
  if (_wsConnecting) return _wsConnecting

  const url = getWebSocketURL()
  _wsConnecting = new Promise((resolve, reject) => {
    const ws = new WebSocket(url)
    ws.onopen = () => { _ws = ws; _wsConnecting = null; resolve(ws) }
    ws.onerror = (err) => { _wsConnecting = null; reject(new Error('WebSocket 连接失败')) }
    ws.onclose = (event) => {
      _ws = null; _wsConnecting = null
      // 1008 = POLICY_VIOLATION — token invalid/expired, redirect to login
      if (event.code === 1008) {
        localStorage.removeItem('auth_token')
        window.location.hash = '#/login'
        return
      }
      if (_wsCallbacks?.onError) { _wsCallbacks.onError(new Error('WebSocket 连接断开')); _wsCallbacks = null }
      if (_skillCreatorCallbacks?.onError) { _skillCreatorCallbacks.onError(new Error('WebSocket 连接断开')); _skillCreatorCallbacks = null }
      _pendingRequests.forEach(({ reject }) => reject(new Error('WebSocket 连接断开')))
      _pendingRequests.clear()
    }
    ws.onmessage = (event) => _handleMessage(event.data)
  })
  return _wsConnecting
}

export function disconnect() {
  if (_ws) { _ws.close(1000, 'Client disconnect'); _ws = null }
  _wsCallbacks = null; _wsConnecting = null; _pendingRequests.clear()
}

export function reconnect() {
  disconnect()
  return ensureWebSocket()
}

function _nextRequestId() {
  return 'req-' + (++_requestIdCounter) + '-' + Date.now()
}

function _handleMessage(raw) {
  try {
    const msg = JSON.parse(raw)

    // Client tool call from server
    if (['directive', 'DIRECTIVE', 'tool_call', 'client_tool_call'].includes(msg.type)) {
      _handleServerToolRequest(msg)
      return
    }

    // Request-response pattern
    if (msg.requestId && _pendingRequests.has(msg.requestId)) {
      const { resolve, reject } = _pendingRequests.get(msg.requestId)
      _pendingRequests.delete(msg.requestId)
      if (msg.type === 'error_response') reject(new Error(msg.data?.error || '请求失败'))
      else resolve(msg.data)
      return
    }

    // Skill Creator stream
    if (msg.type?.startsWith('skill_creator_')) {
      const scCb = _skillCreatorCallbacks
      if (!scCb) return
      if (msg.requestId && scCb.requestId && msg.requestId !== scCb.requestId) return
      switch (msg.type) {
        case 'skill_creator_token': scCb.onDelta?.(msg.data); break
        case 'skill_creator_draft': scCb.onDraft?.(msg.data); break
        case 'skill_creator_error': scCb.onError?.(new Error(msg.message || 'Skill Creator 错误')); _skillCreatorCallbacks = null; break
        case 'skill_creator_stream_end': scCb.onComplete?.(); _skillCreatorCallbacks = null; break
      }
      return
    }

    // Skill Pipeline stream (case generation / test execution / simulation)
    if (msg.type?.startsWith('skill_generate_') || msg.type?.startsWith('skill_test_') || msg.type?.startsWith('skill_simulation_')) {
      const pcb = _pipelineCallbacks
      if (!pcb) return
      if (msg.requestId && pcb.requestId && msg.requestId !== pcb.requestId) return
      switch (msg.type) {
        // Case generation
        case 'skill_generate_token': pcb.onDelta?.(msg.data); break
        case 'skill_generate_test_cases_update': pcb.onCasesUpdate?.(msg.data); break
        case 'skill_generate_error': pcb.onError?.(new Error(msg.message || 'Generation error')); _pipelineCallbacks = null; break
        case 'skill_generate_stream_end': pcb.onComplete?.(); _pipelineCallbacks = null; break
        // Test execution
        case 'skill_test_test_case_start': pcb.onCaseStart?.(msg.data); break
        case 'skill_test_test_case_result': pcb.onCaseResult?.(msg.data); break
        case 'skill_test_test_run_complete': pcb.onRunComplete?.(msg.data); pcb.onComplete?.(msg.data); _pipelineCallbacks = null; break
        case 'skill_test_error': pcb.onError?.(new Error(msg.message || 'Test execution error')); _pipelineCallbacks = null; break
        case 'skill_test_stream_end': pcb.onComplete?.(msg.data); _pipelineCallbacks = null; break
        case 'skill_simulation_simulation_turn': pcb.onTurn?.(msg.data); break
        case 'skill_simulation_simulation_complete': pcb.onSimComplete?.(msg.data); pcb.onComplete?.(msg.data); _pipelineCallbacks = null; break
        case 'skill_simulation_error': pcb.onError?.(new Error(msg.message || 'Simulation error')); _pipelineCallbacks = null; break
        case 'skill_simulation_stream_end': pcb.onComplete?.(msg.data); _pipelineCallbacks = null; break
      }
      return
    }

    // Chat stream
    const cb = _wsCallbacks
    if (!cb) return
    switch (msg.type) {
      case 'token': cb.onDelta?.(msg.data); break
      case 'stream_end': cb.onComplete?.(msg.meta); _wsCallbacks = null; break
      case 'error': cb.onError?.(new Error(msg.message)); _wsCallbacks = null; break
      case 'crisis_alert': cb.onCrisis?.(msg.resources); _wsCallbacks = null; break
      case 'tool_call_start': cb.onToolCallStart?.(msg.data); break
      case 'tool_progress': cb.onToolProgress?.(msg.data); break
      case 'tool_log': cb.onToolLog?.(msg.data); break
      case 'tool_error': cb.onToolError?.(msg.data); break
      case 'post_process': cb.onPostProcess?.(msg.category, msg.data); break
      case 'trace': cb.onTrace?.(msg.data); break
    }
  } catch (e) {
    console.error('[WS] Failed to parse message:', e)
  }
}

async function _sendRequest(type, data = {}, timeoutMs = 30000) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { _pendingRequests.delete(requestId); reject(new Error(`请求超时: ${type}`)) }, timeoutMs)
    _pendingRequests.set(requestId, {
      resolve: (result) => { clearTimeout(timer); resolve(result) },
      reject: (err) => { clearTimeout(timer); reject(err) }
    })
    ws.send(JSON.stringify({ type, requestId, ...data }))
  })
}

// ========== Chat ==========
export async function sendChatStream(message, options = {}) {
  const ws = await ensureWebSocket()
  return new Promise((resolve, reject) => {
    _wsCallbacks = {
      onDelta: options.onDelta,
      onComplete: (meta) => { options.onComplete?.(meta); resolve() },
      onError: (err) => { options.onError?.(err); reject(err) },
      onCrisis: options.onCrisis,
      onToolCallStart: options.onToolCallStart,
      onToolProgress: options.onToolProgress,
      onToolLog: options.onToolLog,
      onToolError: options.onToolError,
      onPostProcess: options.onPostProcess,
      onTrace: options.onTrace
    }
    ws.send(JSON.stringify({
      type: 'chat', sessionId: options.sessionId || 'default',
      message, model: options.model || null, reactive: true
    }))
  })
}

// ========== Session ==========
export const getSessionHistory = (sessionId) => _sendRequest('get_history', { sessionId })
export const getSessionSummary = (sessionId) => _sendRequest('get_summary', { sessionId })
export const clearSession = (sessionId) => _sendRequest('clear_session', { sessionId })

// ========== Skills & Tools ==========
export const getSkills = () => _sendRequest('get_skills')
export const getTools = () => _sendRequest('get_tools')
export const getToolsDetail = () => _sendRequest('get_tools_detail')
export const getToolDetail = (toolName) => _sendRequest('get_tool_detail', { toolName })
export const getMcpServers = () => _sendRequest('get_mcp_servers')
export const getToolDefinitions = () => _sendRequest('get_tool_definitions')

// ========== Health ==========
export const checkHealth = () => _sendRequest('health')

// ========== Assessment ==========
export const getScales = () => _sendRequest('get_scales')
export const getScale = (scaleType) => _sendRequest('get_scale', { scaleType })
export const submitAssessment = (userId, scaleType, answers) => _sendRequest('submit_assessment', { userId, scaleType, answers }, 60000)
export const getAssessmentHistory = (userId) => _sendRequest('get_assessment_history', { userId })

// ========== User ==========
export const createUser = () => _sendRequest('create_user')
export const checkin = (userId, emotion, note) => _sendRequest('checkin', { userId, emotion, note })
export const getEmotions = (userId, days = 7) => _sendRequest('get_emotions', { userId, days })
export const getStats = (userId) => _sendRequest('get_stats', { userId })

// ========== Memory ==========
export const searchMemory = (query, topK = 10) => _sendRequest('search_memory', { query, topK })
export const rememberDurable = (section, content) => _sendRequest('remember_durable', { section, content })

// ========== Skill Creator ==========
export async function skillCreatorChat(message, options = {}) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  const hardTimeoutMs = options.timeout || 180000
  const idleTimeoutMs = options.idleTimeout || 20000

  return new Promise((resolve, reject) => {
    let settled = false
    let hardTimer = null, idleTimer = null

    const clearTimers = () => { clearTimeout(hardTimer); clearTimeout(idleTimer) }
    const fail = (msg) => {
      if (settled) return; settled = true; clearTimers(); _skillCreatorCallbacks = null
      const err = new Error(msg); options.onError?.(err); reject(err)
    }
    const refreshIdle = () => {
      clearTimeout(idleTimer)
      idleTimer = setTimeout(() => fail(`空闲超时 (${idleTimeoutMs}ms)`), idleTimeoutMs)
    }

    hardTimer = setTimeout(() => fail(`总超时 (${hardTimeoutMs}ms)`), hardTimeoutMs)
    refreshIdle()

    _skillCreatorCallbacks = {
      requestId,
      onDelta: (d) => { if (!settled) { refreshIdle(); options.onDelta?.(d) } },
      onDraft: (d) => { if (!settled) { refreshIdle(); options.onDraft?.(d) } },
      onComplete: () => { if (!settled) { settled = true; clearTimers(); options.onComplete?.(); resolve() } },
      onError: (err) => { if (!settled) { settled = true; clearTimers(); options.onError?.(err); reject(err) } }
    }

    ws.send(JSON.stringify({
      type: 'skill_creator_chat', requestId,
      sessionId: options.sessionId || 'skill-creator-default', message
    }))
  })
}

export const skillCreatorSave = (sessionId) => _sendRequest('skill_creator_save', { sessionId })
export const skillCreatorList = () => _sendRequest('skill_creator_list')
export const skillCreatorDelete = (skillId) => _sendRequest('skill_creator_delete', { skillId })
export const skillCreatorLoad = (skillId, sessionId) => _sendRequest('skill_creator_load', { skillId, sessionId })
export const skillCreatorGetDraft = (sessionId) => _sendRequest('skill_creator_get_draft', { sessionId })

// ========== Skill Test Cases ==========
export const skillTestList = (skillId) => _sendRequest('skill_test_list', { skillId })
export const skillTestSave = (testCase) => _sendRequest('skill_test_save', { testCase })
export const skillTestSaveBatch = (testCases) => _sendRequest('skill_test_save_batch', { testCases })
export const skillTestDelete = (testCaseId) => _sendRequest('skill_test_delete', { testCaseId })
export const skillTestDeleteAll = (skillId) => _sendRequest('skill_test_delete_all', { skillId })

// ========== Skill Pipeline: Case Generation (streaming) ==========
export async function skillGenerateCases(skillId, sessionId, message, options = {}) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  return new Promise((resolve, reject) => {
    _pipelineCallbacks = {
      requestId,
      onDelta: options.onDelta,
      onCasesUpdate: options.onCasesUpdate,
      onComplete: () => { options.onComplete?.(); resolve() },
      onError: (err) => { options.onError?.(err); reject(err) }
    }
    ws.send(JSON.stringify({ type: 'skill_generate_cases', requestId, skillId, sessionId, message }))
  })
}
export const skillConfirmCases = (sessionId) => _sendRequest('skill_confirm_cases', { sessionId })
export const skillGetGeneratedCases = (sessionId) => _sendRequest('skill_get_generated_cases', { sessionId })

// ========== Skill Pipeline: Test Execution (streaming) ==========
export async function skillTestExecute(skillId, options = {}) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  return new Promise((resolve, reject) => {
    _pipelineCallbacks = {
      requestId,
      onCaseStart: options.onCaseStart,
      onCaseResult: options.onCaseResult,
      onRunComplete: options.onRunComplete,
      onComplete: () => { options.onComplete?.(); resolve() },
      onError: (err) => { options.onError?.(err); reject(err) }
    }
    ws.send(JSON.stringify({ type: 'skill_test_execute', requestId, skillId }))
  })
}

export async function skillTestExecuteSingle(skillId, testCaseId, options = {}) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  return new Promise((resolve, reject) => {
    _pipelineCallbacks = {
      requestId,
      onCaseStart: options.onCaseStart,
      onCaseResult: options.onCaseResult,
      onComplete: () => { options.onComplete?.(); resolve() },
      onError: (err) => { options.onError?.(err); reject(err) }
    }
    ws.send(JSON.stringify({ type: 'skill_test_execute_single', requestId, skillId, testCaseId }))
  })
}

// ========== Skill Pipeline: Publish ==========
export const skillTestRunHistory = (skillId) => _sendRequest('skill_test_run_history', { skillId })
export const skillPublishStage = (skillId, threshold = 0.9) => _sendRequest('skill_publish_stage', { skillId, threshold })
export const skillPublishActivate = (versionId) => _sendRequest('skill_publish_activate', { versionId })
export const skillPublishRollback = (skillId, versionId) => _sendRequest('skill_publish_rollback', { skillId, versionId })
export const skillVersionHistory = (skillId) => _sendRequest('skill_version_history', { skillId })

// ========== Skill Pipeline: Simulation (streaming) ==========
export async function skillSimulate(versionId, scenarios, options = {}) {
  const ws = await ensureWebSocket()
  const requestId = _nextRequestId()
  return new Promise((resolve, reject) => {
    _pipelineCallbacks = {
      requestId,
      onTurn: options.onTurn,
      onSimComplete: options.onSimComplete,
      onComplete: () => { options.onComplete?.(); resolve() },
      onError: (err) => { options.onError?.(err); reject(err) }
    }
    ws.send(JSON.stringify({ type: 'skill_simulate', requestId, versionId, scenarios }))
  })
}

// ========== Client Tool Simulation ==========

async function _handleServerToolRequest(msg) {
  if (msg.type === 'directive' || msg.type === 'DIRECTIVE') {
    await _handleDirective(msg)
  } else {
    await _handleToolCall(msg)
  }
}

async function _handleDirective(msg) {
  const data = msg.data || {}
  const directiveId = data.directive_id || data.directiveId || ''
  const directives = data.directives || []
  const primary = directives[0]
  if (!directiveId || !primary?.header) return

  const toolKey = primary.header.namespace + '.' + primary.header.name
  const args = primary.payload || {}
  const tool = _clientTools.get(toolKey)

  _onDirective?.(directiveId, toolKey, args, tool)

  const startTime = Date.now()
  const result = await invokeClientTool(toolKey, args)
  const elapsed = Date.now() - startTime

  const response = {
    type: 'directive_result', directiveId,
    success: result.ok,
    content: result.ok ? JSON.stringify(result.data || {}) : (result.error?.message || 'Tool execution failed'),
    metadata: { elapsed_ms: elapsed, toolId: toolKey, code: result.error?.code || null }
  }
  _sendWsPayload(response)
  _onDirectiveResult?.(directiveId, response, elapsed)
}

async function _handleToolCall(msg) {
  const data = msg.data || {}
  const callId = msg.callId || data.callId || ''
  const toolId = msg.toolId || msg.tool || data.toolId || data.tool || data.name || ''
  const args = msg.args || data.args || data.input || {}
  if (!callId || !toolId) return

  const result = await invokeClientTool(toolId, args)
  _sendWsPayload({
    type: 'client_tool_result', callId, isError: !result.ok,
    content: result.ok ? JSON.stringify(result.data || {}) : (result.error?.message || 'Tool execution failed'),
    meta: { toolId, code: result.error?.code || null }
  })
}

function _sendWsPayload(payload) {
  if (_ws?.readyState === WebSocket.OPEN) _ws.send(JSON.stringify(payload))
}

export function registerClientTool(toolDef) {
  _clientTools.set(toolDef.id, { ...toolDef, enabled: toolDef.enabled !== false })
}

export function getClientTool(id) { return _clientTools.get(id) || null }
export function listClientTools() { return Array.from(_clientTools.entries()) }

export function setClientToolEnabled(id, enabled) {
  const tool = _clientTools.get(id)
  if (tool) tool.enabled = enabled
}

export function updateClientToolMock(id, mockResponse) {
  const tool = _clientTools.get(id)
  if (tool) tool.mockResponse = mockResponse
}

export function removeClientTool(id) { _clientTools.delete(id) }

async function invokeClientTool(toolId, args) {
  const tool = _clientTools.get(toolId)
  if (!tool) return { ok: false, data: null, error: { code: 'TOOL_NOT_FOUND', message: `Tool not found: ${toolId}` } }
  if (tool.enabled === false) return { ok: false, data: null, error: { code: 'TOOL_DISABLED', message: `Tool disabled: ${toolId}` } }
  try {
    if (typeof tool.handler === 'function') {
      const data = await Promise.resolve(tool.handler(args || {}, tool))
      return { ok: true, data, error: null }
    }
    // Use mock response
    if (tool.mockResponse) return { ok: true, data: tool.mockResponse, error: null }
    return { ok: true, data: {}, error: null }
  } catch (e) {
    return { ok: false, data: null, error: { code: 'TOOL_EXECUTION_ERROR', message: e?.message || 'Tool execution failed' } }
  }
}

export function setDirectiveCallback(onDirective, onResult) {
  _onDirective = onDirective
  _onDirectiveResult = onResult
}

export function isConnected() {
  return _ws && _ws.readyState === WebSocket.OPEN
}
