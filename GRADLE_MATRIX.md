# Z80Z Health Bar — Build Matrix

V1 锁 1.20.1 单分支，Forge + Fabric 双平台（2026-09-24 实测 `BUILD SUCCESSFUL`，含 21 项单元测试）。

## 双平台构建矩阵

| 平台 | Minecraft | Loom / 工具链 | Architectury API | Cloth Config | JAR |
|---|---|---|---|---|---|
| Forge | 1.20.1 (47.3.0) | Architectury Loom 1.6-SNAPSHOT（仅工具链） | **无**（网络层 SimpleChannel 原生实现） | **无**（自绘设置界面） | ~206 KB |
| Fabric | 1.20.1 (loader 0.15.11) | Architectury Loom 1.6-SNAPSHOT（仅工具链） | **无**（Fabric 原生 networking） | **无** | ~200 KB |

构建命令：`./gradlew build`；测试：`./gradlew :common:test`。

## 各平台版本附录

- Architectury Plugin: 3.4-SNAPSHOT（构建插件，不进入产物）
- Architectury Loom: 1.6-SNAPSHOT（同上；`architectury_inject_*` 存根为工具链生成）
- Fabric Loader: 0.15.11；Fabric API: 0.92.2+1.20.1（Fabric 版运行时依赖，生态惯例不捆绑）
- ModMenu (dev only): 7.2.2
- Mixin: sponge-mixin 0.12.5（common 编译期；运行期由平台提供）
- V1.5 移植参考：1.21.1 → Architectury API v13.0.11（如需），RenderType API 变化见 platform/OverheadRenderTypeFactory
