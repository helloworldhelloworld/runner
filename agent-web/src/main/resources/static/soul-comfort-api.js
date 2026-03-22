/**
 * Soul Comfort API Client - WebSocket Only
 * 所有通信通过 WebSocket 进行，不使用 REST/SSE
 */

class SoulComfortAPI {
    constructor() {
        this._ws = null;
        this._wsConnecting = null;
        this._wsCallbacks = null;
        /** 请求-响应式回调映射 (requestId → {resolve, reject}) */
        this._pendingRequests = new Map();
        this._requestIdCounter = 0;

        /** 客户端工具模拟配置 */
        this._clientTools = new Map();
        this._onDirective = null;
        this._onDirectiveResult = null;
        this._loadClientToolsFromStorage();
        if (this._clientTools.size === 0) {
            // 预配置 GetPosition 端工具
            this._clientTools.set('GeoInformation.GetPosition', {
                namespace: 'GeoInformation',
                name: 'GetPosition',
                description: '获取设备GPS定位和城市名',
                enabled: true,
                mockResponse: {
                    header: { namespace: 'GeoInformation', name: 'PositionInfo' },
                    payload: {
                        errorCode: '0',
                        position: { longitude: '116.397', latitude: '39.909', locationSystem: 'WGS84' },
                        city: '北京'
                    }
                }
            });
            this._saveClientToolsToStorage();
        }
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

            // 0. 处理 DIRECTIVE 消息（端工具调用）
            if (msg.type === 'DIRECTIVE') {
                this._handleDirective(msg);
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

            // 2. 流式消息（chat 相关）
            const cb = this._wsCallbacks;
            if (!cb) return;

            switch (msg.type) {
                case 'token':
                    if (cb.onDelta) cb.onDelta(msg.data);
                    break;
                case 'reasoning_token':
                    if (cb.onReasoningDelta) cb.onReasoningDelta(msg.data);
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

    // ========== Client Tool Simulation ==========

    _handleDirective(msg) {
        const data = msg.data;
        const directiveId = data.directive_id;
        const directives = data.directives || [];

        console.log('[WS] Received DIRECTIVE:', directiveId, directives);

        const primaryDirective = directives[0];
        if (!primaryDirective) return;

        const namespace = primaryDirective.header.namespace;
        const name = primaryDirective.header.name;
        const toolKey = namespace + '.' + name;
        const tool = this._clientTools.get(toolKey);

        // 通知 UI
        if (this._onDirective) {
            this._onDirective(directiveId, toolKey, primaryDirective.payload, tool);
        }

        const startTime = Date.now();

        // 模拟异步执行（200ms 延迟）
        setTimeout(() => {
            const elapsed = Date.now() - startTime;
            let response;

            if (tool && tool.enabled && tool.mockResponse) {
                response = {
                    type: 'directive_result',
                    directiveId: directiveId,
                    success: true,
                    content: JSON.stringify(tool.mockResponse),
                    metadata: { elapsed_ms: elapsed, simulated: true }
                };
            } else {
                response = {
                    type: 'directive_result',
                    directiveId: directiveId,
                    success: false,
                    content: tool ? 'Tool disabled: ' + toolKey : 'No mock configured for ' + toolKey,
                    metadata: { elapsed_ms: elapsed, simulated: true }
                };
            }

            if (this._ws && this._ws.readyState === WebSocket.OPEN) {
                this._ws.send(JSON.stringify(response));
                console.log('[WS] Sent directive_result for', directiveId, `(${elapsed}ms)`);
            }

            // 通知 UI 结果
            if (this._onDirectiveResult) {
                this._onDirectiveResult(directiveId, response, elapsed);
            }
        }, 200);
    }

    getClientTools() {
        return Array.from(this._clientTools.entries());
    }

    setClientTool(key, config) {
        this._clientTools.set(key, config);
        this._saveClientToolsToStorage();
    }

    removeClientTool(key) {
        this._clientTools.delete(key);
        this._saveClientToolsToStorage();
    }

    toggleClientTool(key, enabled) {
        const tool = this._clientTools.get(key);
        if (tool) {
            tool.enabled = enabled;
            this._saveClientToolsToStorage();
        }
    }

    updateMockResponse(key, jsonStr) {
        const tool = this._clientTools.get(key);
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
        try {
            const data = {};
            this._clientTools.forEach((v, k) => { data[k] = v; });
            localStorage.setItem('clientToolSimulations', JSON.stringify(data));
        } catch (e) { /* ignore */ }
    }

    _loadClientToolsFromStorage() {
        try {
            const saved = localStorage.getItem('clientToolSimulations');
            if (saved) {
                const data = JSON.parse(saved);
                Object.entries(data).forEach(([k, v]) => this._clientTools.set(k, v));
            }
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
