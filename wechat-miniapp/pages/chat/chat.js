const ws = require('../../utils/wsService');

let msgIdCounter = 0;
function nextId() {
  return 'msg-' + (++msgIdCounter);
}

Page({
  data: {
    messages: [],
    inputText: '',
    isSending: false,
    isConnected: false,
    scrollId: ''
  },

  _sessionId: '',
  _userId: '',

  onLoad() {
    const app = getApp();
    this._sessionId = app.globalData.sessionId;
    this._userId = app.globalData.userId || '';

    // Register WebSocket callbacks
    ws.onConnect(() => {
      this.setData({ isConnected: true });
      if (this.data.messages.length === 0) {
        this._addWelcome();
      }
    });

    ws.onToken((delta) => {
      const messages = this.data.messages.slice();
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = Object.assign({}, last, {
          content: last.content + delta,
          isLoading: false
        });
        this.setData({ messages, scrollId: last.id });
      }
    });

    ws.onStreamEnd((meta) => {
      const messages = this.data.messages.slice();
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant') {
        messages[messages.length - 1] = Object.assign({}, last, {
          emotion: (meta && meta.emotion) || '',
          isLoading: false
        });
        this.setData({ messages, isSending: false });
      } else {
        this.setData({ isSending: false });
      }
    });

    ws.onCrisisAlert((resources) => {
      this.setData({ isSending: false });
      // Remove the loading bubble
      const messages = this.data.messages.slice();
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant' && last.isLoading) {
        messages.pop();
        this.setData({ messages });
      }
      this._showCrisisAlert(resources);
    });

    ws.onError((msg) => {
      this.setData({ isSending: false });
      const messages = this.data.messages.slice();
      const last = messages[messages.length - 1];
      if (last && last.role === 'assistant' && last.isLoading) {
        messages[messages.length - 1] = Object.assign({}, last, {
          content: '连接出了点问题，请稍后再试。',
          isLoading: false
        });
        this.setData({ messages });
      }
    });

    // Connect (auto-retries if server not ready)
    ws.connect(this._userId, this._sessionId);
  },

  onUnload() {
    ws.disconnect();
  },

  _addWelcome() {
    const id = nextId();
    this.setData({
      messages: [{
        id,
        role: 'assistant',
        content: '你好，我是心灵港湾 ⚓。无论你有什么心事，都可以跟我说说。',
        emotion: '',
        isLoading: false
      }],
      scrollId: id
    });
  },

  _addUserMessage(text) {
    const id = nextId();
    const messages = this.data.messages.concat({
      id,
      role: 'user',
      content: text,
      emotion: '',
      isLoading: false
    });
    this.setData({ messages, scrollId: id });
    return id;
  },

  _addLoadingMessage() {
    const id = nextId();
    const messages = this.data.messages.concat({
      id,
      role: 'assistant',
      content: '',
      emotion: '',
      isLoading: true
    });
    this.setData({ messages, scrollId: id });
    return id;
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value });
  },

  sendMessage() {
    const text = this.data.inputText.trim();
    if (!text || this.data.isSending) return;

    this.setData({ inputText: '', isSending: true });
    this._addUserMessage(text);
    this._addLoadingMessage();

    ws.sendMessage(text);
  },

  _showCrisisAlert(resources) {
    const lines = (resources || []).map(r => `${r.name}: ${r.phone}`).join('\n');
    wx.showModal({
      title: '❤️ 我注意到你可能很难受',
      content: '你的生命非常宝贵。以下专业热线可以帮助你：\n\n' + lines,
      confirmText: '好的，谢谢',
      showCancel: false,
      confirmColor: '#5B8AF0'
    });
  }
});
