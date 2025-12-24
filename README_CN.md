# WebIDE

![Version](https://img.shields.io/badge/version-0.2.0-blue?style=flat-square)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue?style=flat-square)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-green?style=flat-square)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPLv3-orange?style=flat-square)](LICENSE)


[ [English](README.md) ] | [ **中文** ]

WebIDE 是一个基于 Android 原生的 Web 前端集成开发环境。该项目完全采用 Jetpack Compose 构建，实现了从代码编辑到在手机上直接构建 APK 的完整工作流。

这是一个实验性的工程项目，其核心架构与代码逻辑由多个 AI 模型（Claude, Gemini, DeepSeek）协作完成。

## 项目截图

<div align="center">
  <img src="https://github.com/user-attachments/assets/2eac6ea4-25a1-4a02-b814-2925ffb2092e" width="45%" />
  <img src="https://github.com/user-attachments/assets/7999b42a-af56-4aea-b705-920e7e168844" width="45%" />
</div>

## 项目结构解析

主要代码位于 `app/src/main/java/com/web/webide/`，目录结构功能如下：

```text
com.web.webide
├── build/              # 自定义 APK 构建系统
│   ├── ApkBuilder.kt   # 负责协调资源编译、打包和输出 APK 的核心逻辑
│   └── ApkInstaller.kt # 处理生成的 APK 安装请求
├── core/               # 核心基础设施
│   ├── utils/          # 日志、代码格式化、权限管理等工具类
│   └── ...
├── files/              # 文件系统模块
│   └── FileTree.kt     # 递归文件树的可视化渲染与交互逻辑
├── ui/                 # 界面层 (Jetpack Compose)
│   ├── editor/         # 代码编辑器模块
│   │   ├── viewmodel/  # 编辑器状态管理 (集成 TextMate 核心)
│   │   └── components/ # 编辑器组件 (行号栏、代码区域等)
│   ├── preview/        # 实时预览模块
│   │   └── webview/    # 带有 JS Bridge 通信能力的 WebView 实现
│   ├── projects/       # 项目管理模块
│   │   └── ...         # 工作区选择、新项目模板生成
│   ├── theme/          # 设计系统
│   │   └── ...         # 动态色彩、字体排版定义
│   └── welcome/        # 欢迎引导流程
```

## 功能特性

*   **语法高亮**: 基于 TextMate 语法文件，完美支持 HTML, CSS, JavaScript 和 JSON。
*   **项目管理**: 完整的文件系统访问权限，支持多文件 Web 项目的创建与管理。
*   **实时预览**: 集成 WebView 预览环境，支持 JavaScript 交互测试。
*   **现代化 UI**: 100% 使用 Kotlin 和 Jetpack Compose 编写，支持动态主题。

## 讨论

* QQ群:[1050254184](https://qm.qq.com/q/tFXuqMQDlK)

## 贡献者

<a href="https://github.com/h465855hgg/WebIDE/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=h465855hgg/WebIDE" />
</a>

## 许可证

```
WebIDE - A powerful IDE for Android web development.
Copyright (C) 2025  如日中天  <3382198490@qq.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```


[![Star History Chart](https://api.star-history.com/svg?repos=h465855hgg/WebIDE&type=Date)](https://star-history.com/#h465855hgg/WebIDE&Date)
