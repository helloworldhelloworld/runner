const { getScale, submitAssessment } = require('../../utils/userService');

Page({
  data: {
    scaleType: '',
    title: '',
    questions: [],
    options: [],
    currentIndex: 0,
    answers: [],
    isSubmitting: false,
    progress: 0
  },

  _userId: '',

  onLoad(query) {
    const app = getApp();
    this._userId = app.globalData.userId || wx.getStorageSync('userId') || '';
    const scaleType = query.scaleType || 'PHQ9';
    this.setData({ scaleType });
    this._loadScale(scaleType);
  },

  _loadScale(scaleType) {
    getScale(scaleType).then((def) => {
      const answers = new Array(def.questions.length).fill(-1);
      this.setData({
        title: def.title,
        questions: def.questions,
        options: def.options,
        answers,
        progress: 0
      });
    }).catch(() => {
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  selectAnswer(e) {
    const optionIndex = e.currentTarget.dataset.index;
    const { currentIndex, answers, questions } = this.data;
    const newAnswers = answers.slice();
    newAnswers[currentIndex] = optionIndex;

    const progress = Math.round(((currentIndex + 1) / questions.length) * 100);

    this.setData({ answers: newAnswers, progress });

    // Auto advance after short delay
    setTimeout(() => {
      if (currentIndex < questions.length - 1) {
        this.setData({ currentIndex: currentIndex + 1 });
      } else {
        this._submit(newAnswers);
      }
    }, 300);
  },

  prevQuestion() {
    const { currentIndex } = this.data;
    if (currentIndex > 0) {
      this.setData({ currentIndex: currentIndex - 1 });
    }
  },

  _submit(answers) {
    if (this.data.isSubmitting) return;
    const unanswered = answers.indexOf(-1);
    if (unanswered >= 0) {
      wx.showToast({ title: `第 ${unanswered + 1} 题还没作答`, icon: 'none' });
      this.setData({ currentIndex: unanswered });
      return;
    }

    this.setData({ isSubmitting: true });
    submitAssessment(this._userId, this.data.scaleType, answers).then((result) => {
      this.setData({ isSubmitting: false });
      wx.redirectTo({
        url: `/pages/assessment-result/assessment-result?resultId=${result.id}&score=${result.totalScore}&severity=${encodeURIComponent(result.severity)}&scaleType=${this.data.scaleType}&aiReport=${encodeURIComponent(result.aiReport || '')}`
      });
    }).catch(() => {
      this.setData({ isSubmitting: false });
      wx.showToast({ title: '提交失败，请重试', icon: 'none' });
    });
  }
});
