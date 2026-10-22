<div align="center">
  <img src="assets/banner.jpg" alt="MinoYSM Client"/>
  <h1>MinoYSM Client</h1>
  <p>Yes Steve Model 的 Minecraft 26.2 纯客户端移植版</p>
  <p><strong>非官方移植，与 Yes Steve Model 官方团队无关</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Minecraft-26.2-62B47A" alt="Minecraft"/>
    <img src="https://img.shields.io/badge/Fabric-0.19.3+-blue" alt="Fabric"/>
    <img src="https://img.shields.io/badge/Java-25-orange" alt="Java"/>
    <img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License"/>
  </p>
  <p>
    <a href="https://github.com/Aamt0013/MinoYSM-Client/releases/latest">
      <img src="https://img.shields.io/github/v/release/Aamt0013/MinoYSM-Client?label=%E4%B8%8B%E8%BD%BD&style=for-the-badge" alt="下载"/>
    </a>
  </p>
</div>

---

## 下载

前往 **[Releases](https://github.com/Aamt0013/MinoYSM-Client/releases/latest)** 下载最新的 `.jar`。

当前版本：**v1.2.8** · 7.63 MB

```
MinoYSM-Client-26.2-1.2.8.jar
SHA256: 2B240A53064D823F49C093DCC8F41B1DBF9D43EA4971C0CA898DAD9D995E2039
```

---

## 这是什么

把 [Yes Steve Model](https://github.com/OpenYSM/OpenYSM)（YSM）移植到 **Minecraft 26.2 / Fabric**，并改造成**纯客户端模组**：

- **不需要服务器安装**。进任何服务器（原版服、整合服都行）都能看到自己的模型。
- 模型从本地 `config/yes_steve_model/custom/` 加载，不依赖服务端下发。
- 装的是客户端模组，服务端无需任何改动。

> 模组 id 是 `minoysm`，与官方版的 `yes_steve_model` 分开，因此不会再出现「Duplicate mod id」这类崩溃。但**资源命名空间仍保留 `yes_steve_model`** —— 模型包、贴图路径、`config/yes_steve_model/` 目录都依赖它，改了会让已有用户的模型文件夹失效。
>
> 注意：虽然 id 分开了，两个模组**仍然不能同时启用**。它们都会接管玩家模型渲染，同时装会重复绘制。检测到这种情况时日志里会有一条明确的错误提示。

---

## 快速开始

1. 安装 **Fabric Loader 0.19.3+** 与 **Java 25**
2. 从 [Releases](https://github.com/Aamt0013/MinoYSM-Client/releases/latest) 下载 jar，放进 `.minecraft/mods/`
3. 启动游戏，按 **Y** 打开模型界面选模型
4. 想加自己的模型：把 `.ysm` 文件丢进 `config/yes_steve_model/custom/`，重启游戏

> **装之前先看一眼 `mods/` 里有没有其它 YSM。** 两个模组会同时接管玩家渲染，重复绘制甚至崩溃。模组 id 已经分开，不会在启动时硬崩，但日志里会提示冲突。

详细步骤见 [安装说明](docs/INSTALL.md)。

---

## 特性

| | |
|---|---|
| **纯客户端运行** | 服务器没装 YSM 也能用，单人/多人一致 |
| **本地模型加载** | 扫描 `custom/` 目录，自行解密解析 `.ysm` |
| **多种模型格式** | YSGP v1/v2/v3 加密包、文件夹模型（`ysm.json` 或 `main.json`+`arm.json`） |
| **完整动画系统** | Molang 表达式、动画控制器、额外动画轮盘 |
| **个性化设置持久化** | 轮盘里的勾选/单选/滑条退出重进后保留 |
| **本地选择记忆** | 上次选的模型和贴图自动套用 |
| **黑名单** | 用正则禁用不需要的内置模型 |
| **启动优化** | 解析缓存、贴图缓存、透光扫描缓存、数值驻留 |

---

## 按键

| 默认键 | 功能 |
|---|---|
| `Y` | 打开玩家模型界面 |
| `Z` | 打开轮盘动画 |
| `L` | 锁定轮盘 |
| `P` | 打开额外玩家渲染配置 |
| `B` | 打开动画调试 |

均可在「选项 → 控制」中修改。

---

## 模型目录

```
config/yes_steve_model/
├── custom/          ← 你自己的模型放这里（.ysm 文件或模型文件夹）
├── built/           ← 内置模型解压目录（自动维护，别手动改）
├── auth/            ← 授权模型
├── cache/           ← 解析/贴图缓存（可安全删除，会重建）
├── blacklist.txt    ← 内置模型黑名单
├── local_selection.json   ← 上次选择的模型/贴图
└── molang_vars.json       ← 轮盘个性化设置
```

模型格式与目录规则的细节见 [模型说明](docs/MODELS.md)。

---

## 内置模型

**只有 `default` 一个** —— 一个基础人形模型，用于兜底和动画骨架继承。

原版 YSM 附带的酒狐系列（`wine_fox`）和杂项模型（`misc`）**已从模组中移除**，需要的话请自行放入 `custom/`。

---

## 已知限制

这些是架构层面的限制，不是 bug：

- **看不到别人的模型**。服务器没装 YSM 时，服务端不会告诉客户端"谁用了什么模型"。这是 YSM 服务端权威架构决定的，纯客户端无法绕过。
- **`.zip` 模型不会自动加载**。文件夹模型如果打包成 zip，需要先解压成文件夹再放进 `custom/`。
- **模型数量影响启动时间**。每个模型首次加载要解析几何+动画+贴图，实测 61 个模型冷启动约 10 秒。建议只保留常用模型。
- **需要 Java 25**。26.x 的最低要求，低版本无法启动。

---

## 从源码构建

见 [构建说明](docs/BUILD.md)。

---

## 版本历史

见 [CHANGELOG.md](CHANGELOG.md)。

---

## 致谢与许可

本项目基于以下开源项目：

- [Yes Steve Model](https://github.com/YesSteveModel/YesSteveModel) —— 原始模组，Apache-2.0
- [OpenYSM](https://github.com/OpenYSM/OpenYSM) —— 开源替代实现，MIT
- [OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated) —— 高版本移植起点，MIT

26.2 移植与纯客户端改造由本项目完成。

以 **Apache License 2.0** 发布，详见 [LICENSE](LICENSE)。第三方组件、上游归属与内置模型资产的许可说明见 [NOTICE.md](NOTICE.md)。

> 模型版权归各模型作者所有，本项目不附带任何第三方模型。内置的 `default` 模型为 CC0。
