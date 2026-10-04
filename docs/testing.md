# 测试报告与验证矩阵

## 0. 游戏内交互验证（2026-09-25，ui-helper.ps1 自动化 ✅）

通过桌面自动化（截图 + GUI 坐标点击/拖拽/滚轮）对 V2 设置界面做了逐页实测：

| 验证项 | 结果 |
|---|---|
| 五个标签页切换（主页/实体/HUD/兼容/高级） | ✅ 高亮与内容正确 |
| 滚轮滚动 + 滚动条（实体页 16+ 项超屏） | ✅ 裁剪正确、到底停住 |
| 开关点击 → 配置落盘（玩家 HUD 开→关→开） | ✅ JSON 实时翻转 |
| 枚举循环点击 → 配置落盘（实体样式 A→B→C） | ✅ JSON 与 UI 一致 |
| 滑杆拖拽 → 松开统一落盘（文本缩放 100%→114%） | ✅ 无每帧写盘 |
| 操作反馈（导出配置 → 底部右侧绿字提示） | ✅ 主菜单下可见 |
| 标题与标签页重叠 | ✅ 已修复（标题移至底部左侧） |

期间修复：滑杆拖动每帧写盘 → 松开统一保存；主菜单无玩家导致 actionbar 反馈丢失 → 改为面板底部临时文本；标题移位。

### 0.1 实体样式选择器（BarStyleSelectScreen）V2 自适应重写 ✅

用户实测发现旧版预览血条画在按钮堆叠区内被遮挡（仅露出填充色条）。V2 修复：
- 控件顶部堆叠，预览 + 缩放文本放入按钮堆叠与完成按钮之间的自由区垂直居中
- GUI 高度不足时按"预览 → 缩放文本"顺序自动省略，任何窗口尺寸不重叠
- "变体仅作用于 MobHealthBar"提示行**始终预留**（界面内切换样式后提示出现不再压住按钮）
- 预览按贴图模式绘制：原版模式用 hpbar_original.png 真实切片（含 type8 竖条放大 3 倍、心形条带），自制模式画同风格示意

### 0.3 世界空间血条深度分层修复（实机反馈）✅

用户实机发现：小史莱姆血条文字显示乱码（如 "36/1"）。诊断结论：
1. 数值本身正确 —— 原版史莱姆最大血量 = 尺寸²（微小 1/1、小 4/4、大 16/16），数值小是原版机制；
2. 乱码根因：世界空间渲染关闭深度测试且无距离分层，夜晚村庄场景中远处怪物（村民 20/20、女巫 26/26）的血条文字直接叠绘在史莱姆的小条上，数字互相糊成乱码。大史莱姆的条大且未与其它条屏幕重叠，所以正常。

修复（与 AsteorBar 原版方案一致）：barRect/originalBar 渲染类型从 NO_DEPTH_TEST 改为 LEQUAL 深度测试；四条世界空间渲染路径（C 长条/样式 A/牌匾常规与复刻/附加行）统一加 z = max(0.002, 距离×0.002) 的分层位移。附带效果：血条默认被墙壁遮挡（符合任务书 3.4 "默认不穿墙"）。

### 0.4 "36/1" 之谜（实机反馈，已定位）✅

用户确认场景中无其它生物后，深度分层未能消除乱码 → 推翻"条叠条"假设。图像逐字符分析确认血条实际显示 **"3.6/4"**：

- 小史莱姆最大血量 = 尺寸² = **4**（微小 1、小 4、大 16，原版机制）；被打掉 0.4 后剩余 3.6；
- AsteorBar 原版语义 `hideDecimalWhenEqualOrMoreThan` 默认 100 → 低于 100 的数值**保留 1 位小数**（我们忠实还原）；
- 世界空间缩放 + 压缩下小数点视觉消失、末位 "4" 像素过少被误读为 "1" → "36/1"。大史莱姆 16/16 是整数故正常。

修复：常规渲染新增 `barStyle.integerHealthText`（默认开）—— 实体血条数值取整显示；原版复刻调试模式保持忠实的小数显示（"3.6/4"）。设置 → 实体状态 → 样式 C 分组可切换。

