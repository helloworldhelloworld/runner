/**
 * Soul Comfort API Client - WebSocket Only
 * 所有通信通过 WebSocket 进行，不使用 REST/SSE
 */

class SoulComfortAPI {
    constructor() {
        this._ws = null;
        this._wsConnecting = null;
        this._wsCallbacks = null;
        /** Skill Creator 流式回调 */
        this._skillCreatorCallbacks = null;
        /** 请求-响应式回调映射 (requestId → {resolve, reject}) */
        this._pendingRequests = new Map();
        this._requestIdCounter = 0;

        /** 客户端工具配置 */
        this._clientToolRegistry = window.clientToolRegistry;
        this._onDirective = null;
        this._onDirectiveResult = null;
        this._loadClientToolsFromStorage();
    }

    /**
     * 获取 WebSocket URL
     */
    getWebSocketURL() {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = location.hostname;
        // 优先使用与 HTTP 相同的端口（Spring WebSocket），Vert.x 模式可通过 ?wsPort=8081 覆盖
        const params = new URLSearchParams(location.search);
        const port = params.get('wsPort') || location.port || (protocol === 'wss:' ? '443' : '80');
        return `${protocol}//${host}:${port}/ws/chat`;
    }

    /**
     * 确保 WebSocket 已连接
     */
    ensureWebSocket() {
        if (this._ws && this._ws.readyState === WebSocket.OPEN) {
            return Promise.resolve(this._ws);
        }
        if (this._wsConnecting) {
            return this._wsConnecting;
        }

        const url = this.getWebSocketURL();
        console.log(`[WS] Connecting to ${url}`);

        this._wsConnecting = new Promise((resolve, reject) => {
            const ws = new WebSocket(url);

            ws.onopen = () => {
                console.log('[WS] Connected');
                this._ws = ws;
                this._wsConnecting = null;
                resolve(ws);
            };

            ws.onerror = (err) => {
                console.error('[WS] Connection error:', err);
                this._wsConnecting = null;
                reject(new Error('WebSocket 连接失败'));
            };

            ws.onclose = (event) => {
                console.log(`[WS] Closed: code=${event.code} reason=${event.reason}`);
                this._ws = null;
                this._wsConnecting = null;
                // 通知流式回调
                if (this._wsCallbacks && this._wsCallbacks.onError) {
                    this._wsCallbacks.onError(new Error('WebSocket 连接断开'));
                    this._wsCallbacks = null;
                }
                // 拒绝所有 pending 请求
                this._pendingRequests.forEach(({ reject }) => {
                    reject(new Error('WebSocket 连接断开'));
                });
                this._pendingRequests.clear();
            };

            ws.onmessage = (event) => {
                this._handleMessage(event.data);
            };
        });

        return this._wsConnecting;
    }

    /**
     * 处理所有服务端消息
     */
    _handleMessage(raw) {
        try {
            const msg = JSON.parse(raw);

            // 0. 处理服务端请求客户端工具执行的消息
            if (['directive', 'DIRECTIVE', 'tool_call', 'client_tool_call'].includes(msg.type)) {
                this._handleServerToolRequest(msg);
                return;
            }

            // 1. 先检查是否是请求-响应式消息（带 requestId）
            if (msg.requestId && this._pendingRequests.has(msg.requestId)) {
                const { resolve, reject } = this._pendingRequests.get(msg.requestId);
                this._pendingRequests.delete(msg.requestId);

                if (msg.type === 'error_response') {
                    reject(new Error(msg.data?.error || '请求失败'));
                } else {
                    resolve(msg.data);
                }
                return;
            }

            // 2. Skill Creator 流式消息
            if (msg.type && msg.type.startsWith('skill_creator_')) {
                const scCb = this._skillCreatorCallbacks;
                if (msg.type !== 'skill_creator_token') {
                    console.log('[SC-WS] received:', msg.type, 'callbacks exist:', !!scCb);
                }
                if (!scCb) {
                    console.warn('[SC-WS] no callbacks for:', msg.type);
                    return;
                }

                switch (msg.type) {
                    case 'skill_creator_token':
                        if (scCb.onDelta) scCb.onDelta(msg.data);
                        break;
                    case 'skill_creator_draft':
                        if (scCb.onDraft) scCb.onDraft(msg.data);
                        break;
                    case 'skill_creator_error':
                        if (scCb.onError) scCb.onError(new Error(msg.message || 'Skill Creator 错误'));
                        this._skillCreatorCallbacks = null;
                        break;
                    case 'skill_creator_stream_end':
                        if (scCb.onComplete) scCb.onComplete();
                        this._skillCreatorCallbacks = null;
                        break;
                }
                return;
            }

            // 3. 流式消息（chat 相关）
            const cb = this._wsCallbacks;
            if (!cb) return;

            switch (msg.type) {
                case 'token':
                    if (cb.onDelta) cb.onDelta(msg.data);
                    break;
                case 'stream_end':
                    if (cb.onComplete) cb.onComplete(msg.meta);
                    this._wsCallbacks = null;
                    break;
                case 'error':
                    if (cb.onError) cb.onError(new Error(msg.message));
                    this._wsCallbacks = null;
                    break;
                case 'crisis_alert':
                    if (cb.onCrisis) cb.onCrisis(msg.resources);
                    this._wsCallbacks = null;
                    break;
                case 'tool_call_start':
                    if (cb.onToolCallStart) cb.onToolCallStart(msg.data);
                    break;
                case 'tool_progress':
                    if (cb.onToolProgress) cb.onToolProgress(msg.data);
                    break;
                case 'tool_log':
                    if (cb.onToolLog) cb.onToolLog(msg.data);
                    break;
                case 'tool_error':
                    if (cb.onToolError) cb.onToolError(msg.data);
                    break;
                case 'post_process':
                    if (cb.onPostProcess) cb.onPostProcess(msg.category, msg.data);
                    break;
                case 'trace':
                    if (cb.onTrace) cb.onTrace(msg.data);
                    break;
            }
        } catch (e) {
            console.error('[WS] Failed to parse message:', e);
        }
    }

