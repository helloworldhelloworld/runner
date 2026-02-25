const { checkin, getStats } = require('../../utils/userService');

const EMOTIONS = [
  { label: '开心', emoji: '😊' },
  { label: '平静', emoji: '😌' },
  { label: '焦虑', emoji: '😰' },
  { label: '难过', emoji: '😢' },
  { label: '愤怒', emoji: '😡' }
];

Page({
  data: {
    emotions: EMOTIONS,
    selectedIndex: -1,
    note: '',
    isSubmitting: false,
    checkedInToday: false,
    streak: 0,
    todayEmotion: ''
  },

  _userId: '',

  onLoad() {
    const app = getApp();
    this._userId = app.globalData.userId || wx.getStorageSync('userId') || '';
    this.setData({ emotions: EMOTIONS });
    this._loadStats();
  },

  onShow() {
    this._loadStats();
  },

  _loadStats() {
    if (!this._userId) return;
    getStats(this._userId).then((stats) => {
      this.setData({
        checkedInToday: stats.checkedInToday || false,
        streak: stats.streak || 0
      });
    }).catch(() => {});
  },

  selectEmotion(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ selectedIndex: index });
  },

  onNoteInput(e) {
    this.setData({ note: e.detail.value });
  },

  submit() {
    const { selectedIndex, note, checkedInToday, isSubmitting } = this.data;
    if (selectedIndex < 0) {
      wx.showToast({ title: '请先选择今天的心情', icon: 'none' });
      return;
    }
    if (checkedInToday) {
      wx.showToast({ title: '今天已经打卡啦 😊', icon: 'none' });
      return;
    }
    if (isSubmitting) return;

    this.setData({ isSubmitting: true });
    const emotion = EMOTIONS[selectedIndex];

    checkin(this._userId, emotion.emoji + ' ' + emotion.label, note).then(() => {
      this.setData({
        isSubmitting: false,
        checkedInToday: true,
        streak: this.data.streak + 1,
        todayEmotion: emotion.emoji
      });
      wx.showToast({ title: '打卡成功 🌈', icon: 'success' });
    }).catch((err) => {
      this.setData({ isSubmitting: false });
      wx.showToast({ title: '打卡失败，请稍后再试', icon: 'none' });
    });
  }
});
