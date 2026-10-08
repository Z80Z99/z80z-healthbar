package com.z80z99.z80zhealthbar.config.configs;

public class CompatConfig {
    public boolean hookToughAsNails = true;
    public boolean hookThirstWasTaken = true;
    public boolean hookMekanism = true;
    public boolean hookDehydration = true;
    public boolean hookParcool = true;
    public boolean hookIronsSpellbooks = true;
    public boolean hookFeathers = true;
    public boolean hookAppleSkin = true;
    public boolean hookSuperiorShields = true;
    public boolean hookLightShield = true;
    public boolean hookVampirism = true;
    public boolean hookHomeostatic = true;
    public boolean hookBotania = true;
    public boolean hookOrigins = true;
    public boolean hookTFC = true;
    public boolean hookArsNouveau = true;
    public boolean hookApoli = true;
    public boolean hookThermoo = true;
    public boolean hookMealApi = true;
    public boolean hookLegendarySurvivalOverhaul = true;

    // --- 原版 HUD 接管（2026-10-08 用户需求:检测到 mod 则顶掉它的 HUD）---
    // 开启后取消对方 overlay 渲染（Forge RenderGuiOverlayEvent.Pre）,由本模组的数据组件接管显示。
    // 安全网:仅当本模组确实会绘制该数据（hook 开 + 数据通道兼容 + 当前 HUD 风格会显示）时才取消。
    /** 接管 ThirstWasTaken 的水滴条 HUD */
    public boolean takeoverThirst = false;
    /** 接管 ParCool 的体力 HUD */
    public boolean takeoverParcool = false;

    public boolean isHookEnabled(String modId) {
        return switch (modId) {
            case "toughasnails" -> hookToughAsNails;
            case "thirst" -> hookThirstWasTaken;
            case "mekanism" -> hookMekanism;
            case "dehydration" -> hookDehydration;
            case "parcool" -> hookParcool;
            case "irons_spellbooks" -> hookIronsSpellbooks;
            case "feathers" -> hookFeathers;
            case "appleskin" -> hookAppleSkin;
            case "superiorshields" -> hookSuperiorShields;
            case "lightshield" -> hookLightShield;
            case "vampirism" -> hookVampirism;
            case "homeostatic" -> hookHomeostatic;
            case "botania" -> hookBotania;
            case "origins" -> hookOrigins;
            case "tfc" -> hookTFC;
            case "ars_nouveau" -> hookArsNouveau;
            case "apoli" -> hookApoli;
            case "thermoo" -> hookThermoo;
            case "mealapi" -> hookMealApi;
            case "legendarysurvivaloverhaul" -> hookLegendarySurvivalOverhaul;
            default -> false;
        };
    }
}
