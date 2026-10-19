# Z80Z Health Bar (z80zhealthbar)

A Minecraft 1.20.1 health bar and player HUD mod for Forge and Fabric — entity health display, a fully customisable player HUD, an in-game layout editor and optional AppleSkin / Thirst Was Taken / ParCool integration.

Minecraft 1.20.1 · Forge 47.x or Fabric Loader 0.15.11+ · Java 17 · Client and server

本项目是 mobhealthbar / Mob Plaques / AsteorBar 三者功能与视觉体验的净室重实现（行为参考，零代码/零素材复用，见 [docs/third-party-licenses.md](docs/third-party-licenses.md)）。

## 安装

1. 安装 **Forge 47.x** 或 **Fabric Loader 0.15.11+**（Fabric 版另需 Fabric API）。
2. 从 [Releases](https://github.com/Z80Z99/z80z-healthbar/releases) 下载对应加载器的 jar，放入 `mods` 文件夹。
3. 启动游戏。Forge 版零额外依赖，无需 Architectury API。

进入设置：Forge 模组列表的 **Config** 按钮、Fabric 的 ModMenu，或在「按键绑定」中绑定打开设置的按键。

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
- 五页设置界面（主页/实体/HUD/兼容/高级），所有改动即时生效
- HUD 布局编辑器：点击拖拽、滚轮缩放、方向键微调、锚点/模式/间距切换、对齐辅助线、组件级/整体重置；预览与运行时共用同一布局求解器
- 7 个可改键位：`K` 切换玩家 HUD、`M` 切换实体血条，其余默认未绑定（避开原 MOD 的 H/J/F8/F10）
- 简体中文 + English 全量 lang；配置校验（非法值夹取回退）、v1→v2 迁移、导入导出（剪贴板）

**第三方兼容（优先级：AppleSkin > Thirst Was Taken > ParCool!）**
- 统一 `CompatibilityAdapter` 接口 + 注册表；未安装目标 MOD 时零渲染零反射热点、绝不崩溃
- 数据不可得时跳过显示（不伪造 0 值）；饱和度/消耗值/实体吸收经服务端同步（需服务端装本 MOD，自动激活）
- AppleSkin 手持食物营养预览；Thirst 口渴条；ParCool 体力条（上限未知时仅文本显示）

## 要求

- Minecraft 1.20.1
- Forge 47.x **或** Fabric Loader 0.15.11+（Fabric 版另需 Fabric API）
- **无需 Architectury API**（网络层为平台原生实现；Forge 版零额外依赖）
- 服务端安装后才会同步饱和度、消耗值和实体吸收等数据

## 配置

配置文件为 `config/z80zhealthbar.json`，也可通过游戏内设置界面修改和导出。外部编辑保存后约 0.3 秒自动热重载。

## 从源码构建

需要 JDK 17：

```bash
./gradlew build
```

产物位于 `forge/build/libs/` 和 `fabric/build/libs/`。开发调试、热重载和项目结构见 [docs/development.md](docs/development.md)。

## 许可证

MIT（见 [LICENSE](LICENSE)）。本项目不包含也不授权三个来源 MOD 的任何代码或素材。
