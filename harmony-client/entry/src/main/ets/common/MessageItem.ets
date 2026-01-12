/**
 * 消息气泡组件 - 极简苹果风格
 */
import { Message, MessageRole } from '../model/Message';

@Component
export struct MessageItem {
  @Prop message: Message;

  build() {
    Column() {
      if (this.message.role === MessageRole.USER) {
        // 用户消息 - 右对齐，蓝色背景
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
        // 助手消息 - 左对齐，灰色背景
        Row() {
          Column() {
            // 头像
            Text('🏮')
              .fontSize(32)
              .margin({ right: 8, top: 0 })
          }
          .alignItems(HorizontalAlign.Start)

          Text(this.message.content)
            .fontSize(16)
            .fontColor('#1D1D1F')
            .padding({ left: 16, right: 16, top: 12, bottom: 12 })
            .backgroundColor('#F2F2F7')
            .borderRadius(20)
            .maxWidth('70%')
            .textAlign(TextAlign.Start)
            .opacity(this.message.isLoading ? 0.6 : 1.0)

          Blank()
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 4, bottom: 4 })
        .alignItems(VerticalAlign.Top)

      } else {
        // 系统消息 - 居中，淡灰色
        Row() {
          Text(this.message.content)
            .fontSize(14)
            .fontColor('#86868B')
            .padding(12)
            .textAlign(TextAlign.Center)
            .maxWidth('80%')
        }
        .width('100%')
        .justifyContent(FlexAlign.Center)
        .padding({ top: 8, bottom: 8 })
      }
    }
    .width('100%')
  }
}
