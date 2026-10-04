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
