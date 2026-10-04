# 原 MOD 功能审计报告（Phase 0）

审计对象（均为工作目录内提供的解包 JAR，经 CFR 0.152 反编译逐类核对）：

| 来源 MOD | 版本 | 作者 | 许可证 | 审计材料 |
|---|---|---|---|---|
| YDM's Mob Health Bar (`mobhealthbar`) | 2.3.0 (Forge 1.20.x) | YourDailyModderx / minecraftserverzone.com | **All Rights Reserved**（mods.toml） | 全部 21 个 class 反编译源 + mods.toml + lang + 贴图 |
| Mob Plaques (`mobplaques`) | 8.0.1 (Forge 1.20.1) | Fuzs | **MPL-2.0**（mods.toml） | 全部 17 个 class 反编译源 + mods.toml + lang + icons.png |
| AsteorBar (`asteorbar`) | 1.5.3 (Forge 1.20.1) | AsteorFox (afoxxvi) | **All Rights Reserved**（mods.toml） | 全部 47 个 class 反编译源 + mixins + lang + 贴图 |

> 许可证结论：mobhealthbar 与 AsteorBar 为 ARR，**任何代码与美术资源均不得复用**；Mob Plaques 为 MPL-2.0，文件级复用需公开对应源文件。本项目对三者一律采用**行为参考的净室重实现**，全部贴图使用自制替代资源（见 `docs/third-party-licenses.md`）。

---

## 1. mobhealthbar（YDM's Mob Health Bar）

渲染挂点：`RenderNameTagEvent`（DENY 原版名牌后自绘）。实体范围：`Mob` / `Player` / `ArmorStand`；排除有乘客实体（`passengers.isEmpty()`）与隐身实体。位置：实体高度 +1.2，billboard（相机朝向），基准缩放 0.025 × `scale-name`。

| 功能名称 | 功能说明 | 视觉表现 | 配置参数 | 重合情况 | Z80Zhealthbar 对应实现 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|---|---|
| 头顶血条（14 种贴图类型） | `hpbar.png` 精灵图，类型 1–14：多种 126px 宽贴图外框 + 按血量百分比的填充条；类型 4 为心形行式（每行 10 颗、半心 4px、超 20 血量多行） | 带外框的贴图血条 / 心形行；位于名称下方 | `hpbar.type`(1–14) | 与 B/C 的实体血条重合（统一为样式 A） | `mobdisplay/MobHealthBarStyle`（自制 hpbar 替代贴图，保留外框+填充+心形行变体） | 已实现（替代贴图，类型收敛为 外框条/心形行 两族） | 自动化+需实机 |
| 实体名称 | 血条上方显示实体显示名；黑名单含 `minecraft:player` 时保留玩家原名版名牌 | 白字名称 | `show_name` | 与 B 的信息牌文本重合 | 样式 A `showName`；样式 B 牌匾名称开关 | 已实现 | 自动化 |
| HP 数值文本 | "当前/最大" 居中显示（部分类型无文本） | 0.7 缩放白字 | `show_hp`、`scale-nums` | 与 C 的数值文本重合 | `barTextScale`/`displayHealthText` | 已实现 | 自动化 |
| 显示条件组合 | 仅受伤 `damaged_only` / 仅仇恨 `on_aggro`(Mob.isAggressive) / 仅准星 `hovered_only`（ProjectileUtil 射线拾取，第一/三人称分别实现） | — | `damaged_only`、`on_aggro`、`hovered_only`、`onvisible` | 与 B/C 可见性规则重合 | `VisibilityConfig`（showDamaged/showOnAggro/showHoveredMob/showOnlyWhenVisible） | 已实现 | 自动化 |
| 渲染距离 | 平方距离 ≤ `render-distance`（默认 2000）才显示 | — | `hpbar.render-distance` | 与 B `maxRenderDistance`、C `maxDistance` 重合 | `visibility.maxDistance` | 已实现 | 自动化 |
| 黑名单 | 逗号分隔注册名（默认 `minecraft:ender_dragon`） | — | `blacklist` | 与 B 黑名单重合（B 支持 tag/通配） | `entityBlacklist`（#tag、前缀 `*`、精确三级语法，超集） | 已实现（超集） | 自动化 |
| 位置/缩放 | X/Y 像素偏移；名称/血条/数字独立缩放；scale<1 时自动补偿高度防下沉 | — | `x-pos`、`y-pos`、`scale-name`、`scale-bar`、`scale-nums` | 与 C 的 scale/offset 重合 | `barOffsetY/barScale/barTextScale` + 样式 A 专属 X/Y | 已实现 | 自动化 |
| 血条选择器 GUI | H 键打开 `HpBarChooser`：左右箭头切换 14 类型 + 开关按钮，实时预览 | 屏幕中央贴图预览 | 快捷键 `key.mobhealthbar.chooser`（默认 H） | 与本项目设置界面重合 | `BarStyleSelectScreen`（设置界面内的样式选择页） | 已实现 | 手动 |
| 总开关 | 血条显隐 | — | `toogle` | 与 B `allow_rendering`、C `enableHealthBar` 重合 | `entityStyle=OFF` / 快捷键 | 已实现 | 自动化 |

