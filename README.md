# APK MCP

把一台 Android 手机变成 **AI 能直接调用的 MCP 工具集**：AI 可以截屏看画面、读控件树、点按、滑动、输入文字、开关 App。

整个 App 不需要 root，不需要 adb，不需要电脑——**装完给两个权限就能用**。

```
AI（Claude / Cursor / 任意 MCP 客户端）
        │  MCP over HTTP
        ▼
   http://127.0.0.1:8765/mcp        ← 手机上的 APK MCP App
        │
        ├── MediaProjection    截图（眼睛）
        └── AccessibilityService  点击/滑动/输入（手）
```

---

## 一、它提供什么工具

| 工具 | 作用 |
|---|---|
| `screenshot` | 截当前屏幕，返回 JPEG + 图片尺寸 |
| `get_ui_tree` | 把界面控件压成文本（省 token，纯文本模型可用） |
| `tap` | 点坐标 |
| `long_press` | 长按 |
| `swipe` | 滑动（滚动、翻页、下拉通知栏） |
| `type_text` | 往输入框写文字 |
| `press_key` | back / home / recents / notifications / quick_settings |
| `find_and_tap` | **按文字找控件并点**，比手算坐标稳 |
| `scroll` | 上下滚动 |
| `launch_app` | 按名字或包名开 App |
| `list_apps` | 列出所有可启动 App |
| `current_app` | 当前前台包名 |
| `open_url` | 浏览器打开网址 |
| `wait` | 等界面动画 |
| `screen_size` | 真实尺寸 / 截图尺寸 / 缩放比 |
| `get_status` | 各权限是否就绪 |

### 坐标怎么算

`screenshot` 返回的图片可能被缩小过（省 token）。**tap/swipe 的 x/y 一律按截图图片像素给**，App 内部会自动换算回真实屏幕像素。

想按真实像素给就传 `"space":"real"`。

---

## 二、装好后怎么配

打开 App，控制台里三步：

1. **开启无障碍** —— 点「去开启」，在系统列表里找到「APK MCP 手机操作」打开
2. **开启屏幕捕获** —— 点「开启捕获」，系统弹窗点「立即开始」（Android 14+ 每次重启 App 都要重来一次）
3. **启动 MCP 服务** —— 点「启动服务」，通知栏会出现常驻通知

设置页可以改端口、加访问令牌、开局域网访问、调截图尺寸/质量。

---

## 三、接到 MCP 客户端

### 方式 A：客户端支持 Streamable HTTP

直接填：

```
http://127.0.0.1:8765/mcp
```

（手机本机上跑客户端时用 127.0.0.1；从电脑连就开「允许局域网访问」，填手机 IP。）

设了令牌的话加请求头：

```
Authorization: Bearer <你的令牌>
```

### 方式 B：客户端只支持 stdio（Claude Desktop / Cursor 等）

用 Termux 里的 `mcp-remote` 之类做桥接，或者自己写个 20 行的 stdio↔HTTP 转发脚本。

### 手动验证

```bash
# 健康检查
curl -s http://127.0.0.1:8765/health

# 工具列表
curl -s http://127.0.0.1:8765/mcp \
  -H 'content-type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# 截一张（存成图片）
curl -s http://127.0.0.1:8765/screenshot.jpg -o shot.jpg

# 点一下
curl -s http://127.0.0.1:8765/tool/tap \
  -H 'content-type: application/json' \
  -d '{"x":540,"y":1200}'

# 按文字点
curl -s http://127.0.0.1:8765/tool/find_and_tap \
  -H 'content-type: application/json' \
  -d '{"text":"设置"}'
```

---

## 四、REST 快捷接口

除了 MCP，还留了一套更顺手的 REST，方便用 shell/脚本直接玩：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 存活检查 |
| GET | `/tools` | 工具列表 |
| GET | `/status` | 状态 |
| GET | `/screenshot.jpg` | 直接拿一张 JPEG（响应头带尺寸） |
| GET | `/ui` | 控件树 |
| GET | `/apps` | App 列表 |
| POST | `/tool/{name}` | 调任意工具，body 是参数 JSON |

---

## 五、安全

- **默认只监听 127.0.0.1**，只有本机（Termux）能连。
- 要局域网访问必须手动打开开关，**强烈建议同时设置访问令牌**。
- 令牌校验支持 `Authorization: Bearer xxx`、裸 token 头、`?token=xxx` 三种。
- 服务可以随时在 App 里或通知栏停掉。
- 无障碍 + 投屏都是显式授权，系统随时可撤销。

> 这类工具等于把手机的完全操作权交给 AI。别在有支付、银行 App 的环境里长期开着局域网访问。

---

## 六、自己编译

### GitHub Actions（推荐，手机上也能触发）

推到 main 就会自动编译，产物在 Actions 页面的 Artifacts 里。

```bash
git clone <你的仓库> && cd apkmcp
git push   # 触发构建
gh run watch
gh run download
```

### 本地

```bash
./gradlew assembleDebug
```

需要 JDK 17+，Android SDK Platform 35，Build-Tools 35。

---

## 七、已知限制

- **Android 14+**：屏幕捕获每次进程重启都要重新授权（系统限制）
- **输入法**：`type_text` 走 `ACTION_SET_TEXT`，少数 App（部分银行、游戏）会拒绝
- **自定义绘制界面**：游戏、Flutter 自绘页面的控件树读不到，只能靠截图 + 坐标
- **华为/EMUI**：需要在「应用启动管理」里关掉本 App 的自动管理，否则后台服务会被杀
- **坐标精度**：截图缩放过会有几像素误差

---

## 八、License

MIT
