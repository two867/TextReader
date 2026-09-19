# TextReader (安卓极简文本阅读器)

轻量级 Android 纯文本阅读器，专为手机端通过系统“用其他应用打开”查看 `.txt`、`.log` 等文件而设计。

---

## ✨ 核心特性

1. **系统级文件关联唤起 (Intent Filter)**：
   - 在手机文件管理器、微信、QQ 等应用中点击 `.txt`、`.log`、`.md` 文件选择“用其他应用打开”，即可唤起本阅读器阅读。
2. **字号自由缩放**：
   - 支持 `10sp` ~ `36sp` 平滑调节，内置预设字号快速切换及步进加减按钮。
3. **排版模式自由切换**：
   - **自动换行模式**：针对小说、随笔阅读，屏幕边缘自适应折行。
   - **单行横向滚动模式**：单行不换行，支持左右滑动手势，针对日志 (Log)、代码、表格型纯文本排版。
4. **行号显示开关**：
   - 左侧配备等宽行号显示槽与浅色分隔线，可一键显示/隐藏。
5. **编码智能探测与切换**：
   - 自动识别 **UTF-8** 与 **GBK / GB18030**（彻底解决国内中文小说与 Windows 记事本文档乱码问题），并支持手动切换编码。
6. **零全局敏感权限**：
   - 基于 Android 官方 Storage Access Framework (SAF)，无需获取危险的手机存储权限，绿色纯净。
7. **极简清新浅色界面**：
   - 遵循 Material 3 设计规范，清新阅读配色，护眼低负担，且自适应系统深浅色。

---

## 🚀 零本地环境编译与打包（GitHub Actions）

**您本地不需要安装任何 Android Studio、Java、SDK 或 Gradle！**

直接将本项目推送到您的 GitHub 仓库，GitHub Actions 会在云端自动构建生成安装包。

### 步骤 1：在 GitHub 创建一个新仓库
前往 [GitHub.com](https://github.com/new) 创建一个新的仓库，例如命名为 `TextReader`。

### 步骤 2：在本地初始化并推送代码
在本地当前目录（PowerShell 7）中执行以下命令：

```powershell
git init
git add .
git commit -m "feat: initial commit for TextReader"
git branch -M main
git remote add origin https://github.com/你的用户名/TextReader.git
git push -u origin main
```

### 步骤 3：下载编译好的 APK
1. 打开您的 GitHub 仓库页面，点击顶部的 **Actions** 标签页。
2. 您会看到正在运行的 **Build Release APK** 流水线（耗时约 1~2 分钟）。
3. 运行完成后点击进入该条记录，在页面最下方的 **Artifacts** 区域直接点击下载 **`TextReader-APK`**。
4. 解压后将 `TextReader-release.apk` 发送到手机即可直接安装运行！

---

## 🛠️ 项目技术栈
- **编程语言**：Kotlin 1.9.23
- **UI 框架**：Jetpack Compose + Material 3
- **架构模式**：MVVM (ViewModel + StateFlow + Coroutines)
- **构建系统**：Android Gradle Plugin 8.3.2 + Gradle 8.7
- **CI/CD**：GitHub Actions (`ubuntu-latest`)
