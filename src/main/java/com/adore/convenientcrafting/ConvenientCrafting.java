package com.adore.convenientcrafting;

import org.slf4j.Logger;

import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.command.RecipeUnlockCommands;
import com.adore.convenientcrafting.event.BagPickupEvents;
import com.adore.convenientcrafting.network.CraftRecipePacket;
import com.adore.convenientcrafting.network.NestedCraftingMissingMaterialsPacket;
import com.adore.convenientcrafting.network.RecipeUnlockSyncPacket;
import com.adore.convenientcrafting.network.SortInventoryPacket;
import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;
import com.adore.convenientcrafting.registry.ModBlocks;
import com.adore.convenientcrafting.registry.ModCreativeModeTabs;
import com.adore.convenientcrafting.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Convenient Crafting 模组的公共入口类。TODO：配置文件、收纳袋shift显示内部 bug:酿造台配方默认解锁了，其他工作方块配方解锁不了了
 *
 * <p>负责注册方块、物品、创造模式标签、配置项和客户端到服务端的数据包处理器。</p>
 */
@Mod(ConvenientCrafting.MODID)
public class ConvenientCrafting {
    /**
     * 模组 ID，用于注册资源位置、配置和事件订阅。
     */
    public static final String MODID = "convenientcrafting";

    /**
     * 模组日志记录器。
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 创建模组入口并注册生命周期监听器。
     *
     * @param modEventBus 模组事件总线
     * @param modContainer 当前模组容器
     */
    public ConvenientCrafting(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPackets);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new BagPickupEvents());
        NeoForge.EVENT_BUS.register(new RecipeUnlocks());
        NeoForge.EVENT_BUS.register(new RecipeUnlockCommands());

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * 注册客户端发往服务端的自定义数据包。
     *
     * @param event 数据包处理器注册事件
     */
    private void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            SortInventoryPacket.TYPE,
            SortInventoryPacket.STREAM_CODEC,
            SortInventoryPacket::handleServer
        );
        registrar.playToServer(
            CraftRecipePacket.TYPE,
            CraftRecipePacket.STREAM_CODEC,
            CraftRecipePacket::handleServer
        );
        registrar.playToClient(
            RecipeUnlockSyncPacket.TYPE,
            RecipeUnlockSyncPacket.STREAM_CODEC,
            RecipeUnlockSyncPacket::handleClient
        );
        registrar.playToClient(
            NestedCraftingMissingMaterialsPacket.TYPE,
            NestedCraftingMissingMaterialsPacket.STREAM_CODEC,
            NestedCraftingMissingMaterialsPacket::handleClient
        );
    }

    /**
     * 执行公共初始化逻辑。
     *
     * @param event 公共初始化事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Convenient Crafting common setup complete.");
    }

    /**
     * 服务器启动时的调试日志事件。
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
