# 快速开始指南 🚀

## 环境准备

### 1. 安装 DevEco Studio

从华为官网下载并安装 DevEco Studio 5.0+：
- 官网: https://developer.huawei.com/consumer/cn/deveco-studio/

### 2. 配置 HarmonyOS SDK

1. 打开 DevEco Studio
2. 进入 **Settings** → **SDK Manager**
3. 下载 **HarmonyOS NEXT SDK** (API 12+)
4. 勾选必要的工具和平台

### 3. 创建模拟器（可选）

1. 打开 **Device Manager**
2. 点击 **Create Device**
3. 选择设备类型和系统镜像
4. 完成创建并启动

## 项目导入

### 步骤 1: 导入项目

```bash
# 克隆项目（如果还没有）
cd /Users/hello/Documents/sourcecode/runner

# 打开 DevEco Studio
# File → Open → 选择 harmony-client 目录
```

### 步骤 2: 同步依赖

项目导入后，DevEco Studio 会自动同步依赖。如果没有自动同步：

1. 点击顶部的 **Sync Now**
2. 等待依赖下载完成

### 步骤 3: 配置签名（开发环境）

对于开发测试，可以使用自动签名：

1. **File** → **Project Structure**
2. 选择 **Signing Configs**
3. 勾选 **Automatically generate signature**
4. 点击 **OK**

## 后端服务配置

### 启动后端服务

```bash
# 在项目根目录
cd agent-web
mvn spring-boot:run
```

服务启动后访问: http://localhost:8080

### 修改后端地址（如需要）

编辑文件：`entry/src/main/ets/service/ChatService.ets`

```typescript
export class ChatService {
  // 开发环境 - 本地服务
  private static readonly BASE_URL = 'http://localhost:8080';

  // 或者使用局域网地址（真机测试）
  // private static readonly BASE_URL = 'http://192.168.1.100:8080';

  // 或者使用云服务地址
  // private static readonly BASE_URL = 'https://your-domain.com';
}
```

### 真机测试配置

如果使用真机测试，需要：

1. **电脑和手机在同一局域网**
2. **查看电脑 IP 地址**:
   ```bash
   # macOS/Linux
   ifconfig | grep "inet "

   # Windows
   ipconfig
   ```
3. **修改 BASE_URL**:
   ```typescript
   private static readonly BASE_URL = 'http://192.168.1.100:8080';
   ```

## 运行应用

### 方式 1: 使用工具栏

1. 点击顶部工具栏的 **Run** 按钮 (绿色三角形)
2. 选择目标设备（模拟器或真机）
3. 等待编译和安装

### 方式 2: 使用菜单

1. **Run** → **Run 'entry'**
2. 选择设备
3. 等待启动

### 方式 3: 使用快捷键

- macOS: `Cmd + R`
- Windows/Linux: `Shift + F10`

## 调试技巧

### 查看日志

1. 打开 **Hilog** 面板（底部工具栏）
2. 筛选 tag: `ChatService`, `ChatViewModel`, `EntryAbility`
3. 观察应用运行日志

### 常见日志输出

```
[ChatService] Health check: connected
[ChatService] Sending message: 你好
[ChatViewModel] Message sent successfully
[EntryAbility] onWindowStageCreate
```

### 调试断点

1. 在代码行号左侧点击设置断点
2. 点击工具栏的 **Debug** 按钮（绿色虫子图标）
3. 应用运行到断点时会暂停
4. 查看变量值、调用栈等信息

## 常见问题

### Q1: 无法连接后端服务

**现象**: 显示"暂时无法连接到服务"

**解决**:
1. 确认后端服务已启动
2. 检查 `BASE_URL` 配置是否正确
3. 真机测试时，检查网络连接
4. 检查防火墙设置

### Q2: 编译失败

**现象**: 编译时报错

**解决**:
1. 清理项目: **Build** → **Clean Project**
2. 重新构建: **Build** → **Rebuild Project**
3. 删除 build 缓存并重新同步
4. 检查 SDK 版本是否符合要求

### Q3: 应用闪退

**现象**: 应用启动后立即崩溃

**解决**:
1. 查看 Hilog 日志中的错误信息
2. 检查是否有语法错误或运行时异常
3. 确认所有资源文件都存在
4. 检查权限配置

### Q4: 模拟器启动失败

**现象**: 模拟器无法启动

**解决**:
1. 关闭其他占用资源的程序
2. 检查虚拟化是否开启（BIOS 设置）
3. 增加模拟器内存配置
4. 重新创建模拟器

### Q5: 消息发送失败

**现象**: 点击发送后没有响应

**解决**:
1. 检查网络连接
2. 查看 Hilog 中的错误日志
3. 使用 Postman 测试后端 API
4. 检查请求格式是否正确

## 开发建议

### 代码风格

遵循 ArkTS 官方代码规范：
- 使用 2 空格缩进
- 组件名使用 PascalCase
- 变量名使用 camelCase
- 常量使用 UPPER_SNAKE_CASE

### 性能优化

1. **避免频繁的状态更新**
   ```typescript
   // 不好 - 频繁更新
   this.messages.push(msg);

   // 好 - 批量更新
   const newMessages = [...this.messages, msg];
   this.messages = newMessages;
   ```

2. **使用 @Prop 传递只读数据**
   ```typescript
   @Prop message: Message;  // 子组件只读
   ```

3. **合理使用 ForEach**
   ```typescript
   ForEach(this.messages,
     (item: Message) => { /* 渲染 */ },
     (item: Message) => item.id  // 提供唯一 key
   )
   ```

### 安全建议

1. **不要在客户端硬编码敏感信息**
2. **使用 HTTPS 通信**（生产环境）
3. **验证用户输入**
4. **处理异常情况**

## 发布准备

### 1. 修改版本号

编辑 `AppConfig.json5`:

```json5
{
  "versionCode": 1000000,  // 内部版本号
  "versionName": "1.0.0"   // 显示版本号
}
```

### 2. 配置发布签名

1. 生成发布证书
2. 在项目配置中添加发布签名
3. 构建发布包

### 3. 优化资源

1. 压缩图片资源
2. 移除调试代码
3. 混淆代码（可选）

### 4. 测试

1. 功能测试
2. 性能测试
3. 兼容性测试
4. 用户体验测试

## 更多资源

- **HarmonyOS 官方文档**: https://developer.huawei.com/consumer/cn/doc/
- **ArkTS 语言指南**: https://developer.huawei.com/consumer/cn/arkts/
- **API 参考**: https://developer.huawei.com/consumer/cn/doc/harmonyos-references-V5/

## 技术支持

遇到问题？

1. 查看项目 README.md
2. 搜索官方文档
3. 访问华为开发者论坛
4. 提交 GitHub Issue

---

**祝你开发顺利！** 🎉
