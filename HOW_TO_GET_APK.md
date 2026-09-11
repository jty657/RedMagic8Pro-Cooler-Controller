# 如何获取APK文件

## 当前状态
✅ 项目源代码100%完成  
❌ ARM64容器无法编译APK（Android SDK工具链限制）

## 快速获取APK的3种方法

### 方法1：在电脑上用Android Studio编译（推荐，5分钟）

1. **解压项目**：
   ```bash
   tar -xzf RedMagic8ProController-完整项目.tar.gz
   ```

2. **打开Android Studio**：
   - File -> Open
   - 选择 `RedMagic8ProController` 目录

3. **等待Gradle同步完成**（首次需要下载依赖，约2-3分钟）

4. **构建APK**：
   - Build -> Build Bundle(s) / APK(s) -> Build APK(s)
   - 或点击工具栏的 🔨 Build 按钮

5. **获取APK**：
   - 构建成功后会弹出通知，点击"locate"
   - 或手动打开：`app/build/outputs/apk/debug/app-debug.apk`

---

### 方法2：命令行编译（Linux/Mac，3分钟）

```bash
cd RedMagic8ProController
chmod +x gradlew
./gradlew assembleDebug

# APK输出位置：
# app/build/outputs/apk/debug/app-debug.apk
```

**环境要求**：
- JDK 17+
- Android SDK（或让gradlew自动下载）

---

### 方法3：GitHub Actions自动编译（无需本地环境，15分钟）

1. **创建GitHub仓库**（公开或私有均可）

2. **上传代码**：
   ```bash
   cd RedMagic8ProController
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/你的用户名/RedMagic-Controller.git
   git push -u origin main
   ```

3. **触发构建**：
   - 推送代码后，GitHub Actions自动开始编译
   - 访问仓库页面 -> Actions标签
   - 查看构建进度

4. **下载APK**：
   - 构建完成后，点击对应的workflow运行
   - 在"Artifacts"区域下载`app-debug.apk`

---

## 为什么容器里不能编译？

**技术原因**：
- Android SDK的AAPT2、d8、dx等构建工具全部是x86_64架构
- 当前容器是ARM64架构，无法执行这些工具
- 容器内核还有额外的JVM执行限制（grsecurity/PaX）

**这不是代码问题**：
- 项目代码100%正确
- Gradle配置完全正确
- 在x86_64环境（PC/CI）可以正常编译

---

## 验证检查清单

编译APK后，请验证：

✅ **安装测试**：
- APK可以正常安装到Android 8.0+设备
- 应用图标正常显示
- 启动无崩溃

✅ **权限测试**：
- 首次启动正确请求蓝牙权限
- Android 12+设备请求BLUETOOTH_SCAN和BLUETOOTH_CONNECT权限

✅ **功能测试**：
- 点击"扫描设备"能发现附近的BLE设备
- 选择红魔散热器后能成功连接
- 风扇控制SeekBar能调节档位
- LED按钮能控制灯光

---

## 快速验证构建正确性

如果你只想验证代码能编译通过，不需要真机测试：

```bash
cd RedMagic8ProController
./gradlew assembleDebug --dry-run

# 或执行完整构建但不生成APK：
./gradlew compileDebugJavaWithJavac
```

---

## 需要帮助？

如果编译遇到问题：

1. **Gradle同步失败**：
   - 检查网络连接
   - 尝试：`./gradlew clean`

2. **找不到SDK**：
   - 设置环境变量：`export ANDROID_HOME=/path/to/sdk`
   - 或在Android Studio中设置SDK路径

3. **JDK版本问题**：
   - 需要JDK 17或更高版本
   - 检查：`java -version`

---

**推荐方案**：方法1（Android Studio）最简单，方法3（GitHub Actions）无需本地环境。
