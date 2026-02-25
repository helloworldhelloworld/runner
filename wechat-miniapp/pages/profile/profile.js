const { getEmotions, getStats, getAssessmentHistory } = require('../../utils/userService');

Page({
  data: {
    userId: '',
    streak: 0,
    totalDays: 0,
    checkedInToday: false,
    distribution: {},
    distributionList: [],
    emotions7: [],
    assessmentHistory: [],
    canvasWidth: 0,
    canvasHeight: 140
  },

  _chartData: [],

  onLoad() {
    const app = getApp();
    const userId = app.globalData.userId || wx.getStorageSync('userId') || '';
    this.setData({ userId });

    // Get canvas width
    const info = wx.getSystemInfoSync();
    const canvasWidth = info.windowWidth - 48; // 24rpx padding each side (px)
    this.setData({ canvasWidth });

    this._loadData();
  },

  onShow() {
    this._loadData();
  },

  _loadData() {
    const { userId } = this.data;
    if (!userId) return;

    Promise.all([
      getStats(userId),
      getEmotions(userId, 7),
      getAssessmentHistory(userId)
    ]).then(([stats, emotions, history]) => {
      const distributionList = Object.entries(stats.distribution || {})
        .sort((a, b) => b[1] - a[1])
        .map(([emotion, count]) => ({ emotion, count }));

      this._chartData = emotions.slice().reverse(); // oldest first for chart

      this.setData({
        streak: stats.streak || 0,
        totalDays: stats.totalDays || 0,
        checkedInToday: stats.checkedInToday || false,
        distribution: stats.distribution || {},
        distributionList,
        emotions7: emotions,
        assessmentHistory: history.slice(0, 3)
      });

      this._drawChart();
    }).catch(() => {});
  },

  _drawChart() {
    const data = this._chartData;
    if (data.length === 0) return;

    const { canvasWidth, canvasHeight } = this.data;
    const ctx = wx.createCanvasContext('emotionChart', this);

    const padL = 0, padR = 0, padT = 16, padB = 20;
    const chartW = canvasWidth - padL - padR;
    const chartH = canvasHeight - padT - padB;

    // Map emotion to numeric score for line chart
    const emotionScore = {
      '😊': 5, '😌': 4, '😰': 2, '😢': 1, '😡': 2
    };

    const scores = data.map(r => {
      const emoji = (r.emotion || '').charAt(0);
      return emotionScore[emoji] || 3;
    });

    const maxScore = 5, minScore = 1;
    const n = scores.length;
    const xStep = n > 1 ? chartW / (n - 1) : chartW;

    // Draw grid lines
    ctx.setStrokeStyle('#F0F0F0');
    ctx.setLineWidth(1);
    for (let i = 1; i <= 4; i++) {
      const y = padT + (i / 4) * chartH;
      ctx.moveTo(padL, y);
      ctx.lineTo(padL + chartW, y);
      ctx.stroke();
    }

    // Draw line
    ctx.beginPath();
    ctx.setStrokeStyle('#5B8AF0');
    ctx.setLineWidth(3);
    ctx.setLineCap('round');
    ctx.setLineJoin('round');

    scores.forEach((s, i) => {
      const x = padL + i * xStep;
      const y = padT + ((maxScore - s) / (maxScore - minScore)) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Draw dots
    scores.forEach((s, i) => {
      const x = padL + i * xStep;
      const y = padT + ((maxScore - s) / (maxScore - minScore)) * chartH;
      ctx.beginPath();
      ctx.arc(x, y, 5, 0, Math.PI * 2);
      ctx.setFillStyle('#5B8AF0');
      ctx.fill();
    });

    // Date labels (first and last)
    if (data.length > 0) {
      ctx.setFontSize(10);
      ctx.setFillStyle('#C7C7CC');
      const fmt = (ts) => {
        const d = new Date(ts);
        return `${d.getMonth() + 1}/${d.getDate()}`;
      };
      ctx.fillText(fmt(data[0].recordedAt), padL, canvasHeight);
      if (data.length > 1) {
        const lastLabel = fmt(data[data.length - 1].recordedAt);
        ctx.fillText(lastLabel, padL + chartW - 24, canvasHeight);
      }
    }

    ctx.draw();
  }
});
