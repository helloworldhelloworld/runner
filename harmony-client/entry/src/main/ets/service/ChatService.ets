/**
 * 聊天服务 - 与后端API通信
 */
import http from '@ohos.net.http';
import { ChatRequest, ChatResponse, Skill, Tool } from '../model/ChatApi';

export class ChatService {
  // 后端服务地址（开发环境）
  private static readonly BASE_URL = 'http://localhost:8080';

  /**
   * 发送聊天消息
   */
  static async sendMessage(message: string, activeSkills: string[] = [], useToolCalling: boolean = false): Promise<ChatResponse> {
    const request: ChatRequest = {
      message: message,
      activeSkills: activeSkills,
      useToolCalling: useToolCalling
    };

    try {
      const httpRequest = http.createHttp();
      const response = await httpRequest.request(
        `${this.BASE_URL}/api/chat`,
        {
          method: http.RequestMethod.POST,
          header: {
            'Content-Type': 'application/json'
          },
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
        throw new Error(`HTTP ${response.responseCode}: ${response.result}`);
      }
    } catch (error) {
      console.error('Chat service error:', error);
      throw error;
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
        {
          method: http.RequestMethod.GET,
          expectDataType: http.HttpDataType.STRING
        }
      );

      httpRequest.destroy();

      if (response.responseCode === 200) {
        return JSON.parse(response.result as string);
      }
      return [];
    } catch (error) {
      console.error('Get skills error:', error);
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
        {
          method: http.RequestMethod.GET,
          expectDataType: http.HttpDataType.STRING
        }
      );

      httpRequest.destroy();

      if (response.responseCode === 200) {
        return JSON.parse(response.result as string);
      }
      return [];
    } catch (error) {
      console.error('Get tools error:', error);
      return [];
    }
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
      console.error('Health check error:', error);
      return false;
    }
  }
}
