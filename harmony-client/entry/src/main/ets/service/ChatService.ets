/**
 * 聊天服务 - 与后端API通信
 * 支持：普通请求、SSE 流式、soul comfort 模式
 */
import http from '@ohos.net.http';
import { ChatRequest, ChatResponse, Skill, Tool } from '../model/ChatApi';

export type StreamDeltaCallback = (delta: string, emotion: string) => void;
export type StreamCompleteCallback = (fullText: string) => void;
export type StreamErrorCallback = (error: string) => void;

export class ChatService {
  // 后端服务地址（开发环境）
  private static readonly BASE_URL = 'http://10.0.2.2:8080'; // 模拟器用 10.0.2.2，真机用实际IP

  /**
   * 发送聊天消息（普通模式，返回完整响应）
   */
  static async sendMessage(
    message: string,
    sessionId: string,
    activeSkills: string[] = [],
    useToolCalling: boolean = false
  ): Promise<ChatResponse> {
    const request: ChatRequest = {
      message: message,
      sessionId: sessionId,
      soulComfortMode: true,
      activeSkills: activeSkills,
      useToolCalling: useToolCalling
    };

    try {
      const httpRequest = http.createHttp();
      const response = await httpRequest.request(
        `${this.BASE_URL}/api/chat`,
        {
          method: http.RequestMethod.POST,
          header: { 'Content-Type': 'application/json' },
          extraData: JSON.stringify(request),
          expectDataType: http.HttpDataType.STRING,
          connectTimeout: 60000,
          readTimeout: 60000
        }
      );

      httpRequest.destroy();

      if (response.responseCode === 200) {
        const data: ChatResponse = JSON.parse(response.result as string);
        return data;
      } else {
        throw new Error(`HTTP ${response.responseCode}`);
      }
    } catch (error) {
      console.error('[ChatService] sendMessage error:', error);
      throw error;
    }
  }

  /**
   * 流式聊天（SSE），逐字回调
   */
  static sendMessageStream(
    message: string,
    sessionId: string,
    onDelta: StreamDeltaCallback,
    onComplete: StreamCompleteCallback,
    onError: StreamErrorCallback
  ): void {
    const request = {
      message: message,
      sessionId: sessionId,
      soulComfortMode: true
    };

    const httpRequest = http.createHttp();

    // 监听流式数据
    let buffer = '';
    let fullText = '';

    httpRequest.on('dataReceive', (data: ArrayBuffer) => {
      const chunk = ChatService.arrayBufferToString(data);
      buffer += chunk;

      // 解析 SSE 事件
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? ''; // 最后不完整的行留着

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;

        const jsonStr = trimmed.slice(5).trim();
        if (!jsonStr || jsonStr === '[DONE]') continue;

        try {
          const event = JSON.parse(jsonStr) as Record<string, string>;
          if (event['type'] === 'delta') {
            const delta = event['delta'] ?? '';
            const emotion = event['emotion'] ?? '温柔';
            if (delta) {
              fullText += delta;
              onDelta(delta, emotion);
            }
          } else if (event['type'] === 'complete') {
            onComplete(fullText || event['response'] ?? '');
            httpRequest.destroy();
          }
        } catch (e) {
          // 跳过非 JSON 行
        }
      }
    });

    httpRequest.on('dataEnd', () => {
      if (fullText) {
        onComplete(fullText);
      }
      httpRequest.destroy();
    });

    httpRequest.on('dataReceiveAbort', () => {
      onError('连接中断');
      httpRequest.destroy();
    });

    // 发起请求（不等待，流式回调处理）
    httpRequest.request(
      `${this.BASE_URL}/api/chat/stream`,
      {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: JSON.stringify(request),
        expectDataType: http.HttpDataType.ARRAY_BUFFER,
        connectTimeout: 10000,
        readTimeout: 60000,
        usingProtocol: http.HttpProtocol.HTTP1_1
      }
    ).then(response => {
      if (response.responseCode !== 200) {
        onError(`HTTP ${response.responseCode}`);
        httpRequest.destroy();
      }
    }).catch((err: Error) => {
      onError(err.message ?? '请求失败');
      httpRequest.destroy();
    });
  }

  /**
   * 健康检查
   */
  static async healthCheck(): Promise<boolean> {
    try {
      const httpRequest = http.createHttp();
      const response = await httpRequest.request(
        `${this.BASE_URL}/api/health`,
        {
          method: http.RequestMethod.GET,
          expectDataType: http.HttpDataType.STRING,
          connectTimeout: 5000,
          readTimeout: 5000
        }
      );
      httpRequest.destroy();
      return response.responseCode === 200;
    } catch (error) {
      return false;
    }
  }

  /**
   * 获取可用技能列表
   */
  static async getSkills(): Promise<Skill[]> {
    try {
      const httpRequest = http.createHttp();
      const response = await httpRequest.request(
        `${this.BASE_URL}/api/skills`,
        { method: http.RequestMethod.GET, expectDataType: http.HttpDataType.STRING }
      );
      httpRequest.destroy();
      return response.responseCode === 200 ? JSON.parse(response.result as string) : [];
    } catch {
      return [];
    }
  }

  /**
   * 获取可用工具列表
   */
  static async getTools(): Promise<Tool[]> {
    try {
      const httpRequest = http.createHttp();
      const response = await httpRequest.request(
        `${this.BASE_URL}/api/tools`,
        { method: http.RequestMethod.GET, expectDataType: http.HttpDataType.STRING }
      );
      httpRequest.destroy();
      return response.responseCode === 200 ? JSON.parse(response.result as string) : [];
    } catch {
      return [];
    }
  }

  // ArrayBuffer → UTF-8 string
  private static arrayBufferToString(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let result = '';
    for (let i = 0; i < bytes.length; i++) {
      result += String.fromCharCode(bytes[i]);
    }
    try {
      return decodeURIComponent(escape(result));
    } catch {
      return result;
    }
  }
}
