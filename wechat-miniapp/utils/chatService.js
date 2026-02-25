const { BASE_URL, CLIENT_TYPE } = require('./config');

/**
 * 将 ArrayBuffer 解码为 UTF-8 字符串
 */
function arrayBufferToString(buf) {
  // TextDecoder 正确处理 UTF-8 多字节序列（中文等）
  // 微信基础库 >= 2.19.0 支持，覆盖绝大多数在用设备
  try {
    return new TextDecoder('utf-8').decode(buf);
  } catch (e) {
    // 极老设备回退：仅 ASCII 可读
    const uint8 = new Uint8Array(buf);
    let str = '';
    for (let i = 0; i < uint8.length; i++) {
      str += String.fromCharCode(uint8[i]);
    }
    return str;
  }
}

/**
 * 健康检查
 * @returns {Promise<boolean>}
 */
function healthCheck() {
  return new Promise((resolve) => {
    wx.request({
      url: BASE_URL + '/gateway/health',
      method: 'GET',
      success(res) {
        resolve(res.statusCode === 200);
      },
      fail() {
        resolve(false);
      }
    });
  });
}

/**
 * 流式发送消息
 * @param {string} message 用户消息
 * @param {string} sessionId 会话 ID
 * @param {{ onDelta, onComplete, onError }} callbacks
 * @returns {object} wx.request 任务，可调用 abort()
 */
function sendStream(message, sessionId, { onDelta, onComplete, onError }) {
  const body = {
    message,
    session_id: sessionId,
    client_type: CLIENT_TYPE
  };

  let buffer = '';

  const task = wx.request({
    url: BASE_URL + '/gateway/chat/stream',
    method: 'POST',
    header: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    data: JSON.stringify(body),
    enableChunked: true,
    responseType: 'arraybuffer',
    success(res) {
      // 非流式兜底：处理完整响应
      if (res.statusCode !== 200) {
        onError && onError(new Error('HTTP ' + res.statusCode));
      }
    },
    fail(err) {
      onError && onError(err);
    }
  });

  task.onChunkReceived((res) => {
    const chunk = arrayBufferToString(res.data);
    buffer += chunk;

    // 按行解析 SSE
    const lines = buffer.split('\n');
    // 保留最后一行（可能不完整）
    buffer = lines.pop();

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('data:')) continue;

      const jsonStr = trimmed.slice(5).trim();
      if (!jsonStr || jsonStr === '[DONE]') continue;

      try {
        const event = JSON.parse(jsonStr);
        if (event.type === 'DELTA') {
          const emotion = (event.metadata && event.metadata.emotion) || '';
          onDelta && onDelta(event.content || '', emotion);
        } else if (event.type === 'COMPLETE') {
          onComplete && onComplete(event.content || '', event.session_id || sessionId);
        } else if (event.type === 'ERROR') {
          onError && onError(new Error(event.content || '服务器错误'));
        }
      } catch (e) {
        // 忽略解析失败的行
      }
    }
  });

  return task;
}

module.exports = { healthCheck, sendStream };
