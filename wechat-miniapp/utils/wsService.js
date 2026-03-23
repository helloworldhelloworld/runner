/**
 * WebSocket 封装
 * 支持自动重连（指数退避，最多 5 次）和消息队列
 */
const { WS_URL } = require('./config');
const clientToolRegistry = require('./clientToolRegistry');

// 注册内置客户端工具（仅需在这里加载一次，新增工具无需改 wsService 核心分发）
require('../client-tools');

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
        case 'directive':
        case 'DIRECTIVE':
          _handleDirectiveCall(msg);
          break;
        case 'tool_call':
        case 'client_tool_call':
          _handleToolCall(msg);
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

async function _handleDirectiveCall(msg) {
  const data = msg.data || {};
  const directiveId = data.directive_id || data.directiveId || '';
  const primary = Array.isArray(data.directives) ? data.directives[0] : null;

  if (!directiveId || !primary || !primary.header) {
    console.warn('[WS] invalid directive payload', msg);
    return;
  }

  const toolId = `${primary.header.namespace}.${primary.header.name}`;
  const args = primary.payload || {};

  const result = await clientToolRegistry.invokeTool(toolId, args);
  const response = {
    type: 'directive_result',
    directiveId,
    success: result.ok,
    content: result.ok ? JSON.stringify(result.data || {}) : (result.error && result.error.message) || '工具执行失败',
    metadata: {
      toolId,
      code: result.error ? result.error.code : null
    }
  };

  _sendRaw(response);
}

async function _handleToolCall(msg) {
  const data = msg.data || {};
  const callId = msg.callId || data.callId || '';
  const toolId = msg.toolId || msg.tool || data.toolId || data.tool || data.name || '';
  const args = msg.args || data.args || data.input || {};

  if (!callId || !toolId) {
    console.warn('[WS] invalid tool_call payload', msg);
    return;
  }

  const result = await clientToolRegistry.invokeTool(toolId, args);
  const response = {
    type: 'client_tool_result',
    callId,
    isError: !result.ok,
    content: result.ok ? JSON.stringify(result.data || {}) : (result.error && result.error.message) || '工具执行失败',
    meta: {
      toolId,
      code: result.error ? result.error.code : null
    }
  };

  _sendRaw(response);
}

function _sendRaw(obj) {
  const payload = JSON.stringify(obj);
  if (_connected && _socket) {
    _socket.send({ data: payload });
    return;
  }
  _queue.push(payload);
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
