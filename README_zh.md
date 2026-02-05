# SimpleShell-Mobile

[English](README.md)

一个简洁优雅的 Android SSH/SFTP 客户端。

## 功能特性

### 连接管理
- 创建、编辑、删除 SSH 连接
- 支持密码和私钥认证
- 凭据本地加密存储
- 连接分组管理
- 从 SimpleShell PC 版导入连接

### 终端
- 实时交互式 SSH 终端
- ANSI 颜色代码支持
- 双指缩放字体 (0.5x - 2.5x)
- 快捷键面板
- 前台服务保持后台连接

### SFTP 文件浏览器
- 浏览远程文件系统
- 创建和删除文件/文件夹
- 查看文件详情（大小、修改日期）
- 持久连接支持

### 个性化
- 主题模式：跟随系统、浅色、深色
- 动态取色支持 (Android 12+)
- 自定义主题颜色
- 多语言：English、简体中文

## 系统要求

- Android 8.0 (API 26) 或更高版本

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Hilt 依赖注入
- **数据库**: Room
- **SSH 库**: SSHJ
- **加密**: BouncyCastle

## 构建

### 环境要求
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35

### 构建步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/yourusername/SimpleShellMobile.git
   ```

2. 在 Android Studio 中打开项目

3. 同步 Gradle 并构建：
   ```bash
   ./gradlew assembleDebug
   ```

4. 安装到设备：
   ```bash
   ./gradlew installDebug
   ```

## 项目结构

```
app/src/main/java/com/example/simpleshell/
├── data/                 # 数据层
│   ├── importing/        # PC 版配置导入
│   ├── local/            # 本地数据源
│   │   ├── database/     # Room 数据库
│   │   └── preferences/  # DataStore 偏好设置
│   ├── remote/           # 远程数据（更新检查）
│   └── repository/       # 仓库层
├── di/                   # Hilt 模块
├── domain/model/         # 领域模型
├── service/              # Android 服务
├── ssh/                  # SSH 连接管理
└── ui/                   # 表现层
    ├── navigation/       # 导航
    ├── screens/          # UI 界面
    ├── theme/            # 主题
    └── util/             # UI 工具类
```

## 许可证

Apache License Version 2.0

## 贡献

欢迎贡献代码！请随时提交 Pull Request。