    /**
     * 生成唯一的请求 ID
     */
    _nextRequestId() {
        return 'req-' + (++this._requestIdCounter) + '-' + Date.now();
    }

    /**
     * 发送请求并等待响应（请求-响应模式）
     */
    async _sendRequest(type, data = {}, timeoutMs = 30000) {
        const ws = await this.ensureWebSocket();
        const requestId = this._nextRequestId();

        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this._pendingRequests.delete(requestId);
                reject(new Error(`请求超时: ${type}`));
            }, timeoutMs);

            this._pendingRequests.set(requestId, {
                resolve: (result) => {
                    clearTimeout(timer);
                    resolve(result);
                },
                reject: (err) => {
                    clearTimeout(timer);
                    reject(err);
                }
            });

            ws.send(JSON.stringify({ type, requestId, ...data }));
        });
    }

    // ========== Chat (流式) ==========

    /**
     * 通过 WebSocket 发送流式聊天消息
     */
    async wsSendStream(message, options = {}) {
        try {
            const ws = await this.ensureWebSocket();

            return new Promise((resolve, reject) => {
                this._wsCallbacks = {
                    onDelta: options.onDelta,
                    onComplete: (meta) => {
                        if (options.onComplete) options.onComplete(meta);
                        resolve();
                    },
                    onError: (err) => {
                        if (options.onError) options.onError(err);
                        reject(err);
                    },
                    onCrisis: options.onCrisis,
                    onToolCallStart: options.onToolCallStart,
                    onToolProgress: options.onToolProgress,
                    onToolLog: options.onToolLog,
                    onToolError: options.onToolError,
                    onPostProcess: options.onPostProcess,
                    onTrace: options.onTrace,
                };

                ws.send(JSON.stringify({
                    type: 'chat',
                    sessionId: options.sessionId || 'default',
                    message: message,
                    model: options.model || null,
                    reactive: true
                }));
            });
        } catch (error) {
            console.error('[WS] Send failed:', error);
            if (options.onError) options.onError(error);
            throw error;
        }
    }

    // ========== Session ==========

    async getSessionHistory(sessionId) {
        return this._sendRequest('get_history', { sessionId });
    }

    async getSessionSummary(sessionId) {
        return this._sendRequest('get_summary', { sessionId });
    }

    async clearSession(sessionId) {
        return this._sendRequest('clear_session', { sessionId });
    }

    // ========== Skills & Tools ==========

    async getSkills() {
        return this._sendRequest('get_skills');
    }

    async getTools() {
        return this._sendRequest('get_tools');
    }

    // ========== Health ==========

    async checkHealth() {
        return this._sendRequest('health');
    }

    // ========== Assessment ==========

    async getScales() {
        return this._sendRequest('get_scales');
    }

    async getScale(scaleType) {
        return this._sendRequest('get_scale', { scaleType });
    }

    async submitAssessment(userId, scaleType, answers) {
        return this._sendRequest('submit_assessment', { userId, scaleType, answers }, 60000);
    }

    async getAssessmentHistory(userId) {
        return this._sendRequest('get_assessment_history', { userId });
    }

    // ========== User ==========

    async createUser() {
        return this._sendRequest('create_user');
    }

    async checkin(userId, emotion, note) {
        return this._sendRequest('checkin', { userId, emotion, note });
    }

    async getEmotions(userId, days = 7) {
        return this._sendRequest('get_emotions', { userId, days });
    }

    async getStats(userId) {
        return this._sendRequest('get_stats', { userId });
    }

    // ========== Memory ==========

    async searchMemory(query, topK = 10) {
        return this._sendRequest('search_memory', { query, topK });
    }

    async rememberDurable(section, content) {
        return this._sendRequest('remember_durable', { section, content });
    }

    // ========== MCP Tools Detail ==========

    async getToolsDetail() {
        return this._sendRequest('get_tools_detail');
    }

    async getToolDetail(toolName) {
        return this._sendRequest('get_tool_detail', { toolName });
    }

    async getMcpServers() {
        return this._sendRequest('get_mcp_servers');
    }

    async getToolDefinitions() {
        return this._sendRequest('get_tool_definitions');
    }

    // ========== Skill Creator ==========

    /**
     * Skill Creator 流式对话
     * @param {string} message 用户消息
     * @param {object} options { sessionId, onDelta, onDraft, onComplete, onError }
     */
    async skillCreatorChat(message, options = {}) {
        const timeoutMs = options.timeout || 120000; // 默认 2 分钟超时
        try {
            const ws = await this.ensureWebSocket();

            return new Promise((resolve, reject) => {
                let settled = false;

                // 超时保护：防止 Promise 永远挂起
                const timer = setTimeout(() => {
                    if (!settled) {
                        settled = true;
                        console.warn('[SC] skillCreatorChat timeout after', timeoutMs, 'ms');
                        this._skillCreatorCallbacks = null;
                        const err = new Error('Skill Creator 响应超时');
                        if (options.onError) options.onError(err);
                        reject(err);
                    }
                }, timeoutMs);

                this._skillCreatorCallbacks = {
                    onDelta: options.onDelta,
                    onDraft: options.onDraft,
                    onComplete: () => {
                        if (settled) return;
                        settled = true;
                        clearTimeout(timer);
                        console.log('[SC] stream complete');
                        try {
                            if (options.onComplete) options.onComplete();
                        } catch (e) {
                            console.error('[SC] onComplete callback error:', e);
                        }
                        resolve();
                    },
                    onError: (err) => {
                        if (settled) return;
                        settled = true;
                        clearTimeout(timer);
                        console.error('[SC] stream error:', err);
                        try {
                            if (options.onError) options.onError(err);
                        } catch (e) {
                            console.error('[SC] onError callback error:', e);
                        }
                        reject(err);
                    }
                };

                console.log('[SC] sending message, sessionId:', options.sessionId);
                ws.send(JSON.stringify({
                    type: 'skill_creator_chat',
                    sessionId: options.sessionId || 'skill-creator-default',
                    message: message
                }));
            });
        } catch (error) {
            console.error('[WS] Skill Creator send failed:', error);
            if (options.onError) options.onError(error);
            throw error;
        }
    }

    /**
     * 保存当前 Skill 草稿
     */
    async skillCreatorSave(sessionId) {
        return this._sendRequest('skill_creator_save', { sessionId });
    }

    /**
     * 获取已保存的 Skills 列表
     */
    async skillCreatorList() {
        return this._sendRequest('skill_creator_list');
    }

    /**
     * 删除 Skill
     */
    async skillCreatorDelete(skillId) {
        return this._sendRequest('skill_creator_delete', { skillId });
    }

    /**
     * 加载 Skill 到编辑器
     */
    async skillCreatorLoad(skillId, sessionId) {
        return this._sendRequest('skill_creator_load', { skillId, sessionId });
    }

    /**
     * 获取当前 draft
     */
    async skillCreatorGetDraft(sessionId) {
        return this._sendRequest('skill_creator_get_draft', { sessionId });
    }

    // ========== Client Tool Simulation ==========

    async _handleServerToolRequest(msg) {
        if (!this._clientToolRegistry) {
            console.error('[WS] clientToolRegistry is not initialized');
            return;
        }

        if (msg.type === 'directive' || msg.type === 'DIRECTIVE') {
            await this._handleDirective(msg);
            return;
        }

        await this._handleToolCall(msg);
    }

    async _handleDirective(msg) {
        const data = msg.data || {};
        const directiveId = data.directive_id || data.directiveId || '';
        const directives = data.directives || [];

        console.log('[WS] Received DIRECTIVE:', directiveId, directives);

        const primaryDirective = directives[0];
        if (!directiveId || !primaryDirective || !primaryDirective.header) {
            console.warn('[WS] Invalid directive payload:', msg);
            return;
        }

        const namespace = primaryDirective.header.namespace;
        const name = primaryDirective.header.name;
        const toolKey = namespace + '.' + name;
        const args = primaryDirective.payload || {};
        const tool = this._clientToolRegistry.getTool(toolKey);

        if (this._onDirective) {
            this._onDirective(directiveId, toolKey, args, tool);
        }

        const startTime = Date.now();
        const result = await this._clientToolRegistry.invokeTool(toolKey, args);
        const elapsed = Date.now() - startTime;

        const response = {
            type: 'directive_result',
            directiveId: directiveId,
            success: result.ok,
            content: result.ok ? JSON.stringify(result.data || {}) : (result.error?.message || 'Tool execution failed'),
            metadata: {
                elapsed_ms: elapsed,
                toolId: toolKey,
                code: result.error?.code || null
            }
        };

        this._sendWsPayload(response);

        if (this._onDirectiveResult) {
            this._onDirectiveResult(directiveId, response, elapsed);
        }
    }

    async _handleToolCall(msg) {
        const data = msg.data || {};
        const callId = msg.callId || data.callId || '';
        const toolId = msg.toolId || msg.tool || data.toolId || data.tool || data.name || '';
        const args = msg.args || data.args || data.input || {};

        if (!callId || !toolId) {
            console.warn('[WS] Invalid tool_call payload:', msg);
            return;
        }

        const result = await this._clientToolRegistry.invokeTool(toolId, args);
        const response = {
            type: 'client_tool_result',
            callId,
            isError: !result.ok,
            content: result.ok ? JSON.stringify(result.data || {}) : (result.error?.message || 'Tool execution failed'),
            meta: {
                toolId,
                code: result.error?.code || null
            }
        };

        this._sendWsPayload(response);
    }

    _sendWsPayload(payload) {
        if (this._ws && this._ws.readyState === WebSocket.OPEN) {
            this._ws.send(JSON.stringify(payload));
        }
    }

    getClientTools() {
        return this._clientToolRegistry ? this._clientToolRegistry.entries() : [];
    }

    setClientTool(key, config) {
        if (!this._clientToolRegistry) return;
        const existing = this._clientToolRegistry.getTool(key);
        if (existing) {
            Object.assign(existing, config);
        } else {
            this._clientToolRegistry.registerTool({
                id: key,
                name: config.name || key,
                description: config.description || '',
                inputSchema: config.inputSchema || { type: 'object' },
                handler: typeof config.handler === 'function' ? config.handler : () => config.mockResponse || {},
                ...config
            });
        }
        this._saveClientToolsToStorage();
    }

    removeClientTool(key) {
        if (!this._clientToolRegistry) return;
        const tool = this._clientToolRegistry.getTool(key);
        if (tool) {
            tool.enabled = false;
        }
        this._saveClientToolsToStorage();
    }

    toggleClientTool(key, enabled) {
        if (!this._clientToolRegistry) return;
        const tool = this._clientToolRegistry.getTool(key);
        if (tool) {
            tool.enabled = enabled;
            this._saveClientToolsToStorage();
        }
    }

    updateMockResponse(key, jsonStr) {
        if (!this._clientToolRegistry) return false;
        const tool = this._clientToolRegistry.getTool(key);
        if (tool) {
            try {
                tool.mockResponse = JSON.parse(jsonStr);
                this._saveClientToolsToStorage();
                return true;
            } catch (e) {
                console.error('Invalid JSON for mock response:', e);
                return false;
            }
        }
        return false;
    }

    setDirectiveCallback(onDirective, onResult) {
        this._onDirective = onDirective;
        this._onDirectiveResult = onResult;
    }

    _saveClientToolsToStorage() {
        if (!this._clientToolRegistry) return;
        try {
            const data = {};
            this._clientToolRegistry.entries().forEach(([key, tool]) => {
                data[key] = {
                    enabled: tool.enabled !== false,
                    mockResponse: tool.mockResponse || null
                };
            });
            localStorage.setItem('clientToolSimulations', JSON.stringify(data));
        } catch (e) { /* ignore */ }
    }

    _loadClientToolsFromStorage() {
        if (!this._clientToolRegistry) return;
        try {
            const saved = localStorage.getItem('clientToolSimulations');
            if (!saved) return;

            const data = JSON.parse(saved);
            Object.entries(data).forEach(([key, value]) => {
                const tool = this._clientToolRegistry.getTool(key);
                if (!tool) return;
                if (typeof value.enabled === 'boolean') {
                    tool.enabled = value.enabled;
                }
                if (value.mockResponse) {
                    tool.mockResponse = value.mockResponse;
                }
            });
        } catch (e) { /* ignore */ }
    }

    // ========== Connection ==========

    wsDisconnect() {
        if (this._ws) {
            this._ws.close(1000, 'Client disconnect');
            this._ws = null;
        }
        this._wsCallbacks = null;
        this._wsConnecting = null;
        this._pendingRequests.clear();
    }
}

// 创建全局实例
window.soulComfortAPI = new SoulComfortAPI();
