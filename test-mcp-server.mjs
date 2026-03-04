#!/usr/bin/env node
/**
 * 最简 MCP Server（STDIO 传输）— 用于验证上游连接
 *
 * 提供两个工具：
 *   - echo: 原样返回输入
 *   - random_number: 返回随机数
 *
 * 协议：JSON-RPC 2.0 over STDIO，每行一个 JSON
 *
 * 用法：node test-mcp-server.mjs
 */

import { createInterface } from 'readline';

const TOOLS = [
  {
    name: 'echo',
    description: 'Echo back the input text',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to echo' }
      },
      required: ['text']
    }
  },
  {
    name: 'random_number',
    description: 'Generate a random number between min and max',
    inputSchema: {
      type: 'object',
      properties: {
        min: { type: 'number', description: 'Minimum value (default 1)' },
        max: { type: 'number', description: 'Maximum value (default 100)' }
      }
    }
  }
];

const SERVER_INFO = {
  name: 'test-mcp-server',
  version: '0.1.0'
};

function handleRequest(req) {
  const { method, params } = req;

  switch (method) {
    case 'initialize':
      return {
        protocolVersion: '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO
      };

    case 'notifications/initialized':
      return null; // notification, no response

    case 'tools/list':
      return { tools: TOOLS };

    case 'tools/call': {
      const toolName = params?.name;
      const args = params?.arguments || {};

      if (toolName === 'echo') {
        return {
          content: [{ type: 'text', text: args.text || '(empty)' }]
        };
      }
      if (toolName === 'random_number') {
        const min = args.min ?? 1;
        const max = args.max ?? 100;
        const num = Math.floor(Math.random() * (max - min + 1)) + min;
        return {
          content: [{ type: 'text', text: String(num) }]
        };
      }
      return {
        content: [{ type: 'text', text: `Unknown tool: ${toolName}` }],
        isError: true
      };
    }

    case 'ping':
      return {};

    default:
      throw { code: -32601, message: `Method not found: ${method}` };
  }
}

// JSON-RPC over STDIO
const rl = createInterface({ input: process.stdin });

rl.on('line', (line) => {
  if (!line.trim()) return;
  try {
    const req = JSON.parse(line);
    const result = handleRequest(req);
    if (result === null) return; // notification
    const response = { jsonrpc: '2.0', id: req.id, result };
    process.stdout.write(JSON.stringify(response) + '\n');
  } catch (err) {
    const req = (() => { try { return JSON.parse(line); } catch { return {}; } })();
    const response = {
      jsonrpc: '2.0',
      id: req.id ?? null,
      error: err.code ? err : { code: -32603, message: String(err.message || err) }
    };
    process.stdout.write(JSON.stringify(response) + '\n');
  }
});

process.stderr.write('[test-mcp-server] Started on STDIO\n');
