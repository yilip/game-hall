# 🌐 ngrok 自定义域名配置指南

## 📋 前提条件

1. **拥有域名**: `yilip.top`
2. **ngrok 账号**: 需要注册并获取 authtoken
3. **DNS 管理权限**: 能修改域名的 DNS 记录

---

## 🔧 配置步骤

### 步骤 1: 注册 ngrok 账号

1. 访问 https://ngrok.com
2. 点击 "Sign Up" 注册账号
3. 登录 Dashboard

### 步骤 2: 获取 Authtoken

1. 登录后访问：https://dashboard.ngrok.com/get-started/your-authtoken
2. 复制你的 authtoken（类似：`2AbCdEfGhIjKlMnOpQrStUvWxYz1234567890`）

### 步骤 3: 配置 ngrok

编辑配置文件：`C:\Users\yi_li\.ngrok2\ngrok.yml`

```yaml
version: "2"
authtoken: 你的 AUTHTOKEN 填在这里

tunnels:
  game-hall:
    proto: http
    addr: 8080
    domain: game.yilip.top
```

### 步骤 4: 配置 DNS 解析

登录你的域名管理后台（阿里云/腾讯云/GoDaddy 等），添加以下 DNS 记录：

#### 添加 CNAME 记录

| 主机记录 | 记录类型 | 记录值 | TTL |
|----------|----------|--------|-----|
| `game` | CNAME | `your-tunnel-id.ngrok.io` | 10 分钟 |

**注意**: `your-tunnel-id.ngrok.io` 需要先从 ngrok Dashboard 获取

或者使用 **A 记录**（推荐）：

| 主机记录 | 记录类型 | 记录值 | TTL |
|----------|----------|--------|-----|
| `game` | A | `34.197.186.218` | 10 分钟 |
| `game` | A | `35.171.156.148` | 10 分钟 |

ngrok 的 IP 地址可能会变，建议从 Dashboard 获取最新的 IP。

### 步骤 5: 启动 ngrok

**方法一：使用配置文件**
```bash
ngrok start game-hall --config C:\Users\yi_li\.ngrok2\ngrok.yml
```

**方法二：命令行指定域名**
```bash
ngrok http 8080 --domain=game.yilip.top
```

---

## 🚀 快速启动脚本

创建 `start-with-domain.bat`：

```batch
@echo off
echo 启动游戏大厅...
cd /d "%~dp0"
start "游戏大厅" cmd /k "mvn spring-boot:run -DskipTests"
timeout /t 10 /nobreak >nul

echo 启动 ngrok 自定义域名...
start "ngrok" cmd /k "ngrok http 8080 --domain=game.yilip.top"

echo 访问地址：http://game.yilip.top
pause
```

---

## ✅ 验证配置

1. **检查 DNS 解析**
   ```bash
   ping game.yilip.top
   ```
   应该能解析到 ngrok 的 IP

2. **访问测试**
   - 本地：`http://localhost:8080`
   - 公网：`http://game.yilip.top`

---

## 💡 备选方案

### 方案 A: 使用 ngrok 免费域名

如果不想配置 DNS，可以使用 ngrok 提供的免费域名：

```bash
ngrok http 8080
```

但每次重启域名都会变。

### 方案 B: 使用 Cloudflare Tunnel

完全免费且稳定：

1. 注册 Cloudflare 账号
2. 将域名 DNS 托管到 Cloudflare
3. 安装 cloudflared
4. 创建 Tunnel

### 方案 C: 使用 frp 内网穿透

需要一台有公网 IP 的服务器：

1. 购买 VPS（阿里云/腾讯云）
2. 搭建 frp 服务端
3. 本地运行 frp 客户端

---

## 🔧 故障排查

### 问题 1: DNS 解析失败
```bash
# 检查 DNS 是否生效
nslookup game.yilip.top

# 清除本地 DNS 缓存
ipconfig /flushdns
```

### 问题 2: ngrok 提示域名已被占用
- 域名可能已被其他 ngrok 用户使用
- 尝试更换子域名：`game-hall.yilip.top` 或 `play.yilip.top`

### 问题 3: 连接超时
- 检查 ngrok 是否正常运行
- 检查 8080 端口是否被占用
- 检查防火墙设置

---

## 📊 完整配置示例

### ngrok.yml 完整配置
```yaml
version: "2"
authtoken: 2AbCdEfGhIjKlMnOpQrStUvWxYz1234567890

region: ap  # 选择亚太地区，速度更快

tunnels:
  game-hall:
    proto: http
    addr: 8080
    domain: game.yilip.top
    inspect: true
    
  # 可以添加更多隧道
  # test:
  #   proto: http
  #   addr: 8081
  #   domain: test.yilip.top
```

---

## 🎯 最终效果

配置成功后：
- 访问 `http://game.yilip.top` 即可玩游戏
- 域名固定，不会变化
- 可以分享给朋友长期使用

---

_配置时间：2026-03-20 | 🦊 OpenClaw_
