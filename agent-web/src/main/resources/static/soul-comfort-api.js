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
}

// 创建全局实例
window.soulComfortAPI = new SoulComfortAPI();

// 导出
if (typeof module !== 'undefined' && module.exports) {
    module.exports = SoulComfortAPI;
}
