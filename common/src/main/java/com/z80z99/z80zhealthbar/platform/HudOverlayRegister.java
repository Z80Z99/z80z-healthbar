package com.z80z99.z80zhealthbar.platform;

/**
 * HUD overlay 注册机制隔离层。
 *
 * <p>V1 (1.20.1):
 * <ul>
 *   <li>Forge: {@code RenderGuiOverlayEvent.Pre} 取消原版条 + {@code RenderGuiOverlayEvent.Post} 自绘</li>
 *   <li>Fabric: {@code HudRenderCallback.EVENT}</li>
 * </ul>
 * 当前 V1 的注册在各平台 {@code ForgeClientEvents} / {@code FabricEventHandlers} 内联完成，
 * 不通过本接口；本接口作为 V1.5 切换 NeoForge {@code RenderFrameEvent} 时的预留扩展点。
 *
 * <p>V1.5 (1.21.x): NeoForge 已移除 {@code IGuiOverlay}/{@code RegisterGuiOverlaysEvent}，
 * 需改用 {@code RenderFrameEvent} 或 mixin 注入 {@code Gui.render}，
 * 实现通过 {@link Holder#set(HudOverlayRegister)} 注入。
 */
public interface HudOverlayRegister {

    /** 注册玩家 HUD 渲染入口（在 mod 初始化期调用一行）。 */
    void registerHudRenderer();

    /** 注入点：沿用 PlatformService 模式。 */
    final class Holder {
        private static HudOverlayRegister register;

        private Holder() {}

        public static HudOverlayRegister get() {
            if (register == null) {
                throw new IllegalStateException(
                        "HudOverlayRegister not set; call Holder.set() in platform init");
            }
            return register;
        }

        public static void set(HudOverlayRegister register) {
            if (Holder.register != null) {
                throw new IllegalStateException("HudOverlayRegister already set");
            }
            Holder.register = register;
        }
    }
}