### 0.2 原版复刻调试渲染模式（提取自反编译源）✅ 代码级

应用户要求，把原 MOD 的渲染行为逐行提取为"原版复刻"调试开关（默认关闭，设置 → 实体状态页）：

| 开关 | 位置 | 复刻内容（反编译来源） |
|---|---|---|
| 样式 C「原版复刻渲染」 | barStyle.originalRender | AsteorBar EntityRenderer.render：POSITION_COLOR_TEX_LIGHTMAP 管线（color→uv→uv2 顶点序）、lightmap 渐变采样（fill v0.625-1.0 / empty v0-0.375、uv2=0xFF00FF）、renderBound 吸收环逐段算法、条下小格、距离分层 z=max(0.002, dist*0.002)、modifyAlpha(0=不修改)、文本居中+吸收黄字。原版实体条无中毒/凋零/冰冻着色，复刻同样不做 |
| 样式 B「原版复刻渲染」 | plaqueStyle.originalRender | Mob Plaques MobPlaqueRenderer/Handler：文字(左)+单图标(右)、宽=textWidth+2+9+2、高 11、注册顺序 health→air→armor→toughness、背景色 options.getBackgroundColor(0.25f)、TransitionPlaque 配色 0x1EB100→0xED230D、文字/图标双通道绘制（穿墙幽灵层 0x20FFFFFF + 全亮 0xF000F0）、健康牌匾容器心底图 |

调试资源包（`scripts/make-dev-art-pack.ps1`，仅本地、不入库/JAR）新增：
- AsteorBar `lightmap.png` → `z80zhealthbar:textures/ui/lightmap_original.png`（C 复刻的渐变源）
- Mob Plaques `icons.png` → `z80zhealthbar:textures/gui/icons_original.png`（B 复刻的图标源，含韧性图标 (18,0)）

**关键修复**：此前 Compress-Archive 生成的 zip 条目使用反斜杠路径，Minecraft 资源系统无法解析——这意味着此前"原版贴图模式"实际从未真正生效过。已改用 JDK jar 工具打包（正斜杠条目），并修正脚本的无 BOM 写入。样式 A 的 13 型原版变体自此才真正可用。

## 1. 自动化测试（已执行 ✅）

`./gradlew :common:test` — 21 项 JUnit 5 全部通过（2026-09-24，构建 `BUILD SUCCESSFUL`）：

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `ConfigValidatorTest` | 5 | 越界数值夹取、null 分段恢复、非法颜色回退默认、非法枚举字符串回退、HUD 布局组件夹取 |
| `ConfigMigrationTest` | 4 | v1 mobDisplayMode→entityStyle 迁移、BARS/PLAQUES/BOTH/null 四路映射、v2 不动、遗留字段必清 |
| `HudLayoutSolverTest` | 8 | BOTTOM_CENTER 对齐物品栏上方、同锚点堆叠、OFF 组件排除、拖拽偏移、缩放尺寸、TOP 向下堆叠、ICON 模式测量、非法锚点/模式回退 |
| `ColorHelperTest` | 4 | ARGB/RGB 解析、safeParse 拒绝非法、lerp 中点、modifyAlpha 比例 |

## 2. 编译与构建（已执行 ✅）

- `./gradlew build`：common + forge + fabric 全部编译、双平台 JAR + sources 产出。
- JAR 内容核验：仅 `com/z80z99/z80zhealthbar` 包；中英 lang；自制 `style_a_bars.png`；
  无任何来源 MOD 素材（hpbar/overlay/lightmap/icons 均未打包）；
  无 Architectury API / Cloth Config 捆绑（`architectury_inject_*` 为构建工具链存根，非 API）；
  Forge mods.toml 强依赖仅 forge/minecraft；Fabric 依赖 loader/mc/java/fabric-api。

## 3. 实机验证清单（待人工执行 ⏳）

以下项需要 `./gradlew :forge:runClient` / `:fabric:runClient` 或放入实际客户端人工核对，
本环境无显示设备，未执行、也未宣称通过：

