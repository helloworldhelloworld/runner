const clientToolRegistry = require('../../utils/clientToolRegistry');

function formatObject(value) {
  return JSON.stringify(value, null, 2);
}

Page({
  data: {
    userModeText: '匿名模式（无需登录）',
    userId: '',
    tools: [],
    filteredTools: [],
    selectedToolId: '',
    selectedToolMeta: null,
    searchKeyword: '',
    paramsText: '{}',
    isRunning: false,
    resultDataText: '',
    resultErrorText: '',
    selectedToolSchemaText: '{}'
  },


  onLoad() {
    this._loadTools();
  },

  onShow() {
    const app = getApp();
    const userId = app.globalData.userId || wx.getStorageSync('userId') || 'anonymous';
    this.setData({ userId });
  },

  _loadTools() {
    const tools = clientToolRegistry.listTools();
    const firstTool = tools[0] || null;
    const selectedToolId = this.data.selectedToolId || (firstTool ? firstTool.id : '');
    const selectedToolMeta = tools.find((item) => item.id === selectedToolId) || firstTool;

    this.setData({
      tools,
      filteredTools: tools,
      selectedToolId: selectedToolMeta ? selectedToolMeta.id : '',
      selectedToolMeta: selectedToolMeta || null,
      paramsText: selectedToolMeta ? formatObject(selectedToolMeta.defaultInput || {}) : '{}',
      resultDataText: '',
      resultErrorText: '',
      selectedToolSchemaText: selectedToolMeta ? formatObject(selectedToolMeta.inputSchema || {}) : '{}'
    });
  },

  onSearchInput(e) {
    const searchKeyword = (e.detail.value || '').trim();
    const filteredTools = this.data.tools.filter((tool) => {
      if (!searchKeyword) {
        return true;
      }
      const target = `${tool.name} ${tool.description} ${tool.owner}`.toLowerCase();
      return target.includes(searchKeyword.toLowerCase());
    });

    this.setData({
      searchKeyword,
      filteredTools
    });
  },

  onSelectTool(e) {
    const selectedToolId = e.currentTarget.dataset.id;
    const selectedToolMeta = this.data.tools.find((item) => item.id === selectedToolId) || null;
    this.setData({
      selectedToolId,
      selectedToolMeta,
      paramsText: selectedToolMeta ? formatObject(selectedToolMeta.defaultInput || {}) : '{}',
      resultDataText: '',
      resultErrorText: '',
      selectedToolSchemaText: selectedToolMeta ? formatObject(selectedToolMeta.inputSchema || {}) : '{}'
    });
  },

  onParamsInput(e) {
    this.setData({
      paramsText: e.detail.value
    });
  },

  onCopyTemplate() {
    const template = `module.exports = {\n  id: 'demo.echo',\n  metadata: {\n    name: '演示回声',\n    description: '返回输入内容，便于联调。',\n    owner: 'team-frontend',\n    version: '1.0.0',\n    inputSchema: {\n      type: 'object',\n      properties: {\n        text: { type: 'string', description: '要回显的文本' }\n      },\n      required: ['text']\n    }\n  },\n  run(input) {\n    return { ok: true, data: { echo: input.text }, error: null };\n  }\n};`;

    wx.setClipboardData({
      data: template,
      success: () => {
        wx.showToast({ title: '模板已复制', icon: 'success' });
      }
    });
  },

  onShowGuide() {
    wx.showModal({
      title: '新增工具指引',
      content: '请查看 wechat-miniapp/CLIENT_TOOL_CONTRIBUTING.md\n包含命名、目录、Schema、错误码与示例。',
      confirmText: '复制路径',
      success: (res) => {
        if (res.confirm) {
          wx.setClipboardData({
            data: 'wechat-miniapp/CLIENT_TOOL_CONTRIBUTING.md'
          });
        }
      }
    });
  },

  onRunTool() {
    if (this.data.isRunning || !this.data.selectedToolId) {
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

    setTimeout(() => {
      const result = clientToolRegistry.runTool(this.data.selectedToolId, parsedParams, {
        userId: this.data.userId || 'anonymous'
      });

      this.setData({
        isRunning: false,
        resultDataText: result.ok ? formatObject(result.data) : '',
        resultErrorText: result.ok ? '' : formatObject(result.error || { message: '未知错误' })
      });
    }, 300);
  }
});
