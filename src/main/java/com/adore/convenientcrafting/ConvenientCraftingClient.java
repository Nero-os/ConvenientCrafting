package com.adore.convenientcrafting;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Convenient Crafting 的客户端入口类。
 *
 * <p>负责注册配置界面、快捷键、按键监听，以及在玩家背包界面中注入整理按钮。</p>
 */
@Mod(value = ConvenientCrafting.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ConvenientCrafting.MODID, value = Dist.CLIENT)
public class ConvenientCraftingClient {

    /**
     * 打开合成助手界面的快捷键，默认绑定为 G。
     */
    private static final Lazy<net.minecraft.client.KeyMapping> OPEN_CRAFT_HELPER_KEY = Lazy.of(() ->
        new net.minecraft.client.KeyMapping(
            "key.convenientcrafting.open_craft_helper",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.convenientcrafting"
        )
    );

    /**
     * 创建客户端入口并注册配置界面工厂。
     *
     * @param container 当前模组容器
     */
    public ConvenientCraftingClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * 客户端初始化事件，用于输出调试日志。
     *
     * @param event 客户端初始化事件
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ConvenientCrafting.LOGGER.info("HELLO FROM CLIENT SETUP");
        ConvenientCrafting.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    /**
     * 注册打开合成助手的快捷键。
     *
     * @param event 快捷键注册事件
     */
    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CRAFT_HELPER_KEY.get());
    }

    /**
     * 监听客户端按键输入并打开合成助手界面。
     *
     * @param event 键盘输入事件
     */
    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        if (OPEN_CRAFT_HELPER_KEY.get().consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.setScreen(new CraftHelperScreen());
            }
        }
    }

    /**
     * 在箱子等容器界面中监听快捷键并打开合成助手。
     *
     * @param event 界面按键事件
     */
    @SubscribeEvent
    static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (OPEN_CRAFT_HELPER_KEY.get().matches(event.getKeyCode(), event.getScanCode())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                mc.setScreen(new CraftHelperScreen());
                event.setCanceled(true);
            }
        }
    }

    /**
     * 在原版玩家背包界面初始化后添加整理按钮。
     *
     * @param event 界面初始化完成事件
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();

        if (screen instanceof InventoryScreen inventoryScreen) {
            int buttonWidth = 10;
            int buttonHeight = 10;
            int guiLeft = (inventoryScreen.width - 176) / 2;
            int guiTop = (inventoryScreen.height - 166) / 2;
            int x = guiLeft + 140;
            int y = guiTop + 64;

            Button sortButton = Button.builder(
                            Component.literal("⇄"), // or ☰
                            btn -> PacketDistributor.sendToServer(new SortInventoryPacket(false))
                    )
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();

            event.addListener(sortButton);
        }
    }
}
