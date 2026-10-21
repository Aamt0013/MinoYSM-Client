# 从源码构建

## 环境要求

| 项目 | 版本 |
|---|---|
| JDK | **25** |
| Gradle | 无需单独安装，用仓库自带 wrapper |
| 网络 | 首次构建需要能访问 Maven 仓库 |

JDK 25 是硬性要求。有两种方式让 Gradle 找到它：

**方式一（推荐）**：设置环境变量 `JAVA_HOME` 指向 JDK 25。

**方式二**：在 `gradle.properties` 里指定路径，取消这行注释并改成你的实际路径：

```properties
org.gradle.java.home=C:/path/to/jdk-25
```

> 注意用正斜杠 `/` 或双反斜杠 `\\`，单反斜杠会被当成转义符。

---

## 构建

```bash
# Windows
gradlew.bat build -x test

# Linux / macOS
./gradlew build -x test
```

产物：

```
fabric/build/libs/MinoYSM-Client-26.2-<版本号>.jar
```

`-dev-shadow.jar` 和 `-sources.jar` 是中间产物，发布时只需那个不带后缀的。

### 代理 / 离线

依赖里包含 JitPack 的 `-SNAPSHOT`（ImageStream），Gradle 每次构建都会联网校验元数据。如果代理没开或者断网，会报 `Could not resolve ... Connection refused`。

这时用离线模式走本地缓存：

```bash
gradlew.bat build -x test --offline
```

前提是之前成功构建过一次，依赖已在 `~/.gradle/caches` 里。

---

## 26.x 的特殊处理：没有映射表

Minecraft 从 26.x 起**不再混淆**：

- Mojang 不再发布 `client_mappings`
- Yarn / intermediary 没有 26.x 构建
- 运行时类名就是源码里的名字

但 architectury-loom 仍然强制要求一个 `mappings` 依赖，否则报 `Configuration 'mappings' has no dependencies`。

解决办法是仓库里那个 **`mappings-identity.jar`** —— 一份零条目的恒等映射：

```
mappings-identity.jar
└── mappings/mappings.tiny
    内容: tiny\t2\t0\tofficial\tnamed\n     （只有表头，没有条目 = 恒等）
```

配合：

```groovy
loom {
    noIntermediateMappings()
    mappings files("$rootDir/mappings-identity.jar")
}
```

### 由此带来的一个副作用

没有映射就没有 `refMap`，所以 **Mixin 里不能使用 intermediary 名字**，必须直接用 26.x 的运行时类名。

Fabric API 自带的 class tweaker 声明的是 `named` 命名空间，与 26.x 的 `official` 对不上。

**实测结论：`build` 不受影响。** 从干净克隆（无任何 `.gradle` 缓存）执行 `gradlew build` 可以正常产出 jar，这一点已验证过。

如果后续跑 `runClient` 之类的开发运行时遇到命名空间相关的报错，通常需要把 `.gradle/loom-cache/remapped_mods/` 下 Fabric API 的 jar 做一次命名空间改写（`named` → `official`）。

> 因此：**能正常构建就不要去删项目里的 `.gradle`**。真要清理，先备份 `.gradle/loom-cache/remapped_mods/`。

---

## 目录说明

```
├── common/          共享代码（绝大部分逻辑）
│   └── src/main/
│       ├── java/    Java 源码
│       └── resources/
│           └── assets/yes_steve_model/builtin/default/   内置默认模型
├── fabric/          Fabric 平台适配层
│   └── src/main/
│       ├── java/
│       └── resources/fabric.mod.json
├── libs/            （可选）本地 jar 仓库声明，当前无模块依赖，可以不存在
├── mappings-identity.jar   26.x 恒等映射（必需）
└── gradle.properties       版本号、依赖版本、JDK 路径
```

### 改版本号

`gradle.properties`：

```properties
mod_version = 1.2.2
archives_name = MinoYSM-Client-26.2
```

产物名 = `archives_name` + `-` + `mod_version`。

### 改模组显示名 / 描述

`fabric/src/main/resources/fabric.mod.json`：

```json
{
  "id": "yes_steve_model",
  "name": "MinoYSM Client",
  "environment": "client"
}
```

> **`id` 不要改。** 模型包格式、配置目录、与 YSM 服务器的通信协议都绑定这个 id。

---

## 常见构建问题

### `Configuration 'mappings' has no dependencies`

`mappings-identity.jar` 不存在或被删了。检查仓库根目录。

### `Could not resolve com.github.TartaricAlkaline:ImageStream:-SNAPSHOT`

JitPack 连不上。开代理，或者加 `--offline` 走缓存。

### 构建成功但进游戏崩溃 / 报 namespace 错误

`.gradle/loom-cache/remapped_mods/` 里的 Fabric API jar 被重新生成、丢了命名空间改写。参考上文「副作用」一节。

### `Unsupported class file major version`

JDK 版本不对，需要 25。检查 `org.gradle.java.home`。
