# WebIDE - AI协作开发的web for Android IDE

## 📖 项目简介

采用Jetpack Compose构建。这个项目最大的特色是**完全由AI开发**，展示了AI在软件开发中的强大潜力。

## 🤖 AI开发

这个项目是多个AI模型协作的成果：

- **Claude**: 负责编写了欢迎界面和主题系统
- **Gemini**: 开发了主要UI界面和文件树组件  
- **DeepSeek**: 与Gemini分别开发了部分代码编辑器核心功能

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **目标平台**: Android

## 📁 项目结构

```
app/src/main/java/com/web/webide/
├── core/           # 核心业务逻辑
├── files/          # 文件管理模块
├── html/           # HTML处理相关
├── textmate/       # 语法高亮支持
├── ui/             # 用户界面层
│   ├── components/ # 可复用组件
│   ├── editor/     # 代码编辑器
│   ├── preview/    # 实时预览
│   ├── projects/   # 项目管理
│   ├── settings/   # 设置界面
│   ├── theme/      # 主题系统
│   └── welcome/    # 欢迎界面
├── App.kt          # 应用入口
└── MainActivity.kt # 主活动
```

## ✨ 主要功能

### 🎨 界面特性
- **现代化UI**: 基于Jetpack Compose的流畅界面
- **主题系统**: 支持多种主题切换
- **响应式设计**: 适配不同屏幕尺寸

### 📝 代码编辑
- **语法高亮**: 支持html css js
- **文件树**: 直观的项目文件管理
- **实时预览**: Web页面即时预览功能

### 🔧 开发工具
- **项目管理**: 完整的项目创建和管理功能
- **设置系统**: 可定制的开发环境配置
- **欢迎界面**: 友好的用户引导体验

## 🤝 贡献
Null

这个项目目前处于开源状态，但由于是AI协作开发的实验性项目，作者已停止维护。欢迎有兴趣的开发者继续完善和扩展功能。

**作者的美好幻想**
- build app [complete]
- 工具栏
- 更强的代码补全
- 代码实时查错
- 预览界面加入调试功能
- 想给预览界面实现DevServer
- 加入全路径自定义功能
- 自定义主题色（已实现但未加入）
- 更好更美观的settings
- 写个关于界面，更新日志之类的

## 💡 项目意义

这个项目是一个功能残缺的Android开发WebIDE，当时想着干掉'WebIDE'我来重新定义WebIDE这个名字，后来因为手机性能带不动准备放弃了

展示了：
- AI在复杂软件开发中的能力
- 不同AI模型在特定领域的专长
- 未来AI辅助开发的潜力

---

*这是一个由AI开发的创新项目，体现了人工智能在软件开发领域的新可能性。*
![image](https://github.com/h465855hgg/WebIDE/blob/main/IMG_20251108_171127.jpg)
