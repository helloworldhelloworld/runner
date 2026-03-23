function normalizeResult(result) {
  if (!result || typeof result !== 'object') {
    return {
      ok: false,
      data: null,
      error: {
        code: 'INVALID_RESULT',
        message: '工具返回结果不符合约定。',
        detail: result || null
      }
    };
  }

  return {
    ok: Boolean(result.ok),
    data: Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : null,
    error: result.error || null
  };
}

const TOOL_DEFINITIONS = [
  {
    id: 'device.getInfo',
    metadata: {
      name: '获取设备信息',
      description: '模拟读取设备型号、系统版本、语言等信息。',
      owner: 'team-miniapp',
      version: '1.0.0',
      inputSchema: {
        type: 'object',
        properties: {
          scene: { type: 'string', description: '调用场景，比如 chat / profile' }
        },
        required: []
      }
    },
    defaultInput: {
      scene: 'chat'
    },
    run(input, context) {
      return {
        ok: true,
        data: {
          platform: 'ios',
          brand: 'Apple',
          model: 'iPhone 15 Pro (Mock)',
          language: 'zh_CN',
          scene: input.scene || 'unknown',
          userId: context.userId
        },
        error: null
      };
    }
  },
  {
    id: 'client.openSetting',
    metadata: {
      name: '打开设置页',
      description: '模拟打开客户端设置页面。',
      owner: 'team-miniapp',
      version: '1.1.0',
      inputSchema: {
        type: 'object',
        properties: {
          section: { type: 'string', description: '指定默认打开的设置分组' }
        },
        required: []
      }
    },
    defaultInput: {
      section: 'general'
    },
    run(input) {
      return {
        ok: true,
        data: {
          opened: true,
          page: 'setting',
          section: input.section || 'general',
          tips: '这是 mock 结果，未真实调用客户端设置。'
        },
        error: null
      };
    }
  },
  {
    id: 'device.scanCode',
    metadata: {
      name: '扫码工具',
      description: '模拟扫码并返回 code 与 type。',
      owner: 'team-growth',
      version: '2.0.0',
      inputSchema: {
        type: 'object',
        properties: {
          onlyFromCamera: { type: 'boolean', description: '是否仅允许相机扫码' }
        },
        required: []
      }
    },
    defaultInput: {
      onlyFromCamera: false
    },
    run(input) {
      if (input.onlyFromCamera && typeof input.onlyFromCamera !== 'boolean') {
        return {
          ok: false,
          data: null,
          error: {
            code: 'INVALID_INPUT',
            message: 'onlyFromCamera 必须是布尔值。'
          }
        };
      }

      return {
        ok: true,
        data: {
          code: 'mock-qrcode-content',
          scanType: 'QR_CODE',
          charSet: 'utf8',
          onlyFromCamera: Boolean(input.onlyFromCamera)
        },
        error: null
      };
    }
  }
];

function listTools() {
  return TOOL_DEFINITIONS.map((tool) => ({
    id: tool.id,
    name: tool.metadata.name,
    description: tool.metadata.description,
    owner: tool.metadata.owner,
    version: tool.metadata.version,
    inputSchema: tool.metadata.inputSchema,
    defaultInput: tool.defaultInput
  }));
}

function runTool(toolId, input, context = {}) {
  const tool = TOOL_DEFINITIONS.find((item) => item.id === toolId);
  if (!tool) {
    return {
      ok: false,
      data: null,
      error: {
        code: 'TOOL_NOT_FOUND',
        message: `未找到工具：${toolId}`
      }
    };
  }

  try {
    const result = tool.run(input || {}, context);
    return normalizeResult(result);
  } catch (error) {
    return {
      ok: false,
      data: null,
      error: {
        code: 'TOOL_RUNTIME_ERROR',
        message: error && error.message ? error.message : '工具执行失败',
        stack: error && error.stack ? error.stack : ''
      }
    };
  }
}

module.exports = {
  listTools,
  runTool
};
