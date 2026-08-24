# 🎮 游戏大厅 - 快速启动脚本
# 用法：双击运行或命令行执行 .\start-game-hall.bat

@echo off
chcp 65001 >nul
echo.
echo ========================================
echo   🎮 游戏大厅启动脚本
echo ========================================
echo.

REM 1. 检查 Java 环境
echo [1/3] 检查 Java 环境...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 未检测到 Java，请安装 JDK 17+
    echo 下载地址：https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
echo ✅ Java 已安装
java -version
echo.

REM 2. 启动 Spring Boot 应用
echo [2/3] 启动游戏大厅...
cd /d "%~dp0"
start "游戏大厅" cmd /k "mvn spring-boot:run -DskipTests"
echo ⏳ 等待服务启动...
timeout /t 10 /nobreak >nul
echo.

REM 3. 启动 ngrok 映射
echo [3/3] 启动 ngrok 公网映射...
start "ngrok" cmd /k "ngrok http 8080 --log=stdout"
echo ⏳ 等待 ngrok 启动...
timeout /t 5 /nobreak >nul
echo.

REM 4. 显示访问地址
echo ========================================
echo   ✅ 游戏大厅已启动！
echo ========================================
echo.
echo 📍 本地访问：http://localhost:8080
echo.
echo 🌐 公网访问：请查看 ngrok 窗口中的 URL
echo    (格式：https://xxx-xxx.ngrok-free.dev)
echo.
echo 💡 提示：
echo   - 按 Ctrl+C 可停止当前服务
echo   - 关闭所有窗口可完全退出
echo.
echo ========================================
echo.

REM 打开浏览器
start http://localhost:8080

pause
