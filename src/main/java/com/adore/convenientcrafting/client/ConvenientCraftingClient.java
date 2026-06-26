package com.adore.convenientcrafting.client;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.client.screen.CraftHelperScreen;
import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.item.CategorizedBagItem;
import com.adore.convenientcrafting.network.SortInventoryPacket;
import com.adore.convenientcrafting.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Convenient Crafting 的客户端入口类。
 *
 * <p>负责注册配置界面、快捷键、按键监听，以及在玩家背包界面中注入整理按钮。</p>
 */
@Mod(value = ConvenientCrafting.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ConvenientCrafting.MODID, value = Dist.CLIENT)
public class ConvenientCraftingClient {
    private static final ResourceLocation FILLED_PROPERTY = ResourceLocation.withDefaultNamespace("filled");
    private static final int PLAYER_INVENTORY_LAST_COLUMN_RIGHT = 168;
    private static final int PLAYER_INVENTORY_TOP = 84;
    private static final int SORT_BUTTON_SIZE = 12;

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
        event.enqueueWork(ConvenientCraftingClient::registerBagItemProperties);
    }

    private static void registerBagItemProperties() {
        ItemProperties.register(ModItems.SEED_BAG.get(), FILLED_PROPERTY, (stack, level, entity, seed) -> hasBagContents(stack) ? 1.0F : 0.0F);
        ItemProperties.register(ModItems.DYE_BAG.get(), FILLED_PROPERTY, (stack, level, entity, seed) -> hasBagContents(stack) ? 1.0F : 0.0F);
        ItemProperties.register(ModItems.MINERAL_BAG.get(), FILLED_PROPERTY, (stack, level, entity, seed) -> hasBagContents(stack) ? 1.0F : 0.0F);
    }

    private static boolean hasBagContents(ItemStack stack) {
        for (ItemStack content : CategorizedBagItem.getContents(stack)) {
            if (!content.isEmpty()) {
                return true;
            }
        }
        return false;
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

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            CraftHelperScreen.clearRecipeIndexCache();
            return;
        }

        if (mc.screen != null) {
            CraftHelperScreen.tickRecipeIndexPreload();
            return;
        }

        CraftHelperScreen.preloadRecipeIndex();
        CraftHelperScreen.tickRecipeIndexPreload();
    }

    @SubscribeEvent
    static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftHelperScreen.clearRecipeIndexCache();
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

        if (isInventorySortingEnabled() && screen instanceof InventoryScreen inventoryScreen) {
            int guiLeft = inventoryScreen.getGuiLeft();
            int guiTop = inventoryScreen.getGuiTop();
            int x = guiLeft + PLAYER_INVENTORY_LAST_COLUMN_RIGHT - SORT_BUTTON_SIZE;
            int y = guiTop + PLAYER_INVENTORY_TOP - SORT_BUTTON_SIZE - 2;

            event.addListener(new SortInventoryButton(x, y, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE, false));
            return;
        }

        if (isContainerSortingEnabled() && screen instanceof AbstractContainerScreen<?> containerScreen) {
            List<Slot> containerSlots = getSortableContainerSlots(containerScreen.getMenu());
            if (!containerSlots.isEmpty()) {
                int guiLeft = containerScreen.getGuiLeft();
                int guiTop = containerScreen.getGuiTop();
                int maxSlotRight = containerSlots.stream()
                        .mapToInt(slot -> slot.x + 16)
                        .max()
                        .orElse(0);
                int topSlotY = containerSlots.stream()
                        .mapToInt(slot -> slot.y)
                        .min()
                        .orElse(0);
                int x = guiLeft + maxSlotRight - SORT_BUTTON_SIZE;
                int y = guiTop + topSlotY - SORT_BUTTON_SIZE - 2;

                event.addListener(new SortInventoryButton(x, y, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE, true));
            }
        }
    }

    @SubscribeEvent
    static void onInventoryMiddleClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return;
        }

        Screen screen = event.getScreen();
        if (isInventorySortingEnabled() && screen instanceof InventoryScreen) {
            sendSortInventoryPacket(false, Screen.hasAltDown());
            event.setCanceled(true);
        } else if (isContainerSortingEnabled() && screen instanceof AbstractContainerScreen<?> containerScreen && !getSortableContainerSlots(containerScreen.getMenu()).isEmpty()) {
            sendSortInventoryPacket(true, Screen.hasAltDown());
            event.setCanceled(true);
        }
    }

    private static void sendSortInventoryPacket(boolean sortContainer, boolean compactMaterials) {
        if (sortContainer ? !isContainerSortingEnabled() : !isInventorySortingEnabled()) {
            return;
        }

        boolean shouldCompactMaterials = !sortContainer && compactMaterials && isAltMaterialCompactionEnabled();
        PacketDistributor.sendToServer(new SortInventoryPacket(sortContainer, shouldCompactMaterials));
    }

    private static boolean isInventorySortingEnabled() {
        return Config.ENABLE_INVENTORY_SORTING.get();
    }

    private static boolean isContainerSortingEnabled() {
        return Config.ENABLE_CONTAINER_SORTING.get();
    }

    private static boolean isAltMaterialCompactionEnabled() {
        return Config.ENABLE_ALT_MATERIAL_COMPACTION.get();
    }

    private static List<Slot> getSortableContainerSlots(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isSortableStorageMenu(menu)) {
            return List.of();
        }

        Inventory inventory = mc.player.getInventory();
        return menu.slots.stream()
                .filter(slot -> slot.container != inventory)
                .toList();
    }

    private static boolean isSortableStorageMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu
                || menu instanceof DispenserMenu;
    }

    private static class SortInventoryButton extends Button {
        private SortInventoryButton(int x, int y, int width, int height, boolean sortContainer) {
            super(
                    x,
                    y,
                    width,
                    height,
                    Component.translatable(sortContainer ? "button.convenientcrafting.sort_container" : "button.convenientcrafting.sort"),
                    button -> sendSortInventoryPacket(sortContainer, Screen.hasAltDown()),
                    DEFAULT_NARRATION
            );
            setTooltip(Tooltip.create(Component.translatable(sortContainer ? "tooltip.convenientcrafting.sort_container" : "tooltip.convenientcrafting.sort_inventory")));
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered();
            int border = hovered ? 0xFFFFFFFF : 0xFF6B6B6B;
            int top = hovered ? 0xFFB8C6B3 : 0xFF8F9B8A;
            int middle = hovered ? 0xFF5A6A56 : 0xFF3D493A;
            int shadow = 0xFF171A17;
            int icon = hovered ? 0xFFFFFFFF : 0xFFE5E5E5;
            int iconShadow = 0xFF1F241F;

            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();

            guiGraphics.fill(x, y, right, bottom, shadow);
            guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, border);
            guiGraphics.fill(x + 2, y + 2, right - 2, bottom - 2, middle);
            guiGraphics.fill(x + 2, y + 2, right - 2, y + 3, top);

            drawSortLine(guiGraphics, x + 4, y + 5, 4, iconShadow);
            drawSortLine(guiGraphics, x + 4, y + 7, 3, iconShadow);
            drawSortLine(guiGraphics, x + 4, y + 9, 5, iconShadow);
            drawSortLine(guiGraphics, x + 3, y + 4, 4, icon);
            drawSortLine(guiGraphics, x + 3, y + 6, 3, icon);
            drawSortLine(guiGraphics, x + 3, y + 8, 5, icon);

            guiGraphics.fill(x + 8, y + 4, x + 9, y + 5, icon);
            guiGraphics.fill(x + 7, y + 6, x + 8, y + 7, icon);
            guiGraphics.fill(x + 9, y + 8, x + 10, y + 9, icon);
        }

        private static void drawSortLine(GuiGraphics guiGraphics, int x, int y, int width, int color) {
            guiGraphics.fill(x, y, x + width, y + 1, color);
        }
    }
}
