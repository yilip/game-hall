# ⚠️ ngrok 自定义域名配置未完成

## 📋 当前状态

- ✅ 游戏大厅已启动：`http://localhost:8080`
- ✅ 临时公网地址：`https://judie-histogenetic-uncontentiously.ngrok-free.dev`
- ⏳ 自定义域名 `game.yilip.top` 待配置

---

## 🔧 需要完成的配置

### 1. 注册 ngrok 账号

访问：https://ngrok.com/signup

### 2. 获取 Authtoken

登录后访问：https://dashboard.ngrok.com/get-started/your-authtoken

复制你的 authtoken（类似：`2AbCdEfGhIjKlMnOpQrStUvWxYz1234567890`）

### 3. 配置 Authtoken

**方法一：命令行配置（推荐）**
```bash
ngrok config add-authtoken 你的 AUTHTOKEN
```

**方法二：编辑配置文件**

编辑 `C:\Users\yi_li\.ngrok2\ngrok.yml`：
```yaml
version: "2"
authtoken: 你的 AUTHTOKEN 填在这里

tunnels:
  game-hall:
    proto: http
    addr: 8080
    domain: game.yilip.top
```

### 4. 配置域名 DNS

登录你的域名管理后台，添加：

**CNAME 记录：**
- 主机记录：`game`
- 记录类型：`CNAME`
- 记录值：`your-tunnel-id.ngrok.io`（从 ngrok Dashboard 获取）

**或使用 A 记录：**
- 主机记录：`game`
- 记录类型：`A`
- 记录值：`34.197.186.218`（ngrok 的 IP，可能变化）

### 5. 启动自定义域名

```bash
ngrok http 8080 --domain=game.yilip.top
```

---

## 🚀 快速启动（当前可用）

**使用临时域名：**
```bash
cd D:\claw\projects\game-hall
.\start-game-hall.bat
```

临时域名：`https://judie-histogenetic-uncontentiously.ngrok-free.dev`

---

## 💡 建议

1. **临时使用**：继续使用 ngrok 免费域名（每次重启会变）
2. **长期使用**：完成上述配置，使用固定域名 `game.yilip.top`
3. **更稳定方案**：考虑 Cloudflare Tunnel（完全免费）

---

_创建时间：2026-03-20 23:02_
