/**
 * 客户端工具注册中心
 * Tool 定义：{ id, name, description, inputSchema, handler }
 */

const _tools = new Map();

function registerTool(toolDef) {
  _validateToolDefinition(toolDef);

  const normalized = {
    ...toolDef,
    inputSchema: toolDef.inputSchema || { type: 'object' }
  };

  _tools.set(normalized.id, normalized);
  return normalized;
}

function getTool(toolId) {
  return _tools.get(toolId) || null;
}

function listTools() {
  return Array.from(_tools.values()).map((tool) => ({
    id: tool.id,
    name: tool.name,
    description: tool.description,
    inputSchema: tool.inputSchema
  }));
}

async function invokeTool(toolId, args) {
  const tool = getTool(toolId);
  if (!tool) {
    return _buildErrorResult('TOOL_NOT_FOUND', `未找到工具: ${toolId}`);
  }

  try {
    _validateInput(args, tool.inputSchema);
  } catch (error) {
    return _buildErrorResult('INVALID_ARGUMENTS', error.message);
  }

  try {
    const data = await Promise.resolve(tool.handler(args || {}));
    return {
      ok: true,
      data,
      error: null
    };
  } catch (error) {
    console.error('[ClientTool] invoke error', toolId, error);
    return _buildErrorResult('TOOL_EXECUTION_ERROR', error && error.message ? error.message : '工具执行失败');
  }
}

function _validateToolDefinition(toolDef) {
  if (!toolDef || typeof toolDef !== 'object') {
    throw new Error('toolDef 必须是对象');
  }
  if (!toolDef.id || typeof toolDef.id !== 'string') {
    throw new Error('toolDef.id 必须是非空字符串');
  }
  if (!toolDef.name || typeof toolDef.name !== 'string') {
    throw new Error('toolDef.name 必须是非空字符串');
  }
  if (!toolDef.description || typeof toolDef.description !== 'string') {
    throw new Error('toolDef.description 必须是非空字符串');
  }
  if (toolDef.inputSchema && typeof toolDef.inputSchema !== 'object') {
    throw new Error('toolDef.inputSchema 必须是对象');
  }
  if (typeof toolDef.handler !== 'function') {
    throw new Error('toolDef.handler 必须是函数');
  }
}

function _validateInput(args, schema = {}) {
  if (schema && schema.type === 'object' && (args == null || Array.isArray(args) || typeof args !== 'object')) {
    throw new Error('参数必须是对象');
  }

  const required = Array.isArray(schema.required) ? schema.required : [];
  required.forEach((field) => {
    if (args == null || !(field in args)) {
      throw new Error(`缺少必填参数: ${field}`);
    }
  });

  const properties = schema.properties || {};
  Object.keys(properties).forEach((key) => {
    if (args == null || !(key in args)) return;

    const expectedType = properties[key] && properties[key].type;
    if (!expectedType) return;

    const value = args[key];
    const actualType = Array.isArray(value) ? 'array' : typeof value;

    if (value === null) {
      throw new Error(`参数 ${key} 不允许为 null`);
    }

    if (expectedType === 'integer') {
      if (!(typeof value === 'number' && Number.isInteger(value))) {
        throw new Error(`参数 ${key} 类型错误，期望 integer`);
      }
      return;
    }

    if (actualType !== expectedType) {
      throw new Error(`参数 ${key} 类型错误，期望 ${expectedType}`);
    }
  });
}

function _buildErrorResult(code, message) {
  return {
    ok: false,
    data: null,
    error: {
      code,
      message
    }
  };
}

module.exports = {
  registerTool,
  getTool,
  listTools,
  invokeTool
};
