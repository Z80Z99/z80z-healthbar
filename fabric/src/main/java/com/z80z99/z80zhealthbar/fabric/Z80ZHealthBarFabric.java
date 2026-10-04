package com.z80z99.z80zhealthbar.fabric;

import com.z80z99.z80zhealthbar.Z80ZHealthBar;
import com.z80z99.z80zhealthbar.platform.OverheadRenderTypeFactory;
import com.z80z99.z80zhealthbar.platform.fabric.OverheadRenderTypeFactoryV1;
import com.z80z99.z80zhealthbar.platform.PlatformService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * 双入口：main（双端）注入 PlatformService 并执行通用初始化 —— 专用服务器上
 * 吸收/饱和度/疲劳同步包才能真正发出；client 只做渲染相关注册。
 */
public class Z80ZHealthBarFabric implements ModInitializer, ClientModInitializer {

    @Override
    public void onInitialize() {
        PlatformService.setHelper(new FabricPlatformHelper());
        Z80ZHealthBar.init();
    }

    @Override
    public void onInitializeClient() {
        OverheadRenderTypeFactory.Holder.set(new OverheadRenderTypeFactoryV1());
        FabricEventHandlers.register();
    }
}
