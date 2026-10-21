# 安装说明

## 前置条件

| 项目 | 版本要求 |
|---|---|
| Minecraft | **26.2** |
| Fabric Loader | **0.19.3** 或更高 |
| Java | **25** 或更高 |

> Java 25 是 Minecraft 26.x 的硬性要求，用 Java 21 或更低版本无法启动游戏。

---

## 安装步骤

### 1. 确认 Fabric 已装好

启动器里能看到 `fabric-loader-0.19.3-26.2` 这个版本即表示就绪。如果还没装，先装 Fabric Loader。

### 2. 放入模组

把 `MinoYSM-Client-26.2-1.2.2.jar` 复制到：

```
.minecraft/mods/
```

> 如果用的是版本隔离的启动器（PCL、HMCL 等），路径通常是
> `.minecraft/versions/<版本名>/mods/`。

### 3. 确认没有其它 YSM

**这是最容易踩的坑。** 模组内部 id 是 `yes_steve_model`，和原版 YSM、其它移植版完全相同。

检查 `mods/` 目录，**只能保留一个** YSM 类模组。如果同时放了两个，游戏会在启动时报重复 mod id 并崩溃。

需要一并移除的文件名特征：

- `MinoYSM-*.jar`
- `openysm-*.jar`
- `YesSteveModel-*.jar`
- 任何带 `ysm` / `yes_steve_model` 字样的旧版本

### 4. 启动游戏

进世界后按 **Y** 打开模型界面。能看到模型列表就说明装好了。

---

## 添加自己的模型

把 `.ysm` 文件放进：

```
config/yes_steve_model/custom/
```

然后**重启游戏**（模型列表只在进世界时扫描）。

支持两种形式：

- **单个 `.ysm` 文件** —— 直接放进去
- **模型文件夹** —— 文件夹里包含 `ysm.json`，或同时有 `main.json` 和 `arm.json`

> `.zip` 压缩包不会被自动识别。如果是 zip 格式的模型包，先解压成文件夹再放进去。

详细规则见 [模型说明](MODELS.md)。

---

## 常见问题

### 模型列表是空的

1. 确认 `config/yes_steve_model/custom/` 里确实有 `.ysm` 文件或模型文件夹
2. 查看日志 `logs/latest.log`，搜索 `[YSM]`，看有没有解析失败记录
3. 确认文件没有损坏（部分下载工具会截断大文件）

### 启动很慢

模型越多越慢。每个模型首次加载需要解析几何、动画、贴图，之后会写入缓存。

- 首次加载：约 0.15~0.5 秒/模型
- 缓存命中后：明显更快

建议只保留常用模型。不用的移出 `custom/` 即可。

### 看不到其他玩家的模型

这是**预期行为**，不是 bug。

YSM 的架构是服务端权威：服务端扫描模型、算哈希、下发给客户端。服务器没装 YSM 时，客户端无从得知别人用了什么模型。

纯客户端模式只能保证**你自己**的模型正常显示。

### 想禁用某些内置模型

编辑 `config/yes_steve_model/blacklist.txt`，每行一个正则：

```
assets/yes_steve_model/builtin/wine_fox/.*
```

改完重启游戏生效。

### 缓存目录能删吗

可以。`config/yes_steve_model/cache/` 是纯缓存，删掉后会自动重建（代价是下次启动变慢）。

### 配置想恢复默认

删掉 `config/yes_steve_model-client.toml` 和 `config/yes_steve_model-server.toml`，重启游戏会重新生成。

注意：这也会清掉额外玩家渲染的位置、提示条位置等设置。