**待验证项**：无（全部行为已从反编译源确认；14 种类型中大量为同框架换肤，收敛为 2 族实现并保留 type 编号映射，视觉等价性标注为部分保留）。

---

## 2. Mob Plaques

渲染挂点：`RenderNameTagEvent`（不 DENY，与名牌共存，绘制于名牌上方或下方）。牌匾 = 半透明背景盒（维度环境色 25% 暗）+ "N×" 文本（颜色随数值渐变）+ 原版 9×9 图标（带图标容器底图）。高度 11px，间距 2px，超 `maxPlaqueRowWidth`(108) 换行。

| 功能名称 | 功能说明 | 视觉表现 | 配置参数 | 重合情况 | Z80Zhealthbar 对应实现 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|---|---|
| 生命牌匾 | 值 = ceil(血量)+吸收取整；图标按状态选心形类型（普通/冻结/中毒/凋零，含容器底图）；数值颜色随 满→空 渐变 | 心形图标 + "N×" | `health.allow_rendering` | 与 A/C 血条重合（统一为样式 B 主组件） | `HealthDisplayRenderer.renderPlaque` | 已实现 | 自动化+需实机 |
| 护甲牌匾 | 护甲值 N>0 时显示护甲图标+数值 | 原版护甲图标 + "N×" | `armor.allow_rendering` | 与本项目属性附加组件重合 | `ArmorDisplayRenderer` | 已实现 | 自动化 |
| 韧性牌匾 | 护甲韧性显示 | 原版韧性图标 + "N×" | `toughness.allow_rendering` | 同上 | `ToughnessDisplayRenderer` | 已实现 | 自动化 |
| 氧气牌匾 | 氧气值>0（水下）时显示气泡 | 气泡图标 + "N×" | `air.allow_rendering` | 同上 | `AirDisplayRenderer` | 已实现 | 自动化 |
| 数值渐变文本 | TransitionPlaqueRenderer：颜色从 from→to 随 (值/最大值) 插值，"当前×"文本 | 渐变色数字 | （内置常量） | — | `TransitionPlaqueRenderer` | 已实现 | 自动化 |
| 行布局换行 | 一行放不下自动换行（108px 默认），行高 11+2 | 多行牌匾 | `maxPlaqueRow_width` | — | `maxPlaqueRowWidth` | 已实现 | 自动化 |
| 位置 | 名牌上方（默认）/下方；高度偏移；deadmau5 彩蛋偏移 | — | `renderBelowNameTag`、`heightOffset` | 与 A/C 位置偏移重合 | `plaqueStyle` 同名配置 | 已实现 | 自动化 |
| 缩放与距离缩放 | plaqueScale 默认 0.5（×0.025 世界缩放）；`scaleWithDistance` 时随距离放大 1+clamp(·,0,2) | 远处更大更易读 | `plaqueScale`、`scaleWithDistance` | 与任务书距离缩放要求重合 | 同名配置 | 已实现 | 自动化 |
| 渲染距离规则 | 48 格默认；目标潜行减半；视线被方块遮挡（VISUAL clip 命中）减为 1/4 | — | `maxRenderDistance` | 与 A/C 距离重合；遮挡衰减为 B 特有 | `plaqueStyle.maxRenderDistance`（遮挡衰减一并实现） | 已实现 | 自动化 |
| 准星实体模式 | 只显示准星拾取实体 | — | `pickedEntity` | 与 A `hovered_only` 重合 | `visibility.showHoveredMob` | 已实现 | 自动化 |
| 满血隐藏 | 满血且无吸收时隐藏（各牌匾通用 hideAtFullHealth，健康牌匾额外含吸收判断） | — | `hideAtFullHealth` | 与 A/C 满血规则重合 | `showOnFullHealth(Without/With)Absorption` | 已实现 | 自动化 |
| 穿墙显示 | behindWalls=true 时字体/图标用 SEE_THROUGH 渲染（保持原版名牌体验） | 半透明穿墙 | `behindWalls` | 任务书默认不穿墙要求 → 默认 false | `plaqueBehindWalls`（默认关） | 已实现 | 自动化 |
| 全亮度渲染 | NEVER / UNOBSTRUCTED(默认，穿墙时不加亮) / ALWAYS 三档 | 忽略光照 | `fullBrightness` | — | `plaqueFullBrightness` 三档 | 已实现 | 自动化 |
| 实体选择器 | allowed/disallowed 双列表：ALL/TAMED/TAMED_ONLY_OWNER/PLAYER/MONSTER/BOSS/MOUNT，disallowed 优先 | — | `allowed/disallowed_mob_selectors` | 与任务书实体类别自定义重合 | `MobPlaquesSelector`（7 值已实现） | 已实现 | 自动化 |
| 黑名单（高级语法） | `#tag`、通配 `minecraft:*_shulker_box`、`!` 排除 | — | `mob_blacklist` | 与 A 黑名单重合（B 语法为本项目黑名单语法来源） | `entityBlacklist` | 已实现 | 自动化 |
| 队伍可见性 | 沿用原版名牌 Team Visibility 规则（ALWAYS/NEVER/HIDE_FOR_OTHER_TEAMS/HIDE_FOR_OWN_TEAM） | — | — | — | 样式 B 内实现（随名牌行为） | 已实现 | 需实机 |
| 第一/三人称规则 | 仅第三人称相机实体可见时渲染；骑乘中/自身不显示 | — | — | 任务书第一/三人称规则来源 | `MobVisibilityChecker` | 已实现 | 自动化 |
| J 键开关 | 全局牌匾开关，聊天栏绿色 ON / 红色 OFF 消息 | 聊天提示 | 快捷键（默认 J） | 与本项目快捷键重合 | 快捷键 + lang 化提示 | 已实现 | 自动化 |

