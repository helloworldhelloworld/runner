/**
 * 消息模型
 */
export class Message {
  id: string;
  content: string;
  role: MessageRole;
  timestamp: number;
  isLoading?: boolean;

  constructor(content: string, role: MessageRole) {
    this.id = Date.now().toString() + Math.random().toString(36).substr(2, 9);
    this.content = content;
    this.role = role;
    this.timestamp = Date.now();
    this.isLoading = false;
  }
}

export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system'
}
