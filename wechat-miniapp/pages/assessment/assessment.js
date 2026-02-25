const { getScales } = require('../../utils/userService');

Page({
  data: {
    scales: [],
    loading: true
  },

  onLoad() {
    this._loadScales();
  },

  _loadScales() {
    getScales().then((scalesMap) => {
      const scales = Object.entries(scalesMap).map(([key, def]) => ({
        key,
        title: def.title,
        description: def.description,
        questionCount: def.questions ? def.questions.length : 0
      }));
      this.setData({ scales, loading: false });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败，请检查网络', icon: 'none' });
    });
  },

  startQuiz(e) {
    const scaleType = e.currentTarget.dataset.type;
    wx.navigateTo({
      url: `/pages/assessment-quiz/assessment-quiz?scaleType=${scaleType}`
    });
  }
});
