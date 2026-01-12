/**
 * 解忧杂货铺 - 聊天主页面
 * 极简苹果风格设计
 */
import { Message, MessageRole } from '../model/Message';
import { ChatViewModel } from '../viewmodel/ChatViewModel';
import { MessageItem } from '../common/MessageItem';

@Entry
@Component
struct ChatPage {
  @State viewModel: ChatViewModel = new ChatViewModel();
  @State inputText: string = '';
  private scroller: Scroller = new Scroller();

  aboutToAppear() {
    // 页面加载时初始化
    this.viewModel.initialize();
  }

  /**
   * 发送消息
   */
  sendMessage() {
    if (this.inputText.trim()) {
      this.viewModel.sendMessage(this.inputText);
      this.inputText = '';

      // 滚动到底部
      setTimeout(() => {
        this.scroller.scrollEdge(Edge.Bottom);
      }, 100);
    }
  }

  build() {
    Column() {
      // 顶部标题栏
      this.buildHeader()

      // 消息列表区域
      this.buildMessageList()

      // 底部输入区域
      this.buildInputArea()
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FFFFFF')
  }

  /**
   * 顶部标题栏
   */
  @Builder
  buildHeader() {
    Column() {
      // 标题
      Text('解忧杂货铺')
        .fontSize(20)
        .fontWeight(600)
        .fontColor('#1D1D1F')
        .margin({ top: 12, bottom: 4 })

      // 副标题
      Text('倾听你的心声')
        .fontSize(13)
        .fontColor('#86868B')
        .margin({ bottom: 12 })

      // 分割线
      Divider()
        .color('#E5E5EA')
        .strokeWidth(0.5)
    }
    .width('100%')
    .backgroundColor('#FFFFFF')
  }

  /**
   * 消息列表
   */
  @Builder
  buildMessageList() {
    Scroll(this.scroller) {
      Column() {
        // 顶部留白 - 空状态提示
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
              .margin({ bottom: 32 })
          }
          .width('100%')
          .padding({ top: 80, left: 32, right: 32 })
          .justifyContent(FlexAlign.Center)
        }

        // 消息列表
        ForEach(this.viewModel.messages, (message: Message) => {
          MessageItem({ message: message })
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
   * 底部输入区域
   */
  @Builder
  buildInputArea() {
    Column() {
      // 分割线
      Divider()
        .color('#E5E5EA')
        .strokeWidth(0.5)

      // 输入框和发送按钮
      Row() {
        // 输入框
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

        // 发送按钮
        Button() {
          Image($r('sys.symbol.paperplane_fill'))
            .width(24)
            .height(24)
            .fillColor(Color.White)
        }
        .width(44)
        .height(44)
        .borderRadius(22)
        .backgroundColor(this.inputText.trim() ? '#007AFF' : '#C7C7CC')
        .margin({ left: 8 })
        .enabled(this.inputText.trim().length > 0 && !this.viewModel.isSending)
        .onClick(() => {
          this.sendMessage();
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
    }
    .width('100%')
    .backgroundColor('#FFFFFF')
  }
}
