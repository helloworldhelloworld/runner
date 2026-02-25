/**
 * 用户 / 打卡 / 测评 REST API 封装
 */
const { BASE_URL } = require('./config');

function request(method, path, data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header: { 'Content-Type': 'application/json' },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          reject(new Error('HTTP ' + res.statusCode));
        }
      },
      fail(err) { reject(err); }
    });
  });
}

/** 创建匿名用户，返回 { id, nickname, memberLevel } */
function createAnonymousUser() {
  return request('POST', '/user/anonymous', {});
}

/** 情绪打卡 */
function checkin(userId, emotion, note) {
  return request('POST', '/user/checkin', { userId, emotion, note: note || '' });
}

/** 获取近 N 天情绪记录 */
function getEmotions(userId, days = 7) {
  return request('GET', `/user/${userId}/emotions?days=${days}`, null);
}

/** 获取统计（streak、distribution） */
function getStats(userId) {
  return request('GET', `/user/${userId}/stats`, null);
}

/** 获取所有量表定义 */
function getScales() {
  return request('GET', '/assessment/scales', null);
}

/** 获取单个量表 */
function getScale(type) {
  return request('GET', `/assessment/scales/${type}`, null);
}

/** 提交答卷 */
function submitAssessment(userId, scaleType, answers) {
  return request('POST', '/assessment/submit', { userId, scaleType, answers });
}

/** 获取测评历史 */
function getAssessmentHistory(userId) {
  return request('GET', `/assessment/${userId}/history`, null);
}

module.exports = {
  createAnonymousUser,
  checkin,
  getEmotions,
  getStats,
  getScales,
  getScale,
  submitAssessment,
  getAssessmentHistory
};
