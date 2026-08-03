# 桌面生命 | Desktop Life

纯原生 Android Live2D 桌面宠物应用。使用 Cubism SDK for Native 进行 OpenGL ES 2 渲染，以悬浮窗形式运行在桌面上。

## 特点

- **纯原生渲染** — GLSurfaceView + Cubism SDK，无需 WebView
- **悬浮窗模式** — 在任意界面上方显示，可拖拽移动
- **边缘吸附** — 松手后自动吸附到屏幕边缘
- **触摸互动** — 点击/触摸时 Live2D 角色做出反应
- **待机动画** — 空闲时自动播放待机动作
- **低功耗** — 针对移动设备优化

## 构建

```bash
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/app-release.apk`

## 技术栈

- Live2D Cubism SDK for Native 5-r.5
- OpenGL ES 2.0
- Kotlin + Java (JNI 桥接)
- CMake + NDK
- GitHub Actions CI

## 许可

本项目基于 Live2D Open Software License 使用 Cubism SDK。
Live2D 模型文件版权归原作者所有。