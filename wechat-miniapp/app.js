const { createAnonymousUser } = require('./utils/userService');

App({
  globalData: {
    sessionId: '',
    userId: ''
  },

  onLaunch() {
    // 从本地存储恢复 sessionId，否则生成新的
    let sessionId = wx.getStorageSync('sessionId');
    if (!sessionId) {
      sessionId = 'wechat-' + Date.now().toString(36);
      wx.setStorageSync('sessionId', sessionId);
    }
    this.globalData.sessionId = sessionId;

    // 初始化匿名 userId
    let userId = wx.getStorageSync('userId');
    if (!userId) {
      createAnonymousUser().then((user) => {
        userId = user.id;
        wx.setStorageSync('userId', userId);
        this.globalData.userId = userId;
      }).catch(() => {
        // 离线时用本地生成的 ID
        userId = 'local-' + Date.now().toString(36);
        wx.setStorageSync('userId', userId);
        this.globalData.userId = userId;
      });
    } else {
      this.globalData.userId = userId;
    }
  }
});
