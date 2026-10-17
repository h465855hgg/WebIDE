# WebIDE ![Stone Badge](https://stone.professorlee.work/api/stone/h465855hgg/WebIDE)

![Version](https://img.shields.io/badge/version-0.4.1-blue?style=flat-square)
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
*   **Code Completion (LSP)**: Built-in language servers for smart completion and diagnostics, with theme-aware popup colors that adapt to light/dark mode.
*   **Project Management**: Full file system access permissions, supporting the creation and management of multi-file Web projects.
*   **Real-time Preview**: Integrated WebView preview environment supporting JavaScript interaction testing.
*   **Terminal Emulator**: Embedded Alpine Linux environment (via proot) with a terminal, multi-session management, and virtual keys for Ctrl/Alt/Shift/Fn.
*   **Modern UI**: Written 100% in Kotlin and Jetpack Compose, supporting dynamic themes.
*   **Git Integration**: Built-in Git version control with a visual commit history graph, supporting Clone, Commit, Push, Pull, and Branch management. Automatically ignores sensitive files and build artifacts.
*   **AI Coding Agent (ACP)**: Built-in [Agent Client Protocol](https://agentclientprotocol.com) client. Connect external coding agents (Gemini CLI, Qwen Code, Claude Code, Codex, OpenCode, Cline, Crush, or a custom command) right inside the editor. Switch model / mode / thought level from a config bar, review plans, tool calls and diffs inline, and approve file writes or commands. See [docs/ACP.md](docs/ACP.md).
*   **Localization (i18n)**: Strings live in a dedicated `:i18n` module, currently shipping Chinese and English. Adding a language only requires dropping a new `values-<locale>/strings.xml` — contributions are welcome.

## Building

This project is built automatically via **GitHub Actions** (`.github/workflows/build-apk.yml`):

* **Push / PR to `main` or `master`**: builds a single variant (`arm64-v8a` / debug) for quick verification
* **Push a tag (`X.Y.Z`)**: builds all 4 variants (debug/release × arm64-v8a / armeabi-v7a) and publishes them to the GitHub Release (release name = tag)
* **Manual run**: Actions → `Build APK` → `Run workflow` (all 4 variants)

Local one-command build (Git Bash / WSL / Linux / macOS):

```bash
scripts/build.sh                 # debug, both architectures
scripts/build.sh release         # release, both architectures
scripts/build.sh release arm64-v8a --open
```

On Windows you can also double-click `scripts\build.bat`.

> Note: the app module has flavors, so Gradle task names must include the architecture (e.g. `:app:assembleArm64-v8aDebug`); flavor-less tasks like `assembleDebug` do not exist. NDK/CMake versions are pinned in the build files (`28.0.13004108` / `3.22.1`). Native modules accept `-PtargetAbi=<abi>[,<abi>]` so only the ABIs you need get compiled (the scripts and CI pass it automatically).

## Roadmap & Challenges

We are actively improving WebIDE. Here are the main plans and current challenges:

*   **More Languages**: The i18n module is ready for community translations (Japanese, French, Traditional Chinese, etc.).
*   **Custom Syntax Highlighting**: Allow users to import and customize TextMate grammars and color themes.
*   **Cloud Resources & Size Optimization**: The biggest challenge today is the large APK size caused by the embedded Linux environment. We plan to move large assets like `rootfs.bin` to cloud storage for on-demand download, significantly reducing the initial install size.

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