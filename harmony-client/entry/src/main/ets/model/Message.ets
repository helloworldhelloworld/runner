/**
 * 消息模型
 */
export class Message {
  id: string;
  content: string;
  role: MessageRole;
  timestamp: number;
  isLoading?: boolean;
  emotion?: string;       // 情绪标签（来自后端）：温柔 | 积极 | 担忧 | 悲伤 等

  constructor(content: string, role: MessageRole, emotion?: string) {
    this.id = Date.now().toString() + Math.random().toString(36).substr(2, 9);
    this.content = content;
    this.role = role;
    this.timestamp = Date.now();
    this.isLoading = false;
    this.emotion = emotion ?? '温柔';
  }
}

export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system'
}
