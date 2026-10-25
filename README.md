# Z80Z Health Bar (z80zhealthbar)

A Minecraft 1.20.1 health bar and player HUD mod for Forge and Fabric — entity health display, a fully customisable player HUD with a drag-and-drop editor, a floating chat window, damage popups, and optional AppleSkin / Thirst Was Taken / ParCool integration — plus a generic data-source framework that can read numbers from *any* installed mod without writing code.

Minecraft 1.20.1 · Forge 47.x or Fabric Loader 0.15.11+ · Java 17 · Client and server

本项目是 mobhealthbar / Mob Plaques / AsteorBar 三者功能与视觉体验的净室重实现（行为参考，零代码/零素材复用，见 [docs/third-party-licenses.md](docs/third-party-licenses.md)）。

## 安装

1. 安装 **Forge 47.x** 或 **Fabric Loader 0.15.11+**（Fabric 版另需 Fabric API）。
2. 从 [Releases](https://github.com/Z80Z99/z80z-healthbar/releases) 下载对应加载器的 jar，放入 `mods` 文件夹。
3. 启动游戏。Forge 版零额外依赖，无需 Architectury API。

进入设置：Forge 模组列表的 **Config** 按钮、Fabric 的 ModMenu，或在「按键绑定」中绑定打开设置的按键。设置页顶部支持**跨页搜索**。

## 功能总览

**实体生命值显示（四选一样式，互斥绘制）**
- `OFF` 关闭
- 样式 A `MobHealthBar`：外框贴图血条（4 种自制配色 + 心形行变体）+ 实体名称 + "当前/最大(+吸收)" 数值；X/Y/缩放三通道独立可调
- 样式 B `Mob Plaques`：原版图标牌匾（心/护甲/韧性/气泡 + 数值渐变文本），行换行、背景盒、随距离缩放、潜行/遮挡距离衰减
- 样式 C `AsteorBar`：动态色长条 + 吸收环 + 居中数值文本；支持分段刻度（均分/固定血量两种模式）
- 附加组件：样式 A/C 下可主动开启护甲/韧性/氧气牌匾行（默认关闭，不重复绘制）
- 统一数据层 `EntityStatusSnapshot`（采集与渲染解耦，未知数据不伪装为 0）
- 组合式显示规则：受伤/仇恨/准星/满血±吸收/敌友/Boss/盔甲架/距离/同屏上限/淡入/死亡淡出/视线遮挡（可选）
- **死亡效果**：玻璃碎裂（不规则四边形碎片 + 断面反光）或死亡收缩；受击抖动三模式、伤害残影、低血脉冲、扫光（恒定像素速度 + 子像素平滑）
- 伤害飘字：跳出角随机、自定义参数、治疗绿色/受伤红色 tint

**玩家 HUD（三模式）**
- `VANILLA`：完全不接管原版 HUD
- `ASTEORBAR`：9 种布局长条系统（含中毒/凋零/冰冻/再生着色、闪烁、低血抖动、堆叠血条、静止隐藏、饱和度/消耗值网络同步）
- `CUSTOM`：完全组件化——六大组件（生命/饥饿/氧气/经验/护甲/坐骑）+ 兼容状态单行组件 + 快捷栏（9 槽独立子组件，逐格增删/独立外观/选中动画）+ 信息组件（坐标/FPS/延迟/游戏时间），全部支持 长条/图标/纯文本/关闭 四形态、9 锚点、拖拽偏移、缩放、间距、旋转、独立文本/图标子件（可拆分摆放）
- **第二层数值**：吸收段/饱和度/润泽度可配 覆盖/右侧追加/顶部细条/底部细条/关闭 五种显示方式
- 创造/旁观模式自动隐藏状态条（对齐原版行为，可关）

**通用数据源框架（零代码读取任意 MOD 数值）**
- 5 种读取通道：Forge Capability / 静态方法 / 工厂实例（含方法链）/ 玩家实例方法 / 手持物品（耐久/NBT/物品方法）
- **模组内扫描选择器**：枚举目标模组的类与方法、实测读数预览、确定性推荐排序、后台变化探测（关掉界面去喝水，回来直接看哪个数字变了）
- AI 标签翻译：导出候选清单 → 任意 AI 翻译 → 导入（仅改名，不参与选择）
- 任意条组件可叠加第二层数据源；可接管目标 MOD 的原版 HUD

**游戏内界面**
- 七页设置界面（主页/实体样式/聊天窗口/伤害跳字/可见性/兼容/高级），**跨页参数搜索**，右半实时预览（敌/友切换、暂停、全屏预览）
- **HUD 布局编辑器**：点击拖拽、滚轮缩放、方向键微调、多级菜单、分组（多组 + 组内独立移动 + 整组移动）、撤销/重做（快照式 Ctrl+Z/Y）、显式保存/放弃、面板仿窗口拖缘调宽高、逐行 (?) 帮助说明、长条样式逐条拖拽自由摆放
- **比例缩放（参考分辨率）**：同一布局在不同逻辑分辨率下构图一致
- **用户样式库**：保存多套布局快照、一键应用、按窗口状态（全屏/最大化/窗口化）自动切换且各状态独立记忆、样式 JSON 复制/导入分享
- **自定义聊天窗口**：独立窗口化聊天记录（常驻/静默淡出）、输入框可分离摆放、拖标题栏移动/拖角缩放、自动换行、弹出/插入/平滑滚动动画（默认关闭，关闭=原版）
- **数据源管理**：列表/实时读数诊断/颜色轮盘；配置文件导出/导入 + 自动备份
- 简体中文 + English 全量 lang（1:1 对称）；配置校验（非法值夹取回退）、历史版本迁移、剪贴板/文件导入导出

**第三方兼容（优先级：AppleSkin > Thirst Was Taken > ParCool!）**
- 统一 `CompatibilityAdapter` 接口 + 注册表；未安装目标 MOD 时零渲染零反射热点、绝不崩溃
- 数据不可得时跳过显示（不伪造 0 值）；饱和度/消耗值/实体吸收经服务端同步（需服务端装本 MOD，自动激活）
- AppleSkin 手持食物营养预览；Thirst 口渴条（含润泽度第二层）；ParCool 体力条（上限未知时仅文本显示）
- 内建等价数据源可一键转换为自定义数据源（再自由改造）

## 要求

- Minecraft 1.20.1
- Forge 47.x **或** Fabric Loader 0.15.11+（Fabric 版另需 Fabric API）
- **无需 Architectury API**（网络层为平台原生实现；Forge 版零额外依赖）
- 服务端安装后才会同步饱和度、消耗值和实体吸收等数据

## 配置

配置文件为 `config/z80zhealthbar.json`，也可通过游戏内设置界面修改；支持导出到文件/剪贴板（导入前自动备份）。外部编辑保存后约 0.3 秒自动热重载（原子写入，读半文件不会损坏配置）。

## 从源码构建

需要 JDK 17：

```bash
./gradlew build
```

产物位于 `forge/build/libs/` 和 `fabric/build/libs/`。开发调试、热重载和项目结构见 [docs/development.md](docs/development.md)；架构与逐迭代变更记录见 [docs/architecture.md](docs/architecture.md)。

## 许可证

MIT（见 [LICENSE](LICENSE)）。本项目不包含也不授权三个来源 MOD 的任何代码或素材。
