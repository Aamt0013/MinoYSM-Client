<div align="center">
  <img src="assets/banner.jpg" alt="MinoYSM Client"/>
  <h1>MinoYSM Client</h1>
  <p>Yes Steve Model 的 Minecraft 26.2 纯客户端移植版</p>
  <p>
    <img src="https://img.shields.io/badge/Minecraft-26.2-62B47A" alt="Minecraft"/>
    <img src="https://img.shields.io/badge/Fabric-0.19.3+-blue" alt="Fabric"/>
    <img src="https://img.shields.io/badge/Java-25-orange" alt="Java"/>
    <img src="https://img.shields.io/badge/license-MIT-green" alt="License"/>
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

当前版本：**v1.2.5** · 7.93 MB

```
MinoYSM-Client-26.2-1.2.5.jar
SHA256: 180DD99EF4C29770AAE5203A259DE33207FD9C40601369191745C44BBFF6A1A5
```

---

## 这是什么

把 [Yes Steve Model](https://github.com/OpenYSM/OpenYSM)（YSM）移植到 **Minecraft 26.2 / Fabric**，并改造成**纯客户端模组**：

- **不需要服务器安装**。进任何服务器（原版服、整合服都行）都能看到自己的模型。
- 模型从本地 `config/yes_steve_model/custom/` 加载，不依赖服务端下发。
- 装的是客户端模组，服务端无需任何改动。

> 模组内部 id 仍是 `yes_steve_model`，这是刻意保留的 —— 模型包格式、配置文件路径、与 YSM 服务器的通信协议都依赖它。因此**不能与其它 YSM 版本同时安装**。

---

## 快速开始

1. 安装 **Fabric Loader 0.19.3+** 与 **Java 25**
2. 从 [Releases](https://github.com/Aamt0013/MinoYSM-Client/releases/latest) 下载 jar，放进 `.minecraft/mods/`
3. 启动游戏，按 **Y** 打开模型界面选模型
4. 想加自己的模型：把 `.ysm` 文件丢进 `config/yes_steve_model/custom/`，重启游戏

> **装之前先看一眼 `mods/` 里有没有其它 YSM。** mod id 都是 `yes_steve_model`，同时存在两个会直接崩溃。

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

- [Yes Steve Model](https://github.com/OpenYSM/OpenYSM) —— 原始模组，MIT
- [OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated) —— 高版本移植起点

26.2 移植与纯客户端改造由本项目完成。

以 **MIT 许可证**发布，详见 [LICENSE](LICENSE)。

> 模型版权归各模型作者所有，本项目不附带任何第三方模型。
