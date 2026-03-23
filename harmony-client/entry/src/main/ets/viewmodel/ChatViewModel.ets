/**
 * 聊天视图模型 - 管理聊天页面状态
 * 支持：流式回复、语音输入 (ASR)、情感化 TTS 自动播报
 */
import { Message, MessageRole } from '../model/Message';
import { ChatService } from '../service/ChatService';
import { VoiceService } from '../service/VoiceService';
import abilityAccessCtrl from '@ohos.abilityAccessCtrl';
import common from '@ohos.app.ability.common';

export class ChatViewModel {
  // 消息列表
  messages: Message[] = [];
  // 发送中
  isSending: boolean = false;
  // 连接状态
  isConnected: boolean = false;
  // 会话 ID（保持跨请求一致）
  sessionId: string = 'harmony-' + Date.now().toString(36);

  // 语音输入状态
  isRecording: boolean = false;
  // 实时识别中间结果
  asrInterimText: string = '';

  // TTS 播报状态
  isSpeaking: boolean = false;
  // 自动播报开关（默认开）
  autoTts: boolean = true;

  // 上下文（用于申请权限）
  private context: common.UIAbilityContext | null = null;

  // UI 状态回调（由 ChatPage 注入，用于触发 @State 刷新）
  onSpeakingChange: (speaking: boolean) => void = () => {};
  onRecordingChange: (recording: boolean) => void = () => {};
  onInterimTextChange: (text: string) => void = () => {};
  onMessagesUpdate: () => void = () => {};

  constructor(context?: common.UIAbilityContext) {
    this.context = context ?? null;
    VoiceService.setTtsStateCallback((speaking: boolean) => {
      this.isSpeaking = speaking;
      this.onSpeakingChange(speaking);
    });
  }

  /**
   * 初始化
   */
  async initialize() {
    this.isConnected = await ChatService.healthCheck();

    // 预初始化 TTS（加快首次播报）
    VoiceService.initTTS();

    if (this.isConnected) {
      const welcomeMessage = new Message(
        '你好，欢迎来到解忧杂货铺。\n\n在这里，你可以倾诉任何心事，分享任何感受。无论是快乐还是忧愁，我都会用心倾听，陪你一起面对。🏮',
        MessageRole.ASSISTANT,
        '温柔'
      );
      this.messages.push(welcomeMessage);
      this.onMessagesUpdate();

      // 自动播报欢迎语
      if (this.autoTts) {
        VoiceService.speak(welcomeMessage.content, welcomeMessage.emotion);
      }
    } else {
      this.messages.push(new Message('抱歉，暂时无法连接到服务。请稍后再试。', MessageRole.SYSTEM));
      this.onMessagesUpdate();
    }
  }

  /**
   * 发送消息（流式）
   */
  sendMessage(content: string) {
    if (!content.trim() || this.isSending) return;

    // 添加用户消息
    const userMessage = new Message(content, MessageRole.USER);
    this.messages.push(userMessage);
    this.isSending = true;
    this.onMessagesUpdate();

    // 添加加载占位消息
    const loadingMsg = new Message('', MessageRole.ASSISTANT, '温柔');
    loadingMsg.isLoading = true;
    this.messages.push(loadingMsg);

    const lastIndex = this.messages.length - 1;
    let currentEmotion = '温柔';

    ChatService.sendMessageStream(
      content,
      this.sessionId,
      // onDelta
      (delta: string, emotion: string) => {
        currentEmotion = emotion || currentEmotion;
        const msg = this.messages[lastIndex];
        if (msg) {
          msg.content += delta;
          msg.isLoading = false;
          msg.emotion = currentEmotion;
          // 触发 UI 刷新（替换数组触发响应式）
          this.messages[lastIndex] = msg;
          this.onMessagesUpdate();
        }
      },
      // onComplete
      (fullText: string) => {
        const msg = this.messages[lastIndex];
        if (msg) {
          msg.content = fullText || msg.content;
          msg.isLoading = false;
          msg.emotion = currentEmotion;
          this.messages[lastIndex] = msg;
        }
        this.isSending = false;
        this.onMessagesUpdate();

        // 自动 TTS 播报
        if (this.autoTts && fullText) {
          VoiceService.speak(fullText, currentEmotion);
        }
      },
      // onError
      (error: string) => {
        const msg = this.messages[lastIndex];
        if (msg) {
          msg.content = '抱歉，我遇到了一些问题。请稍后再试。';
          msg.isLoading = false;
          this.messages[lastIndex] = msg;
        }
        this.isSending = false;
        this.onMessagesUpdate();
        console.error('[ChatViewModel] stream error:', error);
      }
    );
  }

  // ==================== 语音输入 ====================

  /**
   * 申请麦克风权限，然后开始录音
   */
  async startVoiceInput() {
    if (this.isRecording || this.isSending) return;

    // 申请权限
    if (this.context) {
      try {
        const atManager = abilityAccessCtrl.createAtManager();
        const result = await atManager.requestPermissionsFromUser(
          this.context,
          ['ohos.permission.MICROPHONE']
        );
        const granted = result.authResults[0] === 0; // 0 = PERMISSION_GRANTED
        if (!granted) {
          this.messages.push(new Message('需要麦克风权限才能使用语音输入', MessageRole.SYSTEM));
          return;
        }
      } catch (err) {
        console.error('[ChatViewModel] Permission request failed:', err);
        return;
      }
    }

    this.asrInterimText = '';
    this.onInterimTextChange('');

    await VoiceService.startListening(
      // onResult
      (text: string, isFinal: boolean) => {
        this.asrInterimText = text;
        this.onInterimTextChange(text);
        if (isFinal && text.trim()) {
          this.isRecording = false;
          this.asrInterimText = '';
          this.onRecordingChange(false);
          this.onInterimTextChange('');
          this.sendMessage(text.trim());
        }
      },
      // onState
      (recording: boolean) => {
        this.isRecording = recording;
        this.onRecordingChange(recording);
        if (!recording) {
          this.asrInterimText = '';
          this.onInterimTextChange('');
        }
      }
    );
  }

  /**
   * 停止录音（手动）
   */
  stopVoiceInput() {
    if (!this.isRecording) return;
    VoiceService.stopListening();
    this.isRecording = false;
    this.asrInterimText = '';
    this.onRecordingChange(false);
    this.onInterimTextChange('');
  }

  // ==================== TTS ====================

  /**
   * 手动朗读某条消息
   */
  speakMessage(message: Message) {
    if (this.isSpeaking) {
      VoiceService.stopSpeaking();
    } else {
      VoiceService.speak(message.content, message.emotion);
    }
  }

  /**
   * 切换 TTS 自动播报
   */
  toggleAutoTts() {
    this.autoTts = !this.autoTts;
    if (!this.autoTts) {
      VoiceService.stopSpeaking();
    }
  }

  // ==================== 其他 ====================

  clearMessages() {
    VoiceService.stopSpeaking();
    this.messages = [];
    this.onMessagesUpdate();
  }

  destroy() {
    VoiceService.destroy();
  }
}
