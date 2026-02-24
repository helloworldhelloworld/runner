/**
 * 消息气泡组件 - 极简苹果风格
 * 助手消息右下角带 TTS 播放按钮
 */
import { Message, MessageRole } from '../model/Message';

// 情绪 → 气泡颜色微调
function emotionBorderColor(emotion?: string): string {
  switch (emotion) {
    case '积极': case '开心': return '#34C75920'; // 浅绿
    case '担忧': case '悲伤':  return '#FF9F0A20'; // 浅橙
    default:                   return 'transparent';
  }
}

@Component
export struct MessageItem {
  @Prop message: Message;
  onSpeakTap: (message: Message) => void = () => {};
  isSpeaking: boolean = false;

  build() {
    Column() {
      if (this.message.role === MessageRole.USER) {
        // 用户消息 - 右对齐，蓝色气泡
        Row() {
          Blank()
          Text(this.message.content)
            .fontSize(16)
            .fontColor(Color.White)
            .padding({ left: 16, right: 16, top: 12, bottom: 12 })
            .backgroundColor('#007AFF')
            .borderRadius(20)
            .maxWidth('75%')
            .textAlign(TextAlign.Start)
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 4, bottom: 4 })

      } else if (this.message.role === MessageRole.ASSISTANT) {
        // 助手消息 - 左对齐，灰色气泡 + TTS 按钮
        Row() {
          // 灯笼头像
          Text('🏮')
            .fontSize(30)
            .margin({ right: 8, top: 2 })

          Column() {
            // 消息气泡
            Text(this.message.isLoading && !this.message.content
              ? '...'
              : this.message.content)
              .fontSize(16)
              .fontColor('#1D1D1F')
              .padding({ left: 16, right: 16, top: 12, bottom: 12 })
              .backgroundColor('#F2F2F7')
              .borderRadius(20)
              .maxWidth('100%')
              .textAlign(TextAlign.Start)
              .opacity(this.message.isLoading && !this.message.content ? 0.5 : 1.0)
              .border({
                width: 1.5,
                color: emotionBorderColor(this.message.emotion),
                radius: 20
              })

            // TTS 按钮（仅在有内容时显示）
            if (this.message.content && !this.message.isLoading) {
              Row() {
                Image(this.isSpeaking
                  ? $r('sys.symbol.speaker_wave_3_fill')
                  : $r('sys.symbol.speaker_fill'))
                  .width(14)
                  .height(14)
                  .fillColor(this.isSpeaking ? '#007AFF' : '#86868B')
                  .animation({ duration: 300, curve: Curve.EaseInOut })

                Text(this.isSpeaking ? '播放中' : '朗读')
                  .fontSize(12)
                  .fontColor(this.isSpeaking ? '#007AFF' : '#86868B')
                  .margin({ left: 4 })
              }
              .margin({ top: 4, left: 4 })
              .padding({ left: 10, right: 10, top: 4, bottom: 4 })
              .borderRadius(12)
              .backgroundColor(this.isSpeaking ? '#007AFF10' : '#00000008')
              .onClick(() => {
                this.onSpeakTap(this.message);
              })
            }
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)

          Blank()
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 4, bottom: 4 })
        .alignItems(VerticalAlign.Top)

      } else {
        // 系统消息 - 居中，淡灰色
        Row() {
          Text(this.message.content)
            .fontSize(13)
            .fontColor('#86868B')
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .textAlign(TextAlign.Center)
            .maxWidth('80%')
            .backgroundColor('#00000008')
            .borderRadius(10)
        }
        .width('100%')
        .justifyContent(FlexAlign.Center)
        .padding({ top: 8, bottom: 8 })
      }
    }
    .width('100%')
  }
}
