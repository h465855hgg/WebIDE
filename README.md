# WebIDE ![Stone Badge](https://stone.professorlee.work/api/stone/h465855hgg/WebIDE)

![Version](https://img.shields.io/badge/version-0.3.2-blue?style=flat-square)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue?style=flat-square)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-green?style=flat-square)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPLv3-orange?style=flat-square)](LICENSE)


[ [**English**] ] | [ [中文](README_CN.md) ]

WebIDE is a native Android Integrated Development Environment (IDE) for web front-end development. Built entirely with Jetpack Compose, this project implements a complete workflow ranging from code editing to building APKs directly on your mobile device.

This is an experimental engineering project; its core architecture and code logic were collaboratively completed by multiple AI models (Claude, Gemini, DeepSeek).

## Screenshots

<div align="center">
  <img src="https://github.com/user-attachments/assets/2eac6ea4-25a1-4a02-b814-2925ffb2092e" width="45%" />
  <img src="https://github.com/user-attachments/assets/7999b42a-af56-4aea-b705-920e7e168844" width="45%" />
</div>

## Project Structure

The main code is located in `app/src/main/java/com/web/webide/`. The directory structure and functions are as follows:

```text
com.web.webide
├── build/              # Custom APK build system
│   ├── ApkBuilder.java # Core logic for compiling and packaging APKs
│   ├── ApkInstaller.kt # Handles APK installation
│   └── ...             # Encryptor, ZipAligner
├── core/               # App-specific core infrastructure
│   └── utils/          # Utilities (Backup, CodeFormatter, WorkspaceManager, etc.)
├── files/              # File system module
│   ├── FileIcons.kt    # Icon resource mapping
│   └── FileTree.kt     # File explorer UI and logic
├── ui/                 # Interface layer (Jetpack Compose)
│   ├── components/     # Shared UI components
│   ├── editor/         # Code editor screen
│   ├── preview/        # Web preview screen
│   ├── settings/       # Application settings and about screens
│   ├── terminal/       # Terminal emulator (Alpine Linux integration)
│   ├── theme/          # Design system (Colors, Typography)
│   └── welcome/        # Welcome/Onboarding screen
```

**Key Assets (`app/src/main/assets/`)**:
*   `textmate/`: TextMate grammars and configurations for syntax highlighting.
*   `queries/`: Syntax tree queries.
*   `init-host.sh`, `init.sh`, `proot`, `rootfs.bin`: Files for the embedded Alpine Linux environment.


## Features

*   **Syntax Highlighting**: Based on TextMate grammar files, providing perfect support for HTML, CSS, JavaScript, and JSON.
*   **Project Management**: Full file system access permissions, supporting the creation and management of multi-file Web projects.
*   **Real-time Preview**: Integrated WebView preview environment supporting JavaScript interaction testing.
*   **Modern UI**: Written 100% in Kotlin and Jetpack Compose, supporting dynamic themes.
*   **Git Integration**: Built-in Git version control with a visual commit history graph, supporting Clone, Commit, Push, Pull, and Branch management. Automatically ignores sensitive files and build artifacts.

## Discussion

* QQ Group: [1050254184](https://qm.qq.com/q/tFXuqMQDlK)
* TG Group: [Android_For_WebIDE](https://t.me/Android_For_WebIDE)

## Contributors

<a href="https://github.com/h465855hgg/WebIDE/graphs/contributors">
   <img src="https://contributors-img.web.app/image?repo=h465855hgg/WebIDE" />
</a>

## License

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
