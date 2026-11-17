# HPCL Launcher

[![Java CI](https://github.com/KJLLin/HPCL-Launcher/actions/workflows/gradle.yml/badge.svg)](https://github.com/KJLLin/HPCL-Launcher/actions/workflows/gradle.yml)

以 [Hello Minecraft! Launcher](https://github.com/HMCL-dev/HMCL)（HMCL）3.16.4 为内核，按 [Plain Craft Launcher 2](https://github.com/Meloong-Git/PCL)（PCL2）**开源实现的设计参数**重构界面与动画的 Minecraft 启动器。

- **内核**：HMCL 的版本管理、实例管理、游戏/模组/整合包下载、账户登录、Java 管理、联机等能力完整保留
- **界面**：PCL2 的胶囊顶栏、纯白左栏、卡片流右栏、浅蓝对角渐变背景与 PCL 蓝强调色
- **数据隔离**：使用独立的 `.hpcl` 配置目录，不会读写 HMCL 的既有数据
- **许可**：GPL-3.0（见 [LICENSE](LICENSE)），与上游 HMCL 一致

> 本项目是社区二次开发版本，**非 HMCL 官方项目**，也与 PCL2 作者无关；相关名称与商标归各自所有者。

## 功能特性

### 界面复刻

- 五个顶层标签「启动 / 下载 / 联机 / 设置 / 更多」，与 PCL2 `FormMain.xaml` 的 `BtnTitleSelect0..4` 一一对应；点击「更多」进入真实页面而非下拉菜单
- 顶栏胶囊导航、纯白左栏、右栏卡片流、PCL2 斜向渐变背景（`ThemeRefreshMain` 的 HSL 195/210/225 换算）
- 页面切换动画：`InBack / OutBack / OutFluent / OutElastic / InFluent` 等缓动按 `ModAnimation.vb` 的时长与曲线实现；按钮 0.955 按压缩放、列表项选中动效、提示条 `AniDispose` 收缩淡出、"你知道吗" 预设卡片
- 左栏入场 stagger 动画（`MyPageLeft.TriggerShowAnimation`），在下载 / 设置 / 联机 / 更多等页面统一播放
- 启动器开场动画：`Angle -4° + Y 60 + 淡入`，按 250 / 600 / 500ms 与 100ms 延迟归位
- 顶栏为纯文字 `HPCL` 字标（17px 白色粗体），栏高 48px；五个胶囊标签居中
- 五个标签文案统一为 2 字（新增 `hpcl.nav.launch` 键，9 个语言包全部补齐），首项显示「启动」而非「启动游戏」
- 启动按钮为 PCL2 的固定双行槽：17px 主标题 + 11px 版本号贴底；无可用版本时按 `RefreshButtonsUI` Case 2 显示「下载游戏 / 未找到可用的游戏版本」
- 右栏含 PCL2 固定「启动日志」卡片，接真实启动事件（时间戳 + 最近 7 行，Consolas 等宽字体），不是假日志
- 主页「关于」卡片、更多 → 关于与鸣谢、完整关于页三处均提供 [HPCL 项目仓库](https://github.com/KJLLin/HPCL-Launcher) 链接
- 弹窗统一为 PCL2 样式：协议授权 / 协议更新 / 消息 / 警告（红色标题与遮罩）/ 输入共五种，2px 标题分隔线 + 描边按钮 + 倾斜回弹进出场

### 动效与操作手感

- **卡片折叠**（`PCLCollapsibleCard`，对应 PCL2 `MyCard.vb` 的 `CanSwap`）：点击标题栏切换折叠，箭头 250ms `OutFluent` 翻转 180°→0°，高度按距离分两段曲线（短距离 150ms、长距离匀速段 + 减速段），折叠态精确收至 40px，内容用 `Rectangle` clip 防溢出；已落地在「你知道吗？」与「启动日志」卡片
- **右下球堆栈**（`PCLExtraButton`，对应 `FormMain.xaml` 的 5 个 `MyExtraButton`）：40px 圆盘、`ColorBrush3 → 4` 悬停 120/150ms 渐变、0.85 按压缩放与 300ms `OutBack` 释放、1000ms `Ribble` 扩散圆环；实现了 PCL2 的「返回顶部」球（页面滚离顶部后出现，点击 300ms `OutFluent` 平滑回顶并收起）。投降 / 关服 / 音乐球在 HPCL 无对应功能，不造假入口
- **滚动惯性**（`PCLScrollViewer`，对应 `MyScrollViewer.vb`）：替换 HMCL 的 MaterialFX 摩擦模型，滚轮一格按 `WHEEL_TO_PCL = 3.0` 换算（WPF 120 ↔ JavaFX 40px），300ms `OutFluent` 缓动到目标；快滚是「延长目标」而非叠加动画，跟手且可精确停住；`FXUtils.smoothScrolling` 全局接入，所有页面手感统一
- 球堆栈固定 40px 足迹并 `pickOnBounds(false)`，不会吞掉卡片流的鼠标 / 滚轮事件

### 图标库

- 图标全部为可控 24×24 纯几何路径，**64 个语义图标**覆盖 `SVG` 的 **107 个枚举**，逐项按 PCL2 语义映射（启动三角 / 下载箭头 / wifi / 六边形齿轮 / 四宫格 / 镐子 / 放大镜……）
- 生成脚本与映射表随仓库提供：`config/icons/gen_icons.py` + `config/icons/icons.json`，可复现重建，避免再次出现图标错乱
- HPCL 方形像素 Logo：`config/launcher/` 内含 16 / 20 / 24 / 32 / 40 / 48 / 64 / 96 / 128 / 256 / 512 多档尺寸，另含 `HPCL.ico`（多帧 ICO）与 `patch_pe_icon.py`（直接修补 exe 的 PE 图标资源）
- 主题样式集中在 `HMCL/src/main/resources/assets/css/hpcl.css`（约 385 条规则），Java 侧由 `ui/pcl/PCLTheme.java` 统一注入

### 下载与任务

- 任务中心 `PCLDownloadCenter`：以 `TaskListener` 桥接真实 `TaskExecutor`，任务管理页 / 悬浮球 / 左栏共用同一份数据
- PCL2 蓝色下载悬浮球（`MyExtraButton` 复刻：自底部进度填充、速度胶囊、按压与 hover 动画），仅在有任务且不在任务页时显示
- 任务管理页：四组统计（总进度 / 下载速度 / 剩余文件 / 剩余线程）+ 卡片流；子任务行按 `✓ / NN% / ••• / ✕` 显示状态，失败可复制错误详情
- 进度按源码公式 `p = p * 0.9 + raw * 0.1` 平滑；活动任务硬钳制 99%，仅在 `onStop(success=true)` 时置 100%，避免"假 100%"

### 启动流程

- PCL2 风格启动进度页（`PCLLaunchProgressPage`）：左栏镐子 + 倾斜标题 + 四行信息网格（当前步骤 / 登录方式 / 启动进度 / 下载速度），右栏任务卡流
- 镐子加载动画（`PCLLoading`）：单一 Timeline 严格按 PCL2 时间轴（55° → -20° → +50° → +25°），触地火花带 ±45° 旋转淡出，支持 Error 状态与点击重试
- 登录方式按真实账户显示（离线模式 / 微软账户 / Authlib-Injector），修正了原先的硬编码

### 安装与账户

- 安装新游戏页：名称卡片 + 纵向加载器行（Forge / NeoForge / Fabric…）+ 蓝色胶囊「开始下载」大按钮
- 主页账号切换浮层，正版 / 离线徽标
- 页面加载卡 `PCLPageLoader`（居中白卡 + 镐子 + 进度文本），替代原 HMCL 的转圈加载

### 其他

- `.hpcl` 配置目录不再被设为隐藏属性，历史安装也会恢复可见
- 移除 HMCL 的预览版常驻横幅与右下角白色方形进度浮层（PCL2 源码中不存在）
- 关闭对上游 HMCL 自更新服务的访问，避免混入官方更新（可用 `-Dhpcl.update.enabled=true` 显式打开，仅在存在专用更新端点时使用）

## 环境要求

| 用途 | 要求 |
|---|---|
| 构建 | **JDK 17 或更高**，且该 JDK **自带 JavaFX**（Zulu 17 FX、Liberica Full 等）。普通 OpenJDK 不含 JavaFX，会导致编译失败 |
| 运行 | Java 17 及以上（推荐 21）；`MINIMUM_REQUIRED_JAVA_VERSION = 17`，`RECOMMENDED_JAVA_VERSION = 21` |
| Gradle | 无需单独安装，仓库自带 Gradle 9.6.1 Wrapper |
| 平台 | Windows / Linux / macOS（JavaFX 界面跨平台） |

## 构建

```bash
# 完整构建（编译 + 校验 + 打包）
./gradlew build --no-daemon --parallel

# 只打包可执行产物
./gradlew :HMCL:makeExecutables
```

构建产物位于 `HMCL/build/libs/`：

| 产物 | 说明 |
|---|---|
| `HPCL-<version>.jar` | 跨平台可执行 fat jar（shadowJar） |
| `HPCL-<version>.exe` | Windows 启动器：原生 `HMCLauncher.exe` 壳 + jar 追加拼接 |
| `HPCL-<version>.sh` | Linux / macOS 自解压脚本（需 `chmod +x`） |
| `HPCL-<version>.deb` | Debian / Ubuntu 安装包（`:HMCL:makeDeb`） |

每次打包同时输出 `sha1 / sha256 / sha512` 校验文件。

**关键约定**

- exe 壳模板为 `config/launcher/HMCLauncher.exe`（已随仓库提供，以 `git add -f` 有意纳入，`*.exe` 的忽略规则对其不适用）；该文件缺失时 `makeExecutables` 无法生成 exe
- 入口类：`org.jackhuang.hmcl.Main`
- 版本号来自 `hmcl.version`；`VERSION_TYPE` 环境变量可设 `stable / nightly / dev`（未设置时默认标记为 `dev`，即「开发中，尚无正式版」；不会伪装成 `stable`）
- 微软登录 / CurseForge 需要构建时注入 `MICROSOFT_AUTH_ID`、`CURSEFORGE_API_KEY`（见下方「服务凭据」）；未配置时对应功能不可用，但 Modrinth、离线账户、外置登录均正常

<a id="credentials"></a>

## 服务凭据（自行注册，不随发行版内置）

HPCL 是社区分支，**不内置 HMCL 官方的微软 OAuth 客户端 ID 与 CurseForge API Key**。这两项属于官方账号的私有凭据，复用会违反服务条款、消耗官方配额，且官方可随时吊销。因此公开发布的版本中两者为空：微软登录按钮会明确说明原因并禁用，CurseForge 下载源不出现（**Modrinth 完全可用**）。这不是安装损坏。

如需启用，请自己注册后在**构建时**注入（值不写入本仓库、不提交、不写进下载站或 Release 正文）：

### 1. 微软登录 `MICROSOFT_AUTH_ID`

1. 打开 [Azure 门户 → Microsoft Entra ID → 应用注册 → 新注册](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade)。
2. 名称随意；**受支持的账户类型**选「**仅个人 Microsoft 账户**」（Minecraft 账户是消费者账户，代码使用 `consumers` 租户，`OAuth.MICROSOFT` 指向 `login.live.com` / `login.microsoftonline.com/consumers`）。
3. **重定向 URI**：平台选「移动和桌面应用程序」，添加 `http://localhost/auth-response`。本机回环监听端口为 `29111–29115`（`OAuthServer.startServer`），微软对 localhost 重定向**忽略端口**，故只需这一条。
4. 创建后进入「身份验证 → 高级设置」，将「**允许公共客户端流**」设为**是**（设备代码流，无 client secret）。API 权限无需添加：登录时动态请求 `XboxLive.signin offline_access`（`MicrosoftService.SCOPE`）。
5. 复制「**应用程序(客户端) ID**」（36 位 GUID）。
6. **重要**：新建的 Azure 应用在通过 Mojang 审核前，`api.minecraftservices.com/authentication/login_with_xbox` 会返回 403 `Invalid app registration`。需按 [Minecraft Java App ID 审核](https://aka.ms/mce-reviewappid) 提交客户端 ID、应用名、项目地址与用途说明，审核通常需数天到数周。这是自建凭据最常见的「配了还是登不上」原因。

### 2. CurseForge `CURSEFORGE_API_KEY`

1. 注册 / 登录 [CurseForge](https://www.curseforge.com/) 账号。
2. 打开 [CurseForge API Console](https://console.curseforge.com/)，接受 API 服务条款。
3. 在「API Keys」创建新密钥（60 位），**立即复制**。
4. 该密钥用于 `https://api.curseforge.com` 的 `x-api-key` 请求头（`CurseForgeRemoteAddonRepository.withApiKey`）。请勿提交到版本库。

### 3. 注入方式

```bash
# 构建时注入（推荐；值只存在于本机构建产物中）
MICROSOFT_AUTH_ID='你的客户端ID' \
CURSEFORGE_API_KEY='你的APIKey' \
VERSION_TYPE=dev \
./gradlew :HMCL:build --no-daemon --parallel
```

发布时把两个值放到 CI 的 **Repository Secrets**（`release.yml` 已引用 `secrets.MICROSOFT_AUTH_ID` / `secrets.CURSEFORGE_API_KEY`），不要放进 `vars` 或代码。

**临时覆盖（不重新打包）**：两个值也可在运行时用 JVM 系统属性覆盖，便于先验证凭据是否可用：

```bash
java -Dhmcl.microsoft.auth.id='你的客户端ID' \
     -Dhmcl.curseforge.apikey='你的APIKey' \
     --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.web \
     -jar HPCL-3.16.SNAPSHOT.jar
```

代码优先级为 `System.getProperty` > `assets/hmcl.properties`（`OAuthServer.Factory.getClientId`、`CurseForgeRemoteAddonRepository.API_KEY`）。

> **说明**：`unofficial.hint` 提示由 `IntegrityChecker.isOfficial()`（签名校验 / `GITHUB_SHA`）决定，与 `VERSION_TYPE` 无关，改成 `dev` 也不会消失。HPCL 作为 fork 显示该提示是诚实的，但文案已改为中性的来源与校验说明，不再写「请下载官方版本」这类误导内容。

<a id="auto-update"></a>

## 自动更新（GitHub Releases）

HPCL 是 fork，**不连接 HMCL 官方更新端点**（否则会被官方包覆盖）。更新源为本仓库的 GitHub Releases，并提供两个用户可选通道：

| 通道 | 设置值 | 匹配的 Release | 当前状态 |
|---|---|---|---|
| **稳定版** | `STABLE` | `prerelease=false`，且 tag 不含 `SNAPSHOT / dev / beta / alpha / nightly / rc` 等预览标记 | 暂未发布正式版。手动检查会提示「当前更新通道暂无可用版本」，自动检查静默 |
| **测试版** | `BETA`（预览构建默认） | 所有已发布 Release，含 GitHub pre-release | `v3.16.SNAPSHOT` 即在此通道 |

> 通道判定同时看 GitHub 的 `prerelease` 标记和 tag 中的预览词，避免有人忘了勾 pre-release 却把 SNAPSHOT 发到稳定通道。BETA 能看到稳定版，STABLE 永远不会收到预发布。

- **首次启动**只询问一次是否开启，**不询问通道**：通道按当前构建自动决定（`stable` 构建 → 稳定版，`dev` / `beta` / `nightly` 及其他预览构建 → 测试版），写入 `UserState.autoUpdatePromptVersion`。已开启自动检测的用户升级后不会被弹窗打扰，但通道会自动对齐当前构建。之后可在「设置 → 全局设置 → 更新」手动更改 `autoUpdateCheckEnabled`、`autoUpdateChannel`、`autoUpdateMode`。
- **网络失败静默**：`api.github.com` 在国内可能超时、被限流（HTTP 403 rate limit）或返回不完整数据。自动检测遇到任何失败只写日志，**不弹窗、不 Toast**；只有用户在关于页 / 设置页手动点「立即检查」时才会报告失败。「当前通道暂无可用版本」同样静默，手动检查时给出准确提示而不是通用错误。
- **更新方式**（`autoUpdateMode`）：
  - `NOTIFY`（默认）：Toast + PCL 风格确认卡片，可立即更新或稍后，标题带上当前通道名。
  - `AUTO`：后台下载 → 用 `SHA256SUMS.txt` 校验 → 启动辅助进程替换当前 jar/exe（保留 PE 头）→ 自动重启。
- 版本判断不只看 tag：同 tag 重新打包时，会用发布包校验值与**当前运行文件**的 SHA-256 比对，避免漏掉重建版本。已处理过的 `(通道, tag, hash)` 组合会记入 `launcher-state.json`，防止自动更新循环；切换通道后仍可再次收到同一 Release。
- 下载包必须带有 `SHA256SUMS.txt` 且校验通过才会应用；校验失败静默放弃，不会替换现有程序。
- 当前以 `.deb` 安装时无法原位替换，会自动回退到 `.jar` 资产；`.exe` / `.sh` 通过 `ExecutableHeaderHelper` 保留启动头。
- **发布约定**：每个 Release 都须带 5 个产物（`HPCL-<版本>.exe/.jar/.sh/.deb` + `HPCL-source.dat`）和覆盖这 5 个产物的 `SHA256SUMS.txt`。要发布稳定版，新建语义化 tag 并保持 `prerelease=false`；预发布保持 `prerelease=true`。两端一起更新，避免本地新包被旧 Release 判为「有更新」。

## 运行

```bash
# 直接运行 fat jar
java -jar HMCL/build/libs/HPCL-3.16.SNAPSHOT.jar

# 或从源码启动（开发调试）
./gradlew :HMCL:run
```

首次启动会在工作目录下创建 `.hpcl`（配置、账户、模组、下载缓存），与 HMCL 的 `.hmcl` 相互独立。

## 项目结构

```
HPCL-Launcher/
├── HMCL/                  # 启动器主体：JavaFX 界面、业务逻辑、PCL 复刻页面
│   └── src/main/
│       ├── java/org/jackhuang/hmcl/
│       │   ├── ui/pcl/    # PCL 复刻组件（PCLTheme / PCLLoading / PCLDownloadCenter /
│       │   │              #  PCLDownloadBall / PCLLaunchProgressPage / PCLHubPage ...）
│       │   └── ui/        # 上游 HMCL 页面与控件
│       └── resources/assets/
│           ├── css/       # hpcl.css（PCL 主题）
│           ├── icons/     # 图标与 Logo
│           └── lang/      # 多语言
├── HMCLCore/              # 核心库：账户、版本解析、下载、模组、启动参数
├── HMCLBoot/              # 引导模块
├── buildSrc/              # Gradle 构建逻辑（打包、JavaFX、deb、文档生成等任务）
├── minecraft/libraries/   # HMCLTransformerDiscoveryService / HMCLMultiMCBootstrap
├── config/
│   ├── icons/             # gen_icons.py + icons.json（图标生成脚本与映射表）
│   ├── launcher/          # HMCLauncher.exe 壳模板、HPCL.ico、多尺寸 Logo、PE 图标补丁脚本
│   ├── checkstyle/        # 代码风格配置
│   └── project.properties
├── lib/                   # 本地依赖：JFoenix、fxsvgimage
├── docs/                  # 上游文档（多语言 README、贡献指南、本地化、发布节奏）
└── .github/workflows/     # CI：gradle / release / check-codes / mirror
```

## 与上游的关系

- **内核继承**：`HMCL` / `HMCLCore` / `HMCLBoot` 来自 HMCL 3.16.4（`UPSTREAM_VERSION = "3.16.4"`），保留其 GPL-3.0 许可与版权声明
- **界面改造**：新增 `ui/pcl` 包与 `hpcl.css`，按 PCL2 开源实现的 XAML 布局参数与动画曲线重写视觉层，未复制 PCL2 的代码文件
- **独立身份**：`NAME = "HPCL"`、`FULL_NAME = "HPCL Launcher"`，使用独立配置目录，不访问上游自更新服务
- **CI 适配**：产物名与 artifact glob 已改为 `HPCL-*`，`release.yml` 中对应前缀同步调整

## 致谢

- [HMCL](https://github.com/HMCL-dev/HMCL) — 上游内核与长期维护的启动器实现
- [PCL2](https://github.com/Meloong-Git/PCL) — 界面设计、动画参数的参考来源
- [JavaFX](https://openjfx.io/)、[JFoenix](https://github.com/sshahine/JFoenix)、[fxsvgimage](https://github.com/Glavo/fxsvgimage) 等开源项目

## 许可证

本项目以 **GNU General Public License v3.0** 发布，完整条款见 [LICENSE](LICENSE)。分发衍生版本时请同样以 GPL-3.0 开源，并保留上游版权声明。

Minecraft 是 Mojang Studios 的商标，本项目与 Mojang 或 Microsoft 无隶属关系；使用前请确保拥有正版游戏账号。
