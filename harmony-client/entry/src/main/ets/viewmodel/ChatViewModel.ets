/**
 * 聊天视图模型 - 管理聊天页面状态
 */
import { Message, MessageRole } from '../model/Message';
import { ChatService } from '../service/ChatService';

export class ChatViewModel {
  messages: Message[] = [];
  inputText: string = '';
  isConnected: boolean = false;
  isSending: boolean = false;

  /**
   * 初始化 - 检查后端连接
   */
  async initialize() {
    this.isConnected = await ChatService.healthCheck();

    // 添加欢迎消息
    if (this.isConnected) {
      const welcomeMessage = new Message(
        '你好，欢迎来到解忧杂货铺。\n\n在这里，你可以倾诉任何心事，分享任何感受。无论是快乐还是忧愁，我都会用心倾听，陪你一起面对。',
        MessageRole.ASSISTANT
      );
      this.messages.push(welcomeMessage);
    } else {
      const errorMessage = new Message(
        '抱歉，暂时无法连接到服务。请稍后再试。',
        MessageRole.SYSTEM
      );
      this.messages.push(errorMessage);
    }
  }

  /**
   * 发送消息
   */
  async sendMessage(content: string) {
    if (!content.trim() || this.isSending) {
      return;
    }

    // 添加用户消息
    const userMessage = new Message(content, MessageRole.USER);
    this.messages.push(userMessage);
    this.inputText = '';
    this.isSending = true;

    // 添加加载占位消息
    const loadingMessage = new Message('正在思考...', MessageRole.ASSISTANT);
    loadingMessage.isLoading = true;
    this.messages.push(loadingMessage);

    try {
      // 调用后端API
      const response = await ChatService.sendMessage(content, [], false);

      // 移除加载消息
      this.messages.pop();

      // 添加助手回复
      const assistantMessage = new Message(
        response.response || '抱歉，我现在有些困惑，能再说一遍吗？',
        MessageRole.ASSISTANT
      );
      this.messages.push(assistantMessage);
    } catch (error) {
      // 移除加载消息
      this.messages.pop();

      // 添加错误消息
      const errorMessage = new Message(
        '抱歉，我遇到了一些问题。请稍后再试。',
        MessageRole.SYSTEM
      );
      this.messages.push(errorMessage);
      console.error('Send message error:', error);
    } finally {
      this.isSending = false;
    }
  }

  /**
   * 清空聊天记录
   */
  clearMessages() {
    this.messages = [];
  }
}
