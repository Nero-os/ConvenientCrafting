package com.adore.smartbundle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = SmartBundle.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SmartBundle.MODID, value = Dist.CLIENT)
public class SmartBundleClient {
    public SmartBundleClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        SmartBundle.LOGGER.info("HELLO FROM CLIENT SETUP");
        SmartBundle.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

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
