Page({
  data: {
    userModeText: '匿名模式（无需登录）',
    userId: '',
    tools: [
      {
        id: 'getDeviceInfo',
        name: '获取设备信息',
        description: '模拟读取设备型号、系统版本、语言等信息。'
      },
      {
        id: 'openSetting',
        name: '打开设置页',
        description: '模拟打开客户端设置页面。'
      },
      {
        id: 'scanCode',
        name: '扫码工具',
        description: '模拟扫码并返回 code 与 type。'
      }
    ],
    selectedToolId: 'getDeviceInfo',
    paramsText: '{"scene":"chat"}',
    isRunning: false,
    resultText: ''
  },

  onShow() {
    const app = getApp();
    const userId = app.globalData.userId || wx.getStorageSync('userId') || 'anonymous';
    this.setData({ userId });
  },

  onSelectTool(e) {
    const selectedToolId = e.currentTarget.dataset.id;
    this.setData({
      selectedToolId,
      resultText: ''
    });
  },

  onParamsInput(e) {
    this.setData({
      paramsText: e.detail.value
    });
  },

  onRunTool() {
    if (this.data.isRunning) {
      return;
    }

    let parsedParams = {};
    const rawParams = (this.data.paramsText || '').trim();

    if (rawParams) {
      try {
        parsedParams = JSON.parse(rawParams);
      } catch (error) {
        wx.showToast({
          title: '参数必须是合法 JSON',
          icon: 'none'
        });
        return;
      }
    }

    this.setData({ isRunning: true });

    const now = new Date();
    setTimeout(() => {
      const mockPayload = this._buildMockResult(this.data.selectedToolId, parsedParams);
      const result = {
        ok: true,
        mock: true,
        tool: this.data.selectedToolId,
        auth: {
          loginRequired: false,
          mode: 'anonymous',
          userId: this.data.userId || 'anonymous'
        },
        executedAt: now.toISOString(),
        input: parsedParams,
        output: mockPayload
      };

      this.setData({
        isRunning: false,
        resultText: JSON.stringify(result, null, 2)
      });
    }, 500);
  },

  _buildMockResult(toolId, params) {
    if (toolId === 'getDeviceInfo') {
      return {
        platform: 'ios',
        brand: 'Apple',
        model: 'iPhone 15 Pro (Mock)',
        language: 'zh_CN',
        extra: params
      };
    }

    if (toolId === 'openSetting') {
      return {
        opened: true,
        page: 'setting',
        tips: '这是 mock 结果，未真实调用客户端设置。',
        extra: params
      };
    }

    return {
      code: 'mock-qrcode-content',
      scanType: 'QR_CODE',
      charSet: 'utf8',
      extra: params
    };
  }
});
