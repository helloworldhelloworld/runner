# Web Client Tool Contributing Guide

本指南用于 Web 端（`agent-web`）客户端工具模拟能力，帮助新同学快速接入并保持一致性。

## 1. 命名约定

- 工具唯一 ID：`<Namespace>.<Name>`，例如 `GeoInformation.GetPosition`
- `Namespace`：大驼峰领域名（如 `GeoInformation`、`DeviceService`）
- `Name`：大驼峰动作名（如 `GetPosition`、`ScanCode`）

## 2. 目录约定

- UI 面板：`agent-web/src/main/resources/static/index.html`
- 工具注册与执行：`agent-web/src/main/resources/static/soul-comfort-api.js`
- 建议统一通过 `window.soulComfortAPI.setClientTool(...)` 维护工具

## 3. metadata 约定

每个工具应包含：

- `name`
- `description`
- `owner`（团队/负责人）
- `version`（语义化版本）
- `inputSchema`（JSON Schema 子集）

## 4. 统一返回结构

所有工具 mock 输出必须满足：

```json
{
  "ok": true,
  "data": {},
  "error": null
}
```

失败示例：

```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "INVALID_INPUT",
    "message": "参数错误"
  }
}
```

## 5. 错误码建议

- `TOOL_NOT_FOUND`
- `TOOL_DISABLED`
- `INVALID_INPUT`
- `INVALID_RESULT`
- `TOOL_RUNTIME_ERROR`

## 6. 最小示例

```js
window.soulComfortAPI.setClientTool('GeoInformation.GetPosition', {
  namespace: 'GeoInformation',
  name: 'GetPosition',
  description: '获取设备定位信息',
  owner: 'team-web',
  version: '1.0.0',
  inputSchema: {
    type: 'object',
    properties: {
      scene: { type: 'string', description: '调用场景' }
    },
    required: []
  },
  enabled: true,
  mockResponse: {
    ok: true,
    data: { city: '北京' },
    error: null
  }
});
```

## 7. 提交前检查

- [ ] 工具可在“客户端工具模拟（Web）”列表检索到
- [ ] metadata 展示完整
- [ ] `runClientTool()` 输出为 `{ ok, data, error }`
- [ ] 错误场景可在页面 error 面板展示
