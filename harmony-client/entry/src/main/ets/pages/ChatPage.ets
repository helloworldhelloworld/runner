/**
 * 解忧杂货铺 - 聊天主页面
 * 支持：流式回复、语音输入 (ASR)、情感化 TTS 播报
 */
import { Message, MessageRole } from '../model/Message';
import { ChatViewModel } from '../viewmodel/ChatViewModel';
import { MessageItem } from '../common/MessageItem';
import common from '@ohos.app.ability.common';

@Entry
@Component
struct ChatPage {
  @State private viewModel: ChatViewModel = new ChatViewModel();
  @State private inputText: string = '';
  @State private isRecording: boolean = false;
  @State private isSpeaking: boolean = false;
  @State private asrInterimText: string = '';
  @State private autoTts: boolean = true;
  @State private speakingMsgId: string = '';
  @State private messageCount: number = 0; // 辅助触发列表刷新
  private scroller: Scroller = new Scroller();

  aboutToAppear() {
    // 获取 UIAbilityContext（用于申请麦克风权限）
    const ctx = getContext(this) as common.UIAbilityContext;
    this.viewModel = new ChatViewModel(ctx);

    // 连接 TTS 播报状态回调
    this.viewModel.onSpeakingChange = (speaking: boolean) => {
      this.isSpeaking = speaking;
      if (!speaking) {
        this.speakingMsgId = '';
      }
    };

    // 连接录音状态回调
    this.viewModel.onRecordingChange = (recording: boolean) => {
      this.isRecording = recording;
      if (!recording) {
        this.asrInterimText = '';
      }
    };

    // 连接 ASR 实时识别结果回调
    this.viewModel.onInterimTextChange = (text: string) => {
      this.asrInterimText = text;
    };

    // 消息更新时自动滚动到底部
    this.viewModel.onMessagesUpdate = () => {
      this.messageCount = this.viewModel.messages.length;
      this.scrollToBottom();
    };

    this.viewModel.initialize();
  }

  aboutToDisappear() {
    this.viewModel.destroy();
  }

  sendMessage() {
    if (this.inputText.trim()) {
      this.viewModel.sendMessage(this.inputText);
      this.inputText = '';
    }
  }

  scrollToBottom() {
    setTimeout(() => {
      this.scroller.scrollEdge(Edge.Bottom);
    }, 100);
  }

