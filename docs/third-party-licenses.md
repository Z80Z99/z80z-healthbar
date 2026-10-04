# 第三方来源与许可证报告

本项目（Z80Zhealthbar，MIT）对三个来源 MOD 及依赖库的处理原则：

1. **代码**：三个来源 MOD 的代码**一律不复用**。全部实现为行为参考的净室重实现（参考对象为反编译后确认的行为规格，见 `docs/source-mod-audit.md`）。
2. **美术资源**：不分发任何来源 MOD 的原始素材。
3. **运行时引用**：仅引用 Minecraft 本体资源（如 `minecraft:textures/gui/icons.png`），由玩家已安装的游戏提供，随 Mojang EULA 与 vanilla 加载管线工作，不进入本项目分发物。

## 1. 来源 MOD

| 来源 | 版本 | 许可证（mods.toml 声明） | 代码复用 | 资源复用 | 处理方式 |
|---|---|---|---|---|---|
| YDM's Mob Health Bar（`mobhealthbar`，YourDailyModderx） | 2.3.0 | All Rights Reserved | 否 | 否 | 行为参考；其 `hpbar.png`/`hpbar_choose.png` 贴图**未采用**，样式 A 使用自制贴图 `assets/z80zhealthbar/textures/gui/style_a_bars.png`（程序化生成） |
| Mob Plaques（`mobplaques`，Fuzs） | 8.0.1 | MPL-2.0 | 否 | 否 | 行为参考（牌匾布局、选择器、距离规则）。MPL-2.0 允许文件级复用，本项目仍选择净室实现以保持单一 MIT 授权；其 `icons.png` 未采用（代码直接使用原版 icons.png） |
| AsteorBar（`asteorbar`，afoxxvi） | 1.5.3 | All Rights Reserved | 否 | 否 | 行为参考（HUD 布局算法、颜色体系、网络同步设计）。其 `overlay.png`/`lightmap.png` 未采用（HUD 绘制为纯色矢量矩形 + 原版图标，无需贴图） |

### 1.1 历史遗留复制资源的清理记录

项目早期版本曾将下列文件直接复制进 `common/src/main/resources/assets/z80zhealthbar/`，**均与来源逐字节相同（MD5 核对）**。本次开发已全部删除/替换：

| 文件 | 来源 | 许可证 | 处理 |
|---|---|---|---|
| `textures/gui/hpbar.png` | mobhealthbar | ARR | **删除**，替换为自制 `style_a_bars.png` |
| `textures/gui/hpbar_choose.png` | mobhealthbar | ARR | **删除**，选择器预览改为程序化绘制 |
| `textures/gui/icons.png` | mobplaques | MPL-2.0 | **删除**（代码本就使用原版 `minecraft:textures/gui/icons.png`） |
| `textures/gui/overlay.png` | asteorbar | ARR | **删除**（死引用） |
| `textures/ui/lightmap.png` | asteorbar | ARR | **删除**（死引用） |
| `textures/gui/widgets.png` | mobhealthbar | ARR | **删除**（死引用） |

## 2. 构建依赖（开发期）

| 依赖 | 许可证 | 用途 | 分发方式 |
|---|---|---|---|
| Architectury Loom / architectury-plugin | MIT（工具链） | 构建工具链 | 不进入产物 |
| Mojang Minecraft + official mappings | Mojang EULA / 使用条款 | 编译目标 | 不进入产物 |
| Forge 47.x / Fabric Loader 0.15.x | LGPL-2.1 / Apache-2.0 | 加载器 | 不进入产物 |
| Cloth Config 11.1.106 | LGPL-3.0 | 设置界面（`include` JiJ 捆绑，含 LGPL 许可文本） | 随 MOD JAR 捆绑分发，LGPL 合规（JiJ 完整携带上游 jar 与其内许可证） |
| Fabric API 0.92.2（仅 Fabric 版运行时） | Apache-2.0 | Fabric 平台 API | 不捆绑（Fabric 生态惯例由玩家安装） |

**Architectury API 依赖状态**：早期版本曾通过 `modApi`+`include` 依赖 Architectury API（网络层）。按任务书第 8 节要求，现已**彻底移除**：网络层改为 Forge `SimpleChannel` / Fabric 原生 networking 的平台实现。Forge 版不携带、不要求 Architectury API。

## 3. 运行时兼容目标（可选安装，不做任何代码/资源复用）

| MOD | 检测方式 | 数据访问方式 | 未安装时行为 |
|---|---|---|---|
| AppleSkin | ModList 含 `appleskin` | 仅当 AppleSkin 存在时经反射调用其 `FoodHelper` 公共 API；不存在时回退原版食物数据 | 无影响（组件自动禁用） |
| Thirst Was Taken | ModList 含 `thirstwasaken` / `thirst` | 反射读取客户端同步的 thirst 数据（该数据本身即客户端可见） | 无影响 |
| ParCool! | ModList 含 `parcool` | 反射读取客户端 stamina（ParCool 为客户端动作 MOD，stamina 本地可见） | 无影响 |
| 其余 9 个扩展兼容（ToughAsNails、TFC、Mekanism 等） | 同上 | 同上 | 同上 |

所有兼容访问遵循：try-catch 包裹 + 一次性缓存 Method/Field + 失败静默禁用 + 绝不修改第三方数据。不写入、不删除第三方配置文件。

## 4. 本项目授权

MIT（见根目录 `LICENSE`）。凡引用本项目的代码须保留版权与许可声明；本项目不授予对来源 MOD 任何权利，来源 MOD 的一切权利归其作者所有。
