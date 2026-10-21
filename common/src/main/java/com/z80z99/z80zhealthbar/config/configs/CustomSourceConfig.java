package com.z80z99.z80zhealthbar.config.configs;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户自定义外部数据源（通用兼容框架,2026-10-21）。
 * <p>目标:不写代码即可读取任意 MOD 暴露在客户端的数值,自动获得
 * compat_custom_&lt;id&gt; 条组件 / {custom_&lt;id&gt;} 模板变量 / 兼容条组行。
 * <p>读取通道（对应 {@link com.z80z99.z80zhealthbar.overlay.parts.compat.ReflectionHelperCompat} 的通用方法）:
 * <ul>
 *   <li>0 = Forge Capability:holderClass 的 holderField 静态字段 → player.getCapability → 解包 → ifaceClass.readMethod()</li>
 *   <li>1 = 静态方法:holderClass.readMethod(Player) → Number</li>
 *   <li>2 = 工厂实例:holderClass.readMethod(Player) → 实例,再调 valueMethod() → Number</li>
 *   <li>3 = Player 实例方法:holderClass = 玩家实例方法名(readMethod 作方法名),无参 → Number</li>
 *   <li>4 = 手持物品:mode 见 itemMode（原版耐久 / NBT 整型 / 物品方法）;不需要类名</li>
 * </ul>
 * 上限:maxMethod（同通道语义）→ staticMax(>0) → 无（仅文本）。
 * <p>所有反射懒执行且静默失败（读不到 → "—"）,管理页提供读数诊断;类加载期绝不触碰目标 MOD 类。
 */
public class CustomSourceConfig {

    public List<CustomSource> customSources = new ArrayList<>();

    /** 手持物品通道的取值方式 */
    public static final int ITEM_VANILLA_DURABILITY = 0; // 原版耐久:max - damage
    public static final int ITEM_NBT_INT = 1;            // NBT 整型:readMethod = NBT key
    public static final int ITEM_METHOD = 2;             // 物品方法:readMethod 在 ItemStack 上调用

    /** 单个自定义数据源（id 即模板变量名与组件键后缀） */
    public static class CustomSource {
        /** id 合法性(预编译,每帧热路径不再重复编译正则——审计 P3) */
        private static final java.util.regex.Pattern ID_PATTERN =
                java.util.regex.Pattern.compile("[a-z0-9_]{1,24}");

        /** 总开关（关 = 三处消费端全部不出现） */
        public boolean enabled = true;
        /** 数据源 id:[a-z0-9_]{1,24};组件键 compat_custom_<id>,模板变量 {custom_<id>} */
        public String id = "";
        /** 目标 mod id（未安装时该源自动失效,不报错;通道 4 玩家物品类数据可留空） */
        public String modId = "";
        /** 显示名（空 = 用 id;HUD 行/编辑器添加页直出,不走 lang） */
        public String displayName = "";
        /** 读取通道:0=Capability 1=静态方法 2=工厂实例 3=Player实例方法 4=手持物品 */
        public int channel = 1;
        /** capability:持有 token 的类;其它通道:类全名（通道 3/4 可空） */
        public String holderClass = "";
        /** capability only:token 的静态字段名 */
        public String holderField = "";
        /** capability only:能力接口全名 */
        public String ifaceClass = "";
        /** capability:值方法名;静态:静态方法名;实例:工厂方法名;通道3:玩家方法名;通道4(NBT):NBT key;通道4(物品方法):方法名 */
        public String readMethod = "";
        /** 实例 only:实例上的值方法名;通道 3 方法链:中间对象上的取值方法名 */
        public String valueMethod = "";
        /** 可选上限方法（同通道语义;失败回退 staticMax/无上限） */
        public String maxMethod = "";
        /** 可选固定上限（≤0 = 无） */
        public float staticMax = 0f;
        /** 条颜色 #RRGGBBAA（空 = 默认灰） */
        public String color = "";
        /** 手持物品通道的取值方式:0=原版耐久 1=NBT整型 2=物品方法 */
        public int itemMode = ITEM_VANILLA_DURABILITY;
        /** 可选:接管目标 HUD 的 overlay id（如 thirst:thirst_level;空 = 不接管） */
        public String overlayId = "";
        /** 是否取消对方原版 HUD（仅 Forge 生效;需保证本模组有条在渲染） */
        public boolean takeover = false;

        /** 按 channel 检查必填字段是否齐全（不齐全 = 配置未完成,适配器自动失效） */
        public boolean paramsComplete() {
            if (isBlank(id) || !ID_PATTERN.matcher(id).matches()) return false;
            if (channel == 4) {
                if (itemMode == ITEM_NBT_INT) return !isBlank(readMethod);
                return itemMode != ITEM_METHOD || !isBlank(readMethod);
            }
            if (channel == 3) return !isBlank(readMethod);
            if (isBlank(holderClass) || isBlank(readMethod)) return false;
            if (channel == 0 && (isBlank(holderField) || isBlank(ifaceClass))) return false;
            return channel != 2 || !isBlank(valueMethod);
        }

        /** 显示名（空回退 id） */
        public String name() {
            return isBlank(displayName) ? id : displayName;
        }

        private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    }
}
