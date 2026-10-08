# Z80Z Health Bar (z80zhealthbar)

Minecraft 1.20.1 综合生命值与状态栏 MOD —— 整合 mobhealthbar / Mob Plaques / AsteorBar 三者功能与视觉体验的净室重实现（行为参考，零代码/零素材复用，见 [docs/third-party-licenses.md](docs/third-party-licenses.md)）。

## 功能总览

**实体生命值显示（四选一样式，互斥绘制）**
- `OFF` 关闭
- 样式 A `MobHealthBar`：外框贴图血条（4 种自制配色 + 心形行变体）+ 实体名称 + "当前/最大(+吸收)" 数值；X/Y/缩放三通道独立可调
- 样式 B `Mob Plaques`：原版图标牌匾（心/护甲/韧性/气泡 + 数值渐变文本），行换行、背景盒、随距离缩放、潜行/遮挡距离衰减
- 样式 C `AsteorBar`：动态色长条 + 吸收环 + 居中数值文本
- 附加组件：样式 A/C 下可主动开启护甲/韧性/氧气牌匾行（默认关闭，不重复绘制）
- 统一数据层 `EntityStatusSnapshot`（采集与渲染解耦，未知数据不伪装为 0）
- 组合式显示规则：受伤/仇恨/准星/满血±吸收/敌友/Boss/盔甲架/距离/同屏上限/淡入/死亡淡出/视线遮挡（可选）

**玩家 HUD（三模式）**
- `VANILLA`：完全不接管原版 HUD
- `ASTEORBAR`：9 种布局长条系统（含中毒/凋零/冰冻/再生着色、闪烁、低血抖动、堆叠血条、静止隐藏、饱和度/消耗值网络同步）
- `CUSTOM`：六大组件（生命/饥饿/氧气/经验/护甲/坐骑）逐项选 长条/图标/关闭 + 9 锚点 + 拖拽偏移 + 缩放 + 间距 + 文本开关，另含兼容状态组件组

**游戏内界面**
- 五页设置界面（主页/实体/HUD/兼容/高级）：Forge Mod 列表 Config 按钮、Fabric ModMenu、快捷键均可进入
- HUD 布局编辑器：点击拖拽、滚轮缩放、方向键微调、锚点/模式/间距切换、对齐辅助线、组件级/整体重置；预览与运行时共用同一布局求解器
- 7 个可改键位（默认 K/M/B，其余未绑定；避开原 MOD 的 H/J/F8/F10）
- 简体中文 + English 全量 lang；配置校验（非法值夹取回退）、v1→v2 迁移、导入导出（剪贴板）

**第三方兼容（优先级：AppleSkin > Thirst Was Taken > ParCool!）**
- 统一 `CompatibilityAdapter` 接口 + 注册表；未安装目标 MOD 时零渲染零反射热点、绝不崩溃
- 数据不可得时跳过显示（不伪造 0 值）；饱和度/消耗值/实体吸收经服务端同步（需服务端装本 MOD，自动激活）
- AppleSkin 手持食物营养预览；Thirst 口渴条；ParCool 体力条（上限未知时仅文本显示）

## 要求

- Minecraft 1.20.1
- Forge 47.x **或** Fabric Loader 0.15.11+（Fabric 版另需 Fabric API）
- **无需 Architectury API**（网络层为平台原生实现；Forge 版零额外依赖）

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

## 构建

```
./gradlew build
```

产物：`forge/build/libs/z80zhealthbar-forge-1.0.104-1.20.1.jar`、`fabric/build/libs/z80zhealthbar-fabric-1.0.104-1.20.1.jar`（含 sources）。测试：`./gradlew :common:test`。

## 项目结构

```
common/    平台无关：status 数据层 / mobdisplay 样式 / overlay HUD / layout 求解器 /
           config / compat / platform 抽象 / gui（设置+编辑器）/ mixin
forge/     Forge 入口、事件、SimpleChannel、RenderType 工厂
fabric/    Fabric 入口、事件、原生 networking、access widener、ModMenu
docs/      审计报告 / 许可证 / 架构 / 测试 / 完成度报告
scripts/   GenTextures.java（自制贴图生成器）
```

跨版本移植：渲染/网络/键位/配置均经 `platform/` 接口隔离；1.21.x 移植时替换平台实现（见 docs/architecture.md）。

## 许可证

MIT（见 LICENSE）。本项目不包含也不授权三个来源 MOD 的任何代码或素材。
