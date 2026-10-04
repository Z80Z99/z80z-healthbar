package com.z80z99.z80zhealthbar.forge;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.gui.ModSettingsScreen;
import com.z80z99.z80zhealthbar.platform.OverheadRenderTypeFactory;
import com.z80z99.z80zhealthbar.platform.forge.OverheadRenderTypeFactoryV1;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;

@Mod(Z80ZHealthBar.MOD_ID)
public class Z80ZHealthBarForge {

    public Z80ZHealthBarForge() {
        PlatformService.setHelper(new ForgePlatformHelper());
        OverheadRenderTypeFactory.Holder.set(new OverheadRenderTypeFactoryV1());
        Z80ZHealthBar.init();

        // 注册设置界面到 Forge Mod 列表的 Config 按钮（不依赖 Cloth Config）
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, screen) -> ModSettingsScreen.create(screen)
                )
        );
    }
}
