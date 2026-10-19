# 开发指南

## 构建

```bash
./gradlew build
```

产物：

- `forge/build/libs/z80z-healthbar-forge-<版本>-1.20.1.jar`
- `fabric/build/libs/z80z-healthbar-fabric-<版本>-1.20.1.jar`
- 对应的 `-sources.jar`

测试：`./gradlew :common:test`

## 不重启调试工作流

1. **配置改动（JSON）——已内置热重载**：外部编辑 `run/client/config/z80zhealthbar.json` 保存后约 0.3 秒自动生效（文件监视器 + CRC 去重）；也可在"控制设置→Z80Z 血条"绑定"从磁盘重新加载配置"键手动触发。渲染路径每帧读配置，重载即下一帧生效。
2. **资源改动（贴图/语言）**：改开发资源包或 JAR 内资源后按 **F3+T** 重载资源即可。
3. **Java 方法体改动**：用 IntelliJ IDEA 以 **Debug 模式**启动（导入 Gradle 项目后先执行 `gradlew genIntellijRuns` 生成运行配置，再 Debug 运行 `Forge Client`），改完代码 Build（Ctrl+F9 或 Ctrl+Shift+F9）→ 弹出"重新加载已更改的类"确认；断点/单步全程可用。局限性：不能增删方法/字段（见第 4 条）。
4. **Java 结构改动（增删方法/字段）——DCEVM 免重启**：
   - 装一个 **JetBrains Runtime 17**（内置 DCEVM）：IDEA 自带 `<IDEA安装目录>\jbr`（需为 17），或从 [JetBrainsRuntime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases) 下载 `jbr_jcef-17.0.x`；
   - 让 Gradle 能发现它：在**用户级** `~/.gradle/gradle.properties`（勿写入项目）加 `org.gradle.java.installations.paths=C:/path/to/jbr`；
   - 把项目根目录 `gradle-local.properties`（已 gitignore）里的 `hotswap.enable=true` 取消注释——runClient 会自动改用 JBR 并加 `-XX:+AllowEnhancedClassRedefinition`；
   - 重新 `gradlew genIntellijRuns`（运行配置会烘焙进 VM 参数），然后照第 3 条 Debug + Build 重载即可；结构性改动同样即时生效。
   - 仍需重启的改动：**mixin 类本体**（注入不会重新应用）、**新增/删除类文件**、依赖与 mods.toml 等元数据。
5. **JAR/classpath 变更**（新增依赖、改 mods.toml 等）：必须重启。
6. **快速启动（可选）**：`gradle-local.properties` 里 `dev.world=<存档名>` 后，runClient 经 Quick Play（`--quickPlaySingleplayer`）跳过主菜单直入存档；需要重启的调试也建议开着。

## 项目结构

```
common/    平台无关：status 数据层 / mobdisplay 样式 / overlay HUD / layout 求解器 /
           config / compat / platform 抽象 / gui（设置+编辑器）/ mixin
forge/     Forge 入口、事件、SimpleChannel、RenderType 工厂
fabric/    Fabric 入口、事件、原生 networking、access widener、ModMenu
docs/      审计报告 / 许可证 / 架构 / 测试 / 完成度报告
scripts/   GenTextures.java（自制贴图生成器）
```

跨版本移植：渲染/网络/键位/配置均经 `platform/` 接口隔离；1.21.x 移植时替换平台实现（见 [architecture.md](architecture.md)）。
