# 今日通话统计

一个用于 Android 手机统计日、周、月通话量的轻量 App。

## 设计原则：按需点击，不常驻后台

本应用采用**按需统计**设计：
- 不常驻后台
- 不持续监听通话状态
- 没有状态栏常驻通知
- 打开 App 时自动读取系统通话记录并统计
- 点击「刷新」按钮手动更新

这样更省电、更稳定，也符合小米等国产系统对后台的严格限制。

## 功能

- 读取系统通话记录，统计今日 **来电次数** 和 **去电次数**
- 支持日、周、月视图；周/月视图提供按天的通话明细
- 支持 **去重**：同一号码在设定时间窗口内只算 1 次
- 支持 **过滤短号**（默认过滤长度小于 7 位的号码）
- 可设置每日目标，打开 App 时若已达标则弹出通知提醒
- 每日首次打开时自动清零计数

## 技术栈

- 原生 Android + Kotlin
- XML + ViewBinding
- 只依赖必要的 AndroidX 和 Material 组件

## 环境要求

- JDK 17+（项目编译目标为 Java 17）
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK API 34
- Gradle 8.4

## 如何运行

### 版本号管理

应用版本号统一在根目录 `gradle.properties` 中维护：

```properties
app.versionCode=2
app.versionName=1.1.0
```

发布新版本时递增 `app.versionCode`，并同步更新 `app.versionName`。

Release 签名信息不写入项目文件。构建 Release 前通过环境变量提供
`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS` 和 `RELEASE_KEY_PASSWORD`。

### 方式一：使用本目录已配置好的命令行环境

本地开发环境可放置以下工具（这些本机工具目录不提交到远端）：
- `tools/jdk-17`
- `tools/android-sdk`
- `tools/gradle-8.4`

直接执行：

```bash
export JAVA_HOME=$(pwd)/tools/jdk-17
export ANDROID_SDK_ROOT=$(pwd)/tools/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH
./tools/gradle-8.4/bin/gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方式二：用 Android Studio 打开

1. 用 Android Studio 打开本项目根目录
2. 等待 Gradle Sync 完成
3. 连接手机，点击 Run

> 如果 Gradle 下载慢，已在 `settings.gradle.kts` 中配置阿里云镜像。

## 权限说明

首次启动会申请以下权限：

| 权限 | 用途 |
|---|---|
| `READ_CALL_LOG` | 读取通话记录 |
| `POST_NOTIFICATIONS` | 发送目标达成提醒（Android 13+） |

## 使用说明

1. 打开 App，授予通话记录权限
2. 设置「目标次数」（默认 50）
3. 设置「去重时间」（默认 5 分钟）
4. 开启「过滤短号」后，长度小于 7 位的号码不计入统计
5. 点击「保存」
6. 每次想查看最新统计时，打开 App 或点击「刷新统计」
7. 达到目标次数后，打开 App 会弹出通知提醒

## 项目结构

```
app/src/main/java/com/example/callcounter/
├── MainActivity.kt                 # 主界面
├── data/model/
│   ├── CallDayStats.kt             # 按天明细统计
│   ├── CallLogItem.kt              # 通话记录项
│   ├── CallStats.kt                # 统计数据
│   └── PeriodStats.kt              # 周/月汇总与明细
└── util/
    ├── CallLogHelper.kt            # 读取、过滤、去重通话记录
    ├── NotificationHelper.kt       # 目标达成通知
    └── PrefsHelper.kt              # 配置存储
```

## 注意事项

- 本应用仅读取本地通话记录，不上传任何数据
- 通话统计基于系统 Call Log，若通话记录被手动删除，统计会相应减少
- 由于不常驻后台，只有在打开 App 时才会检查目标是否达成并提醒

## 后续可扩展

- 桌面 Widget 显示今日数量
- 导出 Excel / CSV
- 自定义黑名单