**发布前清理（发布流程必做）**
- [ ] 移除本地开发资源包 `forge/run/client/resourcepacks/z80z-dev-original-art/`（内含原 MOD ARR 素材，仅限本机对照测试，禁止分发/提交仓库）
- [ ] 移除或完成调试路径后再发布：`styleA.textureMode=ORIGINAL`（引用 hpbar_original.png）、`barStyle.originalRender`（引用 textures/ui/lightmap_original.png）、`plaqueStyle.originalRender`（引用 icons_original.png）——三处素材不在分发 JAR 内，发布版应确认这三个调试开关不会面向普通玩家（或删除相关代码与 GUI 入口）
- [ ] 重新 `./gradlew build` 并核验 JAR 内无任何 `*_original*.png`

**基础（任务书 11.1）**
- [ ] 纯净 Forge/Fabric 环境启动无报错；未装任何兼容 MOD 时正常
- [ ] 实体样式 A/B/C/OFF 切换即时生效、无重复绘制
- [ ] HUD 三模式切换；VANILLA 下原版六项 HUD 完整保留
- [ ] K/M 键开关独立；配置保存重载正确

**实体（任务书 11.2）**
- [ ] 僵尸/骷髅/牛/村民/狼/玩家/末影龙/凋灵 的常规、低血、满血、吸收、受伤、死亡淡出
- [ ] 跨维度、超距隐藏、同屏上限、淡入动画

**伤害跳字（新增功能）**
- [ ] 服务端+客户端都装：精确模式数字 = 实际扣血；吸收抵消部分 APEX 主题金色第二行；击杀红字放大 + 低音 ping
- [ ] 仅客户端装：估算模式出现数字（服务端在场 10 秒内不重复弹）
- [ ] 五主题视觉核对：APEX 分色底板 / TACTICAL 白+大额金色+散布+描边 / WARFRAME 类型配色倾斜底板 / CLASSIC 类型配色描边 / MINIMAL 小字快消
- [ ] 准星命中标记 X 出现与消退（320ms），击杀变红加粗；音效开关生效
- [ ] 连击合并窗口（mergeWindowTicks>0）多条伤害合并为 "N ×n"
- [ ] 类型配色：火/爆炸/魔法（药水）/投射物/摔落 各自颜色；对应类别开关关闭后不显示
- [ ] 性能：大批量实体同时受击时无掉帧（全局 64 条上限生效）

**HUD（任务书 11.3）**
- [ ] 1080p/1440p/4K/小窗、GUI Scale 1-4、中文/英文/强制 Unicode
- [ ] 高最大生命值（>100）文本不溢出；骑乘/水下/多效果并存
- [ ] 布局编辑器拖拽/缩放后退出重进位置一致（编辑器=运行时已由共用求解器+单测保证）

**兼容（任务书 11.4）**
- [ ] AppleSkin / Thirst Was Taken / ParCool! 单独与组合安装；不装时无报错
- [ ] 服务端装本 MOD 后饱和度/消耗值/实体吸收同步生效

## 4. 已知限制（如实声明）

1. 样式 A 的 14 种原变体收敛为 5 种（4 外框配色 + 心形行）——原 MOD 大量类型为同框架换肤，视觉等价性为"部分保留"（见审计报告）。
2. 同屏数量上限按"当帧先渲染优先"近似（渲染钩子无法预知全实体集合）。
3. 实体死亡淡出依赖死亡动画期间（20 tick）的渲染钩子；实体被立即移除时无淡出。
4. 视线遮挡检测默认关闭（每实体每帧射线开销），开启后为精确行为。
5. 骑乘中相机实体的自身状态栏、第一人称自身：不显示（与原 MOD 行为一致）。
6. Fabric 专用服务器：网络同步跳过（无客户端入口），功能等价于未装。
7. ParCool/Thirst 的 API 路径按公开源码编写候选反射表；若实际版本 API 变更，适配器自动降级为不显示（不崩溃、不伪造数据）——待实机确认具体版本行为。
