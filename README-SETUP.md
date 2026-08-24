# 🚀 游戏大厅项目 - 环境配置指南

## ⚠️ 缺少 JDK

当前系统只安装了 JRE，需要安装 JDK 才能编译运行 Spring Boot 项目。

---

## 📦 方案一：使用 IDEA 内置 JDK（推荐）

IntelliJ IDEA 自带 JDK，可以直接使用：

### 1. 在 IDEA 中配置
1. 打开 IDEA → `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`
2. 设置 `JRE` 为 IDEA 内置的 JDK（通常是 `17` 或 `21`）

### 2. 或者手动指定 JAVA_HOME
```powershell
# 找到 IDEA 的 JDK 路径（通常在下面位置）
$ideaJdk = "D:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr"
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $ideaJdk, "User")
$env:JAVA_HOME = $ideaJdk
$env:Path = "$ideaJdk\bin;" + $env:Path
```

---

## 📦 方案二：安装独立 JDK

### 下载 JDK 17
- **官网**: https://www.oracle.com/java/technologies/downloads/#java17
- **国内镜像**: https://repo.huaweicloud.com/java/jdk/

### 安装后配置环境变量
```powershell
# 假设安装在 C:\Program Files\Java\jdk-17
$javaHome = "C:\Program Files\Java\jdk-17"
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;" + $env:Path
```

### 验证安装
```powershell
java -version
javac -version
```

---

## 🎮 运行游戏大厅

配置好 JDK 后，在项目根目录执行：

```powershell
# 编译
mvn clean compile

# 运行（内嵌 Tomcat）
mvn spring-boot:run
```

或者直接右键点击 `GameHallApplication.java` → `Run`

---

## 🌐 访问游戏大厅

启动成功后访问：
```
http://localhost:8080
```

---

## 🔧 快速修复脚本

运行以下 PowerShell 脚本自动配置：

```powershell
cd D:\claw\projects\game-hall
.\setup-jdk.ps1
```

---

_创建时间：2026-03-20 | 🦊 OpenClaw_
