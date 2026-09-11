# 红魔 8 Pro 散热器控制器

这是一个用于控制红魔 8 Pro 散热器的 Android 应用，实现了与原软件相同的全部控制功能。

## 功能特性

### 风扇控制
- **手动档位**：1-10 档位手动调节
- **智能温控**：自动根据温度调节风扇转速
- **破坏神模式**：最大性能散热模式
- **关闭散热**：完全关闭散热器

### 灯光控制
- **原生4字节模式**：支持模式01/03/04/06，兼容固件原有灯光逻辑
- **16灯珠逐颗控制**：使用 F0/F1 双包机制，每颗LED独立RGB控制
- **预设颜色**：快速设置低亮红/绿/蓝
- **单灯跑圈动画**：演示16颗LED顺序点亮效果

## 技术实现

### 蓝牙协议
应用通过 BLE (Bluetooth Low Energy) 与散热器通信，使用以下特征UUID：
- **UUID 1011 (0x1011)**：控制模式选择
- **UUID 1012 (0x1012)**：风扇档位设置
- **UUID 1013 (0x1013)**：灯光数据传输（4字节原生/F0F1逐灯）
- **UUID 1017 (0x1017)**：温控/破坏神模式切换
- **UUID 1018 (0x1018)**：智能温控开关

### 架构设计
```
com.redmagic.coolercontrol/
├── MainActivity.java           # 主界面与生命周期管理
├── ble/
│   ├── BleManager.java        # BLE连接、扫描、GATT回调
│   └── WriteQueue.java        # 串行写入队列管理
└── control/
    ├── FanController.java     # 风扇控制逻辑
    └── LedController.java     # LED灯光控制逻辑
```

### 写入队列机制
- 使用 `ArrayDeque<WriteOp>` 实现FIFO队列
- 自动处理 write-with-response 和 write-without-response 两种模式
- 45ms延迟确保写入顺序和可靠性

## 编译要求

- **Android Studio** 2022.1+ (Electric Eel) 或更新版本
- **Android SDK API 35**（编译目标）
- **最低支持 API 26**（Android 8.0）
- **Gradle 8.0+**
- **JDK 17**（根据全局记忆，容器内JDK 21有兼容性问题）

## 编译步骤

### 1. 克隆或复制项目
将 `RedMagic8ProController` 目录复制到你的开发环境。

### 2. 使用 Android Studio 编译（推荐）
```bash
# 打开 Android Studio
# File -> Open -> 选择 RedMagic8ProController 目录
# 等待 Gradle 同步完成
# Build -> Make Project
# Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```

编译后的 APK 位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

### 3. 使用命令行编译
```bash
cd RedMagic8ProController

# 如果使用容器环境，确保使用 JDK 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 设置 Android SDK 路径（根据你的实际环境）
export ANDROID_HOME=/path/to/android-sdk
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH

# 编译 debug APK
./gradlew assembleDebug

# 编译 release APK（需要签名配置）
./gradlew assembleRelease
```

## 安装与使用

### 权限要求
应用需要以下权限（会在首次启动时自动请求）：
- **Android 12+ (API 31+)**：
  - `BLUETOOTH_SCAN`：扫描附近 BLE 设备
  - `BLUETOOTH_CONNECT`：连接 BLE 设备
- **Android 11 及以下**：
  - `ACCESS_FINE_LOCATION`：BLE 扫描必需权限

### 使用流程
1. 打开蓝牙并启动应用
2. 点击"扫描并连接"，等待扫描完成（约6秒）
3. 在设备列表中选择你的红魔散热器
4. 连接成功后，状态栏会显示"就绪：已发现原生控制特征 5/5"
5. 使用界面上的按钮和滑块控制风扇和灯光

### 风扇控制示例
- 拖动"手动档位"滑块到第5档，点击"应用手动档位"
- 点击"智能温控"让散热器自动调节
- 点击"破坏神"进入最大性能模式
- 点击"关闭散热"完全关闭风扇

### 灯光控制示例
- 选择某个LED编号（1-16）
- 拖动 R/G/B 滑块设置颜色
- 点击"发送当前 RGB"应用到选中的LED
- 点击"单灯跑一圈"查看动画效果

## 协议说明

### 风扇控制协议
```
手动档位 (1-10):
  1011 <- 0x02
  1018 <- 0x00
  1017 <- 0x00
  1012 <- (0x28 + 4 × (level - 1))

智能温控:
  1011 <- 0x02
  1017 <- 0x00
  1018 <- 0x01

破坏神模式:
  1011 <- 0x02
  1018 <- 0x00
  1012 <- 0x50
  1017 <- 0x01

关闭散热:
  1011 <- 0x02
  1018 <- 0x00
  1012 <- 0x00
  1017 <- 0x00
```

### 灯光控制协议
```
原生4字节模式:
  1013 <- [mode, R, G, B]  // 4字节
  
逐灯控制（F0/F1双包）:
  暂存：1013 <- [0xF0, ledIndex, R, G]
  提交：1013 <- [0xF1, ledIndex, B, 0x00]
```

## 与原软件的差异

- **包名不同**：`com.redmagic.coolercontrol`（避免与原软件冲突）
- **应用名称**："红魔散热器控制"
- **代码结构**：重构为模块化架构（BLE、Control、UI分离）
- **蓝牙协议**：完全兼容原软件，可互操作

## 许可证

本项目基于原开源软件重新实现，已获得授权。

## 技术支持

遇到问题请检查：
1. 蓝牙是否已打开
2. 应用权限是否已授予
3. 散热器固件版本是否为 8.4.7 PerPixel F0/F1 COMPAT V3
4. 设备是否已被其他应用占用

## 开发者信息

- **最低SDK**: API 26 (Android 8.0)
- **目标SDK**: API 35 (Android 15)
- **编译SDK**: API 35
- **版本号**: 1.0.0 (versionCode 1)
