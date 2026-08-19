# 小米外呼统计

## 项目定位

原生 Android 通话统计 App，读取本地 Call Log，支持日、周、月汇总及周/月按天明细。

## 如何运行

- 使用 JDK 17、Android SDK API 34 和本地 `tools/gradle-8.4`（`tools/` 不提交到远端）。
- 构建 Debug APK：`tools/gradle-8.4/bin/gradle.bat :app:assembleDebug`。
- APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

- Kotlin + Android Views/XML + ViewBinding
- AndroidX、Material 3、ConstraintLayout
- 最低 Android API 26，目标 API 34

## 目录与约定

- 页面入口：`app/src/main/java/com/example/callcounter/MainActivity.kt`
- 通话查询、过滤、去重和周期聚合：`util/CallLogHelper.kt`
- 统计模型：`data/model/`
- 布局与字符串资源：`app/src/main/res/`
- 版本号只在根目录 `gradle.properties` 的 `app.versionCode` 和 `app.versionName` 中维护。
- Release 签名密码只通过环境变量提供，不得写入 Gradle 文件或提交到远端。
- 新版本安装包作为 Gitee Release asset 上传，不提交 APK 到源码仓库；发布前必须同步递增版本号并构建签名 Release APK。
- 统计必须沿用短号过滤和去重配置；周从周一开始，月从当月 1 日开始。

## 当前状态与下一步

- 当前版本：`1.1.0`，`versionCode=2`。
- 已实现日/周/月切换、周/月按天明细、每日目标和通话记录权限处理。
- 构建验证：`assembleDebug` 已通过。
- 后续可选：桌面 Widget、CSV/Excel 导出、自定义黑名单。
