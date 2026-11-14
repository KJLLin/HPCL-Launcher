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

### 图标库

- 图标全部为可控 24×24 纯几何路径，**64 个语义图标**覆盖 `SVG` 的 **107 个枚举**，逐项按 PCL2 语义映射（启动三角 / 下载箭头 / wifi / 六边形齿轮 / 四宫格 / 镐子 / 放大镜……）
- 生成脚本与映射表随仓库提供：`config/icons/gen_icons.py` + `config/icons/icons.json`，可复现重建，避免再次出现图标错乱
- HPCL 方形像素 Logo：`config/launcher/` 内含 16 / 20 / 24 / 32 / 40 / 48 / 64 / 96 / 128 / 256 / 512 多档尺寸，另含 `HPCL.ico`（多帧 ICO）与 `patch_pe_icon.py`（直接修补 exe 的 PE 图标资源）
- 主题样式集中在 `HMCL/src/main/resources/assets/css/hpcl.css`（约 357 条规则），Java 侧由 `ui/pcl/PCLTheme.java` 统一注入

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
- 版本号来自 `hmcl.version`；`VERSION_TYPE` 环境变量可设 `stable / nightly / dev`（未设置时非官方构建标记为 `unofficial`）
- CI 的微软登录 / CurseForge 需要仓库 Secrets：`MICROSOFT_AUTH_ID`、`CURSEFORGE_API_KEY`（本地构建可不配置，对应功能不可用）

## 运行

```bash
# 直接运行 fat jar
java -jar HMCL/build/libs/HPCL-3.16.4.jar

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
