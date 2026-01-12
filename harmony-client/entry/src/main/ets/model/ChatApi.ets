/**
 * API 请求和响应模型
 */

export interface ChatRequest {
  message: string;
  activeSkills: string[];
  useToolCalling: boolean;
}

export interface ChatResponse {
  response: string;
  toolCallsUsed?: string[];
  skillsApplied?: string[];
  metadata?: {
    provider: string;
    skillsCount: number;
    toolCallingEnabled: boolean;
  };
  error?: string;
}

export interface Skill {
  name: string;
  format: string;
  description: string;
}

export interface Tool {
  name: string;
  description: string;
}
