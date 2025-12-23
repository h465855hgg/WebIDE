# WebIDE

[![Language](https://img.shields.io/badge/Language-Kotlin-blue?style=flat-square)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-green?style=flat-square)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPLv3-orange?style=flat-square)](LICENSE)

[ **English** ] | [ [中文](README_CN.md) ]

WebIDE is a native Android integrated development environment tailored for web technologies. Built entirely with Jetpack Compose, it demonstrates a complete workflow from code editing to APK building directly on a mobile device.

This project is an experimental engineering feat, architected and coded collaboratively by AI models (Claude, Gemini, and DeepSeek).

## Screenshots

<div align="center">
  <img src="https://github.com/user-attachments/assets/2eac6ea4-25a1-4a02-b814-2925ffb2092e" width="45%" />
  <img src="https://github.com/user-attachments/assets/7999b42a-af56-4aea-b705-920e7e168844" width="45%" />
</div>

## Project Structure

A breakdown of the core codebase located in `app/src/main/java/com/web/webide/`:

```text
com.web.webide
├── build/              # Custom APK build system
│   ├── ApkBuilder.kt   # Orchestrates the compilation and packaging process
│   └── ApkInstaller.kt # Handles installation of generated APKs
├── core/               # Core infrastructure
│   ├── utils/          # Utilities for logging, formatting, and permissions
│   └── ...
├── files/              # File System
│   └── FileTree.kt     # Recursive file tree visualization logic
├── ui/                 # User Interface (Jetpack Compose)
│   ├── editor/         # Code Editor Module
│   │   ├── viewmodel/  # Editor state management (TextMate integration)
│   │   └── components/ # Editor UI components (Line numbers, gutter, etc.)
│   ├── preview/        # Live Preview Module
│   │   └── webview/    # WebView implementation with JS Bridge
│   ├── projects/       # Project Management
│   │   └── ...         # Workspace creation and template logic
│   ├── theme/          # Design System
│   │   └── ...         # Dynamic colors and typography definitions
│   └── welcome/        # Onboarding flow
└── signer/             # Signing Infrastructure
    └── ...             # Pure Kotlin implementation of APK V1/V2/V3 signing schemes
```

## Features

*   **Syntax Highlighting**: Powered by TextMate grammars, supporting HTML, CSS, JavaScript, and JSON.
*   **Native Build System**: Capable of packaging web projects into installable Android APKs utilizing a custom implementation of `apksig`.
*   **Project Management**: Full filesystem access for creating and managing multi-file web projects.
*   **Live Preview**: Integrated WebView with bridge capabilities for real-time testing.
*   **Modern UI**: 100% Kotlin and Jetpack Compose implementation with dynamic theming.

## License

This project is licensed under the **GNU General Public License v3.0**.
Copyright (C) 2025 程国荣

---

[![Star History Chart](https://api.star-history.com/svg?repos=h465855hgg/WebIDE&type=Date&theme=dark)](https://star-history.com/#h465855hgg/WebIDE&Date)


