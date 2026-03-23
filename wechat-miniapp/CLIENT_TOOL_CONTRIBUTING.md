# Client Tool Contributing Guide

本文档定义微信小程序侧新增 Client Tool 的统一约定，目标是：
- 工具可发现（可枚举）
- 责任清晰（owner/version）
- 协议稳定（inputSchema + 统一返回结构）
- 错误可观测（标准错误码）

## 1) 命名规范

- 工具 ID：`<domain>.<action>`，例如：`device.scanCode`、`client.openSetting`
- `domain` 建议使用业务域：`device` / `client` / `user` / `media`
- `action` 使用小驼峰动词短语：`getInfo` / `openSetting` / `scanCode`

## 2) 目录规范

- 注册中心：`utils/clientToolRegistry.js`
- 每个工具建议以对象形式注册到 `TOOL_DEFINITIONS`
- 页面统一通过 `clientToolRegistry.listTools()` 展示，通过 `runTool()` 调用

## 3) metadata 与 schema 规范

每个工具必须定义：

- `name`: 工具名（人可读）
- `description`: 作用描述
- `owner`: 责任团队或人（如 `team-miniapp`）
- `version`: 语义化版本（`MAJOR.MINOR.PATCH`）
- `inputSchema`: JSON Schema 子集（至少 `type/properties/required`）

示例：

```js
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
  }
}
```

## 4) 统一结果结构

所有工具执行后必须返回：

```json
{
  "ok": true,
  "data": {},
  "error": null
}
```

或失败时：

```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "INVALID_INPUT",
    "message": "参数校验失败",
    "detail": {}
  }
}
```

## 5) 错误码约定

通用错误码：

- `TOOL_NOT_FOUND`: 工具未注册
- `INVALID_INPUT`: 入参不合法
- `TOOL_RUNTIME_ERROR`: 运行异常
- `INVALID_RESULT`: 返回结构不符合 `{ ok, data, error }`

业务错误码可扩展，建议前缀化（如 `SCAN_TIMEOUT`, `SETTING_DENIED`）。

## 6) 新增工具最小示例

```js
{
  id: 'demo.echo',
  metadata: {
    name: '演示回声',
    description: '返回输入内容，便于联调。',
    owner: 'team-frontend',
    version: '1.0.0',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: '要回显的文本' }
      },
      required: ['text']
    }
  },
  run(input) {
    return { ok: true, data: { echo: input.text }, error: null };
  }
}
```

## 7) 发布检查清单

- [ ] `listTools()` 能检索到新工具
- [ ] 页面可搜索、可选择并展示 metadata
- [ ] `runTool()` 返回结构符合 `{ ok, data, error }`
- [ ] 错误码符合本文档约定
- [ ] 更新示例输入（`defaultInput`）便于联调
