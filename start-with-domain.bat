@echo off
chcp 65001 >nul
echo.
echo ========================================
echo   🌐 游戏大厅 - 自定义域名启动
echo ========================================
echo.

REM 1. 启动 Spring Boot
echo [1/3] 启动游戏大厅...
cd /d "%~dp0"
start "游戏大厅" cmd /k "mvn spring-boot:run -DskipTests"
echo ⏳ 等待服务启动...
timeout /t 10 /nobreak >nul
echo.

REM 2. 启动 ngrok 自定义域名
echo [2/3] 启动 ngrok 自定义域名...
echo 💡 提示：首次使用需要先配置域名
echo    配置文件：%USERPROFILE%\.ngrok2\ngrok.yml
echo.
start "ngrok" cmd /k "ngrok http 8080 --domain=game.yilip.top"
echo ⏳ 等待 ngrok 启动...
timeout /t 5 /nobreak >nul
echo.

REM 3. 显示访问地址
echo ========================================
echo   ✅ 服务已启动！
echo ========================================
echo.
echo 📍 本地访问：http://localhost:8080
echo 🌐 公网访问：http://game.yilip.top
echo.
echo ⚠️ 注意:
echo   - 首次使用需要配置 DNS 解析
echo   - 详见：ngrok-domain-setup.md
echo.
echo ========================================
echo.

REM 打开浏览器
start http://localhost:8080

pause