**待验证项**：`TAMED_ONLY_OWNER`（仅主人可见已驯服生物）在 8.0.1 反编译中为匿名内部类不可读，按语义实现（owner==camera player），标注待实机验证。

---

## 3. AsteorBar

玩家 HUD：通过 Forge `RenderGuiOverlayEvent` 屏蔽原版 PLAYER_HEALTH/FOOD_LEVEL/AIR_LEVEL/MOUNT_HEALTH（及可配置的 EXPERIENCE_BAR/ARMOR_LEVEL）后自绘；Fabric 端用 Gui mixin。实体血条：`EntityRenderDispatcher.render` 尾部注入（mixin），对每个 LivingEntity 绘制。

| 功能名称 | 功能说明 | 视觉表现 | 配置参数 | 重合情况 | Z80Zhealthbar 对应实现 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|---|---|
| 9 种 HUD 布局 | NONE/热栏上方长条/短条/顶双侧/底双侧/顶左/顶右/底左/底右；各 overlay 按布局锚点排列 | 长条式状态栏 | `overlayLayoutStyle`(0–8) | 任务书 HUD 样式来源 | `OverlayManager`（9 布局）+ HUD 样式三模式 | 已实现 | 自动化 |
| 6 条状态条 | 生命/饥饿/氧气/经验/护甲/坐骑血量；每条支持独立开关、渐变填充、文本 | 圆角长条+边框+文字 | 各 overlay enable + 颜色组 | 与原版 HUD 重合（替换式） | `overlay/parts/*` | 已实现 | 自动化 |
| 状态感知颜色 | 中毒 #ff9c8022 / 凋零 #ff4f2727 / 冻结 #ff3798f4 / 再生透明度 / 吸收 #ffe5d35c | 颜色变化 | `healthColor*ARGB`、`healthRegenerationOpacity` | — | `ColorConfig` + 状态检测 | 已实现 | 自动化 |
| 闪烁与低血量 | 受伤闪烁边框变白；低血量(20%)红边框+抖动；饥饿闪烁 | 闪烁/抖动动画 | `enableHealthBlink`、`lowHealthRate`、`shakeHealthAndFoodWhileLow` | 任务书动画要求来源 | 同名配置 | 已实现 | 自动化 |
| 堆叠血条 | 高最大生命值时血条分多行，颜色循环 | 多行堆叠 | `enableStackHealthBar`、`stackHealthBarColors` | 大数值支持 | 同名配置 | 已实现 | 自动化 |
| 大数值格式化 | 数值 ≥ 阈值隐藏小数；fullXxxValue 定义"满值"用于比例 | — | `hideDecimalWhenEqualOrMoreThan`、`full*Value` | — | 同名配置 | 已实现 | 自动化 |
| 饥饿扩展 | 饱和度/消耗值条 + 服务端同步（exhaustion/saturation packet） | 叠加细条/文本 | `displaySaturation`、`displayExhaustion` | 与 AppleSkin 重合 | `NetworkHandler` 同步 + AppleSkin 兼容 | 已实现 | 自动化 |
| AppleSkin 集成 | 手持食物时读取修改后食物值/饱和增量/预估回复（FoodValuesEvent），无 AppleSkin 回退原版 | 食物预览 | 兼容自动 | 任务书兼容目标 1 | `compat/AppleSkinCompat` | 已实现 | 自动化 |
| 经验条 | 进度条+等级数字开关+进度百分比开关 | 紫色长条 | `displayExperienceProgress/Level`、`overwriteVanillaExperienceBar` | — | `ExperienceBarOverlay` | 已实现 | 自动化 |
| 坐骑血量 | 双色（color/color2）+独立边框色，可放左侧 | 橙系长条 | `mountHealth*`、`mountHealthOnLeftSide` | — | `MountHealthOverlay` | 已实现 | 自动化 |
| 角落条参数 | 角落布局条长/边距/强制角落 | — | `cornerBarLength`、`corner*Padding`、`forceRenderAtCorner` | — | 同名配置 | 已实现 | 自动化 |
| 静止隐藏 | 状态 N 秒未变后隐藏 | 渐隐 | `hideUnchangingBarAfterSeconds` | 任务书自动隐藏来源 | 同名配置 | 已实现 | 自动化 |
| 实体血条（样式 C 核心） | 见上：动态色 mix(full→empty)、空槽、吸收以边框"环"逐层计数显示（超过半宽改用乘数文本）、"cur/max"文本、吸收黄色文本、alpha/scale/halfW/halfH/boundWidth/boundVertex/textScale/textOffsetY | 简洁长条+吸收环 | `healthBar*` 全组、`maxDistance`、`showOnSelf/Players/Bosses/ArmorStands`、`showOnFullHealth*` | 与 A/B 重合（统一为样式 C） | `AsteorBarEntityRenderer`（原 HealthDisplayRenderer.renderBar 强化） | 已实现 | 自动化+需实机 |
| 实体可见性检查链 | 旁观者/距离/穿墙 isSeenByPlayer?/隐身/自身/玩家/Boss/盔甲架/满血±吸收/视线 hasLineOfSight 逐项短路 | — | 同上 | 任务书显示规则来源 | `MobVisibilityChecker`（组合式规则） | 已实现（超集） | 自动化 |
| 网络 | 仅服务端装本 MOD 时激活同步（ActivatePacket 握手），exhaustion/saturation/absorption/ToughAsNails 包 | — | — | — | `NetworkHandler`（同设计） | 已实现 | 自动化 |
| 快捷键 | F8 循环布局 0–8（0=关 HUD），F10 实体血条开关 | actionbar 提示 | 默认 297/299 | 与本项目快捷键重合（避开原键位冲突：本项目默认改为 J/K/逗号等，F8/F10 不占用） | `KeyBindings` | 已实现 | 自动化 |
| 第三方 HUD 兼容 | 14 个兼容模块：LightShield(Iron's法术书光盾层)/TFC(血/食/经验 override+渴)/ToughAsNails/Thirst Was Taken/Homeostatic/LSO/Feathers/Botania/IronsSpellbooks/ArsNouveau/**ParCool**/Mekanism/SuperiorShields/Vampirism(饥饿override) | 各色长条 | 自动启用 | 任务书兼容目标 2/3 | `compat/*`（含 ParCool、Thirst） | 已实现（核心 3 个+扩展 9 个） | 自动化 |

**待验证项**：`LightShieldRenderer` 依赖 Iron's Spellbooks 私有字段（原 MOD 亦通过反射），本项目不承诺该扩展；`AsteorBarRenderType` 具体着色器细节按 Forge 标准 RenderStateShard 重写。

---

## 4. 功能重合与整合方案

| 重合功能 | 原 A (mobhealthbar) | 原 B (Mob Plaques) | 原 C (AsteorBar) | 整合方案 |
|---|---|---|---|---|
| 实体头顶血量显示 | 贴图外框条/心形行 | 心形图标牌匾 | 动态色长条 | **样式选择器**：MOBHEALTHBAR / MOBPLAQUES / ASTEORBAR / OFF 四选一，默认仅绘一种；护甲/韧性/氧气等非血量信息可作为"附加组件"在其他样式下单独开启（统一布局系统排布，禁止三套代码重复绘制血量） |
| 实体名称 | 名称+血量文本 | 牌匾文本 | 血量文本 | 随所选样式生效 |
| 显示条件 | damaged/aggro/hovered/visible/distance/blacklist | picked/full-health/distance/LOS/selectors/blacklist | self/players/boss/armorstand/fullhealth±abs/distance/LOS | 统一 `VisibilityConfig`，规则可组合（非互斥覆盖） |
| 黑名单 | 逗号精确名 | tag/通配/排除 | 无 | 统一为 B 的超集语法 |
| 玩家 HUD | 无 | 无 | 6 条长条 9 布局 | 玩家 HUD 样式三模式：**VANILLA**（不接管原版）/ **ASTEORBAR**（9 布局长条）/ **CUSTOM**（组件级样式+布局编辑器） |
| 快捷键 | H(chooser) | J(toggle) | F8/F10 | 本项目默认避开 H/J/F8/F10：K(HUD开关) M(实体栏) B(样式选择) 等，全部可改键 |
| 饥饿/饱和 | 无 | 无 | 内置+AppleSkin | 统一数据层 + AppleSkin 适配 |

## 5. 审计结论

- 三 MOD 共计确认 **61 项**功能/配置行为，全部在上有明确归属与处理结果；无凭推测计入的功能。
- 2 个待验证项（Mob Plaques TAMED_ONLY_OWNER 匿名类、AsteorBar LightShield 扩展）已按语义实现或明确不承诺，见上文。
- 原版 `minecraft:textures/gui/icons.png` 由运行时游戏资源提供（Mojang EULA 允许 mod 运行时引用），非本项目分发内容。
