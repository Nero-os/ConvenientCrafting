package com.adore.convenientcrafting.network;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.inventory.InventorySorter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 客户端请求服务端整理背包或当前容器的数据包。
 *
 * @param isContainer {@code true} 表示整理当前打开的容器，{@code false} 表示整理玩家背包
 */
public record SortInventoryPacket(boolean isContainer, boolean compactMaterials) implements CustomPacketPayload {
    /**
     * 数据包类型标识。
     */
    public static final Type<SortInventoryPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "sort_inventory")
    );

    /**
     * 背包整理请求的数据包编解码器。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SortInventoryPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SortInventoryPacket::isContainer,
            ByteBufCodecs.BOOL,
            SortInventoryPacket::compactMaterials,
            SortInventoryPacket::new
        );

    /**
     * 获取 NeoForge 自定义数据包类型。
     *
     * @return 当前数据包类型
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 在服务端线程处理整理请求。
     *
     * @param message 客户端发来的整理请求
     * @param context 数据包处理上下文
     */
    public static void handleServer(SortInventoryPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (message.isContainer() ? !Config.ENABLE_CONTAINER_SORTING.get() : !Config.ENABLE_INVENTORY_SORTING.get()) {
                return;
            }

            if (context.player() instanceof ServerPlayer player) {
                if (message.isContainer()) {
                    AbstractContainerMenu menu = player.containerMenu;
                    List<Slot> containerSlots = getSortableContainerSlots(player, menu);
                    for (Slot slot : containerSlots) {
                        if (slot != null && !slot.getItem().isEmpty()) {
                            InventorySorter.sortContainer(new SlotListContainer(containerSlots));
                            break;
                        }
                    }
                } else {
                    Inventory inventory = player.getInventory();
                    boolean compactMaterials = message.compactMaterials() && Config.ENABLE_ALT_MATERIAL_COMPACTION.get();
                    InventorySorter.sortInventory(inventory, player, compactMaterials);
                }

                player.containerMenu.broadcastChanges();
            }
        });
    }

    private static List<Slot> getSortableContainerSlots(ServerPlayer player, AbstractContainerMenu menu) {
        if (!isSortableStorageMenu(menu)) {
            return List.of();
        }

        Inventory inventory = player.getInventory();
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

    /**
     * 把 {@link AbstractContainerMenu} 的槽位列表适配成 {@link net.minecraft.world.Container}。
     *
     * <p>整理器只依赖 {@code Container} 接口，而玩家当前打开的菜单持有的是槽位列表。
     * 该适配器负责把 get、set、remove 等容器操作转发到菜单槽位上。</p>
     */
    private static class SlotListContainer implements net.minecraft.world.Container {
        private final List<Slot> slots;

        /**
         * 创建菜单槽位容器适配器。
         *
         * @param slots 要适配的容器槽位
         */
        public SlotListContainer(List<Slot> slots) {
            this.slots = slots;
        }

        @Override
        public int getContainerSize() {
            return slots.size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public net.minecraft.world.item.ItemStack getItem(int slot) {
            if (slot >= 0 && slot < slots.size()) {
                Slot s = slots.get(slot);
                return s != null ? s.getItem() : net.minecraft.world.item.ItemStack.EMPTY;
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            if (slot >= 0 && slot < slots.size()) {
                Slot s = slots.get(slot);
                if (s != null) {
                    return s.remove(amount);
                }
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
            if (slot >= 0 && slot < slots.size()) {
                Slot s = slots.get(slot);
                if (s != null) {
                    return s.remove(s.getItem().getCount());
                }
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
            if (slot >= 0 && slot < slots.size()) {
                Slot s = slots.get(slot);
                if (s != null) {
                    s.set(stack);
                }
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}
