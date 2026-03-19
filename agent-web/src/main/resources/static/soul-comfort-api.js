/**
 * Soul Comfort API Client
 * 统一的API客户端，支持Web和App
 */

class SoulComfortAPI {
    constructor(baseURL = '') {
        this.baseURL = baseURL || this.detectBaseURL();
        console.log(`[API] Base URL: ${this.baseURL}`);
    }

    /**
     * 检测API基础URL
     */
    detectBaseURL() {
        // App环境使用完整URL
        if (window.appBridge && window.appBridge.isApp) {
            // TODO: 从App配置中获取
            return 'http://your-backend-server.com';
        }
        // Web环境使用相对路径
        return '';
    }

    /**
     * 通用请求方法
     */
    async request(url, options = {}) {
        const fullURL = this.baseURL + url;

        try {
            const response = await fetch(fullURL, {
                ...options,
                headers: {
                    'Content-Type': 'application/json',
                    ...options.headers
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            // 如果是音频响应
            if (response.headers.get('Content-Type')?.includes('audio')) {
                return {
                    audio: await response.blob(),
                    headers: this.extractHeaders(response)
                };
            }

            // JSON响应
            const data = await response.json();
            return data;

        } catch (error) {
            console.error('[API] Request failed:', error);
            throw error;
        }
    }

    /**
     * 提取响应头
     */
    extractHeaders(response) {
        const headers = {};
        const keys = [
            'X-Recognized-Text',
            'X-Response-Text',
            'X-Emotion',
            'X-Session-Id'
        ];

        keys.forEach(key => {
            const value = response.headers.get(key);
            if (value) {
                headers[key] = value;
            }
        });

        return headers;
    }

    // ========== Chat API ==========

    /**
     * 发送文字消息
     */
    async sendTextMessage(message, options = {}) {
        return this.request('/api/chat', {
            method: 'POST',
            body: JSON.stringify({
                message,
                sessionId: options.sessionId,
                model: options.model,
                soulComfortMode: true,
                debug: options.debug || false
            })
        });
    }

    /**
     * 发送流式文字消息 (SSE)
     * @param {string} message - 消息内容
     * @param {Object} options - 选项
     * @param {Function} options.onDelta - 每个文本片段的回调
     * @param {Function} options.onComplete - 完成时的回调
     * @param {Function} options.onError - 错误时的回调
     */
    async sendTextMessageStream(message, options = {}) {
        const url = this.baseURL + '/api/chat/stream';

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message,
                    sessionId: options.sessionId,
                    model: options.model,
                    soulComfortMode: true,
                    debug: options.debug || false
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });

                // Parse SSE events
                const lines = buffer.split('\n');
                buffer = lines.pop() || ''; // Keep incomplete line in buffer

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        try {
                            const data = JSON.parse(line.slice(5).trim());

                            if (data.type === 'delta' && options.onDelta) {
                                options.onDelta(data.delta, data.emotion);
                            } else if (data.type === 'complete' && options.onComplete) {
                                options.onComplete(data.response, data);
                            } else if (data.type === 'error' && options.onError) {
                                options.onError(new Error(data.message));
                            }
                        } catch (e) {
                            // Ignore parse errors for incomplete JSON
                        }
                    }
                }
            }
        } catch (error) {
            console.error('[API] Stream request failed:', error);
            if (options.onError) {
                options.onError(error);
            }
            throw error;
        }
    }

    /**
     * 发送语音消息（完整流程）
     */
    async sendVoiceMessage(audioBlob, options = {}) {
        const formData = new FormData();
        formData.append('audio', audioBlob, 'recording.webm');

        if (options.sessionId) {
            formData.append('sessionId', options.sessionId);
        }

        if (options.model) {
            formData.append('model', options.model);
        }

        const response = await fetch(this.baseURL + '/api/voice/chat', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            // Try to get detailed error message from response header
            const errorMessage = response.headers.get('X-Error-Message');
            if (errorMessage) {
                throw new Error(errorMessage);
            }
            throw new Error(`语音处理失败 (HTTP ${response.status})`);
        }

        return {
            audio: await response.blob(),
            recognizedText: response.headers.get('X-Recognized-Text'),
            responseText: response.headers.get('X-Response-Text'),
            emotion: response.headers.get('X-Emotion'),
            sessionId: response.headers.get('X-Session-Id')
        };
    }

    /**
     * 语音识别（仅STT）
     */
    async recognizeSpeech(audioBlob) {
        const formData = new FormData();
        formData.append('audio', audioBlob);

        return this.request('/api/voice/recognize', {
            method: 'POST',
            body: formData,
            headers: {} // 不设置Content-Type，让浏览器自动设置
        });
    }

    /**
     * 语音合成（仅TTS）
     */
    async synthesizeSpeech(text, emotion = '温柔') {
        const response = await fetch(this.baseURL + '/api/voice/synthesize', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text, emotion })
        });

        if (!response.ok) {
            throw new Error(`TTS failed: ${response.status}`);
        }

        return await response.blob();
    }

    // ========== Session API ==========

    /**
     * 获取会话摘要
     */
    async getSessionSummary(sessionId) {
        return this.request(`/api/session/${sessionId}/summary`);
    }

    /**
     * 清空会话
     */
    async clearSession(sessionId) {
        return this.request(`/api/session/${sessionId}`, {
            method: 'DELETE'
        });
    }

    // ========== Health API ==========

    /**
     * 健康检查
     */
    async checkHealth() {
        return this.request('/api/health');
    }

    // ========== Gateway API (统一入口) ==========

    /**
     * 通过 Gateway 发送消息（同步）
     */
    async gatewaySend(message, options = {}) {
        return this.request('/gateway/chat', {
            method: 'POST',
            body: JSON.stringify({
                message,
                session_id: options.sessionId,
                model: options.model,
                stream: false,
                client_type: this.detectClientType(),
                extra: options.extra
            })
        });
    }

    /**
     * 通过 Gateway 发送消息（流式）
     */
    async gatewaySendStream(message, options = {}) {
        const url = this.baseURL + '/gateway/chat/stream';

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message,
                    session_id: options.sessionId,
                    model: options.model,
                    stream: true,
                    client_type: this.detectClientType(),
                    extra: options.extra
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });

                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        try {
                            const data = JSON.parse(line.slice(5).trim());

                            // 统一响应格式
                            if (data.type === 'DELTA' && options.onDelta) {
                                options.onDelta(data.content, data.metadata?.emotion);
                            } else if (data.type === 'COMPLETE' && options.onComplete) {
                                options.onComplete(data.content, data);
                            } else if (data.type === 'ERROR' && options.onError) {
                                options.onError(new Error(data.error_message));
                            }
                        } catch (e) {
                            // Ignore parse errors
                        }
                    }
                }
            }
        } catch (error) {
            console.error('[Gateway] Stream request failed:', error);
            if (options.onError) {
                options.onError(error);
            }
            throw error;
        }
    }

    /**
     * 检测客户端类型
     */
    detectClientType() {
        if (window.appBridge) {
            if (window.appBridge.platform === 'harmonyos') return 'HARMONYOS';
            if (window.appBridge.platform === 'ios') return 'IOS';
            if (window.appBridge.platform === 'android') return 'ANDROID';
        }

        // 小程序检测
        if (typeof wx !== 'undefined') return 'MINIPROGRAM';

        return 'WEB';
    }

    /**
     * Gateway 健康检查
     */
    async gatewayHealth() {
        return this.request('/gateway/health');
    }

    // ========== WebSocket 流式聊天 ==========

    /**
     * 获取 WebSocket URL（自动推断 ws:// 或 wss://）
     */
    getWebSocketURL() {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = location.hostname;
        // Vert.x WebSocket 服务运行在 8081 端口
        const port = 8081;
        return `${protocol}//${host}:${port}/ws/chat`;
    }

    /**
     * 确保 WebSocket 已连接，返回连接的 Promise
     */
    ensureWebSocket() {
        if (this._ws && this._ws.readyState === WebSocket.OPEN) {
            return Promise.resolve(this._ws);
        }
        // 正在连接中，等待
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
                // 通知当前活跃的回调（如果有）
                if (this._wsCallbacks && this._wsCallbacks.onError) {
                    this._wsCallbacks.onError(new Error('WebSocket 连接断开'));
                    this._wsCallbacks = null;
                }
            };

            ws.onmessage = (event) => {
                this._handleWebSocketMessage(event.data);
            };
        });

        return this._wsConnecting;
    }

    /**
     * 处理 WebSocket 服务端消息
     */
    _handleWebSocketMessage(raw) {
        try {
            const msg = JSON.parse(raw);
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
                default:
                    console.log('[WS] Unknown message type:', msg.type);
            }
        } catch (e) {
            console.error('[WS] Failed to parse message:', e);
        }
    }

    /**
     * 通过 WebSocket 发送流式聊天消息
     *
     * @param {string} message - 用户消息
     * @param {Object} options
     * @param {string} options.sessionId - 会话 ID
     * @param {Function} options.onDelta - 文本片段回调 (delta)
     * @param {Function} options.onComplete - 完成回调 (meta)
     * @param {Function} options.onError - 错误回调 (error)
     * @param {Function} [options.onToolCallStart] - 工具调用开始
     * @param {Function} [options.onToolProgress] - 工具执行进度
     * @returns {Promise<void>}
     */
    async wsSendStream(message, options = {}) {
        try {
            const ws = await this.ensureWebSocket();

            return new Promise((resolve, reject) => {
                // 注册回调
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
                };

                // 发送 chat 消息
                ws.send(JSON.stringify({
                    type: 'chat',
                    sessionId: options.sessionId || 'default',
                    message: message,
                    reactive: true
                }));
            });
        } catch (error) {
            console.error('[WS] Send failed:', error);
            if (options.onError) options.onError(error);
            throw error;
        }
    }

    /**
     * 断开 WebSocket 连接
     */
    wsDisconnect() {
        if (this._ws) {
            this._ws.close(1000, 'Client disconnect');
            this._ws = null;
        }
        this._wsCallbacks = null;
        this._wsConnecting = null;
    }
}

// 创建全局实例
window.soulComfortAPI = new SoulComfortAPI();

// 导出
if (typeof module !== 'undefined' && module.exports) {
    module.exports = SoulComfortAPI;
}
