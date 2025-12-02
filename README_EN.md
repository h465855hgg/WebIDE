# WebIDE – AI‑Built Web/Android IDE  

[English 🇺🇸](README_EN.md) | [中文 🇨🇳](README.md)

## 📖 Project Overview  
WebIDE is a lightweight, web-focused Android IDE built 100 % with **Jetpack Compose**—and 100 % by **AI collaboration**.  
The repo demonstrates how far modern language models can go when they act as a real engineering team.

## 🤖 AI Development Credits  
No human wrote a single line of code. The work was split between:

| Model   | Responsibility |
|---------|----------------|
| **Claude 4.5** | Welcome flow, theme engine, UX polish |
| **Gemini 3.0 Pro** | Main UI shell, file-tree component, project management |
| **DeepSeek** | Editor core, syntax-highlight engine, real-time preview bridge |

## 🛠️ Tech Stack  
- **Language**: Kotlin  
- **UI Toolkit**: Jetpack Compose (100 %)  
- **Target SDK**: Android 8.0+ (API 26 → 34)  
- **Build system**: Gradle Kotlin-DSL  
- **VCS**: Git (GitHub)

## 📁 Project Structure  
```
app/src/main/java/com/web/webide/
├── core/           # Business logic & DI
├── files/          # File-system abstraction
├── html/           # HTML/CSS/JS parsers & helpers
├── textmate/       # Syntax-highlight grammar files
├── ui/             # Compose UI layer
│   ├── components/ # Re-usable widgets
│   ├── editor/     # Code editor with line numbers
│   ├── preview/    # Live Web-view
│   ├── projects/   # Create / open / delete projects
│   ├── settings/   # Theme & editor config
│   ├── theme/      # Dark / Light / Custom palettes
│   └── welcome/    # On-boarding screen
├── App.kt          # Application singleton
└── MainActivity.kt # Single-Activity entry point
```

## ✨ Key Features  

### 🎨 Interface  
- Modern Material-3 dynamic colors  
- Responsive layouts (phone → tablet → foldable)  
- Animated theme switching without recreation  

### 📝 Code Editing  
- Syntax highlighting for **HTML**, **CSS**, **JavaScript**  
- Collapsible file-tree with long-press actions  
- Undo / redo, find / replace, soft keyboard extensions  

### 🔧 Developer Tools  
- One-tap project templates (blank, bootstrap, react-lite)  
- Live preview with local HTTP server (localhost:8080)  
- Auto-save + crash recovery  

## 👥 Contributors (The Core Team)  

| <img src="https://github.com/h465855hgg.png" width="50px" alt="h465855hgg"/> | <img src="https://github.com/user-attachments/assets/d3afe9ed-460c-4ee7-a041-70bd320da367" width="50px" alt="Claude"/> | <img src="https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/Google_Gemini_logo.svg/2560px-Google_Gemini_logo.svg.png" width="50px" alt="Gemini"/> | <img src="https://avatars.githubusercontent.com/u/148330874?s=200&v=4" width="50px" alt="DeepSeek"/> |
|:---:|:---:|:---:|:---:|
| **h465855hgg** | **Claude 4.5** | **Gemini 3.0 Pro** | **DeepSeek** |
| 🧠 Architect / Prompt Engineer | 🎨 UI/UX & Theme | 🏗️ Logic & Components | ⚙️ Core Algorithms |

## 🚀 Roadmap / Wish-list  
- [x] Build app skeleton  
- [x] Custom theme colors  
- [x] Polished settings screen  
- [ ] Toolbar with quick actions  
- [ ] Smarter auto-completion  
- [ ] Real-time error linting  
- [ ] DevTools console inside preview  
- [ ] Full path customization  
- [ ] About screen & changelog  

## 💡 Why This Matters  
WebIDE is intentionally **minimal**—a proof that AI can ship a usable Android app from scratch.  
It shows:  
- How different models excel in different domains.  
- That AI-driven development is no longer sci-fi.  
- A glimpse of tomorrow’s human-AI pair programming.

> *“If the phone can run it, the AI can build it.”*

![Screenshot](https://github.com/h465855hgg/WebIDE/blob/main/IMG_20251108_171127.jpg)

---

<div align="center">

**Star History**  
![Star History Chart](https://api.star-history.com/svg?repos=h465855hgg/WebIDE&type=Date)

</div>