  build() {
    Column() {
      this.buildHeader()
      this.buildMessageList()
      this.buildInputArea()
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FFFFFF')
  }

  /**
   * 顶部标题栏（含 TTS 自动播报开关）
   */
  @Builder
  buildHeader() {
    Column() {
      Row() {
        // 左侧：灯笼 + 标题
        Row() {
          Text('🏮')
            .fontSize(22)
            .margin({ right: 8 })
          Column() {
            Text('解忧杂货铺')
              .fontSize(18)
              .fontWeight(600)
              .fontColor('#1D1D1F')
            Text('倾听你的心声')
              .fontSize(12)
              .fontColor('#86868B')
              .margin({ top: 1 })
          }
          .alignItems(HorizontalAlign.Start)
        }
        .layoutWeight(1)
        .alignItems(VerticalAlign.Center)

        // 右侧：TTS 自动播报切换按钮
        Button() {
          Row() {
            Image(this.autoTts
              ? $r('sys.symbol.speaker_wave_2_fill')
              : $r('sys.symbol.speaker_slash_fill'))
              .width(18)
              .height(18)
              .fillColor(this.autoTts ? '#007AFF' : '#86868B')
            Text(this.autoTts ? '自动朗读' : '静音')
              .fontSize(12)
              .fontColor(this.autoTts ? '#007AFF' : '#86868B')
              .margin({ left: 4 })
          }
        }
        .height(32)
        .padding({ left: 10, right: 10 })
        .borderRadius(16)
        .backgroundColor(this.autoTts ? '#007AFF10' : '#00000008')
        .onClick(() => {
          this.viewModel.toggleAutoTts();
          this.autoTts = this.viewModel.autoTts;
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 10 })
      .alignItems(VerticalAlign.Center)

      Divider()
        .color('#E5E5EA')
        .strokeWidth(0.5)
    }
    .width('100%')
    .backgroundColor('#FFFFFF')
  }

  /**
   * 消息列表区域
   */
  @Builder
  buildMessageList() {
    Scroll(this.scroller) {
      Column() {
        // 空状态提示
        if (this.viewModel.messages.length === 0) {
          Column() {
            Text('🏮')
              .fontSize(64)
              .margin({ bottom: 16 })
            Text('解忧杂货铺')
              .fontSize(28)
              .fontWeight(600)
              .fontColor('#1D1D1F')
              .margin({ bottom: 8 })
            Text('在这里，每一份烦恼都会被温柔对待')
              .fontSize(15)
              .fontColor('#86868B')
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .padding({ top: 80, left: 32, right: 32 })
          .justifyContent(FlexAlign.Center)
        }

        // 消息气泡列表
        ForEach(this.viewModel.messages, (message: Message) => {
          MessageItem({
            message: message,
            isSpeaking: this.isSpeaking && this.speakingMsgId === message.id,
            onSpeakTap: (msg: Message) => {
              if (this.isSpeaking && this.speakingMsgId === msg.id) {
                // 再次点击同一条 → 停止
                this.viewModel.speakMessage(msg);
              } else {
                // 点击新消息 → 播放
                this.speakingMsgId = msg.id;
                this.viewModel.speakMessage(msg);
              }
            }
          })
        }, (message: Message) => message.id)

        // 底部留白
        Column()
          .height(20)
      }
      .width('100%')
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .edgeEffect(EdgeEffect.Spring)
    .width('100%')
    .layoutWeight(1)
  }

  /**
   * 底部输入区域（文字输入 + 语音按钮）
   */
  @Builder
  buildInputArea() {
    Column() {
      Divider()
        .color('#E5E5EA')
        .strokeWidth(0.5)

      // ASR 实时识别中间结果（录音中且有内容时显示）
      if (this.isRecording && this.asrInterimText) {
        Row() {
          Text(this.asrInterimText)
            .fontSize(14)
            .fontColor('#86868B')
            .fontStyle(FontStyle.Italic)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .layoutWeight(1)
        }
        .width('100%')
        .padding({ left: 20, right: 20, top: 8, bottom: 0 })
      }

      Row() {
        // 输入区主体：录音中显示状态指示器，否则显示文本框
        if (this.isRecording) {
          // 录音状态指示器
          Row() {
            // 红色指示点
            Column()
              .width(8)
              .height(8)
              .borderRadius(4)
              .backgroundColor('#FF3B30')
              .margin({ right: 8 })

            Text(this.asrInterimText.length > 0 ? this.asrInterimText : '正在聆听...')
              .fontSize(16)
              .fontColor(this.asrInterimText.length > 0 ? '#1D1D1F' : '#86868B')
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
          }
          .layoutWeight(1)
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })
          .backgroundColor('#FFF5F5')
          .borderRadius(20)
          .border({ width: 1, color: '#FF3B3030', radius: 20 })
        } else {
          // 普通文本输入框
          TextInput({ placeholder: '说说你的心事...', text: this.inputText })
            .fontSize(16)
            .fontColor('#1D1D1F')
            .placeholderColor('#86868B')
            .backgroundColor('#F2F2F7')
            .borderRadius(20)
            .padding({ left: 16, right: 16, top: 10, bottom: 10 })
            .layoutWeight(1)
            .onChange((value: string) => {
              this.inputText = value;
            })
            .onSubmit(() => {
              this.sendMessage();
            })
        }

        // 麦克风按钮（点击开始/停止录音）
        Button() {
          Image(this.isRecording
            ? $r('sys.symbol.stop_fill')
            : $r('sys.symbol.mic_fill'))
            .width(20)
            .height(20)
            .fillColor(this.isRecording ? Color.White : '#007AFF')
        }
        .width(44)
        .height(44)
        .borderRadius(22)
        .backgroundColor(this.isRecording ? '#FF3B30' : '#F2F2F7')
        .margin({ left: 8 })
        .animation({ duration: 200, curve: Curve.EaseInOut })
        .onClick(() => {
          if (this.isRecording) {
            this.viewModel.stopVoiceInput();
          } else {
            this.viewModel.startVoiceInput();
          }
        })

        // 发送按钮（仅在有文字且非录音时显示）
        if (!this.isRecording && this.inputText.trim().length > 0) {
          Button() {
            Image($r('sys.symbol.paperplane_fill'))
              .width(20)
              .height(20)
              .fillColor(Color.White)
          }
          .width(44)
          .height(44)
          .borderRadius(22)
          .backgroundColor(this.viewModel.isSending ? '#C7C7CC' : '#007AFF')
          .margin({ left: 8 })
          .enabled(!this.viewModel.isSending)
          .onClick(() => {
            this.sendMessage();
          })
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 10, bottom: 12 })
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .backgroundColor('#FFFFFF')
  }
}
