# WebIDE ![Stone Badge](https://stone.professorlee.work/api/stone/h465855hgg/WebIDE)

![Version](https://img.shields.io/badge/version-0.3.5-blue?style=flat-square)
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
│   ├── ApkBuilder.java # 编译和打包 APK 的核心逻辑
│   ├── ApkInstaller.kt # 处理 APK 安装
│   └── ...             # 加密器, ZipAligner
├── core/               # 核心基础设施
│   └── utils/          # 工具类 (备份, 代码格式化, 工作区管理等)
├── files/              # 文件系统模块
│   ├── FileIcons.kt    # 图标资源映射
│   └── FileTree.kt     # 文件资源管理器 UI 和逻辑
├── ui/                 # 界面层 (Jetpack Compose)
│   ├── components/     # 共享 UI 组件
│   ├── editor/         # 代码编辑器界面
│   ├── preview/        # Web 预览界面
│   ├── settings/       # 应用设置和关于界面
│   ├── terminal/       # 终端模拟器 (集成 Alpine Linux)
│   ├── theme/          # 设计系统 (颜色, 排版)
│   └── welcome/        # 欢迎/引导界面
```

**关键资源 (`app/src/main/assets/`)**:
*   `textmate/`: 用于语法高亮的 TextMate 语法和配置。
*   `queries/`: 语法树查询。
*   `init-host.sh`, `init.sh`, `proot`, `rootfs.bin`: 嵌入式 Alpine Linux 环境的文件。


## 功能特性

*   **语法高亮**: 基于 TextMate 语法文件，完美支持 HTML, CSS, JavaScript 和 JSON。
*   **项目管理**: 完整的文件系统访问权限，支持多文件 Web 项目的创建与管理。
*   **实时预览**: 集成 WebView 预览环境，支持 JavaScript 交互测试。
*   **现代化 UI**: 100% 使用 Kotlin 和 Jetpack Compose 编写，支持动态主题。
*   **Git 集成**: 内置 Git 版本控制，提供可视化提交历史图谱，支持克隆、提交、推送、拉取和分支管理。自动忽略敏感文件和构建产物。

## 展望与挑战

我们正在积极改进 WebIDE。以下是我们的主要规划和目前面临的挑战：

*   **多语言支持**: 原生支持应用内中英文切换。
*   **自定义代码高亮**: 允许用户导入和自定义 TextMate 语法及颜色主题。
*   **云端资源与体积优化**: 目前最大的挑战是嵌入式 Linux 环境导致的 APK 体积过大。我们计划将 `rootfs.bin` 等大型资源移至云端存储，实现按需下载。这将显著减小初始安装包的体积。

## 讨论

* QQ群:[1050254184](https://qm.qq.com/q/tFXuqMQDlK)
* TG 频道: [Android_For_WebIDE](https://t.me/Android_For_WebIDE)

## 贡献者

<a href="https://github.com/h465855hgg/WebIDE/graphs/contributors">
  <img src="https://contributors-img.web.app/image?repo=h465855hgg/WebIDE" />
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
