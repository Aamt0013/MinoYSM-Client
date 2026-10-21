# 模型说明

## 目录结构

```
config/yes_steve_model/
├── custom/     ← 你自己的模型（本模组主要读取这里）
├── built/      ← 内置模型解压目录，由模组自动维护
├── auth/       ← 授权模型
└── cache/      ← 解析与贴图缓存
```

模型 id 就是**相对路径**：

```
custom/Tsuki_Velpro v1.1.ysm      →  id = "Tsuki_Velpro v1.1.ysm"
custom/mypack/mychar/             →  id = "mypack/mychar"
```

同名时 `custom/` 覆盖 `built/`。

---

## 支持的格式

### 1. `.ysm` 加密模型包

单文件形式，模组会自动识别加密版本并解密：

| 版本 | 文件头 | 说明 |
|---|---|---|
| v3 | `EF BB BF` + `YSGP` | 新版二进制格式，当前主流 |
| v2 | `YSGP` + 版本号 | 旧版容器格式 |
| v1 | `YSGP` + 版本号 | 更早期的格式 |

直接放进 `custom/` 即可，无需手动解密。

### 2. 文件夹模型（未加密）

一个目录，满足以下**任一**条件即被识别为模型：

- 目录里有 `ysm.json`
- 目录里同时有 `main.json` 和 `arm.json`

典型结构：

```
custom/MyModel/
├── ysm.json
├── models/
│   ├── main.json
│   └── arm.json
├── animations/
├── textures/
└── avatar/
```

### 3. `.zip` 压缩包 —— 不支持自动加载

如果模型是 zip 打包的文件夹模型，**需要先解压成文件夹**再放进 `custom/`。

```
custom/MyModel.zip     ✗ 不会被识别
custom/MyModel/        ✓ 解压后可以
```

---

## 模型包分组（可选）

如果想让多个模型在选择界面里归为一组并显示自定义图标，在**共同父目录**放一组文件：

```
custom/MyPack/
├── ysm-pack.json     ← 组名、描述、多语言
├── ysm-pack.png      ← 组图标
├── char_a.ysm
└── char_b.ysm
```

`ysm-pack.json` 示例：

```json
{
  "name": "我的模型包",
  "description": "一组自用模型",
  "lang": {
    "zh_cn": { "name": "我的模型包" },
    "en_us": { "name": "My Pack" }
  }
}
```

没有这组文件时，模型会以散装形式出现在列表里，功能不受影响。

---

## 内置模型与 `default`

模组只内置 **`default`** 一个模型，用途有两个：

1. 兜底 —— 没有选中任何模型时显示它
2. **动画骨架继承** —— 其它模型的动画表从它继承，因此它不能被禁用或删除

`built/default/` 由模组自动解压维护，不要手动修改。版本更新时该目录会被整体重建。

想彻底不看到某个内置模型，用 `blacklist.txt` 而不是删文件：

```
# config/yes_steve_model/blacklist.txt
assets/yes_steve_model/builtin/wine_fox/.*
```

---

## 轮盘个性化设置

部分模型在 `ysm.json` 的 `properties.extra_animation_buttons` 里定义了个性化选项（勾选/单选/滑条），可以在轮盘界面里调整。

这些设置的当前值保存在：

```
config/yes_steve_model/molang_vars.json
```

按模型 id 分组。删除该文件即可把某个模型的所有个性化设置恢复默认。

> 这类设置本质是 `v.roaming.*` 漫游变量，模型作者在动画里通过它们切换外观部件。

---

## 模型不生效的排查顺序

1. **文件名和路径** —— 有没有放进 `custom/`，`.ysm` 后缀对不对
2. **格式是否支持** —— 是不是 `.zip`（需解压）、文件头对不对
3. **日志** —— `logs/latest.log` 搜索 `[YSM]`，看有没有 `Local file model failed` 之类的记录
4. **文件完整性** —— 大文件下载被截断很常见，对比一下文件大小
5. **是否重启** —— 模型列表只在进世界时扫描，加完模型要重启游戏

---

## 关于模型版权

模组本身不附带任何第三方模型。请确保你有权使用从各处收集来的模型文件，并遵守原作者的使用条款。
