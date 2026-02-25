Page({
  data: {
    scaleType: '',
    score: 0,
    severity: '',
    aiReport: '',
    severityColor: '#5B8AF0'
  },

  onLoad(query) {
    const score = parseInt(query.score || '0', 10);
    const severity = decodeURIComponent(query.severity || '');
    const aiReport = decodeURIComponent(query.aiReport || '');
    const scaleType = query.scaleType || '';

    this.setData({
      scaleType,
      score,
      severity,
      aiReport,
      severityColor: this._severityColor(severity)
    });
  },

  _severityColor(severity) {
    if (!severity) return '#5B8AF0';
    if (severity.includes('无') || severity.includes('低')) return '#A8D5BA';
    if (severity.includes('轻')) return '#FFD166';
    if (severity.includes('中')) return '#F4A261';
    if (severity.includes('重') || severity.includes('高')) return '#FF6B6B';
    return '#5B8AF0';
  },

  goBack() {
    wx.navigateBack({ delta: 2 });
  },

  goChat() {
    wx.switchTab({ url: '/pages/chat/chat' });
  }
});
