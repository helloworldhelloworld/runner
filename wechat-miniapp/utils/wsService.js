/**
 * WebSocket 封装
 * 支持自动重连（指数退避，最多 5 次）和消息队列
 */
const { WS_URL } = require('./config');

const MAX_RETRIES = 5;
const BASE_DELAY = 1000; // ms

let _socket = null;
let _connected = false;
let _userId = '';
let _sessionId = '';
let _retryCount = 0;
let _retryTimer = null;

// 消息队列：连接前入队，连接后批量发送
const _queue = [];

// 回调注册
let _onToken = null;
let _onStreamEnd = null;
let _onCrisisAlert = null;
let _onError = null;
let _onConnect = null;

function connect(userId, sessionId) {
  _userId = userId;
  _sessionId = sessionId;

  if (_socket) {
    try { _socket.close(); } catch (_) {}
    _socket = null;
  }

  _socket = wx.connectSocket({
    url: WS_URL,
    success() {},
    fail(err) {
      console.error('[WS] connectSocket failed', err);
      _scheduleRetry();
    }
  });

  _socket.onOpen(() => {
    console.log('[WS] connected');
    _connected = true;
    _retryCount = 0;
    if (_onConnect) _onConnect();
    // Flush queue
    while (_queue.length > 0) {
      const msg = _queue.shift();
      _socket.send({ data: msg });
    }
  });

  _socket.onMessage((res) => {
    try {
      const msg = JSON.parse(res.data);
      switch (msg.type) {
        case 'token':
          if (_onToken) _onToken(msg.data || '');
          break;
        case 'stream_end':
          if (_onStreamEnd) _onStreamEnd(msg.meta || {});
          break;
        case 'crisis_alert':
          if (_onCrisisAlert) _onCrisisAlert(msg.resources || []);
          break;
        case 'error':
          if (_onError) _onError(msg.message || '服务器错误');
          break;
      }
    } catch (e) {
      console.error('[WS] parse error', e);
    }
  });

  _socket.onError((err) => {
    console.error('[WS] error', err);
    _connected = false;
    if (_onError) _onError('连接出错');
    _scheduleRetry();
  });

  _socket.onClose(() => {
    console.log('[WS] closed');
    _connected = false;
    _scheduleRetry();
  });
}

function _scheduleRetry() {
  if (_retryCount >= MAX_RETRIES) {
    console.warn('[WS] max retries reached');
    return;
  }
  const delay = BASE_DELAY * Math.pow(2, _retryCount);
  _retryCount++;
  console.log(`[WS] retry #${_retryCount} in ${delay}ms`);
  clearTimeout(_retryTimer);
  _retryTimer = setTimeout(() => {
    connect(_userId, _sessionId);
  }, delay);
}

function sendMessage(message) {
  const payload = JSON.stringify({
    type: 'chat',
    sessionId: _sessionId,
    userId: _userId,
    message
  });

  if (_connected && _socket) {
    _socket.send({ data: payload });
  } else {
    _queue.push(payload);
    if (!_socket) {
      connect(_userId, _sessionId);
    }
  }
}

function disconnect() {
  clearTimeout(_retryTimer);
  _retryCount = MAX_RETRIES; // Prevent auto-reconnect
  if (_socket) {
    try { _socket.close(); } catch (_) {}
    _socket = null;
  }
  _connected = false;
}

function isConnected() {
  return _connected;
}

function onToken(cb) { _onToken = cb; }
function onStreamEnd(cb) { _onStreamEnd = cb; }
function onCrisisAlert(cb) { _onCrisisAlert = cb; }
function onError(cb) { _onError = cb; }
function onConnect(cb) { _onConnect = cb; }

module.exports = {
  connect,
  disconnect,
  sendMessage,
  isConnected,
  onToken,
  onStreamEnd,
  onCrisisAlert,
  onError,
  onConnect
};
