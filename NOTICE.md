# NOTICE

MinoYSM Client — Yes Steve Model 的 Minecraft 26.2 纯客户端移植版。

本项目依据 **Apache License 2.0** 发布，许可正文见 [LICENSE](LICENSE)。

---

## 上游归属

本项目包含来自以下项目的代码，均已作出修改（适配 Minecraft 26.2 的渲染与网络 API、改造为纯客户端运行、性能与稳定性修复）。修改后的文件不逐一标注，此处统一声明。

| 项目 | 许可证 | 说明 |
|---|---|---|
| [Yes Steve Model](https://github.com/YesSteveModel/YesSteveModel) | Apache-2.0 | 原始模组，本项目的主要代码来源 |
| [OpenYSM](https://github.com/OpenYSM/OpenYSM) | MIT | YSM 的开源替代实现 |
| [OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated) | MIT | 高版本移植起点 |
| [LgeacyYSM](https://github.com/YesSteveModel/LgeacyYSM) | BSD-3-Clause | 更早的开源实现 |

原始版权声明：

```
Copyright (c) 2026 OpenYSM
Copyright Yes Steve Model contributors
```

26.2 移植与纯客户端改造部分：`Copyright (c) 2026 Aamt0013`

---

## 内置模型资产

**内置模型不属于 Apache License 2.0。**

`assets/yes_steve_model/builtin/**` 下的模型资产适用其自身许可证：

- `default` —— CC0 1.0，无使用限制

模型资产中的 `metadata.authors` 保留作者与归属信息。用户自行放入 `custom/` 目录的模型版权归各模型作者所有，本项目不附带、也不再分发。

---

## 源码内嵌的第三方组件

以下组件以源码形式拷贝并修改后编入本项目，保留其原始许可证：

| 本项目路径 | 上游项目 | 许可证 | 许可正文 |
|---|---|---|---|
| `org/concentus` | [Concentus](https://github.com/lostromb/concentus) | BSD-style（Opus 条款） | `licenses/concentus.txt` |
| `org/gagravarr` | [VorbisJava / Gagravarr](https://github.com/Gagravarr/VorbisJava) | Apache-2.0 | `licenses/apache-2.0.txt` |
| `com/elfmcys/yesstevemodel/geckolib3` | [GeckoLib](https://github.com/bernie-g/geckolib) | MIT | `licenses/geckolib-MIT.txt` |
| `molang` | Mocha Molang 实现 | MIT | 见上游项目 |

以上组件不因位于本仓库内而改为 Apache License 2.0。

---

## 随包分发的依赖

构建时将以下依赖以嵌套 JAR 或合并进主 JAR 的形式打入最终制品，各自保留原许可证：

| 依赖 | 许可证 | 分发形式 | 许可正文 |
|---|---|---|---|
| [Fabric API](https://github.com/FabricMC/fabric) | Apache-2.0 | 嵌套 JAR | `licenses/fabric-api-Apache-2.0.txt` |
| [Architectury API](https://github.com/architectury/architectury-api) | LGPL-3.0 | 嵌套 JAR | `licenses/architectury-LGPL-3.0.txt` |
| [Cardinal Components API](https://github.com/Ladysnake/Cardinal-Components-API) | MIT | 嵌套 JAR | `licenses/cardinal-components-MIT.txt` |
| [Forge Config API Port](https://github.com/Fuzss/forgeconfigapiport) | MPL-2.0 | 嵌套 JAR | `licenses/forge-config-api-port-*.txt` |
| [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) | BSD-3-Clause | 合并进主 JAR | `licenses/twelvemonkeys-BSD-3-Clause.txt` |

LGPL-3.0 组件（Architectury）以独立 JAR 形式分发，未与本体代码静态链接，可按 LGPL-3.0 第 4 条替换为修改版本。

### 关于图像格式支持

贴图解码支持 PNG、JPEG、BMP（JDK 自带 ImageIO）与 WebP（TwelveMonkeys）。

**AVIF 不受支持。** 可用的 AVIF 解码器要么依赖原生库（libavif / dav1d），要么没有明确的开源许可，都不适合打包进模组。此前使用的 ImageStream 属于后者 —— 其上游仓库 `TartaricAlkaline/ImageStream` 没有任何许可证文件，无法再分发，已于 1.2.6 移除。

遇到 AVIF 贴图时不会中断模型加载：尺寸从 ISOBMFF 的 `ispe` 盒中读取，像素解码失败则回退为默认贴图，并在日志中提示一次。

---

## 许可正文位置

完整许可正文随制品分发，位于 JAR 内的 `licenses/` 目录，同时保存在仓库的 `common/src/main/resources/licenses/`。

本文件是合规辅助材料，不替代各上游许可证正文。
